package dev.juicey.juicebox.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun platformHttpClientEngine(): HttpClientEngine = Darwin.create {
    // Uploads run several parts at once; URLSession's default of six
    // connections per host would queue the rest behind a slow part.
    configureSession {
        HTTPMaximumConnectionsPerHost = 8
    }
}

actual fun nowEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
