package dev.juicey.juicebox.core.net

import dev.juicey.juicebox.core.friendlyMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFailureTest {

    @Test
    fun host_lookup_messages_are_classified() {
        val android = RuntimeException(
            "Unable to resolve host \"box.juicey.dev\": No address associated with hostname",
        )
        val darwin = RuntimeException(
            "A server with the specified hostname could not be found.",
        )

        assertTrue(android.isHostLookupFailure())
        assertTrue(darwin.isHostLookupFailure())
        assertTrue(android.isTransientNetworkFailure())
    }

    @Test
    fun wrapped_causes_are_inspected() {
        val wrapped = RuntimeException("request failed", IllegalStateException("Connection reset"))
        assertTrue(wrapped.isTransientNetworkFailure())
        assertFalse(wrapped.isHostLookupFailure())
    }

    @Test
    fun server_errors_keep_their_own_message() {
        val error = ApiException("RATE_LIMITED", "Slow down", 429)
        assertEquals("Slow down", error.friendlyMessage())
    }

    @Test
    fun host_lookup_gets_actionable_copy() {
        val error = RuntimeException("Unable to resolve host \"box.juicey.dev\"")
        assertTrue(error.friendlyMessage().contains("server address"))
    }

    @Test
    fun connection_retry_succeeds_after_a_blip() = runTest {
        var attempts = 0
        val result = withConnectionRetry(attempts = 3) {
            attempts += 1
            if (attempts < 3) throw RuntimeException("Unable to resolve host \"box.juicey.dev\"")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun connection_retry_does_not_retry_server_errors() = runTest {
        var attempts = 0
        val error = runCatching {
            withConnectionRetry(attempts = 3) {
                attempts += 1
                throw ApiException("BAD_REQUEST", "Bad request", 400)
            }
        }.exceptionOrNull()

        assertEquals("Bad request", error?.message)
        assertEquals(1, attempts)
    }
}
