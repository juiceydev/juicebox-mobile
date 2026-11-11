package dev.juicey.juicebox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.juicey.juicebox.R

/** The site's General/Roboto file, used for the wordmark only. */
val SiteFont: FontFamily = FontFamily(Font(R.font.roboto_site))

/** The site's title face (I pixel u), used for the main-screen wordmark. */
val TitleFont: FontFamily = FontFamily(Font(R.font.i_pixel_u))

/**
 * Platform type, shaped by the Material 3 Expressive scale: geometric,
 * medium-weight display styles and tighter tracking on the big sizes.
 */
val JuiceTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = (-1).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp,
        ),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}
