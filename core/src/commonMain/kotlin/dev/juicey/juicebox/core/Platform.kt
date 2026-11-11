package dev.juicey.juicebox.core

import io.ktor.client.engine.HttpClientEngine

/** One engine per app process; picked by the platform module. */
expect fun platformHttpClientEngine(): HttpClientEngine

/** Millisecond clock; kept out of common code so core stays platform-free. */
expect fun nowEpochMillis(): Long

fun nowEpochSeconds(): Long = nowEpochMillis() / 1000
