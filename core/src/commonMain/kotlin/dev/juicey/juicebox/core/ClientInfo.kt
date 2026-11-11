package dev.juicey.juicebox.core

/**
 * What the server sees as the calling app. Each platform passes its own name
 * and version so the shared core stays free of Android/iOS assumptions.
 */
data class ClientInfo(
    val name: String,
    val userAgent: String,
)
