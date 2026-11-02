package com.yume.studio.ui.fx

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yume.studio.audio.FxDef
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.stepIndex
import com.yume.studio.audio.stepValue
import com.yume.studio.model.EffectSlot
import com.yume.studio.model.Track
import com.yume.studio.ui.components.CatFaceIcon
import com.yume.studio.ui.components.Knob
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText

private data class BoardPreset(val name: String, val effects: List<EffectSlot>)

private fun fx(def: FxDef, vararg p: Float, on: Boolean = true) =
    EffectSlot(def.type, on, if (p.isEmpty()) def.defaults else def.defaults.mapIndexed { i, d -> p.getOrElse(i) { d } })

private val GUITAR_PRESETS = listOf(
    BoardPreset("City pop clean", listOf(fx(FxDef.COMPRESSOR, 0.5f, 0.35f), fx(FxDef.CHORUS, 0.25f, 0.45f, 0.4f), fx(FxDef.DELAY, 0.45f, 0.25f, 0.18f), fx(FxDef.AMP, 0.1f, 0.35f, 0.5f, 0.5f, 0.65f, 0.55f))),
    BoardPreset("Shoegaze dream", listOf(fx(FxDef.FUZZ, 0.55f, 0.4f, 0.45f), fx(FxDef.CHORUS, 0.15f, 0.8f, 0.6f), fx(FxDef.DELAY, 0.7f, 0.6f, 0.45f, 0.4f, 1f), fx(FxDef.REVERB, 0.95f, 0.6f, 0.65f, 1f), fx(FxDef.AMP, 0.34f, 0.5f, 0.55f, 0.45f, 0.5f, 0.5f))),
    BoardPreset("Grunge 1991", listOf(fx(FxDef.GATE, 0.35f, 0.25f), fx(FxDef.DISTORTION, 0.7f, 0.45f, 0.55f), fx(FxDef.AMP, 0.6f, 0.55f, 0.6f, 0.4f, 0.6f, 0.5f))),
    BoardPreset("Lo-fi Kitty", listOf(fx(FxDef.BITCRUSHER, 0.35f, 0.25f, 0.5f, 0.5f), fx(FxDef.TREMOLO, 0.3f, 0.35f, 0.1f), fx(FxDef.REVERB, 0.6f, 0.7f, 0.35f, 0.8f), fx(FxDef.AMP, 0.1f, 0.3f, 0.6f, 0.5f, 0.4f, 0.55f))),
    BoardPreset("Funky wah", listOf(fx(FxDef.COMPRESSOR, 0.45f, 0.5f), fx(FxDef.WAH, 1f, 0.5f, 0.7f, 0.65f, 1f), fx(FxDef.AMP, 0.1f, 0.4f, 0.45f, 0.6f, 0.6f, 0.55f))),
    BoardPreset("Hyperpop crush", listOf(fx(FxDef.OCTAVER, 0.5f, 0.5f, 0.6f), fx(FxDef.FUZZ, 0.8f, 0.7f, 0.4f), fx(FxDef.FLANGER, 0.5f, 0.7f, 0.75f, 0.5f), fx(FxDef.AMP, 0.6f, 0.5f, 0.5f, 0.5f, 0.7f, 0.45f))),
    BoardPreset("Чистый звук", emptyList()),
)

