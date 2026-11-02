package com.yume.studio.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.SynthParam
import com.yume.studio.model.Track
import com.yume.studio.ui.components.Knob
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

private enum class SynthTab(val title: String) { OSC("Осциллятор"), ENV("Огибающая"), FILTER("Фильтр"), MOD("Модуляция") }

@Composable
fun SynthInstrument(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    var tab by remember { mutableStateOf(SynthTab.ENV) }
    val p = track.synthParams
    fun v(id: Int) = p.getOrElse(id) { 0.5f }
    fun set(id: Int, value: Float) = vm.setSynthParam(index, id, value)

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.5f)) {
            ArtHeader(vm, index, track, Modifier.weight(0.42f).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(0.58f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Segmented(SynthTab.entries, tab, { it.title }, { tab = it }, height = 26.dp)
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WaveScope(p, Modifier.width(150.dp).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        when (tab) {
                            SynthTab.OSC -> {
                                Knob(v(SynthParam.MIX2), { set(SynthParam.MIX2, it) }, "Осц. 2", default = 0.35f)
                                Knob(v(SynthParam.DETUNE2), { set(SynthParam.DETUNE2, it) }, "Строй 2", default = 0.5f,
                                    valueText = "%+d пт".format(((v(SynthParam.DETUNE2) - 0.5f) * 24).toInt()))
                                Knob(v(SynthParam.UNISON), { set(SynthParam.UNISON, it) }, "Унисон", default = 0f)
                                Knob(v(SynthParam.GLIDE), { set(SynthParam.GLIDE, it) }, "Глайд", default = 0f)
                                Knob(v(SynthParam.VOLUME), { set(SynthParam.VOLUME, it) }, "Громк.", default = 0.7f)
                            }
                            SynthTab.ENV -> {
                                Knob(v(SynthParam.A), { set(SynthParam.A, it) }, "Атака", default = 0.1f)
                                Knob(v(SynthParam.D), { set(SynthParam.D, it) }, "Затух.", default = 0.45f)
                                Knob(v(SynthParam.S), { set(SynthParam.S, it) }, "Сустейн", default = 0.7f)
                                Knob(v(SynthParam.R), { set(SynthParam.R, it) }, "Релиз", default = 0.4f)
                            }
                            SynthTab.FILTER -> {
                                Knob(v(SynthParam.CUTOFF), { set(SynthParam.CUTOFF, it) }, "Срез", default = 0.55f, color = c.accent)
                                Knob(v(SynthParam.RES), { set(SynthParam.RES, it) }, "Резонанс", default = 0.25f, color = c.accent)
                                Knob(v(SynthParam.FENV), { set(SynthParam.FENV, it) }, "Огиб.", default = 0.35f, color = c.accent)
                                Knob(v(SynthParam.FA), { set(SynthParam.FA, it) }, "Ф.атака", default = 0.2f, color = c.accent)
                                Knob(v(SynthParam.FD), { set(SynthParam.FD, it) }, "Ф.затух.", default = 0.5f, color = c.accent)
                            }
                            SynthTab.MOD -> {
                                Knob(v(SynthParam.LFO_RATE), { set(SynthParam.LFO_RATE, it) }, "LFO", default = 0.3f, color = c.mint)
                                Knob(v(SynthParam.LFO_PITCH), { set(SynthParam.LFO_PITCH, it) }, "Вибрато", default = 0.05f, color = c.mint)
                                Knob(v(SynthParam.LFO_CUTOFF), { set(SynthParam.LFO_CUTOFF, it) }, "Вау", default = 0.1f, color = c.mint)
                                Knob(v(SynthParam.MONO), { set(SynthParam.MONO, if (it > 0.5f) 1f else 0f) }, "Моно",
                                    default = 0f, valueText = if (v(SynthParam.MONO) > 0.5f) "Моно" else "Поли", color = c.mint)
                            }
                        }
                    }
                }
                // выбор форм волны, как на макете
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val wIdx = SynthParam.waveIndex(v(if (tab == SynthTab.OSC) SynthParam.WAVE2 else SynthParam.WAVE1))
                    (0..4).forEach { w ->
                        val sel = w == wIdx
                        Box(
                            Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) c.primary else c.surfaceHigh)
                                .clickable { set(if (tab == SynthTab.OSC) SynthParam.WAVE2 else SynthParam.WAVE1, SynthParam.waveValue(w)) },
                            contentAlignment = Alignment.Center,
                        ) { WaveIcon(w, if (sel) c.onPrimary else c.text) }
                    }
                }
                if (tab == SynthTab.OSC) Text("Формы волны — для осциллятора 2", style = YumeText.tiny, color = c.textFaint)
            }
        }
        Spacer(Modifier.height(4.dp))
        KeyboardSurface(vm, index, Modifier.weight(0.5f), defaultStart = 48)
    }
}

