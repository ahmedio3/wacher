package com.aistudio.cinemios.fxtyr.ui.components.downloads

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0.0MB"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(java.util.Locale.US, "%.1fGB", gb)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1fMB", mb)
}
