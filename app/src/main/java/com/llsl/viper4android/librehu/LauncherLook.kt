package com.llsl.viper4android.librehu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * LibreHU: follows LibreHU Launcher's look, as the LibreHU apps do. The launcher publishes it (content provider
 * `org.librehu.launcher.theme`, broadcast `org.librehu.action.THEME_CHANGED`): light / dark, accent colour, style
 * (modern, retro…, factory = Modularer Infotainment-Baukasten, ambient = LHU-Link) and key click sound. Light / dark
 * and the accent are applied by LibreHuTheme; this adds the car styles and the clicks. Without the launcher nothing
 * changes.
 */
object LauncherLook {
    /** True once the launcher answered; false: ViPER's own colours. */
    var present: Boolean by mutableStateOf(false)
        private set
    var dark: Boolean by mutableStateOf(true)
        private set

    /** ARGB, 0: none. */
    var accent: Int by mutableStateOf(0)
        private set
    var style: String by mutableStateOf("modern")
        private set
    var click: String? by mutableStateOf(null)
        private set

    val factory: Boolean get() = present && style == "factory"
    val ambient: Boolean get() = present && style == "ambient"

    /** One of the launcher's car styles: black, square, the accent colour as the only colour. */
    val styled: Boolean get() = factory || ambient

    /** The key click to play (a name of [KeyClick.SOUNDS] or `off`): the launcher's choice, else its style's. */
    val clickSound: String get() = if (!present) "off" else click ?: KeyClick.default(style)

    private const val ACTION = "org.librehu.action.THEME_CHANGED"
    private val URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")
    private var started = false

    /** Reads the launcher's look and follows its changes (once per process). */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    i: Intent,
                ) {
                    present = true
                    dark = i.getBooleanExtra("dark", true)
                    accent = i.getIntExtra("accent", 0)
                    style = i.getStringExtra("style") ?: "modern"
                    click = i.getStringExtra("click")
                }
            },
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            app.contentResolver.registerContentObserver(
                URI,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = query(app)
                },
            )
        } catch (_: SecurityException) {
        }
        query(app)
    }

    private fun query(context: Context) {
        try {
            context.contentResolver.query(URI, null, null, null, null)?.use { c -> if (c.moveToFirst()) read(c) }
        } catch (_: Exception) {
        }
    }

    private fun read(c: Cursor) {
        present = true
        dark = c.getInt(c.getColumnIndexOrThrow("dark")) != 0
        accent = c.getInt(c.getColumnIndexOrThrow("accent"))
        style = c.columnString("style") ?: "modern"
        click = c.columnString("click")
    }

    private fun Cursor.columnString(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { getString(it) }

    /**
     * In the launcher's car styles, [base] (the launcher's accent, see LibreHuTheme) on black: black background and
     * title bar, near-black panels; [base] as it is otherwise.
     */
    fun styledScheme(base: ColorScheme): ColorScheme {
        if (!styled) return base
        val surface = if (factory) Color(0xFF111215) else Color(0xFF0B0F18)
        val high = if (factory) Color(0xFF1D1E22) else Color(0xFF121A2A)
        val light = Color(0xFFEEF2F6)
        return base.copy(
            primaryContainer = Color.Black,
            onPrimaryContainer = light,
            secondaryContainer = lerp(surface, base.primary, 0.35f),
            onSecondaryContainer = Color.White,
            tertiaryContainer = lerp(surface, base.primary, 0.35f),
            onTertiaryContainer = Color.White,
            background = Color.Black,
            onBackground = light,
            surface = Color.Black,
            onSurface = light,
            surfaceVariant = high,
            onSurfaceVariant = Color(0xFF98A3B0),
            surfaceTint = Color.Transparent,
            surfaceBright = high,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = high,
            surfaceContainerHighest = high,
            outline = Color(0xFF3A3B3F),
            outlineVariant = Color(0xFF2C2D31),
        )
    }

    /** Square corners in the car styles, like the launcher's; ViPER's otherwise. */
    fun shapes(fallback: Shapes): Shapes {
        if (!styled) return fallback
        val square = RoundedCornerShape(0)
        return Shapes(extraSmall = square, small = square, medium = square, large = square, extraLarge = square)
    }

    /** The frame of an effect card: the accent colour (LHU-Link), a dark line (factory), none otherwise. */
    fun cardBorder(): BorderStroke? =
        when {
            ambient -> BorderStroke(1.dp, Color(accent).takeIf { accent != 0 } ?: Color(0xFF0078DC))
            factory -> BorderStroke(1.dp, Color(0xFF2C2D31))
            else -> null
        }

    /** The line under the title bar in the car styles. */
    fun accentLine(): Color? = if (styled) Color(accent).takeIf { accent != 0 } ?: Color(0xFF0078DC) else null

}