private fun wave(w: Int, t: Float): Float = when (w) {
    0 -> sin(2 * PI * t).toFloat()
    1 -> 1f - 4f * abs(t - 0.5f)
    2 -> 2f * t - 1f
    3 -> if (t < 0.5f) 0.8f else -0.8f
    else -> (sin(t * 91.7f) * sin(t * 317.3f + 1.3f)) * 0.8f
}

@Composable
private fun WaveIcon(w: Int, color: Color) {
    Canvas(Modifier.size(34.dp, 18.dp)) {
        val path = Path()
        for (i in 0..40) {
            val t = (i / 40f * 2f) % 1f
            val x = size.width * i / 40f
            val y = size.height / 2 - wave(w, t) * size.height * 0.42f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
    }
}

/** Экран-«осциллограф»: смесь двух волн + огибающая ADSR. */
@Composable
private fun WaveScope(p: List<Float>, modifier: Modifier) {
    val c = Yume.colors
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A1B24))) {
        val w1 = SynthParam.waveIndex(p.getOrElse(SynthParam.WAVE1) { 0.5f })
        val w2 = SynthParam.waveIndex(p.getOrElse(SynthParam.WAVE2) { 0.5f })
        val mix = p.getOrElse(SynthParam.MIX2) { 0.3f }
        val det = 2f.pow((p.getOrElse(SynthParam.DETUNE2) { 0.5f } - 0.5f) * 2f)
        val cut = p.getOrElse(SynthParam.CUTOFF) { 0.5f }
        val path = Path()
        val n = 120
        for (i in 0..n) {
            val t = i / n.toFloat() * 2f
            val s = wave(w1, t % 1f) * (1 - mix) + wave(w2, (t * det) % 1f) * mix
            val x = size.width * i / n
            val y = size.height * 0.42f - s * size.height * 0.28f * (0.5f + cut * 0.5f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, c.primary, style = Stroke(2.5.dp.toPx()))
        // ADSR
        val a = p.getOrElse(SynthParam.A) { 0.1f }
        val d = p.getOrElse(SynthParam.D) { 0.4f }
        val s = p.getOrElse(SynthParam.S) { 0.7f }
        val r = p.getOrElse(SynthParam.R) { 0.4f }
        val base = size.height * 0.95f
        val top = size.height * 0.62f
        val wA = size.width * 0.22f * (0.15f + a)
        val wD = size.width * 0.22f * (0.15f + d)
        val wS = size.width * 0.2f
        val wR = size.width * 0.22f * (0.15f + r)
        val sy = base - (base - top) * s
        val env = Path().apply {
            moveTo(4f, base); lineTo(4f + wA, top); lineTo(4f + wA + wD, sy); lineTo(4f + wA + wD + wS, sy); lineTo(4f + wA + wD + wS + wR, base)
        }
        drawPath(env, c.mint.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
        drawLine(Color(0x22FFFFFF), Offset(0f, size.height * 0.42f), Offset(size.width, size.height * 0.42f), 1f)
    }
}
