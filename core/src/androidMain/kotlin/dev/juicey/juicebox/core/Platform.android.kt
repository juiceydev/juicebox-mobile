package dev.juicey.juicebox.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformHttpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun nowEpochMillis(): Long = System.currentTimeMillis()
