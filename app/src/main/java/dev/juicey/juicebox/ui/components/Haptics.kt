package dev.juicey.juicebox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Pixel-style haptics: short, low-amplitude ticks tied to the action rather
 * than one long buzz. Every call is a no-op when the user turns haptics off.
 */
class JuiceHaptics(
    private val feedback: HapticFeedback?,
    private val enabled: Boolean,
) {
    fun tick() = perform(HapticFeedbackType.SegmentFrequentTick)

    fun select() = perform(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) = perform(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
    )

    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun reject() = perform(HapticFeedbackType.Reject)

    fun longPress() = perform(HapticFeedbackType.LongPress)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback?.performHapticFeedback(type)
    }
}

val LocalJuiceHaptics = staticCompositionLocalOf { JuiceHaptics(null, false) }

@Composable
fun rememberJuiceHaptics(enabled: Boolean): JuiceHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { JuiceHaptics(feedback, enabled) }
}
