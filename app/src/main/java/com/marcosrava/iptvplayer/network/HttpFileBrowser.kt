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
 * En Ubuntu: python3 -m http.server 8080
 * Compatible con Python 3.x, nginx autoindex, Apache directory listing.
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

    private fun parseDirectoryListing(
        html: String,
        baseUrl: String,
        serverHeader: String
    ): List<RemoteFile> {
        val doc = Jsoup.parse(html)
        val files = mutableListOf<RemoteFile>()

        when {
            // Python http.server se identifica por el header "Server: SimpleHTTP/x Python/x"
            // o por "Directory listing for" en el título del HTML
            serverHeader.contains("SimpleHTTP", ignoreCase = true) ||
            serverHeader.contains("Python", ignoreCase = true) ||
            html.contains("Directory listing for", ignoreCase = true) ->
                parsePythonHttpServer(doc, baseUrl, files)

            html.contains("nginx", ignoreCase = true) ->
                parseNginxAutoindex(doc, baseUrl, files)

            html.contains("apache", ignoreCase = true) ||
            html.contains("Index of", ignoreCase = true) ->
                parseApacheAutoindex(doc, baseUrl, files)

            else -> parseGenericListing(doc, baseUrl, files)
        }

        return files.sortedWith(
            compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    /**
     * Python 3 http.server genera HTML con <ul><li><a href="...">nombre</a></li></ul>
     * En Python 3.12+ el formato es igual pero con charset UTF-8.
     */
    private fun parsePythonHttpServer(
        doc: org.jsoup.nodes.Document,
        baseUrl: String,
        files: MutableList<RemoteFile>
    ) {
        // Python pone los archivos en <ul> → <li> → <a>
        // Intentar primero con la estructura <li><a>
        val liLinks = doc.select("ul li a, ol li a")
        val links = if (liLinks.isNotEmpty()) liLinks else doc.select("a[href]")

        links.forEach { link ->
            val href = link.attr("href")
            // Saltar navegación hacia atrás y anchors
            if (href == "../" || href == "./" || href == "/" || href.startsWith("#") || href.startsWith("?")) return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank()) return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = buildAbsoluteUrl(href, baseUrl)

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun parseNginxAutoindex(
        doc: org.jsoup.nodes.Document,
        baseUrl: String,
        files: MutableList<RemoteFile>
    ) {
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")
            if (href == "../" || href == "./" || href.startsWith("?")) return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank()) return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = buildAbsoluteUrl(href, baseUrl)

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun parseApacheAutoindex(
        doc: org.jsoup.nodes.Document,
        baseUrl: String,
        files: MutableList<RemoteFile>
    ) {
        doc.select("tr td a, pre a").forEach { link ->
            val href = link.attr("href")
            if (href.contains("?") || href == "/" || href == "../") return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank() || name == "Parent Directory" || name == "..") return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = buildAbsoluteUrl(href, baseUrl)

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun parseGenericListing(
        doc: org.jsoup.nodes.Document,
        baseUrl: String,
        files: MutableList<RemoteFile>
    ) {
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")
            if (href.startsWith("#") || href.startsWith("?")) return@forEach
            if (href == "../" || href == "./" || href == "/") return@forEach

            val name = link.text().trim().trimEnd('/')
            if (name.isBlank()) return@forEach

            val isDir = href.endsWith("/")
            val absoluteUrl = buildAbsoluteUrl(href, baseUrl)

            if (isDir || isMediaFile(name)) {
                files.add(RemoteFile(name = name, url = absoluteUrl, isDirectory = isDir))
            }
        }
    }

    private fun buildAbsoluteUrl(href: String, baseUrl: String): String {
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("/") -> {
                runCatching {
                    val base = java.net.URL(baseUrl)
                    "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$href"
                }.getOrDefault("$baseUrl$href")
            }
            else -> "$baseUrl$href"
        }
    }

    private fun isMediaFile(filename: String): Boolean {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return ext in setOf("m3u", "m3u8", "ts", "mp4", "mkv", "avi", "xml", "gz", "txt", "strm")
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
