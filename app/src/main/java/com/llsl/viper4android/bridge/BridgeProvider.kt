package com.llsl.viper4android.bridge

import android.content.Context
import com.llsl.viper4android.R
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.effect.BoolListPref
import com.llsl.viper4android.effect.BoolPref
import com.llsl.viper4android.effect.DoubleListPref
import com.llsl.viper4android.effect.EFFECT_GROUPS
import com.llsl.viper4android.effect.EffectPref
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.FloatListPref
import com.llsl.viper4android.effect.FloatPref
import com.llsl.viper4android.effect.IntListPref
import com.llsl.viper4android.effect.IntPref
import com.llsl.viper4android.effect.StringPref
import com.llsl.viper4android.effect.loadEffectStateFromPrefs
import com.llsl.viper4android.effect.saveEffectPrefs
import com.llsl.viper4android.service.ViperService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViPER4Android from A to Z for LibreHU's phone app (through LibreHU-service's bridge, [BridgeKitProvider]): the
 * master switch, then one page per effect with every parameter of it, built from the effects' own list
 * ([EFFECT_GROUPS]: each parameter with its range), so that nothing is left out and a new parameter shows up by itself.
 * A change is saved where the app saves it ([saveEffectPrefs]) and the service applies the whole state again. Ranges
 * of decimal values are cut in 1000 steps; parameters without a range are typed. Not here: presets, DDC and convolver
 * files (picked on the head unit).
 */
class BridgeProvider : BridgeKitProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun repository(): ViperRepository
    }

    private fun repo(c: Context) = EntryPointAccessors.fromApplication(c, Entry::class.java).repository()

    private fun load(c: Context): EffectState? = runBlocking(Dispatchers.IO) { withTimeoutOrNull(5_000) { loadEffectStateFromPrefs(repo(c)) } }

    private fun save(
        c: Context,
        s: EffectState,
    ) {
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(5_000) { saveEffectPrefs(repo(c), s) } }
        // The service reads the saved state again and pushes all of it to the effect.
        runCatching { ViperService.toggleMaster(c, s.masterEnable) }
    }

    override fun pages(context: Context): List<KitPage> {
        val state = load(context) ?: return emptyList()
        val app = context.getString(R.string.app_name)

        fun <T> change(
            pref: EffectPref<T>,
            v: T,
        ) {
            val now = load(context) ?: return
            save(context, pref.set(now, v))
        }
        val master =
            page(context, "master", app, "equalizer") {
                switch("master", app, Effects.masterEnable.get(state)) { v -> change(Effects.masterEnable, v) }
            }
        return listOf(master) +
            EFFECT_GROUPS.map { group ->
                page(context, group.effectKey, "$app · ${human(group.effectKey)}", "equalizer") {
                    for (p in group.prefs) item(p, state) { pref, v -> change(pref, v) }
                }
            }
    }

    /** The items of one parameter (one per element for a list). */
    @Suppress("UNCHECKED_CAST")
    private fun KitBuilder.item(
        p: EffectPref<*>,
        state: EffectState,
        change: (EffectPref<Any?>, Any?) -> Unit,
    ) {
        val id = p.jsonKey
        val label = human(p.jsonKey)
        val set = { v: Any? -> change(p as EffectPref<Any?>, v) }
        when (p) {
            is BoolPref -> {
                switch(id, label, p.get(state)) { set(it) }
            }

            is IntPref -> {
                val r = p.range
                if (r != null) slider(id, label, p.get(state), r.first, r.last) { set(it) } else number(id, label, p.get(state).toString()) { it.toIntOrNull()?.let(set) }
            }

            is FloatPref -> {
                val r = p.range
                if (r != null) {
                    fraction(id, label, p.get(state).toDouble(), r.start.toDouble(), r.endInclusive.toDouble()) { set(it.toFloat()) }
                } else {
                    number(id, label, p.get(state).toString()) { it.toFloatOrNull()?.let(set) }
                }
            }

            is StringPref -> {
                text(id, label, p.get(state), 200) { set(it) }
            }

            is IntListPref -> {
                val list = p.get(state)
                val r = p.range
                list.forEachIndexed { i, v ->
                    val put = { x: Int -> set(list.toMutableList().also { it[i] = x }) }
                    if (r != null) slider("${id}_$i", "$label ${i + 1}", v, r.first, r.last) { put(it) } else number("${id}_$i", "$label ${i + 1}", "$v") { it.toIntOrNull()?.let(put) }
                }
            }

            is FloatListPref -> {
                val list = p.get(state)
                val r = p.range
                list.forEachIndexed { i, v ->
                    val put = { x: Float -> set(list.toMutableList().also { it[i] = x }) }
                    if (r != null) {
                        fraction("${id}_$i", "$label ${i + 1}", v.toDouble(), r.start.toDouble(), r.endInclusive.toDouble()) { put(it.toFloat()) }
                    } else {
                        number("${id}_$i", "$label ${i + 1}", "$v") { it.toFloatOrNull()?.let(put) }
                    }
                }
            }

            is DoubleListPref -> {
                val list = p.get(state)
                val r = p.range
                list.forEachIndexed { i, v ->
                    val put = { x: Double -> set(list.toMutableList().also { it[i] = x }) }
                    if (r != null) fraction("${id}_$i", "$label ${i + 1}", v, r.start, r.endInclusive) { put(it) } else number("${id}_$i", "$label ${i + 1}", "$v") { it.toDoubleOrNull()?.let(put) }
                }
            }

            is BoolListPref -> {
                val list = p.get(state)
                list.forEachIndexed { i, v -> switch("${id}_$i", "$label ${i + 1}", v) { x -> set(list.toMutableList().also { it[i] = x }) } }
            }

            else -> {
                Unit
            }
        }
    }

    /** A decimal value in [min, max] as a slider of 1000 steps, written as the value itself. */
    private fun KitBuilder.fraction(
        id: String,
        label: String,
        value: Double,
        min: Double,
        max: Double,
        set: (Double) -> Unit,
    ) {
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pos = (((value - min) / span) * STEPS).toInt().coerceIn(0, STEPS)
        slider(id, label, pos, 0, STEPS, display = { "%.3f".format(min + span * it / STEPS) }) { set(min + span * it / STEPS) }
    }

    private fun KitBuilder.number(
        id: String,
        label: String,
        value: String,
        set: (String) -> Unit,
    ) = text(id, label, value, 24) { set(it.trim()) }

    private companion object {
        const val STEPS = 1000

        /** `fetCompressor` → "Fet compressor", `kneeAuto` → "Knee auto". */
        fun human(key: String): String =
            key
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .replace('_', ' ')
                .lowercase()
                .replaceFirstChar { it.uppercase() }
    }
}
