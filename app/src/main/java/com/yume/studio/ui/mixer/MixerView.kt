package com.yume.studio.ui.mixer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.NativeEngine
import com.yume.studio.model.Track
import com.yume.studio.ui.components.Fader
import com.yume.studio.ui.components.Knob
import com.yume.studio.ui.components.LevelMeter
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlin.math.log10

private fun db(v: Float): String = if (v <= 0.001f) "−∞" else "%+.1f".format(20 * log10(v / 0.8f))

@Composable
fun MixerView(vm: StudioViewModel) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val ui by vm.ui.collectAsState()
    val meters by vm.meters.collectAsState()
    val p = project ?: return
    Row(Modifier.fillMaxSize().padding(10.dp)) {
        Row(
            Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            p.tracks.forEachIndexed { i, t ->
                ChannelStrip(
                    vm, i, t, selected = i == ui.selected,
                    meterL = meters.getOrElse(i * 2) { 0f }, meterR = meters.getOrElse(i * 2 + 1) { 0f },
                )
            }
            if (p.tracks.isEmpty()) {
                Box(Modifier.fillMaxHeight().width(300.dp), contentAlignment = Alignment.Center) {
                    Text("Добавь дорожки — и здесь появятся каналы", style = YumeText.body, color = c.textDim)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        // мастер
        Column(
            Modifier.width(118.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surfaceHigh).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Мастер", style = YumeText.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Knob(p.reverbSize, vm::setReverbSize, "Зал", default = 0.6f, size = 40.dp, color = c.accent)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Fader(p.masterVolume, vm::setMasterVolume, Modifier.fillMaxHeight(), color = c.primary, default = 0.9f)
                Spacer(Modifier.width(6.dp))
                LevelMeter(
                    meters.getOrElse(NativeEngine.MASTER_METER) { 0f }, meters.getOrElse(NativeEngine.MASTER_METER + 1) { 0f },
                    Modifier.fillMaxHeight().padding(vertical = 14.dp),
                )
            }
            Text(db(p.masterVolume * 0.8f / 0.9f), style = YumeText.tiny, color = c.textDim)
        }
    }
}

@Composable
private fun ChannelStrip(vm: StudioViewModel, i: Int, t: Track, selected: Boolean, meterL: Float, meterR: Float) {
    val c = Yume.colors
    val color = c.track(t.colorIndex)
    Column(
        Modifier
            .width(104.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.surfaceHigh else c.surface)
            .clickable { vm.select(i) }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(t.type.avatar), null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, color, RoundedCornerShape(12.dp)),
        )
        Text(t.name, style = YumeText.tiny, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Knob(
                (t.pan + 1f) / 2f, { vm.setPan(i, it * 2f - 1f) }, "Пан", default = 0.5f, size = 34.dp, color = color,
                valueText = when { t.pan < -0.05f -> "Л${(-t.pan * 100).toInt()}"; t.pan > 0.05f -> "П${(t.pan * 100).toInt()}"; else -> "Центр" },
            )
            Knob(t.reverbSend, { vm.setSend(i, it) }, "Реверб", default = 0.12f, size = 34.dp, color = c.accent)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Fader(t.volume, { vm.setVolume(i, it) }, Modifier.fillMaxHeight(), color = color)
            Spacer(Modifier.width(4.dp))
            LevelMeter(meterL, meterR, Modifier.fillMaxHeight().padding(vertical = 14.dp))
        }
        Text(db(t.volume), style = YumeText.tiny, color = c.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            SmButton("S", t.solo, c.peach) { vm.toggleSolo(i) }
            SmButton("M", t.mute, c.danger) { vm.toggleMute(i) }
        }
    }
}

@Composable
private fun SmButton(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    val c = Yume.colors
    Box(
        Modifier.size(34.dp, 28.dp).clip(RoundedCornerShape(8.dp)).background(if (on) color else c.surfaceLow).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = YumeText.small, color = if (on) Color.White else c.textDim) }
}
