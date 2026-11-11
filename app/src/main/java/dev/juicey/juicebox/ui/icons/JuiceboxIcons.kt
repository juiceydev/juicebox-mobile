package dev.juicey.juicebox.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.juicey.juicebox.R

/**
 * Pixelarticons (MIT, halfmage). 24px grid, single colour; the Compose Icon
 * tint does the work, so the drawables stay black.
 */
object JuiceboxIcons {
    @DrawableRes val archive = R.drawable.ic_px_archive
    @DrawableRes val arrowLeft = R.drawable.ic_px_arrow_left
    @DrawableRes val arrowRight = R.drawable.ic_px_arrow_right
    @DrawableRes val audioWaveform = R.drawable.ic_px_audio_waveform
    @DrawableRes val camera = R.drawable.ic_px_camera
    @DrawableRes val check = R.drawable.ic_px_check
    @DrawableRes val checkbox = R.drawable.ic_px_checkbox
    @DrawableRes val checkboxOn = R.drawable.ic_px_checkbox_on
    @DrawableRes val chevronDown = R.drawable.ic_px_chevron_down
    @DrawableRes val chevronRight = R.drawable.ic_px_chevron_right
    @DrawableRes val chevronUp = R.drawable.ic_px_chevron_up
    @DrawableRes val clipboard = R.drawable.ic_px_clipboard
    @DrawableRes val clock = R.drawable.ic_px_clock
    @DrawableRes val close = R.drawable.ic_px_close
    @DrawableRes val cloud = R.drawable.ic_px_cloud
    @DrawableRes val copy = R.drawable.ic_px_copy
    @DrawableRes val download = R.drawable.ic_px_download
    @DrawableRes val edit = R.drawable.ic_px_edit
    @DrawableRes val externalLink = R.drawable.ic_px_external_link
    @DrawableRes val eye = R.drawable.ic_px_eye
    @DrawableRes val eyeOff = R.drawable.ic_px_eye_off
    @DrawableRes val file = R.drawable.ic_px_file
    @DrawableRes val fileText = R.drawable.ic_px_file_text
    @DrawableRes val files = R.drawable.ic_px_folder
    @DrawableRes val globe = R.drawable.ic_px_globe
    @DrawableRes val image = R.drawable.ic_px_image
    @DrawableRes val info = R.drawable.ic_px_info
    @DrawableRes val key = R.drawable.ic_px_key
    @DrawableRes val link = R.drawable.ic_px_link
    @DrawableRes val lock = R.drawable.ic_px_lock
    @DrawableRes val moreHorizontal = R.drawable.ic_px_more_horizontal
    @DrawableRes val moon = R.drawable.ic_jb_moon
    @DrawableRes val music = R.drawable.ic_px_music
    @DrawableRes val play = R.drawable.ic_px_play
    @DrawableRes val plus = R.drawable.ic_px_plus
    @DrawableRes val qrCode = R.drawable.ic_px_qr_code
    @DrawableRes val refresh = R.drawable.ic_px_refresh
    @DrawableRes val search = R.drawable.ic_px_search
    @DrawableRes val settings = R.drawable.ic_px_settings
    @DrawableRes val share = R.drawable.ic_px_share
    @DrawableRes val shield = R.drawable.ic_px_shield
    @DrawableRes val sliders = R.drawable.ic_px_sliders
    @DrawableRes val smartphone = R.drawable.ic_px_smartphone
    @DrawableRes val trash = R.drawable.ic_px_trash
    @DrawableRes val upload = R.drawable.ic_px_upload
    @DrawableRes val video = R.drawable.ic_px_video
    @DrawableRes val warning = R.drawable.ic_px_warning
}

@Composable
fun juiceboxIcon(@DrawableRes id: Int): ImageVector =
    ImageVector.vectorResource(id)
