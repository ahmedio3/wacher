package com.aistudio.cinemios.fxtyr.utils

// Arabic Unicode block ranges used to decide whether a string is Latin-only.
private val ARABIC_RANGES = listOf(
    '\u0600'..'\u06FF', // Arabic
    '\u0750'..'\u077F', // Arabic Supplement
    '\u08A0'..'\u08FF', // Arabic Extended-A
    '\uFB50'..'\uFDFF', // Arabic Presentation Forms-A
    '\uFE70'..'\uFEFF'  // Arabic Presentation Forms-B
)

// Returns true when the text contains no Arabic-range characters (i.e. it is a
// Latin/English/numeric string that should be rendered with the mono font + LTR.
fun isLatinText(text: String): Boolean {
    if (text.isBlank()) return false
    return text.none { ch -> ARABIC_RANGES.any { ch in it } }
}
