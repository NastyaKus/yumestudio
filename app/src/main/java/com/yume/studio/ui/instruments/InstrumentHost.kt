package com.yume.studio.ui.instruments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yume.studio.audio.InstrumentType
import com.yume.studio.model.Track
import com.yume.studio.ui.components.PresetSwitcher
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText

@Composable
fun InstrumentHost(vm: StudioViewModel, index: Int, track: Track) {
    when (track.type) {
        InstrumentType.KEYS -> KeyboardInstrument(vm, index, track)
        InstrumentType.SYNTH -> SynthInstrument(vm, index, track)
        InstrumentType.GUITAR -> GuitarInstrument(vm, index, track)
        InstrumentType.BASS -> BassInstrument(vm, index, track)
        InstrumentType.DRUMS -> DrumsInstrument(vm, index, track)
        InstrumentType.STRINGS -> StringsInstrument(vm, index, track)
        InstrumentType.AUDIO -> VocalInstrument(vm, index, track)
    }
}

/**
 * Шапка инструмента: арт персонажа, переключатель пресетов «‹ … ›» по центру снизу
 * (закрывает место, где на макете была плашка) и слот для виджетов справа.
 */
@Composable
fun ArtHeader(
    vm: StudioViewModel,
    index: Int,
    track: Track,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var picker by remember { mutableStateOf(false) }
    val presets = track.type.presets
    val name = presets.getOrNull(track.presetIndex)?.name ?: track.type.title
    Box(
        modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        Image(painterResource(track.type.art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0x662B1F27)))))
        PresetSwitcher(
            name = name,
            onPrev = { vm.setPreset(index, track.presetIndex - 1) },
            onNext = { vm.setPreset(index, track.presetIndex + 1) },
            onClick = { picker = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
        overlay()
    }
    if (picker) PresetPicker(track, onPick = { vm.setPreset(index, it); picker = false }, onDismiss = { picker = false })
}

@Composable
fun PresetPicker(track: Track, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = Yume.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface).padding(16.dp).width(520.dp).height(340.dp)) {
            Text("${track.type.title}: звуки", style = YumeText.title, color = c.text)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(track.type.presets) { i, p ->
                    val sel = i == track.presetIndex
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) c.primary else c.surfaceHigh)
                            .clickable { onPick(i) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(p.name, style = YumeText.small, color = if (sel) c.onPrimary else c.text, maxLines = 2) }
                }
            }
        }
    }
}

/** Ряд управляющих элементов под артом. */
@Composable
fun ControlStrip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
