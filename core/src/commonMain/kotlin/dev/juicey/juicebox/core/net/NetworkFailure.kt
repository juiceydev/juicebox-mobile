package dev.juicey.juicebox.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Transport failures reach common code as engine-specific exceptions (OkHttp
 * on Android, Darwin on iOS) with different class names but recognisable
 * messages. Classifying the message chain keeps one retry policy and one piece
 * of user-facing copy across both platforms.
 */
private val HOST_LOOKUP_MARKERS = listOf(
    "unable to resolve host",
    "no address associated with hostname",
    "nodename nor servname",
    "could not resolve host",
    "cannot find host",
    "hostname could not be found",
    "a server with the specified hostname could not be found",
    "name or service not known",
    "temporary failure in name resolution",
    "unknownhost",
)

private val TRANSIENT_MARKERS = HOST_LOOKUP_MARKERS + listOf(
    "timed out",
    "timeout",
    "the internet connection appears to be offline",
    "network connection was lost",
    "connection reset",
    "connection was lost",
    "connection abort",
    "connection refused",
)

private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

private fun Throwable.matches(markers: List<String>): Boolean =
    causeChain().any { error ->
        val text = error.message.orEmpty().lowercase()
        markers.any { marker -> text.contains(marker) }
    }

/** True when the device could not turn a host name into an address. */
fun Throwable.isHostLookupFailure(): Boolean = matches(HOST_LOOKUP_MARKERS)

/** True when the same request has a reasonable chance of succeeding shortly. */
fun Throwable.isTransientNetworkFailure(): Boolean = matches(TRANSIENT_MARKERS)

/**
 * Runs [block], retrying transient connection failures with a short linear
 * backoff. HTTP GETs also retry inside the Ktor client; this covers callers
 * that bundle several requests, such as the connect handshake.
 */
internal suspend fun <T> withConnectionRetry(
    attempts: Int = 3,
    block: suspend () -> T,
): T {
    var last: Throwable? = null
    for (attempt in 0 until attempts) {
        try {
            return block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!error.isTransientNetworkFailure() || attempt == attempts - 1) throw error
            last = error
            delay(350L * (attempt + 1))
        }
    }
    throw last ?: IllegalStateException("Network request failed")
}
