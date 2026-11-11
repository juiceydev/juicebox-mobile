package dev.juicey.juicebox.core

import kotlinx.serialization.json.Json

/**
 * Wire format shared with the server. Unknown fields are ignored on purpose:
 * the backend is deployed independently of the app and adds fields over time.
 */
val JuiceboxJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}

const val DEFAULT_SERVER = "https://box.juicey.dev"

/** Explicit client marker for server-side upload statistics. */
const val CLIENT_HEADER = "X-Juicebox-Client"

/** A single direct request is still cheaper than reserving parts. */
const val DIRECT_SINGLE_MAX_BYTES = 16L * 1024 * 1024

/** Target part size for direct-parallel; smaller parts retry cheaply. */
const val DIRECT_PART_TARGET_BYTES = 32L * 1024 * 1024
const val DIRECT_PART_CONCURRENCY = 4

/** Unprotected files this large can use several TUS connections at once. */
const val TUS_PARALLEL_THRESHOLD_BYTES = 64L * 1024 * 1024
const val TUS_PARALLEL_PARTS = 4
const val TUS_PARALLEL_CHUNK = 16L * 1024 * 1024

/** Above this a resumable TUS session beats a whole-file relay request. */
const val TUS_THRESHOLD_BYTES = 30L * 1024 * 1024

const val MAX_DIRECT_PART_BYTES = 96L * 1024 * 1024
const val MAX_DIRECT_PARTS = 64

const val TUS_CHUNK_MIN = 8L * 1024 * 1024
const val TUS_CHUNK_START = 16L * 1024 * 1024
const val TUS_CHUNK_MAX = 64L * 1024 * 1024
