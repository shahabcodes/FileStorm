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

/**
 * The colour of each file type's icon. These used to be hard-coded to the iOS
 * palette, which is why folders stayed blue whichever theme was picked; owning
 * them here lets a theme restyle every icon in the app at once.
 */
data class KindColors(
    val folder: Color,
    val image: Color,
    val video: Color,
    val audio: Color,
    val document: Color,
    val archive: Color,
    val apk: Color,
    val other: Color,
)

private val IosKinds = KindColors(
    folder = Ios.Blue,
    image = Ios.Green,
    video = Ios.Purple,
    audio = Ios.Pink,
    document = Ios.Orange,
    archive = Ios.Indigo,
    apk = Ios.Teal,
    other = Color(0xFF8E8E93),
)

private val IosKindsDark = KindColors(
    folder = Ios.BlueDark,
    image = Ios.GreenDark,
    video = Ios.PurpleDark,
    audio = Ios.Pink,
    document = Ios.OrangeDark,
    archive = Ios.Indigo,
    apk = Ios.Teal,
    other = Color(0xFF98989D),
)

/**
 * Blossom: frosted rose and violet, built from the Glass app icon, whose
 * background runs indigo (#5856D6) into rose (#FF375F) under translucent white
 * orbs. Surfaces are barely-there blush rather than white and text is deep plum
 * rather than black, so nothing in it is plain grey.
 */
object Blossom {
    val Rose = Color(0xFFDB3E90)
    val RoseNight = Color(0xFFFF74B4)
    val Violet = Color(0xFF7A5AF0)
    val VioletNight = Color(0xFF9F87FF)

    val Background = Color(0xFFFBF2F9)
    val Card = Color(0xFFFFFBFE)
    val CardSecondary = Color(0xFFFAEFF7)
    val Label = Color(0xFF3B2445)
    val SecondaryLabel = Color(0xFF9C82AC)
    val Separator = Color(0xFFF1DFEE)
    val Fill = Color(0xFFF4E4F1)
    val Green = Color(0xFF34BE9B)
    val Red = Color(0xFFF2416B)
    val Orange = Color(0xFFFF9A76)

    val BackgroundNight = Color(0xFF140F1D)
    val CardNight = Color(0xFF201A2D)
    val CardSecondaryNight = Color(0xFF2A2239)
    val LabelNight = Color(0xFFF7ECFC)
    val SecondaryLabelNight = Color(0xFFA795BE)
    val SeparatorNight = Color(0xFF332841)
    val FillNight = Color(0xFF2C2338)
    val GreenNight = Color(0xFF4FD8B2)
    val RedNight = Color(0xFFFF5D87)
    val OrangeNight = Color(0xFFFFB088)

    val kinds = KindColors(
        folder = Violet,
        image = Rose,
        video = Color(0xFFB05CE8),
        audio = Color(0xFFFF7AA8),
        document = Orange,
        archive = Color(0xFF8C6BEA),
        apk = Green,
        other = Color(0xFFA893B4),
    )
    val kindsNight = KindColors(
        folder = VioletNight,
        image = RoseNight,
        video = Color(0xFFC77EFF),
        audio = Color(0xFFFF9BC0),
        document = OrangeNight,
        archive = Color(0xFFA98CFF),
        apk = GreenNight,
        other = Color(0xFFB9A7C9),
    )
}

/**
 * Sakura: petals and cream. Warmer and gentler than Blossom — cherry pink over
 * a soft ivory ground with sage and apricot, where Blossom leans cooler and
 * more violet.
 */
object Sakura {
    val Pink = Color(0xFFEE6F9C)
    val PinkNight = Color(0xFFFF92B6)
    val Gold = Color(0xFFE8A860)

    val Background = Color(0xFFFFF5F3)
    val Card = Color(0xFFFFFDFC)
    val CardSecondary = Color(0xFFFFEFEC)
    val Label = Color(0xFF4C2D34)
    val SecondaryLabel = Color(0xFFB18B94)
    val Separator = Color(0xFFFAE1DE)
    val Fill = Color(0xFFFCE7E4)
    val Green = Color(0xFF6FC3A2)
    val Red = Color(0xFFE2566F)
    val Orange = Color(0xFFEFA469)

    val BackgroundNight = Color(0xFF181114)
    val CardNight = Color(0xFF241A1E)
    val CardSecondaryNight = Color(0xFF2F2228)
    val LabelNight = Color(0xFFFCEFF2)
    val SecondaryLabelNight = Color(0xFFC3A2AB)
    val SeparatorNight = Color(0xFF382730)
    val FillNight = Color(0xFF31232A)
    val GreenNight = Color(0xFF74D8B6)
    val RedNight = Color(0xFFFF6E88)
    val OrangeNight = Color(0xFFFFC08C)

