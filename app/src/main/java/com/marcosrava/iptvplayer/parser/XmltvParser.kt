package com.marcosrava.iptvplayer.parser

import android.util.Xml
import com.marcosrava.iptvplayer.data.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XmltvParser @Inject constructor() {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
    )

    fun parse(content: String): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            var currentProgram: ProgramBuilder? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                currentProgram = ProgramBuilder().apply {
                                    channelId = parser.getAttributeValue(null, "channel") ?: ""
                                    startTime = parseDate(parser.getAttributeValue(null, "start"))
                                    endTime = parseDate(parser.getAttributeValue(null, "stop"))
                                }
                            }
                            "title" -> {
                                if (currentProgram != null) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        currentProgram.title = parser.text ?: ""
                                    }
                                }
                            }
                            "desc" -> {
                                if (currentProgram != null) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        currentProgram.description = parser.text ?: ""
                                    }
                                }
                            }
                            "category" -> {
                                if (currentProgram != null) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        currentProgram.category = parser.text ?: ""
                                    }
                                }
                            }
                            "icon" -> {
                                if (currentProgram != null) {
                                    currentProgram.iconUrl = parser.getAttributeValue(null, "src")
                                }
                            }
                            "rating" -> {
                                if (currentProgram != null) {
                                    currentProgram.rating = parser.getAttributeValue(null, "system")
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme" && currentProgram != null) {
                            val program = currentProgram.build()
                            if (program != null) programs.add(program)
                            currentProgram = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return programs
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {}
        }
        return 0L
    }

    private inner class ProgramBuilder {
        var channelId: String = ""
        var title: String = ""
        var description: String = ""
        var startTime: Long = 0L
        var endTime: Long = 0L
        var category: String = ""
        var iconUrl: String? = null
        var rating: String? = null

        fun build(): EpgProgram? {
            if (channelId.isBlank() || title.isBlank() || startTime == 0L) return null
            return EpgProgram(
                channelTvgId = channelId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                category = category,
                iconUrl = iconUrl,
                rating = rating
            )
        }
    }
}
