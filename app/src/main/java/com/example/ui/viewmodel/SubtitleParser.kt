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

    private fun parseTime(h: String, m: String, s: String, ms: String): Long {
        return (h.toLong() * 3600000) + (m.toLong() * 60000) + (s.toLong() * 1000) + ms.toLong()
    }
}