    val kinds = KindColors(
        folder = Pink,
        image = Green,
        video = Color(0xFFC98BD6),
        audio = Color(0xFFF4A0B8),
        document = Gold,
        archive = Color(0xFFB79AE0),
        apk = Color(0xFF7FC4C0),
        other = Color(0xFFC3A7AE),
    )
    val kindsNight = KindColors(
        folder = PinkNight,
        image = GreenNight,
        video = Color(0xFFDDA3EA),
        audio = Color(0xFFFFB6CB),
        document = Color(0xFFFFC98A),
        archive = Color(0xFFCBB2F5),
        apk = Color(0xFF96DCD8),
        other = Color(0xFFD3BAC1),
    )
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
    val kinds: KindColors = IosKinds,
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
        kinds = IosKinds,
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
    val appearance = com.shahabcodes.filestorm.data.Prefs.appearance
    val systemDark = isSystemInDarkTheme()
    val dark = appearance.isDark(systemDark)

    val accentChoice = com.shahabcodes.filestorm.data.Prefs.accent
    // The decorated themes ship with their own accent, but a deliberate accent
    // choice still wins — only the untouched default blue gets replaced.
    val useThemeAccent = accentChoice == com.shahabcodes.filestorm.data.Accent.BLUE
    val plainAccent = Color(if (dark) accentChoice.dark else accentChoice.light)

    val fs = when (appearance) {
        com.shahabcodes.filestorm.data.Appearance.BLOSSOM -> FsColors(
            accent = if (useThemeAccent) Blossom.Rose else plainAccent,
            groupedBackground = Blossom.Background,
            card = Blossom.Card,
            cardSecondary = Blossom.CardSecondary,
            label = Blossom.Label,
            secondaryLabel = Blossom.SecondaryLabel,
            separator = Blossom.Separator,
            fill = Blossom.Fill,
            green = Blossom.Green,
            red = Blossom.Red,
            orange = Blossom.Orange,
            isDark = false,
            kinds = Blossom.kinds,
        )

        com.shahabcodes.filestorm.data.Appearance.BLOSSOM_NIGHT -> FsColors(
            accent = if (useThemeAccent) Blossom.RoseNight else plainAccent,
            groupedBackground = Blossom.BackgroundNight,
            card = Blossom.CardNight,
            cardSecondary = Blossom.CardSecondaryNight,
            label = Blossom.LabelNight,
            secondaryLabel = Blossom.SecondaryLabelNight,
            separator = Blossom.SeparatorNight,
            fill = Blossom.FillNight,
            green = Blossom.GreenNight,
            red = Blossom.RedNight,
            orange = Blossom.OrangeNight,
            isDark = true,
            kinds = Blossom.kindsNight,
        )

        com.shahabcodes.filestorm.data.Appearance.SAKURA -> FsColors(
            accent = if (useThemeAccent) Sakura.Pink else plainAccent,
            groupedBackground = Sakura.Background,
            card = Sakura.Card,
            cardSecondary = Sakura.CardSecondary,
            label = Sakura.Label,
            secondaryLabel = Sakura.SecondaryLabel,
            separator = Sakura.Separator,
            fill = Sakura.Fill,
            green = Sakura.Green,
            red = Sakura.Red,
            orange = Sakura.Orange,
            isDark = false,
            kinds = Sakura.kinds,
        )

        com.shahabcodes.filestorm.data.Appearance.SAKURA_NIGHT -> FsColors(
            accent = if (useThemeAccent) Sakura.PinkNight else plainAccent,
            groupedBackground = Sakura.BackgroundNight,
            card = Sakura.CardNight,
            cardSecondary = Sakura.CardSecondaryNight,
            label = Sakura.LabelNight,
            secondaryLabel = Sakura.SecondaryLabelNight,
            separator = Sakura.SeparatorNight,
            fill = Sakura.FillNight,
            green = Sakura.GreenNight,
            red = Sakura.RedNight,
            orange = Sakura.OrangeNight,
            isDark = true,
            kinds = Sakura.kindsNight,
        )

        else -> if (dark) FsColors(
            accent = plainAccent,
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
            kinds = IosKindsDark,
        ) else FsColors(
            accent = plainAccent,
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
            kinds = IosKinds,
        )
    }

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

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !dark
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalFsColors provides fs) {
        MaterialTheme(colorScheme = scheme, typography = iosTypography, shapes = iosShapes, content = content)
    }
}
