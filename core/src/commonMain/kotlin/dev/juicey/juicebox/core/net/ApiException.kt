package dev.juicey.juicebox.core.net

/** Server error envelope: `{"error": "CODE", "message": "human text"}`. */
class ApiException(
    val code: String,
    override val message: String,
    val status: Int,
) : Exception(message) {
    val retryable: Boolean
        get() = status == 408 || status == 429 || status >= 500

    val isAuth: Boolean
        get() = status == 401 || status == 403

    val isNotFound: Boolean
        get() = status == 404
}

class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
