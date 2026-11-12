package dev.juicey.juicebox.core.upload

import dev.juicey.juicebox.core.model.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class TransportPlannerTest {

    private val direct = ServerConfig(directUpload = true, maxTusParallelParts = 64)
    private val noDirect = ServerConfig(directUpload = false, maxTusParallelParts = 64)
    private val noParallelTus = ServerConfig(directUpload = false, maxTusParallelParts = 1)

    private val mib = 1024L * 1024

    @Test
    fun smallUnprotectedUsesSingleDirectRequest() {
        assertEquals(
            UploadTransport.DIRECT,
            planTransport(direct, 1 * mib, hasPassword = false),
        )
        assertEquals(
            UploadTransport.DIRECT,
            planTransport(direct, 8 * mib, hasPassword = false),
        )
    }

    @Test
    fun largeUnprotectedUsesDirectParallel() {
        assertEquals(
            UploadTransport.PARALLEL,
            planTransport(direct, 8 * mib + 1, hasPassword = false),
        )
        assertEquals(
            UploadTransport.PARALLEL,
            planTransport(direct, 600 * mib, hasPassword = false),
        )
    }

    @Test
    fun anyProtectedUploadAvoidsDirect() {
        assertEquals(
            UploadTransport.RELAY,
            planTransport(direct, 1 * mib, hasPassword = true),
        )
        assertEquals(
            UploadTransport.TUS,
            planTransport(direct, 120 * mib, hasPassword = true),
        )
        assertEquals(
            UploadTransport.TUS,
            planTransport(direct, 600 * mib, hasPassword = true),
        )
        assertEquals(
            UploadTransport.TUS,
            planTransport(noParallelTus, 120 * mib, hasPassword = true),
        )
    }

    @Test
    fun directDisabledFallsBackToTusPaths() {
        assertEquals(
            UploadTransport.RELAY,
            planTransport(noDirect, 10 * mib, hasPassword = false),
        )
        assertEquals(
            UploadTransport.TUS,
            planTransport(noDirect, 40 * mib, hasPassword = false),
        )
        assertEquals(
            UploadTransport.TUS_PARALLEL,
            planTransport(noDirect, 120 * mib, hasPassword = false),
        )
    }

    @Test
    fun parallelTusRequiresAtLeastTwoParts() {
        assertEquals(
            UploadTransport.TUS,
            planTransport(noParallelTus, 120 * mib, hasPassword = false),
        )
    }

    @Test
    fun thresholdBoundaries() {
        assertEquals(
            UploadTransport.TUS,
            planTransport(noDirect, 64 * mib, hasPassword = true),
        )
        assertEquals(
            UploadTransport.TUS,
            planTransport(noDirect, 64 * mib - 1, hasPassword = true),
        )
        assertEquals(
            UploadTransport.RELAY,
            planTransport(noDirect, 30 * mib, hasPassword = false),
        )
    }
}
