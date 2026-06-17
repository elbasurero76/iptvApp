package com.marcosrava.iptvplayer.parser

import com.marcosrava.iptvplayer.data.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3UParser @Inject constructor() {

    companion object {
        private const val EXTINF = "#EXTINF:"
        private const val EXT_X_STREAM_INF = "#EXT-X-STREAM-INF:"

        // Atributos M3U extendidos
        private val TVG_ID = Regex("""tvg-id="([^"]*)" """)
        private val TVG_NAME = Regex("""tvg-name="([^"]*)" """)
        private val TVG_LOGO = Regex("""tvg-logo="([^"]*)" """)
        private val GROUP_TITLE = Regex("""group-title="([^"]*)" """)
        private val CATCHUP_SOURCE = Regex("""catchup-source="([^"]*)" """)
        private val CATCHUP_DAYS = Regex("""catchup-days="(\d+)" """)
        private val USER_AGENT = Regex("""user-agent="([^"]*)" """)
        private val REFERRER = Regex("""referrer="([^"]*)" """)
        private val CHANNEL_NAME = Regex(""",[^,\n]*$""")
    }

    fun parse(content: String, playlistId: Long): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith(EXTINF) || line.startsWith(EXT_X_STREAM_INF)) {
                // Parsear metadatos
                val tvgId = TVG_ID.find("$line ")?.groupValues?.get(1)
                val tvgName = TVG_NAME.find("$line ")?.groupValues?.get(1)
                val logoUrl = TVG_LOGO.find("$line ")?.groupValues?.get(1)?.ifBlank { null }
                val group = GROUP_TITLE.find("$line ")?.groupValues?.get(1) ?: ""
                val catchupSource = CATCHUP_SOURCE.find("$line ")?.groupValues?.get(1)
                val catchupDays = CATCHUP_DAYS.find("$line ")?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val userAgent = USER_AGENT.find("$line ")?.groupValues?.get(1)
                val referrer = REFERRER.find("$line ")?.groupValues?.get(1)

                // Extraer nombre del canal (después de la última coma)
                val channelName = line.substringAfterLast(",").trim()
                    .ifBlank { tvgName ?: "Canal ${channels.size + 1}" }

                // Buscar URL en la siguiente línea (puede haber líneas vacías o de comentario)
                var j = i + 1
                var url = ""
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                        url = nextLine
                        i = j
                        break
                    }
                    j++
                }

                if (url.isNotBlank() && (url.startsWith("http") || url.startsWith("rtmp") || url.startsWith("rtsp"))) {
                    channels.add(
                        Channel(
                            playlistId = playlistId,
                            name = channelName,
                            url = url,
                            logoUrl = logoUrl,
                            group = group,
                            tvgId = tvgId,
                            tvgName = tvgName,
                            catchupSource = catchupSource,
                            catchupDays = catchupDays,
                            userAgent = userAgent,
                            referrer = referrer
                        )
                    )
                }
            }
            i++
        }

        return channels
    }
}
