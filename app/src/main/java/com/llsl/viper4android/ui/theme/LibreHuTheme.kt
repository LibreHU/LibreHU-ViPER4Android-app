package com.llsl.viper4android.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Theme published by LibreHU Launcher: night mode and accent, or null when the launcher is not installed. */
data class LibreHuTheme(
    val dark: Boolean,
    val accent: Int,
)

private val URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")
private const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"

private fun query(context: Context): LibreHuTheme? =
    try {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) LibreHuTheme(c.getInt(0) != 0, c.getInt(1)) else null
        }
    } catch (_: Exception) {
        null
    }

/** LibreHU Launcher's theme, kept up to date (content observer and THEME_CHANGED broadcast). */
@Composable
fun rememberLibreHuTheme(): LibreHuTheme? {
    val context = LocalContext.current.applicationContext
    var theme by remember { mutableStateOf(query(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    theme = LibreHuTheme(intent.getBooleanExtra("dark", true), intent.getIntExtra("accent", 0))
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    theme = query(context)
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_THEME_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.contentResolver.registerContentObserver(URI, false, observer)
        } catch (_: SecurityException) {
        }
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return theme
}

/** Same car palette as the LibreHU apps (black / light grey surfaces, launcher accent on every Material role). */
fun libreHuColorScheme(theme: LibreHuTheme): ColorScheme {
    val dark = theme.dark
    val accent = if (theme.accent != 0) Color(theme.accent) else Color(if (dark) 0xFF8AB4F8 else 0xFF1A73E8)
    val background = if (dark) Color(0xFF000000) else Color(0xFFF1F3F4)
    val surface = if (dark) Color(0xFF1E1F22) else Color(0xFFFFFFFF)
    val high = if (dark) Color(0xFF2B2D31) else Color(0xFFE8EAED)
    val onAccent = if (dark) Color(0xFF202124) else Color(0xFFFFFFFF)
    val text = if (dark) Color(0xFFE8EAED) else Color(0xFF202124)
    val dim = if (dark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
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