private val VOCAL_PRESETS = listOf(
    BoardPreset("Idol pop", listOf(fx(FxDef.PITCH, 0.85f), fx(FxDef.COMPRESSOR, 0.45f, 0.45f, 0.15f, 0.35f, 0.35f), fx(FxDef.EQ, 0.45f, 0.5f, 0.68f), fx(FxDef.REVERB, 0.6f, 0.45f, 0.28f), fx(FxDef.DELAY, 0.5f, 0.3f, 0.15f, 0.6f, 1f))),
    BoardPreset("Bedroom", listOf(fx(FxDef.COMPRESSOR, 0.5f, 0.35f), fx(FxDef.REVERB, 0.4f, 0.6f, 0.2f), fx(FxDef.BITCRUSHER, 0.2f, 0.1f, 0.25f, 0.3f))),
    BoardPreset("Hyperpop", listOf(fx(FxDef.PITCH, 1f), fx(FxDef.OCTAVER, 0.2f, 0.45f, 0.8f), fx(FxDef.DISTORTION, 0.25f, 0.7f, 0.45f), fx(FxDef.DELAY, 0.35f, 0.4f, 0.25f, 0.7f, 1f))),
    BoardPreset("Сухой голос", listOf(fx(FxDef.COMPRESSOR))),
)

@Composable
fun FxView(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    var adding by remember { mutableStateOf(false) }
    var presetsMenu by remember { mutableStateOf(false) }
    val presets = when (track.type) {
        InstrumentType.AUDIO -> VOCAL_PRESETS
        else -> GUITAR_PRESETS
    }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Педалборд · ${track.name}", style = YumeText.title, color = c.text)
            Spacer(Modifier.weight(1f))
            Box {
                Pill("Пресеты", Icons.Rounded.AutoAwesome, height = 34.dp) { presetsMenu = true }
                DropdownMenu(presetsMenu, { presetsMenu = false }, containerColor = c.surfaceHigh) {
                    presets.forEach { pr -> DropdownMenuItem({ Text(pr.name) }, { presetsMenu = false; vm.setEffects(index, pr.effects) }) }
                }
            }
            Pill("Педаль", Icons.Rounded.Add, selected = true, height = 34.dp, enabled = track.effects.size < 8) { adding = true }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(c.surfaceLow, c.surface)))
                .padding(12.dp),
        ) {
            // «доска» с текстурой-полосками
            Canvas(Modifier.fillMaxSize()) {
                var y = 10f
                while (y < size.height) { drawLine(Color(0x10FFFFFF), Offset(0f, y), Offset(size.width, y), 2f); y += 26.dp.toPx() }
            }
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Jack("ВХОД")
                track.effects.forEachIndexed { slot, e ->
                    Cable()
                    val def = FxDef.byType(e.type) ?: return@forEachIndexed
                    PedalCard(
                        def = def, slot = e,
                        onToggle = { vm.toggleFx(index, slot) },
                        onParam = { pid, v -> vm.setFxParam(index, slot, pid, v) },
                        onRemove = { vm.removeFx(index, slot) },
                        onLeft = { vm.moveFx(index, slot, slot - 1) },
                        onRight = { vm.moveFx(index, slot, slot + 1) },
                    )
                }
                Cable()
                Jack("ВЫХОД")
                if (track.effects.isEmpty()) {
                    Spacer(Modifier.width(20.dp))
                    Text("Пока без эффектов — добавь педаль или выбери пресет ♡", style = YumeText.body, color = c.textDim)
                }
            }
        }
    }
    if (adding) AddPedalDialog(track, onPick = { vm.addFx(index, it.type); adding = false }, onDismiss = { adding = false })
}

@Composable
private fun Jack(label: String) {
    val c = Yume.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF8B8B95)).border(4.dp, Color(0xFF55505A), CircleShape))
        Text(label, style = YumeText.tiny, color = c.textFaint)
    }
}

@Composable
private fun Cable() {
    Canvas(Modifier.width(26.dp).height(30.dp)) {
        drawLine(Color(0xFF2A1B24), Offset(0f, size.height / 2 + 2f), Offset(size.width, size.height / 2 + 2f), 7f)
        drawLine(Color(0xFFF29BB5), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 5f)
    }
}

