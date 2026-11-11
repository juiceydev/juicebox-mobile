package dev.juicey.juicebox.core.net

import dev.juicey.juicebox.core.CLIENT_HEADER
import dev.juicey.juicebox.core.ClientInfo
import dev.juicey.juicebox.core.JuiceboxJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/** Reads are safe to replay; request bodies must never be sent twice. */
private val RETRYABLE_METHODS = setOf(HttpMethod.Get, HttpMethod.Head)

fun createJuiceboxHttpClient(
    engine: HttpClientEngine,
    cookies: CookiesStorage,
    client: ClientInfo,
): HttpClient = HttpClient(engine) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(JuiceboxJson)
    }
    install(HttpCookies) {
        storage = cookies
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 90_000
    }
    install(HttpRequestRetry) {
        // A device that just regained signal can fail the first DNS lookup.
        // Replaying idempotent requests hides that blip without risking a
        // duplicated upload.
        retryOnExceptionIf(2) { request, cause ->
            request.method in RETRYABLE_METHODS && cause.isTransientNetworkFailure()
        }
        delayMillis { attempt -> 350L * (attempt + 1) }
    }
    install(Logging) {
        level = LogLevel.INFO
        sanitizeHeader { name ->
            name == HttpHeaders.Authorization || name == "X-Delete-Token"
        }
    }

    defaultRequest {
        header(HttpHeaders.UserAgent, client.userAgent)
        header(CLIENT_HEADER, client.name)
        contentType(ContentType.Application.Json)
    }
}
