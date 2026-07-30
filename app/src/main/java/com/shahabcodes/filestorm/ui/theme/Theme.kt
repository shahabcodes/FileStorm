package com.shahabcodes.filestorm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS system palette
object Ios {
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF30D158)
    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9500)
    val OrangeDark = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFCC00)
    val Purple = Color(0xFFAF52DE)
    val PurpleDark = Color(0xFFBF5AF2)
    val Teal = Color(0xFF30B0C7)
    val Pink = Color(0xFFFF2D55)
    val Indigo = Color(0xFF5856D6)

    val GroupedBgLight = Color(0xFFF2F2F7)
    val GroupedBgDark = Color(0xFF000000)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF1C1C1E)
    val Card2Dark = Color(0xFF2C2C2E)
    val LabelLight = Color(0xFF000000)
    val LabelDark = Color(0xFFFFFFFF)
    val SecondaryLabelLight = Color(0xFF8A8A8E)
    val SecondaryLabelDark = Color(0xFF8D8D93)
    val SeparatorLight = Color(0xFFE5E5EA)
    val SeparatorDark = Color(0xFF38383A)
    val FillLight = Color(0xFFE9E9EB)
    val FillDark = Color(0xFF2C2C2E)
}

data class FsColors(
    val accent: Color,
    val groupedBackground: Color,
    val card: Color,
    val cardSecondary: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val isDark: Boolean,
)

val LocalFsColors = staticCompositionLocalOf {
    FsColors(
        accent = Ios.Blue,
        groupedBackground = Ios.GroupedBgLight,
        card = Ios.CardLight,
        cardSecondary = Ios.CardLight,
        label = Ios.LabelLight,
        secondaryLabel = Ios.SecondaryLabelLight,
        separator = Ios.SeparatorLight,
        fill = Ios.FillLight,
        green = Ios.Green,
        red = Ios.Red,
        orange = Ios.Orange,
        isDark = false,
    )
}

val fsColors: FsColors
    @Composable get() = LocalFsColors.current

private val iosTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp
    ),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val iosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FileStormTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val fs = if (dark) FsColors(
        accent = Ios.BlueDark,
        groupedBackground = Ios.GroupedBgDark,
        card = Ios.CardDark,
        cardSecondary = Ios.Card2Dark,
        label = Ios.LabelDark,
        secondaryLabel = Ios.SecondaryLabelDark,
        separator = Ios.SeparatorDark,
        fill = Ios.FillDark,
        green = Ios.GreenDark,
        red = Ios.RedDark,
        orange = Ios.OrangeDark,
        isDark = true,
    ) else FsColors(
        accent = Ios.Blue,
        groupedBackground = Ios.GroupedBgLight,
        card = Ios.CardLight,
        cardSecondary = Ios.CardLight,
        label = Ios.LabelLight,
        secondaryLabel = Ios.SecondaryLabelLight,
        separator = Ios.SeparatorLight,
        fill = Ios.FillLight,
        green = Ios.Green,
        red = Ios.Red,
        orange = Ios.Orange,
        isDark = false,
    )

    val scheme = if (dark) darkColorScheme(
        primary = fs.accent,
        background = fs.groupedBackground,
        surface = fs.card,
        surfaceVariant = fs.cardSecondary,
        onPrimary = Color.White,
        onBackground = fs.label,
        onSurface = fs.label,
        error = fs.red,
    ) else lightColorScheme(
        primary = fs.accent,
        background = fs.groupedBackground,
        surface = fs.card,
        surfaceVariant = fs.cardSecondary,
        onPrimary = Color.White,
        onBackground = fs.label,
        onSurface = fs.label,
        error = fs.red,
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalFsColors provides fs) {
        MaterialTheme(colorScheme = scheme, typography = iosTypography, shapes = iosShapes, content = content)
    }
}
