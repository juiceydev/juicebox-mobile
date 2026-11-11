package dev.juicey.juicebox.core.storage

/**
 * Tiny persistence seam. The Android app implements this over
 * SharedPreferences so the core module stays platform-independent.
 */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun delete(key: String)
}
