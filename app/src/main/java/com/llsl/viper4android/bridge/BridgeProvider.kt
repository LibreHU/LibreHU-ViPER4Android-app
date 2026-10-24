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
 * master switch, then one page per effect with every parameter of it (the app's own labels, translated), built from the effects' own list
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
                switch("master", context.getString(R.string.master_enable), Effects.masterEnable.get(state)) { v -> change(Effects.masterEnable, v) }
            }
        return listOf(master) +
            EFFECT_GROUPS.map { group ->
                page(context, group.effectKey, "$app · ${label(context, SECTIONS[group.effectKey], group.effectKey)}", "equalizer") {
                    for (p in group.prefs) item(context, p, state) { pref, v -> change(pref, v) }
                }
            }
    }

    /** The items of one parameter (one per element for a list). */
    @Suppress("UNCHECKED_CAST")
    private fun KitBuilder.item(
        c: Context,
        p: EffectPref<*>,
        state: EffectState,
        change: (EffectPref<Any?>, Any?) -> Unit,
    ) {
        val id = p.jsonKey
        val label = label(c, LABELS["${p.effectKey}.${p.jsonKey}"] ?: LABELS[p.jsonKey], p.jsonKey)
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

        /** The app's own label (translated), else the key made readable. */
        fun label(
            c: Context,
            res: Int?,
            key: String,
        ): String = res?.let { c.getString(it) } ?: human(key)

        /** Each effect's title, as on the app's screen. */
        val SECTIONS =
            mapOf(
                "masterLimiter" to R.string.section_master_limiter,
                "playbackGainControl" to R.string.section_agc,
                "lufs" to R.string.section_lufs_targeting,
                "fetCompressor" to R.string.section_fet_compressor,
                "multibandCompressor" to R.string.section_multiband_compressor,
                "ddc" to R.string.section_ddc,
                "spectrumExtension" to R.string.section_spectrum_extension,
                "equalizer" to R.string.section_equalizer,
                "dynamicEq" to R.string.section_dynamic_eq,
                "convolver" to R.string.section_convolver,
                "fieldSurround" to R.string.section_field_surround,
                "diffSurround" to R.string.section_diff_surround,
                "stereoImager" to R.string.section_stereo_imager,
                "headphoneSurround" to R.string.section_headphone_surround,
                "reverb" to R.string.section_reverb,
                "dynamicSystem" to R.string.section_dynamic_system,
                "psychoacousticBass" to R.string.section_psycho_bass,
                "bass" to R.string.section_viper_bass,
                "bassMono" to R.string.section_viper_bass_mono,
                "clarity" to R.string.section_viper_clarity,
                "cure" to R.string.section_cure,
                "tubeSimulator" to R.string.section_tube_simulator,
                "analogX" to R.string.section_analogx,
                "speakerCorrection" to R.string.section_speaker_optimization,
            )

        /** Each parameter's label, as on the app's screen: "effect.key" first, then the key alone. */
        val LABELS =
            mapOf(
                "enable" to R.string.bridge_enable,
                "threshold" to R.string.label_threshold,
                "thresholds" to R.string.label_threshold,
                "maxGain" to R.string.label_max_gain,
                "strength" to R.string.label_strength,
                "gain" to R.string.label_gain,
                "gains" to R.string.label_gain,
                "attack" to R.string.label_attack,
                "attacks" to R.string.label_attack,
                "release" to R.string.label_release,
                "releases" to R.string.label_release,
                "frequency" to R.string.label_frequency,
                "freqs" to R.string.label_frequency,
                "width" to R.string.label_width,
                "depth" to R.string.label_depth,
                "delay" to R.string.label_delay,
                "mode" to R.string.label_mode,
                "presetId" to R.string.label_preset,
                "outputVolume" to R.string.label_output_volume,
                "channelPan" to R.string.label_output_pan,
                "masterLimiter.threshold" to R.string.label_output_limiter,
                "outputThreshold" to R.string.label_agc_output_threshold,
                "target" to R.string.label_lufs_target_lufs,
                "speed" to R.string.label_lufs_speed,
                "ratio" to R.string.label_fet_ratio,
                "ratios" to R.string.label_fet_ratio,
                "knee" to R.string.label_fet_knee,
                "knees" to R.string.label_fet_knee,
                "kneeAuto" to R.string.label_fet_auto_knee,
                "kneeAutos" to R.string.label_fet_auto_knee,
                "kneeMulti" to R.string.label_fet_knee_multi,
                "kneeMultis" to R.string.label_fet_knee_multi,
                "gainAuto" to R.string.label_fet_auto_gain,
                "gainAutos" to R.string.label_fet_auto_gain,
                "attackAuto" to R.string.label_fet_auto_attack,
                "attackAutos" to R.string.label_fet_auto_attack,
                "maxAttack" to R.string.label_fet_max_attack,
                "maxAttacks" to R.string.label_fet_max_attack,
                "releaseAuto" to R.string.label_fet_auto_release,
                "releaseAutos" to R.string.label_fet_auto_release,
                "maxRelease" to R.string.label_fet_max_release,
                "maxReleases" to R.string.label_fet_max_release,
                "crest" to R.string.label_fet_crest,
                "crests" to R.string.label_fet_crest,
                "adapt" to R.string.label_fet_adapt,
                "adapts" to R.string.label_fet_adapt,
                "noClip" to R.string.label_fet_no_clip,
                "noClips" to R.string.label_fet_no_clip,
                "bandEnables" to R.string.label_band_enabled,
                "crossovers" to R.string.label_upper_crossover,
                "ddc.device" to R.string.label_ddc_device,
                "exciter" to R.string.label_vse_exciter,
                "bandCount" to R.string.label_eq_bands,
                "bands" to R.string.bridge_band,
                "qs" to R.string.label_dynamic_eq_q_factor,
                "filterTypes" to R.string.label_dynamic_eq_filter_type,
                "kernelFile" to R.string.label_convolver_kernel,
                "crossChannel" to R.string.label_convolver_cross_channel,
                "widening" to R.string.label_field_surround_widening,
                "midImage" to R.string.label_field_surround_mid_image,
                "reverse" to R.string.label_diff_surround_reverse,
                "wetDryMix" to R.string.label_diff_surround_wet_dry_mix,
                "lpCutoff" to R.string.label_diff_surround_lp_cutoff,
                "lowWidth" to R.string.label_stereo_imager_low_width,
                "midWidth" to R.string.label_stereo_imager_mid_width,
                "highWidth" to R.string.label_stereo_imager_high_width,
                "lowCrossover" to R.string.label_stereo_imager_low_crossover,
                "highCrossover" to R.string.label_stereo_imager_high_crossover,
                "quality" to R.string.label_vhe_quality,
                "roomSize" to R.string.label_reverb_room_size,
                "damp" to R.string.label_reverb_dampening,
                "wet" to R.string.label_reverb_wet,
                "dry" to R.string.label_reverb_dry,
                "dynamicSystem.strength" to R.string.label_dynamic_system_strength,
                "dynamicSystem.device" to R.string.bridge_device,
                "xLow" to R.string.label_dynamic_system_x_low_freq,
                "xHigh" to R.string.label_dynamic_system_x_high_freq,
                "yLow" to R.string.label_dynamic_system_y_low_freq,
                "yHigh" to R.string.label_dynamic_system_y_high_freq,
                "sideGainLow" to R.string.label_dynamic_system_side_gain_low,
                "sideGainHigh" to R.string.label_dynamic_system_side_gain_high,
                "cutoff" to R.string.label_psycho_bass_cutoff,
                "intensity" to R.string.label_psycho_bass_intensity,
                "harmonicOrder" to R.string.label_psycho_bass_harmonic_order,
                "originalLevel" to R.string.label_psycho_bass_ori_bass_level,
                "antiPop" to R.string.label_bass_anti_pop,
                "crossfeedPreset" to R.string.label_cure_strength,
                "model" to R.string.label_tube_model,
                "drive" to R.string.label_tube_drive,
                "mix" to R.string.label_tube_mix,
            )

        /** `fetCompressor` → "Fet compressor", `kneeAuto` → "Knee auto". */
        fun human(key: String): String =
            key
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .replace('_', ' ')
                .lowercase()
                .replaceFirstChar { it.uppercase() }
    }
}
