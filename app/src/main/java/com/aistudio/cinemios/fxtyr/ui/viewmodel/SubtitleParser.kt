package com.aistudio.cinemios.fxtyr.ui.viewmodel

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class SubtitleLine(
    val startTime: Long,
    val endTime: Long,
    val text: String
)

/**
 * Tolerant subtitle parser for SRT, WebVTT and ASS/SSA.
 *
 * Design goals:
 *  - Never fail because of a BOM, CR-only line endings, cue settings
 *    (VTT `align:start position:10%`), missing cue indexes, 1-digit hours,
 *    dot-separated milliseconds, or uppercase extensions.
 *  - Decode Arabic legacy encodings (Windows-1256) instead of producing
 *    mojibake through a blind UTF-8 read.
 *  - Keep the SRT/VTT millisecond math unchanged (it was already correct).
 *  - Return an empty list for unsupported/corrupt input rather than throwing.
 */
object SubtitleParser {

    private enum class Format { SRT, VTT, ASS, UNKNOWN }

    private val SRT_TIME = Regex("""(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val VTT_TIME = Regex("""(?:(\d{1,3}):)?(\d{2}):(\d{2})[.,](\d{1,3})""")
    private val ASS_TIME = Regex("""(\d+):(\d{2}):(\d{2})[.:](\d{1,3})""")
    private val HTML_TAG = Regex("""<[^>]*>""")
    private val ASS_TAG = Regex("""\{[^}]*\}""")
    private val BLANK_LINES = Regex("""\n{2,}""")

    fun parseBlock(file: File): List<SubtitleLine> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            val content = decode(file.readBytes())
            when (detectFormat(file.name, content)) {
                Format.SRT -> parseSrt(content)
                Format.VTT -> parseVtt(content)
                Format.ASS -> parseAss(content)
                Format.UNKNOWN -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun detectFormat(fileName: String, content: String): Format {
        val lowerName = fileName.lowercase()
        when {
            lowerName.endsWith(".srt") -> return Format.SRT
            lowerName.endsWith(".vtt") -> return Format.VTT
            lowerName.endsWith(".ass") || lowerName.endsWith(".ssa") -> return Format.ASS
        }

        val head = content.take(4096)
        return when {
            head.contains("[Events]", ignoreCase = true) ||
                head.contains("Dialogue:", ignoreCase = true) -> Format.ASS
            head.trimStart().startsWith("WEBVTT", ignoreCase = true) -> Format.VTT
            head.contains("-->") -> Format.SRT
            else -> Format.UNKNOWN
        }
    }

    /**
     * Decode bytes using BOM first, then strict UTF-8, then Windows-1256
     * (the common legacy Arabic encoding), with UTF-8 as a last resort.
     */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        // UTF-16 without BOM: look for the NUL-byte pattern in the first 1 KiB.
        val sample = bytes.take(minOf(bytes.size, 1024))
        val nulOdd = sample.indices.count { it % 2 == 1 && sample[it] == 0.toByte() }
        val nulEven = sample.indices.count { it % 2 == 0 && sample[it] == 0.toByte() }
        if (sample.size >= 8 && nulOdd > sample.size / 4) {
            return String(bytes, StandardCharsets.UTF_16LE)
        }
        if (sample.size >= 8 && nulEven > sample.size / 4) {
            return String(bytes, StandardCharsets.UTF_16BE)
        }

        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            // Fall through to legacy Arabic.
        }

        return try {
            String(bytes, Charset.forName("windows-1256"))
        } catch (_: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun normalize(raw: String): String =
        raw.replace("\r\n", "\n")
            .replace('\r', '\n')
            .removePrefix("\uFEFF")

    private fun parseSrt(raw: String): List<SubtitleLine> {
        val result = mutableListOf<SubtitleLine>()
        for (block in normalize(raw).split(BLANK_LINES)) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex == -1) continue

            val matches = SRT_TIME.findAll(lines[timeIndex]).toList()
            if (matches.size < 2) continue

            val text = lines.drop(timeIndex + 1).joinToString("\n")
            if (text.isBlank()) continue

            result.add(
                SubtitleLine(
                    startTime = parseTime(matches[0]),
                    endTime = parseTime(matches[1]),
                    text = cleanText(text)
                )
            )
        }
        return result
    }

    private fun parseVtt(raw: String): List<SubtitleLine> {
        val result = mutableListOf<SubtitleLine>()
        for (block in normalize(raw).split(BLANK_LINES)) {
            val trimmedBlock = block.trim()
            if (trimmedBlock.isEmpty()) continue

            // VTT metadata blocks are not cues.
            val upper = trimmedBlock.uppercase()
            if (upper.startsWith("WEBVTT") ||
                upper.startsWith("NOTE") ||
                upper.startsWith("STYLE") ||
                upper.startsWith("REGION")
            ) {
                continue
            }

            val lines = trimmedBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex == -1) continue

            // Use findAll so trailing cue settings are ignored.
            val matches = VTT_TIME.findAll(lines[timeIndex]).toList()
            if (matches.size < 2) continue

            val text = lines.drop(timeIndex + 1).joinToString("\n")
            if (text.isBlank()) continue

            result.add(
                SubtitleLine(
                    startTime = parseVttTime(matches[0]),
                    endTime = parseVttTime(matches[1]),
                    text = cleanText(text)
                )
            )
        }
        return result
    }

    /**
     * Parse ASS/SSA.
     *
     * The previous implementation split the whole `Dialogue:` line, which
     * shifted every field by one. Here we strip `Dialogue:` first and use the
     * `Format:` column order directly. Splitting with a limit equal to the
     * column count preserves commas inside the Text column.
     */
    private fun parseAss(raw: String): List<SubtitleLine> {
        val lines = normalize(raw).lines()
        val result = mutableListOf<SubtitleLine>()

        var inEvents = false
        var formatCols: List<String>? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("[Events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (!inEvents) continue
            if (trimmed.startsWith("[") && !trimmed.equals("[Events]", ignoreCase = true)) break

            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                formatCols = trimmed.substringAfter(":", "")
                    .split(",")
                    .map { it.trim().lowercase() }
                break
            }
        }

        if (formatCols.isNullOrEmpty()) return result

        val startIdx = formatCols.indexOf("start")
        val endIdx = formatCols.indexOf("end")
        val textIdx = formatCols.indexOf("text")
        if (startIdx < 0 || endIdx < 0 || textIdx < 0) return result

        val requiredIndex = maxOf(startIdx, endIdx, textIdx)
        inEvents = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("[Events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (!inEvents) continue
            if (trimmed.startsWith("[") && !trimmed.equals("[Events]", ignoreCase = true)) break
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) continue

            val payload = trimmed.substringAfter(":", "").trim()
            if (payload.isEmpty()) continue

            // The Text column is last; preserving commas is mandatory.
            val parts = payload.split(",", limit = maxOf(formatCols.size, textIdx + 1))
            if (parts.size <= requiredIndex) continue

            val startMatch = ASS_TIME.find(parts[startIdx].trim()) ?: continue
            val endMatch = ASS_TIME.find(parts[endIdx].trim()) ?: continue

            var text = parts.drop(textIdx).joinToString(",").trim()
            text = text.replace(ASS_TAG, "")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .replace("\\h", " ")
                .trim()

            if (text.isEmpty()) continue

            result.add(
                SubtitleLine(
                    startTime = parseAssTime(startMatch),
                    endTime = parseAssTime(endMatch),
                    text = text
                )
            )
        }
        return result
    }

    private fun parseTime(match: MatchResult): Long {
        val h = match.groupValues[1].toLongOrNull() ?: 0L
        val m = match.groupValues[2].toLongOrNull() ?: 0L
        val s = match.groupValues[3].toLongOrNull() ?: 0L
        val ms = fractionToMillis(match.groupValues[4])
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    private fun parseVttTime(match: MatchResult): Long {
        val h = match.groupValues[1].toLongOrNull() ?: 0L
        val m = match.groupValues[2].toLongOrNull() ?: 0L
        val s = match.groupValues[3].toLongOrNull() ?: 0L
        val ms = fractionToMillis(match.groupValues[4])
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    private fun parseAssTime(match: MatchResult): Long {
        val h = match.groupValues[1].toLongOrNull() ?: 0L
        val m = match.groupValues[2].toLongOrNull() ?: 0L
        val s = match.groupValues[3].toLongOrNull() ?: 0L
        // ASS normally uses centiseconds (2 digits); accept milliseconds too.
        val fraction = match.groupValues[4]
        val ms = when (fraction.length) {
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            else -> fraction.toLongOrNull() ?: 0L
        }
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    private fun fractionToMillis(fraction: String): Long = when (fraction.length) {
        1 -> (fraction.toLongOrNull() ?: 0L) * 100L
        2 -> (fraction.toLongOrNull() ?: 0L) * 10L
        else -> fraction.toLongOrNull() ?: 0L
    }

    private fun cleanText(text: String): String =
        text.replace(HTML_TAG, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&lrm;", "")
            .replace("&rlm;", "")
            .trim()
}
