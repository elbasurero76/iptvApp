package com.marcosrava.iptvplayer.network

import com.marcosrava.iptvplayer.data.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navega por el servidor de archivos HTTP de Ubuntu.
 *
 * En Ubuntu, ejecuta: python3 -m http.server 8080
 * desde el directorio con tus listas M3U.
 *
 * También compatible con nginx autoindex, Apache directory listing.
 */
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

            val html = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
                response.body?.string() ?: throw Exception("Respuesta vacía")
            }

            val files = parseDirectoryListing(html, normalizedUrl)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDirectoryListing(html: String, baseUrl: String): List<RemoteFile> {
        val doc = Jsoup.parse(html)
        val files = mutableListOf<RemoteFile>()

        // Detectar tipo de servidor y parsear en consecuencia
        when {
            isPythonHttpServer(html) -> parsePythonHttpServer(doc, baseUrl, files)
            isNginxAutoindex(html) -> parseNginxAutoindex(doc, baseUrl, files)
            isApacheAutoindex(html) -> parseApacheAutoindex(doc, baseUrl, files)
            else -> parseGenericListing(doc, baseUrl, files)
        }

        return files.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })
    }

    private fun isPythonHttpServer(html: String) =
        html.contains("python", ignoreCase = true) || html.contains("SimpleHTTP", ignoreCase = true)

    private fun isNginxAutoindex(html: String) =
        html.contains("nginx", ignoreCase = true)

    private fun isApacheAutoindex(html: String) =
        html.contains("apache", ignoreCase = true) || html.contains("Index of", ignoreCase = true)

    private fun parsePythonHttpServer(doc: org.jsoup.nodes.Document, baseUrl: String, files: MutableList<RemoteFile>) {
        doc.select("a").forEach { link ->
            val href = link.attr("href")
            if (href == "../" || href == "./") return@forEach
            if (href.startsWith("?") || href.startsWith("/") && !href.startsWith(baseUrl)) return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank()) return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun parseNginxAutoindex(doc: org.jsoup.nodes.Document, baseUrl: String, files: MutableList<RemoteFile>) {
        doc.select("a").forEach { link ->
            val href = link.attr("href")
            if (href == "../" || href == "./") return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank()) return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun parseApacheAutoindex(doc: org.jsoup.nodes.Document, baseUrl: String, files: MutableList<RemoteFile>) {
        doc.select("tr td a").forEach { link ->
            val href = link.attr("href")
            if (href.contains("?") || href == "/") return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank() || name == "Parent Directory") return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl$href"

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun parseGenericListing(doc: org.jsoup.nodes.Document, baseUrl: String, files: MutableList<RemoteFile>) {
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")
            if (href.startsWith("#") || href.startsWith("?")) return@forEach
            if (href == "../" || href == "./") return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank()) return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = when {
                href.startsWith("http") -> href
                href.startsWith("/") -> {
                    val base = java.net.URL(baseUrl)
                    "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$href"
                }
                else -> "$baseUrl$href"
            }

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun isMediaFile(filename: String): Boolean {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return ext in setOf("m3u", "m3u8", "ts", "mp4", "mkv", "avi", "xml", "gz", "txt")
    }

    suspend fun downloadFile(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVPlayer/1.0 Android")
                .build()
            val content = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                response.body?.string() ?: throw Exception("Respuesta vacía")
            }
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
