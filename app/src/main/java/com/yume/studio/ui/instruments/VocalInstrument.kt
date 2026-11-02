package com.yume.studio.ui.instruments

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yume.studio.audio.FxDef
import com.yume.studio.audio.stepValue
import com.yume.studio.model.EffectSlot
import com.yume.studio.model.Track
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.YumeToggle
import com.yume.studio.ui.components.toMeter
import com.yume.studio.ui.studio.StudioMode
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText

/** Тумблеры как на макете: Тюнинг / Компрессор / Реверберация / Дилей. */
private val VOCAL_FX = listOf(FxDef.PITCH, FxDef.COMPRESSOR, FxDef.REVERB, FxDef.DELAY)

@Composable
fun VocalInstrument(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val transport by vm.transport.collectAsState()
    val wave by vm.inputWave.collectAsState()
    val project by vm.project.collectAsState()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) vm.openInput()
    }
    LaunchedEffect(granted) { if (granted) vm.openInput() }
    DisposableEffect(Unit) { onDispose { vm.closeInput() } }

    fun toggleFx(def: FxDef, on: Boolean) {
        val slot = track.effects.indexOfFirst { it.type == def.type }
        if (slot >= 0) {
            if (track.effects[slot].enabled != on) vm.toggleFx(index, slot)
        } else if (on) {
            vm.addFx(index, def.type)
        }
        // тюнинг сразу настраиваем под тональность проекта
        if (def == FxDef.PITCH && on) {
            val p = project ?: return
            val effects = vm.project.value?.tracks?.getOrNull(index)?.effects ?: return
            val s = effects.indexOfFirst { it.type == def.type }
            if (s >= 0) {
                vm.setFxParam(index, s, 1, def.params[1].stepValue(p.keyRoot))
                vm.setFxParam(index, s, 2, def.params[2].stepValue(if (p.minor) 2 else 1))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.55f)) {
            ArtHeader(vm, index, track, Modifier.weight(0.64f).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(0.36f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(12.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                VOCAL_FX.forEach { def ->
                    val slot: EffectSlot? = track.effects.firstOrNull { it.type == def.type }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(def.title, style = YumeText.body, color = c.text, modifier = Modifier.weight(1f))
                        YumeToggle(slot?.enabled == true, { toggleFx(def, it) })
                    }
                }
                Pill("Все эффекты", Icons.Rounded.Tune, height = 30.dp) { vm.setMode(StudioMode.FX) }
            }
        }
        Spacer(Modifier.padding(3.dp))
        Row(Modifier.weight(0.45f).fillMaxWidth()) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!granted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Нужен доступ к микрофону, чтобы записывать голос", style = YumeText.body, color = c.textDim)
                        Spacer(Modifier.padding(4.dp))
                        Pill("Разрешить микрофон", Icons.Rounded.Mic, selected = true) { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                } else {
                    InputWave(wave, recording = transport.recording, modifier = Modifier.fillMaxSize())
                    Text(
                        if (transport.recording) "● идёт запись" else "Нажми ● сверху, чтобы записать",
                        style = YumeText.small, color = if (transport.recording) c.danger else c.textFaint,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.width(170.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(10.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Мониторинг", style = YumeText.small, color = c.text, modifier = Modifier.weight(1f))
                    YumeToggle(ui.monitoring, { if (granted) vm.setMonitoring(it) })
                }
                Text("Слушай себя с эффектами — только в наушниках, иначе будет фон", style = YumeText.tiny, color = c.textFaint)
                Pill(if (transport.recording) "Стоп" else "Запись", if (transport.recording) Icons.Rounded.Headphones else Icons.Rounded.Circle,
                    color = c.danger, contentColor = Color.White, enabled = granted) { vm.toggleRecord() }
            }
        }
    }
}

@Composable
private fun InputWave(values: List<Float>, recording: Boolean, modifier: Modifier) {
    val c = Yume.colors
    Canvas(modifier) {
        val n = values.size
        val w = size.width / n
        val mid = size.height / 2
        values.forEachIndexed { i, v ->
            val h = toMeter(v) * size.height * 0.9f
            drawLine(
                if (recording) c.danger.copy(alpha = 0.85f) else c.primary.copy(alpha = 0.85f),
                Offset(i * w + w / 2, mid - h / 2), Offset(i * w + w / 2, mid + h / 2), strokeWidth = w * 0.6f,
            )
        }
    }
}
