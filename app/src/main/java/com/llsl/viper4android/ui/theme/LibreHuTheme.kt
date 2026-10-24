package com.llsl.viper4android.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat

/** Effective dark theme of the app, provided by [ViperTheme]. */
val LocalDarkTheme = compositionLocalOf { true }

private const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"
private val LAUNCHER_THEME_URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")

/** Light / dark theme of LibreHU Launcher (null when it is not installed). */
private fun readLauncherDark(context: Context): Boolean? = readLauncherTheme(context)?.first

/** LibreHU Launcher's theme: dark, accent ARGB (0 = default); null when the launcher is not installed. */
private fun readLauncherTheme(context: Context): Pair<Boolean, Int>? =
    try {
        context.contentResolver.query(LAUNCHER_THEME_URI, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getInt(0) != 0) to c.getInt(1) else null
        }
    } catch (_: Exception) {
        null
    }

/**
 * LibreHU: follows LibreHU Launcher's theme (light, dark, or automatic with the headlights / time of day), live;
 * without the launcher, Android's dark theme as before.
 */
@Composable
fun rememberLibreHuDarkTheme(): Boolean {
    val system = isSystemInDarkTheme()
    val context = LocalContext.current
    var launcher by remember { mutableStateOf(readLauncherDark(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    launcher = intent.getBooleanExtra("dark", true)
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    launcher = readLauncherDark(context)
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_THEME_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.contentResolver.registerContentObserver(LAUNCHER_THEME_URI, false, observer)
        } catch (_: SecurityException) {
        }
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return launcher ?: system
}

/** Accent of LibreHU Launcher, kept up to date like the theme; null when the launcher is not installed. */
@Composable
fun rememberLibreHuAccent(): Int? {
    val context = LocalContext.current
    var accent by remember { mutableStateOf(readLauncherTheme(context)?.second) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    accent = intent.getIntExtra("accent", 0)
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    accent = readLauncherTheme(context)?.second
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_THEME_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.contentResolver.registerContentObserver(LAUNCHER_THEME_URI, false, observer)
        } catch (_: SecurityException) {
        }
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return accent
}

/**
 * Retro style of LibreHU Launcher (VFD amber, green LCD, cassette) with its palette: provider columns / broadcast
 * extras `style` (`retro_…`), `background`, `surface`, `surface_high`, `accent`, `on_accent`, `text`, `text_dim`.
 */
data class LibreHuRetro(
    val dark: Boolean,
    val colors: List<Color>,
)

private val RETRO_COLORS = listOf("background", "surface", "surface_high", "accent", "on_accent", "text", "text_dim")

private fun readLauncherRetro(context: Context): LibreHuRetro? =
    try {
        context.contentResolver.query(LAUNCHER_THEME_URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val style = c.getString(c.getColumnIndexOrThrow("style"))
            if (style == null || !style.startsWith("retro")) return@use null
            LibreHuRetro(c.getInt(0) != 0, RETRO_COLORS.map { Color(c.getInt(c.getColumnIndexOrThrow(it))) })
        }
    } catch (_: Exception) {
        null
    }

private fun retroFrom(intent: Intent): LibreHuRetro? {
    val style = intent.getStringExtra("style")
    if (style == null || !style.startsWith("retro") || RETRO_COLORS.any { !intent.hasExtra(it) }) return null
    return LibreHuRetro(intent.getBooleanExtra("dark", true), RETRO_COLORS.map { Color(intent.getIntExtra(it, 0)) })
}

/** LibreHU Launcher's retro style, kept up to date like the theme; null for the modern look or without the launcher. */
@Composable
fun rememberLibreHuRetro(): LibreHuRetro? {
    val context = LocalContext.current
    var retro by remember { mutableStateOf(readLauncherRetro(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    retro = retroFrom(intent)
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    retro = readLauncherRetro(context)
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_THEME_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.contentResolver.registerContentObserver(LAUNCHER_THEME_URI, false, observer)
        } catch (_: SecurityException) {
        }
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return retro
}

/** Segment display font of the launcher's retro styles (LibreHU Seg14: DSEG14, capitals without accents, SIL OFL 1.1). */
fun libreHuTypography(retro: LibreHuRetro?): Typography {
    if (retro == null) return Typography()
    val f = FontFamily(Font(com.llsl.viper4android.R.font.librehu_seg14))
    return Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = f),
            displayMedium = displayMedium.copy(fontFamily = f),
            displaySmall = displaySmall.copy(fontFamily = f),
            headlineLarge = headlineLarge.copy(fontFamily = f),
            headlineMedium = headlineMedium.copy(fontFamily = f),
            headlineSmall = headlineSmall.copy(fontFamily = f),
            titleLarge = titleLarge.copy(fontFamily = f),
            titleMedium = titleMedium.copy(fontFamily = f),
            titleSmall = titleSmall.copy(fontFamily = f),
            bodyLarge = bodyLarge.copy(fontFamily = f),
            bodyMedium = bodyMedium.copy(fontFamily = f),
            bodySmall = bodySmall.copy(fontFamily = f),
            labelLarge = labelLarge.copy(fontFamily = f),
            labelMedium = labelMedium.copy(fontFamily = f),
            labelSmall = labelSmall.copy(fontFamily = f),
        )
    }
}

/**
 * Car palette of the LibreHU apps, every Material role on the launcher accent (default blue when it is 0); the
 * launcher's retro palette instead when its style is retro (same light / dark).
 */
fun libreHuColorScheme(
    dark: Boolean,
    accentArgb: Int,
    retro: LibreHuRetro? = null,
): ColorScheme {
    val r = retro?.takeIf { it.dark == dark }?.colors
    val accent = r?.get(3) ?: if (accentArgb != 0) Color(accentArgb) else Color(if (dark) 0xFF8AB4F8 else 0xFF1A73E8)
    val background = r?.get(0) ?: if (dark) Color(0xFF000000) else Color(0xFFF1F3F4)
    val surface = r?.get(1) ?: if (dark) Color(0xFF1E1F22) else Color(0xFFFFFFFF)
    val high = r?.get(2) ?: if (dark) Color(0xFF2B2D31) else Color(0xFFE8EAED)
    val onAccent = r?.get(4) ?: if (dark) Color(0xFF202124) else Color(0xFFFFFFFF)
    val text = r?.get(5) ?: if (dark) Color(0xFFE8EAED) else Color(0xFF202124)
    val dim = r?.get(6) ?: if (dark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val container = lerp(surface, accent, 0.30f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = high,
        onSurfaceVariant = dim,
        surfaceTint = Color.Transparent,
        surfaceBright = high,
        surfaceDim = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = high,
        surfaceContainerHighest = high,
        outline = dim,
        outlineVariant = lerp(high, dim, 0.35f),
    )
}
