package dev.juicey.juicebox.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConfigTest {

    private val dynamic = ServerConfig(
        maxFileSizeBytes = 8L * 1024 * 1024 * 1024,
        maxFileSizeHardBytes = 10L * 1024 * 1024 * 1024,
        maxFileSizeByTtlHours = mapOf(
            "1" to 10L * 1024 * 1024 * 1024,
            "72" to 7L * 1024 * 1024 * 1024,
            "720" to 1L * 1024 * 1024 * 1024,
        ),
    )

    @Test
    fun usesTheCapForTheSelectedRetention() {
        assertEquals(10L * 1024 * 1024 * 1024, dynamic.maxFileSizeBytes(1.0))
        assertEquals(7L * 1024 * 1024 * 1024, dynamic.maxFileSizeBytes(72.0))
        assertEquals(1L * 1024 * 1024 * 1024, dynamic.maxFileSizeBytes(720.0))
    }

    @Test
    fun unknownRetentionFallsBackToTheGlobalCap() {
        assertEquals(8L * 1024 * 1024 * 1024, dynamic.maxFileSizeBytes(6.0))
    }

    @Test
    fun olderHostsKeepTheSingleGlobalCap() {
        val old = ServerConfig(maxFileSizeBytes = 500L * 1024 * 1024)
        assertEquals(500L * 1024 * 1024, old.maxFileSizeBytes(24.0))
    }
}
