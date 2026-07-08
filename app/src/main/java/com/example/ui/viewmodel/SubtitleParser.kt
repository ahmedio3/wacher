package com.example.ui.viewmodel

import java.io.File
import java.util.regex.Pattern

data class SubtitleLine(
    val startTime: Long,
    val endTime: Long,
    val text: String
)

object SubtitleParser {
    fun parseBlock(file: File): List<SubtitleLine> {
        val lines = mutableListOf<SubtitleLine>()
        if (!file.exists()) return lines

        val content = file.readText()
        if (file.name.endsWith(".srt")) {
            lines.addAll(parseSrt(content))
        } else if (file.name.endsWith(".vtt")) {
            lines.addAll(parseVtt(content))
        } else if (file.name.endsWith(".ass") || file.name.endsWith(".ssa")) {
            lines.addAll(parseAss(content))
        }
        return lines
    }

    private fun parseSrt(content: String): List<SubtitleLine> {
        val result = mutableListOf<SubtitleLine>()
        val blocks = content.split("\r\n\r\n", "\n\n")
        val timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
        
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size >= 3) {
                val timeMatcher = timePattern.matcher(lines[1])
                if (timeMatcher.matches()) {
                    val start = parseTime(timeMatcher.group(1)!!, timeMatcher.group(2)!!, timeMatcher.group(3)!!, timeMatcher.group(4)!!)
                    val end = parseTime(timeMatcher.group(5)!!, timeMatcher.group(6)!!, timeMatcher.group(7)!!, timeMatcher.group(8)!!)
                    val text = lines.subList(2, lines.size).joinToString("\n").replace(Regex("<[^>]*>"), "")
                    result.add(SubtitleLine(start, end, text))
                }
            }
        }
        return result
    }

    private fun parseVtt(content: String): List<SubtitleLine> {
        val result = mutableListOf<SubtitleLine>()
        val blocks = content.split("\r\n\r\n", "\n\n")
        // VTT time format: 00:00:00.000 or 00:00.000
        val timePattern = Pattern.compile("(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})")
        
        for (block in blocks) {
            if (block.startsWith("WEBVTT")) continue
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var timeLineIndex = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIndex = i
                    break
                }
            }
            if (timeLineIndex != -1 && timeLineIndex + 1 < lines.size) {
                val timeMatcher = timePattern.matcher(lines[timeLineIndex])
                if (timeMatcher.matches()) {
                    val h1 = timeMatcher.group(1) ?: "00"
                    val h2 = timeMatcher.group(5) ?: "00"
                    val start = parseTime(h1, timeMatcher.group(2)!!, timeMatcher.group(3)!!, timeMatcher.group(4)!!)
                    val end = parseTime(h2, timeMatcher.group(6)!!, timeMatcher.group(7)!!, timeMatcher.group(8)!!)
                    val text = lines.subList(timeLineIndex + 1, lines.size).joinToString("\n")
                        .replace(Regex("<[^>]*>"), "")
                    result.add(SubtitleLine(start, end, text))
                }
            }
        }
        return result
    }

    /**
     * Parse ASS/SSA (SubStation Alpha) subtitle format.
     * Extracts Dialogue lines from the [Events] section.
     * ASS time format: H:MM:SS.CC (centiseconds)
     * Dialogue format: Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
     */
    private fun parseAss(content: String): List<SubtitleLine> {
        val result = mutableListOf<SubtitleLine>()
        val lines = content.lines()

        // Find the [Events] section and read the Format line to determine column indices
        var inEvents = false
        var dialogueStartIdx = -1
        var dialogueEndIdx = -1
        var dialogueTextIdx = -1

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.equals("[events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (inEvents) {
                if (trimmed.startsWith("[")) break // Next section
                if (trimmed.startsWith("format:", ignoreCase = true)) {
                    val cols = trimmed.substringAfter("format:", "").split(",").map { it.trim().lowercase() }
                    dialogueStartIdx = cols.indexOf("start")
                    dialogueEndIdx = cols.indexOf("end")
                    dialogueTextIdx = cols.indexOf("text")
                    break
                }
            }
        }

        if (dialogueStartIdx < 0 || dialogueEndIdx < 0 || dialogueTextIdx < 0) return result

        // ASS time regex: H:MM:SS.CC
        val assTimePattern = Pattern.compile(
            "(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})"
        )

        // Now parse all Dialogue lines
        inEvents = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("[events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (!inEvents) continue
            if (trimmed.startsWith("[")) break // Next section

            if (trimmed.startsWith("dialogue:", ignoreCase = true)) {
                val parts = trimmed.split(",", limit = 10)
                if (parts.size < 10) continue

                val startStr = parts[1 + dialogueStartIdx].trim()
                val endStr = parts[1 + dialogueEndIdx].trim()
                var text = parts.drop(1 + dialogueTextIdx).joinToString(",").trim()

                // Strip ASS override tags like {\...}
                text = text.replace(Regex("\\{[^}]*}"), "")
                // Strip line breaks and leading/trailing spaces
                text = text.replace("\\N", "\n").trim()
                if (text.isEmpty()) continue

                val startMatcher = assTimePattern.matcher(startStr)
                val endMatcher = assTimePattern.matcher(endStr)
                if (startMatcher.find() && endMatcher.find()) {
                    val start = parseAssTime(
                        startMatcher.group(1) ?: "0",
                        startMatcher.group(2) ?: "00",
                        startMatcher.group(3) ?: "00",
                        startMatcher.group(4) ?: "00"
                    )
                    val end = parseAssTime(
                        endMatcher.group(1) ?: "0",
                        endMatcher.group(2) ?: "00",
                        endMatcher.group(3) ?: "00",
                        endMatcher.group(4) ?: "00"
                    )
                    result.add(SubtitleLine(start, end, text))
                }
            }
        }

        return result
    }

    /**
     * Parse ASS time format: H:MM:SS.CC (centiseconds)
     * Returns milliseconds.
     */
    private fun parseAssTime(h: String, m: String, s: String, cs: String): Long {
        return (h.toLong() * 3600000L) +
                (m.toLong() * 60000L) +
                (s.toLong() * 1000L) +
                (cs.toLong() * 10L)
    }

    private fun parseTime(h: String, m: String, s: String, ms: String): Long {
        return (h.toLong() * 3600000) + (m.toLong() * 60000) + (s.toLong() * 1000) + ms.toLong()
    }
}
