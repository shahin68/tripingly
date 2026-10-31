package com.falcon.tripingly.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.manrope_bold
import com.falcon.tripingly.core.designsystem.generated.resources.manrope_extrabold
import com.falcon.tripingly.core.designsystem.generated.resources.manrope_medium
import com.falcon.tripingly.core.designsystem.generated.resources.manrope_regular
import com.falcon.tripingly.core.designsystem.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

/** Manrope (SIL Open Font License), bundled with the app so it looks the same everywhere and offline. */
@Composable
private fun manrope() = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
    Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
)

/** Material 3's type scale in Manrope: screen titles extra bold, titles bold, labels semi bold. */
@Composable
internal fun appTypography(): Typography {
    val manrope = manrope()
    fun style(weight: FontWeight, size: Int, line: Int, spacing: Float = 0f) =
        TextStyle(fontFamily = manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)
    return Typography(
        displaySmall = style(FontWeight.ExtraBold, 36, 44, -0.25f),
        headlineLarge = style(FontWeight.ExtraBold, 30, 36, -0.25f),
        headlineMedium = style(FontWeight.ExtraBold, 24, 30, -0.2f),
        headlineSmall = style(FontWeight.Bold, 22, 28),
        titleLarge = style(FontWeight.Bold, 20, 26),
        titleMedium = style(FontWeight.Bold, 16, 22),
        titleSmall = style(FontWeight.Bold, 14, 20),
        bodyLarge = style(FontWeight.Normal, 16, 24),
        bodyMedium = style(FontWeight.Normal, 14, 20),
        bodySmall = style(FontWeight.Medium, 12, 16),
        labelLarge = style(FontWeight.SemiBold, 14, 20),
        labelMedium = style(FontWeight.SemiBold, 12, 16),
        labelSmall = style(FontWeight.Bold, 11, 16, 0.2f),
    )
}
