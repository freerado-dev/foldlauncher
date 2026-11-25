package sk.fold.launcher.ui

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import sk.fold.launcher.data.FontChoice
import sk.fold.launcher.data.Settings
import sk.fold.launcher.data.ThemeMode

val LocalDark = staticCompositionLocalOf { false }
val LocalFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

fun fontFamily(f: FontChoice): FontFamily = when (f) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
    FontChoice.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    FontChoice.LIGHT -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
}

private fun typographyFor(ff: FontFamily): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = ff),
        displayMedium = t.displayMedium.copy(fontFamily = ff),
        displaySmall = t.displaySmall.copy(fontFamily = ff),
        headlineLarge = t.headlineLarge.copy(fontFamily = ff),
        headlineMedium = t.headlineMedium.copy(fontFamily = ff),
        headlineSmall = t.headlineSmall.copy(fontFamily = ff),
        titleLarge = t.titleLarge.copy(fontFamily = ff),
        titleMedium = t.titleMedium.copy(fontFamily = ff),
        titleSmall = t.titleSmall.copy(fontFamily = ff),
        bodyLarge = t.bodyLarge.copy(fontFamily = ff),
        bodyMedium = t.bodyMedium.copy(fontFamily = ff),
        bodySmall = t.bodySmall.copy(fontFamily = ff),
        labelLarge = t.labelLarge.copy(fontFamily = ff),
        labelMedium = t.labelMedium.copy(fontFamily = ff),
        labelSmall = t.labelSmall.copy(fontFamily = ff),
    )
}

@Composable
fun FoldTheme(s: Settings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (s.theme) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (s.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val a = Color(s.accent)
        if (dark) darkColorScheme(primary = a, secondary = a, tertiary = a)
        else lightColorScheme(primary = a, secondary = a, tertiary = a)
    }
    val ff = fontFamily(s.font)
    CompositionLocalProvider(LocalDark provides dark, LocalFont provides ff) {
        MaterialTheme(colorScheme = scheme, typography = typographyFor(ff), content = content)
    }
}

/** Haptická odozva podľa nastavenia. */
fun buzz(h: androidx.compose.ui.hapticfeedback.HapticFeedback, t: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
    if (sk.fold.launcher.data.Store.settings.value.haptics) h.performHapticFeedback(t)
}