@Composable
private fun PedalCard(
    def: FxDef,
    slot: EffectSlot,
    onToggle: () -> Unit,
    onParam: (Int, Float) -> Unit,
    onRemove: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    val c = Yume.colors
    val isAmp = def == FxDef.AMP
    val body = c.pedal(def.colorIndex)
    val ink = if (isAmp) Color(0xFFFCE9EC) else Color(0xFF4A2E3B)
    val knobs = def.params.withIndex().filter { it.value.steps == null }
    val switches = def.params.withIndex().filter { it.value.steps != null }
    val width = if (isAmp) 250.dp else if (knobs.size > 3) 176.dp else 150.dp
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(body, body.copy(red = body.red * 0.9f, green = body.green * 0.88f, blue = body.blue * 0.9f))))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Левее", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onLeft))
            Spacer(Modifier.weight(1f))
            // светодиод
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (slot.enabled) Color(0xFFFF5D86) else Color(0x55000000)))
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Правее", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onRight))
            Icon(Icons.Rounded.Close, "Убрать", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onRemove))
        }
        if (isAmp) AmpGrill(Modifier.fillMaxWidth().height(26.dp))
        // ручки
        val rows = knobs.chunked(3)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (pid, pdef) ->
                        Knob(
                            slot.params.getOrElse(pid) { pdef.default }, { onParam(pid, it) }, pdef.name,
                            default = pdef.default, size = if (rows.size > 1) 36.dp else 44.dp,
                            color = Color(0xFF8A4A63), trackColor = Color(0x33000000), labelColor = ink,
                        )
                    }
                }
            }
            switches.forEach { (pid, pdef) ->
                val steps = pdef.steps!!
                val idx = pdef.stepIndex(slot.params.getOrElse(pid) { pdef.default })
                Row(
                    Modifier.clip(CircleShape).background(Color(0x33000000)).clickable { onParam(pid, pdef.stepValue((idx + 1) % steps.size)) }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${pdef.name}: ", style = YumeText.tiny, color = ink.copy(alpha = 0.75f))
                    Text(steps[idx], style = YumeText.tiny, color = ink)
                }
            }
        }
        Text(def.brand, style = YumeText.title.copy(fontSize = 15.sp), color = ink, maxLines = 1)
        Text(def.title, style = YumeText.tiny, color = ink.copy(alpha = 0.7f))
        Spacer(Modifier.height(4.dp))
        // футсвитч
        Box(
            Modifier.size(46.dp).shadow(4.dp, CircleShape).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFF2F0F4), Color(0xFFA9A3AE))))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            CatFaceIcon(if (slot.enabled) Color(0xFFFFC1D3) else Color(0xFFD8D2DA), Color(0xFF6A4552), 28.dp, sleepy = !slot.enabled)
        }
    }
}

@Composable
private fun AmpGrill(modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A2631))) {
        var x = 4f
        while (x < size.width) {
            drawLine(Color(0xFF5E4352), Offset(x, 0f), Offset(x + size.height, size.height), 2f)
            x += 7f
        }
        drawRoundRect(Color(0xFFF6C9D6), Offset(size.width / 2 - 26.dp.toPx(), size.height / 2 - 7.dp.toPx()), Size(52.dp.toPx(), 14.dp.toPx()), CornerRadius(7.dp.toPx()), style = Stroke(1.5f))
    }
}

@Composable
private fun AddPedalDialog(track: Track, onPick: (FxDef) -> Unit, onDismiss: () -> Unit) {
    val c = Yume.colors
    val list = FxDef.entries
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface).padding(16.dp).width(560.dp).height(340.dp)) {
            Text("Добавить педаль", style = YumeText.title, color = c.text)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(GridCells.Adaptive(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { def ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(c.pedal(def.colorIndex)).clickable { onPick(def) }.padding(10.dp),
                    ) {
                        Text(def.brand, style = YumeText.body, color = if (def == FxDef.AMP) Color.White else Color(0xFF4A2E3B))
                        Text(def.title, style = YumeText.tiny, color = if (def == FxDef.AMP) Color(0xFFF7D9E3) else Color(0xFF6A4552))
                    }
                }
            }
        }
    }
}
