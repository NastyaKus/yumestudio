package com.yume.studio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yume.studio.BuildConfig
import com.yume.studio.YumeApp
import com.yume.studio.audio.NativeEngine
import com.yume.studio.data.Settings
import com.yume.studio.data.ThemeMode
import com.yume.studio.ui.components.Panel
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.components.YumeSlider
import com.yume.studio.ui.components.YumeToggle
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlinx.coroutines.launch

@Composable
fun SettingsContent(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as YumeApp
    val s by app.prefs.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    val engineState by app.engine.state.collectAsState()
    val c = Yume.colors
    fun upd(f: (Settings) -> Settings) = scope.launch { app.prefs.update(f) }
    val latency = remember(engineState) {
        if (engineState == com.yume.studio.audio.EngineBridge.State.READY) NativeEngine.nativeOutputLatencyMs() else 0f
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Внешний вид", style = YumeText.title, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Тема", style = YumeText.body, color = c.textDim, modifier = Modifier.width(180.dp))
                    Segmented(ThemeMode.entries, s.theme, { it.title }, { m -> upd { it.copy(theme = m) } })
                }
                SettingRow("Лепестки сакуры на главном экране") {
                    YumeToggle(s.sakuraPetals, { v -> upd { it.copy(sakuraPetals = v) } })
                }
                SettingRow("Подписи нот на клавишах") {
                    YumeToggle(s.showNoteLabels, { v -> upd { it.copy(showNoteLabels = v) } })
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Игра и запись", style = YumeText.title, color = c.text)
                SettingRow("Сила нажатия по высоте касания") {
                    YumeToggle(s.velocityByPosition, { v -> upd { it.copy(velocityByPosition = v) } })
                }
                SettingRow("Вибрация на пэдах") { YumeToggle(s.haptics, { v -> upd { it.copy(haptics = v) } }) }
                SettingRow("Отсчёт перед записью") {
                    Segmented(listOf(0, 1, 2), s.countInBars, { if (it == 0) "Нет" else "$it такт" }, { v -> upd { it.copy(countInBars = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Громкость метронома", style = YumeText.body, color = c.textDim, modifier = Modifier.width(220.dp))
                    YumeSlider(s.metronomeVolume, { v ->
                        upd { it.copy(metronomeVolume = v) }
                        if (engineState == com.yume.studio.audio.EngineBridge.State.READY) NativeEngine.nativeSetMetronome(true, v)
                    }, Modifier.weight(1f))
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Экспорт", style = YumeText.title, color = c.text)
                SettingRow("Разрядность WAV") {
                    Segmented(listOf(16, 24), s.exportBits, { "$it бит" }, { v -> upd { it.copy(exportBits = v) } })
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("О приложении", style = YumeText.title, color = c.text)
                Text("Yume Studio ${BuildConfig.VERSION_NAME} · движок Oboe + TinySoundFont, 48 кГц", style = YumeText.body, color = c.textDim)
                Text(
                    when (engineState) {
                        com.yume.studio.audio.EngineBridge.State.READY -> "Задержка вывода ≈ ${"%.1f".format(latency)} мс"
                        com.yume.studio.audio.EngineBridge.State.LOADING -> "Аудиодвижок загружается…"
                        else -> "Аудиодвижок не запустился"
                    },
                    style = YumeText.body, color = c.textDim,
                )
                Text("Звуки: GeneralUser GS © S. Christian Collins. Шрифт Nunito (OFL). TinySoundFont (MIT).", style = YumeText.small, color = c.textFaint)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = YumeText.body, color = Yume.colors.textDim, modifier = Modifier.weight(1f))
        content()
    }
}
