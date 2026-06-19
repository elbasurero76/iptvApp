package com.marcosrava.iptvplayer.network

import com.marcosrava.iptvplayer.data.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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

            val files = parseDirectoryListing(html, normalizedUrl, serverHeader)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDirectoryListing(html: String, baseUrl: String, serverHeader: String): List<RemoteFile> {
        val doc = Jsoup.parse(html)
        val files = mutableListOf<RemoteFile>()

        // Detectar tipo de servidor por header HTTP (más fiable que el HTML)
        val isPython = serverHeader.contains("SimpleHTTP", ignoreCase = true) ||
                serverHeader.contains("Python", ignoreCase = true) ||
                html.contains("Directory listing for", ignoreCase = true)
        val isNginx  = serverHeader.contains("nginx", ignoreCase = true)
        val isApache = serverHeader.contains("Apache", ignoreCase = true) ||
                html.contains("Index of", ignoreCase = true)

        // Seleccionar los enlaces según el tipo de servidor
        val rawLinks = when {
            isPython -> doc.select("ul li a, ol li a").ifEmpty { doc.select("a[href]") }
            isApache -> doc.select("tr td a, pre a").ifEmpty { doc.select("a[href]") }
            else     -> doc.select("a[href]")  // nginx, genérico
        }

        rawLinks.forEach { link ->
            val href = link.attr("href")

            // Ignorar navegación y anchors
            if (href.isBlank() || href == "/" || href == "./" || href == "../"
                || href.startsWith("#") || href.startsWith("?")) return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank() || name == "Parent Directory" || name == "..") return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = buildAbsoluteUrl(href, baseUrl)

            // Mostrar TODOS los archivos y carpetas (sin filtrar por extensión)
            // así el usuario ve todo lo que hay en el servidor
            files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
        }

        return files.sortedWith(
            compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
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
