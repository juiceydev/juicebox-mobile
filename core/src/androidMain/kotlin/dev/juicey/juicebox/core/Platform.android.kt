package dev.juicey.juicebox.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher

actual fun platformHttpClientEngine(): HttpClientEngine = OkHttp.create {
    config {
        // OkHttp caps a host at five requests by default, which would strand
        // the parallel upload parts behind a queue instead of the network.
        dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 8
            },
        )
    }
}

actual fun nowEpochMillis(): Long = System.currentTimeMillis()
