package com.marcosrava.iptvplayer.network

import com.marcosrava.iptvplayer.data.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpFileBrowser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun listFiles(url: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = if (url.endsWith("/")) url else "$url/"
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "IPTVPlayer/1.0 Android")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val serverHeader = response.header("Server") ?: ""
            val html = response.use { r ->
                if (!r.isSuccessful) throw Exception("HTTP ${r.code}: ${r.message}")
                r.body?.string() ?: throw Exception("Respuesta vacía")
            }

            val files = parseHtml(html, normalizedUrl, serverHeader)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHtml(html: String, baseUrl: String, serverHeader: String): List<RemoteFile> {
        val doc = Jsoup.parse(html)
        val files = mutableListOf<RemoteFile>()
        val seen = mutableSetOf<String>()

        val isPythonOrApache = serverHeader.contains("SimpleHTTP", ignoreCase = true) ||
                serverHeader.contains("Python", ignoreCase = true) ||
                serverHeader.contains("Apache", ignoreCase = true) ||
                html.contains("Directory listing for", ignoreCase = true) ||
                html.contains("Index of", ignoreCase = true)

        // ── ESTRATEGIA 1: links <a href> que apuntan a .m3u/.m3u8 ────────
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript")) return@forEach

            val absUrl = buildAbsoluteUrl(href, baseUrl)
            if (absUrl in seen) return@forEach

            val isM3u = href.contains(".m3u", ignoreCase = true)
            val isDir = href.endsWith("/") && !href.startsWith("?")

            if (isM3u) {
                seen.add(absUrl)
                val name = resolveNameForLink(link, href)
                files.add(RemoteFile(name = name, url = absUrl, isDirectory = false))
            } else if (isDir && isPythonOrApache) {
                // En listados de directorio, incluir carpetas
                val name = link.text().trim().trimEnd('/')
                if (name.isNotBlank() && name != "Parent Directory" && name != "..") {
                    seen.add(absUrl)
                    files.add(RemoteFile(name = name, url = absUrl, isDirectory = true))
                }
            }
        }

        // ── ESTRATEGIA 2: data-url / data-href con .m3u (botones JS) ─────
        val dataAttrs = listOf("data-url", "data-href", "data-src", "data-link", "data-file")
        doc.allElements.forEach { el ->
            dataAttrs.forEach { attr ->
                val value = el.attr(attr)
                if (value.contains(".m3u", ignoreCase = true)) {
                    val absUrl = buildAbsoluteUrl(value, baseUrl)
                    if (absUrl !in seen) {
                        seen.add(absUrl)
                        // Buscar nombre en el contenedor del elemento
                        val name = findNameInContainer(el)
                            .ifBlank { value.substringAfterLast("/").substringBefore("?") }
                        files.add(RemoteFile(name = name, url = absUrl, isDirectory = false))
                    }
                }
            }
        }

        // ── ESTRATEGIA 3: URLs .m3u en texto plano del HTML ──────────────
        if (files.isEmpty()) {
            val urlRegex = Regex("""https?://[^\s"'<>]+\.m3u8?""", RegexOption.IGNORE_CASE)
            urlRegex.findAll(html).forEach { match ->
                val absUrl = match.value
                if (absUrl !in seen) {
                    seen.add(absUrl)
                    val name = absUrl.substringAfterLast("/").substringBefore("?")
                    files.add(RemoteFile(name = name, url = absUrl, isDirectory = false))
                }
            }
        }

        return files.sortedWith(
            compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    /** Encuentra el nombre más descriptivo para un link <a href="...m3u"> */
    private fun resolveNameForLink(link: Element, href: String): String {
        val linkText = link.text().trim()

        // Si el texto del link es útil (no solo "Descargar"/"Download"/iconos)
        val skipWords = setOf("descargar", "download", "ver", "play", "open", "abrir", "get")
        if (linkText.isNotBlank() && linkText.lowercase() !in skipWords && linkText.length > 2) {
            return linkText
        }

        // Buscar heading en el contenedor padre (card, li, tr, article...)
        val containerName = findNameInContainer(link)
        if (containerName.isNotBlank()) return containerName

        // title attribute del link
        val title = link.attr("title").trim()
        if (title.isNotBlank()) return title

        // Nombre del archivo desde la URL
        return href.substringAfterLast("/").substringBefore("?")
            .replace(".m3u8", "").replace(".m3u", "")
            .replace("-", " ").replace("_", " ")
            .trim().ifBlank { href }
    }

    /** Busca el heading/título más cercano al elemento dentro de su contenedor */
    private fun findNameInContainer(el: Element): String {
        val containers = listOf(
            ".card", ".playlist", ".item", ".list-item",
            "article", "section", "li", "tr", ".row", "div[class]"
        )
        for (selector in containers) {
            val container = el.closest(selector) ?: continue
            val heading = container.select(
                "h1, h2, h3, h4, h5, h6, strong, b, .title, .name, .playlist-name, [class*=title], [class*=name]"
            ).firstOrNull()
            val text = heading?.text()?.trim() ?: continue
            if (text.isNotBlank() && text.length > 1) return text
        }
        return ""
    }

    private fun buildAbsoluteUrl(href: String, baseUrl: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> runCatching {
            val base = java.net.URL(baseUrl)
            "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$href"
        }.getOrDefault("$baseUrl$href")
        else -> "$baseUrl$href"
    }

    suspend fun downloadFile(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVPlayer/1.0 Android")
                .build()
            val content = okHttpClient.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
                r.body?.string() ?: throw Exception("Respuesta vacía")
            }
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
