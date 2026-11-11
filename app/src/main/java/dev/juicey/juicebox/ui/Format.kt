package dev.juicey.juicebox.ui

import dev.juicey.juicebox.core.nowEpochSeconds
import java.text.DateFormat
import java.util.Date

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (value >= 100) {
        "${value.toInt()} ${units[index]}"
    } else {
        String.format("%.1f %s", value, units[index])
    }
}

fun formatTtl(hours: Double): String = when {
    hours < 1 -> "${(hours * 60).toInt()} min"
    hours == 1.0 -> "1 hr"
    hours < 24 -> "${hours.toInt()} hr"
    hours == 24.0 -> "1 day"
    hours % 24 == 0.0 -> "${(hours / 24).toInt()} days"
    else -> "${hours.toInt()} hr"
}

/** Very compact scale label for the retention rail. */
fun formatTtlShort(hours: Double): String = when {
    hours < 1 -> "${(hours * 60).toInt()}m"
    hours < 24 -> "${hours.toInt()}h"
    hours % 24 == 0.0 -> "${(hours / 24).toInt()}d"
    else -> "${hours.toInt()}h"
}

/** Compact remaining time: "3d 4h", "42m", "expired". */
fun formatRemaining(expiresAt: Long): String {
    val now = nowEpochSeconds()
    val seconds = expiresAt - now
    return when {
        expiresAt <= 0 -> ""
        seconds <= 0 -> "expired"
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m left"
        seconds < 86_400 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m == 0L) "${h}h left" else "${h}h ${m}m left"
        }
        else -> {
            val d = seconds / 86_400
            val h = (seconds % 86_400) / 3600
            if (h == 0L) "${d}d left" else "${d}d ${h}h left"
        }
    }
}

/** "12 Nov 2026 at 14:32" rather than a locale-dependent run-together string. */
fun formatDate(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "-"
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1000))
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochSeconds * 1000))
    return "$date at $time"
}

fun mimeBadge(mimeType: String, filename: String): String = when {
    mimeType.startsWith("image/") -> "IMG"
    mimeType.startsWith("video/") -> "VID"
    mimeType.startsWith("audio/") -> "AUD"
    mimeType == "application/pdf" -> "PDF"
    mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("compressed") -> "ZIP"
    mimeType.startsWith("text/") -> "TXT"
    else -> filename.substringAfterLast('.', "").takeIf { it.length in 2..4 }?.uppercase() ?: "FILE"
}
