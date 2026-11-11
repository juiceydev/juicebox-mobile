package dev.juicey.juicebox.core.net

import dev.juicey.juicebox.core.JuiceboxJson
import dev.juicey.juicebox.core.nowEpochMillis
import dev.juicey.juicebox.core.storage.KeyValueStore
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String = "/",
    val secure: Boolean = false,
    @SerialName("http_only") val httpOnly: Boolean = false,
    @SerialName("expires_at") val expiresAt: Long = Long.MAX_VALUE,
)

/**
 * The session lives in a `jb_session` cookie. Persisting it is what makes the
 * anonymous library survive restarts, so this is kept deliberately small and
 * boring: a JSON list in [KeyValueStore].
 */
class PersistentCookieStorage(
    private val store: KeyValueStore,
) : CookiesStorage {

    private val mutex = Mutex()
    private var loaded = false
    private val cookies = mutableListOf<StoredCookie>()

    private fun load() {
        if (loaded) return
        loaded = true
        val raw = store.read(KEY) ?: return
        runCatching {
            cookies += JuiceboxJson.decodeFromString<List<StoredCookie>>(raw)
        }
    }

    private fun persist() {
        store.write(KEY, JuiceboxJson.encodeToString(cookies))
    }

    private fun prune() {
        val now = nowEpochMillis()
        cookies.removeAll { it.expiresAt in 1..<now }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        mutex.withLock {
            load()
            val host = requestUrl.host.lowercase()
            val maxAge = cookie.maxAge ?: 0
            val expiresAt = when {
                maxAge > 0 -> nowEpochMillis() + maxAge * 1000L
                else -> Long.MAX_VALUE
            }
            val entry = StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain?.lowercase() ?: host,
                path = cookie.path ?: "/",
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                expiresAt = expiresAt,
            )
            cookies.removeAll {
                it.name == entry.name && it.domain == entry.domain && it.path == entry.path
            }
            cookies += entry
            persist()
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        mutex.withLock {
            load()
            prune()
            return cookies
                .filter { matches(it, requestUrl) }
                .map { it.toCookie() }
        }
    }

    override fun close() = Unit

    suspend fun getAllCookies(): List<Cookie> = mutex.withLock {
        load()
        prune()
        cookies.map { it.toCookie() }
    }

    suspend fun clear() {
        mutex.withLock {
            loaded = true
            cookies.clear()
            persist()
        }
    }

    suspend fun sessionCookie(): Cookie? = mutex.withLock {
        load()
        prune()
        cookies.firstOrNull { it.name == "jb_session" }?.toCookie()
    }

    suspend fun hasSession(): Boolean = sessionCookie() != null

    suspend fun importRaw(raw: String) {
        mutex.withLock {
            loaded = true
            runCatching {
                cookies.clear()
                cookies += JuiceboxJson.decodeFromString<List<StoredCookie>>(raw)
                persist()
            }
        }
    }

    suspend fun exportRaw(): String = mutex.withLock {
        load()
        JuiceboxJson.encodeToString(cookies)
    }

    private fun matches(cookie: StoredCookie, url: Url): Boolean {
        val host = url.host.lowercase()
        val domain = cookie.domain?.lowercase()?.removePrefix(".") ?: return false
        val hostMatch = host == domain || host.endsWith(".$domain")
        if (!hostMatch) return false
        if (cookie.secure && url.protocol.name != "https") return false
        val path = url.encodedPath.ifEmpty { "/" }
        val cookiePath = cookie.path.ifEmpty { "/" }
        return path == cookiePath ||
            path.startsWith("$cookiePath/") ||
            (cookiePath.endsWith("/") && path.startsWith(cookiePath))
    }

    private fun StoredCookie.toCookie(): Cookie = Cookie(
        name = name,
        value = value,
        encoding = CookieEncoding.URI_ENCODING,
        maxAge = 0,
        expires = null,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
    )

    private companion object {
        const val KEY = "cookies"
    }
}
