package dev.juicey.juicebox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The site-wide announcement returned by `/api/announcement`. */
@Serializable
data class SiteAnnouncement(
    @SerialName("message") val message: String? = null,
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("mode") val mode: String? = null,
) {
    val hasMessage: Boolean get() = !message.isNullOrBlank()
    val hasLink: Boolean get() = !linkUrl.isNullOrBlank()
}
