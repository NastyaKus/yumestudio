package com.yume.studio.ui.studio

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yume.studio.YumeApp
import com.yume.studio.audio.Articulation
import com.yume.studio.audio.EngineBridge
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.NativeEngine
import com.yume.studio.audio.SynthPresets
import com.yume.studio.data.AudioImporter
import com.yume.studio.data.ExportStore
import com.yume.studio.data.Loop
import com.yume.studio.data.Settings
import com.yume.studio.model.EffectSlot
import com.yume.studio.model.Grid
import com.yume.studio.model.Note
import com.yume.studio.model.PPQ
import com.yume.studio.model.Project
import com.yume.studio.model.Quantize
import com.yume.studio.model.Region
import com.yume.studio.model.Timing
import com.yume.studio.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class StudioMode { TRACKS, INSTRUMENT, MIXER, FX, EDITOR, LIBRARY }

data class StudioUi(
    val selected: Int = 0,
    val mode: StudioMode = StudioMode.TRACKS,
    val selectedRegion: Long? = null,
    val editingRegion: Long? = null,
    val grid: Grid = Grid.SIXTEENTH,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val exportProgress: Float? = null,
    val lastExport: File? = null,
    val message: String? = null,
    val inputOpen: Boolean = false,
    val monitoring: Boolean = false,
    val showTrackPicker: Boolean = false,
    val engineFailed: Boolean = false,
)

data class TransportState(
    val playing: Boolean = false,
    val recording: Boolean = false,
    val position: Double = 0.0,
    val countIn: Boolean = false,
)

data class ClipInfo(val peaks: FloatArray, val frames: Long, val sampleRate: Int)

class StudioViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {
    private val yume = app as YumeApp
    private val repo = yume.repository
    private val engine = yume.engine
    private val prefs = yume.prefs

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()
    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _ui = MutableStateFlow(StudioUi())
    val ui: StateFlow<StudioUi> = _ui.asStateFlow()
    private val _transport = MutableStateFlow(TransportState())
    val transport: StateFlow<TransportState> = _transport.asStateFlow()
    private val _meters = MutableStateFlow(FloatArray(NativeEngine.METER_COUNT))
    val meters: StateFlow<FloatArray> = _meters.asStateFlow()
    private val _inputWave = MutableStateFlow(List(160) { 0f })
    val inputWave: StateFlow<List<Float>> = _inputWave.asStateFlow()
    private val _clips = MutableStateFlow<Map<String, ClipInfo>>(emptyMap())
    val clips: StateFlow<Map<String, ClipInfo>> = _clips.asStateFlow()
    private val _recordingRegion = MutableStateFlow<Region?>(null)
    val recordingRegion: StateFlow<Region?> = _recordingRegion.asStateFlow()

    private val clipIds = HashMap<String, Int>()
    private var nextClipId = 1
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var lastUndoKey: String? = null
    private var lastUndoAt = 0L
    private var saveJob: Job? = null

    // запись
    private var recording = false
    private var recordStartTick = 0.0
    private var recTrackIndex = -1
    private var recAudioFile: File? = null
    private val pendingNotes = HashMap<Int, Pair<Int, Int>>()
    private val recordedNotes = ArrayList<Note>()

    init {
        viewModelScope.launch {
            engine.init()
            if (engine.state.value != EngineBridge.State.READY) {
                _ui.update { it.copy(engineFailed = true) }
            }
            val p = repo.load(projectId) ?: return@launch
            loadClips(p)
            if (engine.state.value == EngineBridge.State.READY) {
                engine.resetCache()
                NativeEngine.nativeStopTransport()
                NativeEngine.nativeSeek(0.0)
                engine.sync(null, p, clipIds, settings.value.metronomeVolume)
            }
            _project.value = p
            if (p.tracks.isEmpty()) _ui.update { it.copy(showTrackPicker = true) }
            ticker()
        }
    }

    private val ready get() = engine.state.value == EngineBridge.State.READY

    // ───────────────────────── мутации и undo ─────────────────────────

    fun mutate(undoKey: String? = null, record: Boolean = true, f: (Project) -> Project) {
        val old = _project.value ?: return
        val changed = f(old)
        if (changed == old) return
        val new = changed.copy(updatedAt = System.currentTimeMillis())
        if (record) pushUndo(old, undoKey)
        _project.value = new
        if (ready) engine.sync(old, new, clipIds, settings.value.metronomeVolume)
        scheduleSave()
    }

    private fun pushUndo(old: Project, key: String?) {
        val now = System.currentTimeMillis()
        val coalesce = key != null && key == lastUndoKey && now - lastUndoAt < 1500
        lastUndoKey = key
        lastUndoAt = now
        if (coalesce) return
        undoStack.addLast(old)
        while (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
        _ui.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() {
        val cur = _project.value ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(cur)
        lastUndoKey = null
        applySnapshot(cur, prev)
    }

    fun redo() {
        val cur = _project.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(cur)
        lastUndoKey = null
        applySnapshot(cur, next)
    }

    private fun applySnapshot(cur: Project, target: Project) {
        _project.value = target
        if (ready) engine.sync(cur, target, clipIds, settings.value.metronomeVolume)
        _ui.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty(),
                selected = it.selected.coerceIn(0, max(0, target.tracks.size - 1)),
            )
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            _project.value?.let { repo.save(it) }
        }
    }

    fun saveNow() {
        viewModelScope.launch { _project.value?.let { repo.save(it) } }
    }

    private fun updateTrack(index: Int, key: String? = null, f: (Track) -> Track) = mutate(key) { p ->
        if (index !in p.tracks.indices) p else p.copy(tracks = p.tracks.toMutableList().also { it[index] = f(it[index]) })
    }

    // ───────────────────────── навигация по студии ─────────────────────────

    fun setMode(mode: StudioMode) {
        if (mode != StudioMode.LIBRARY) engine.stopPreview()
        _ui.update { it.copy(mode = mode) }
    }

    fun select(index: Int) {
        val p = _project.value ?: return
        if (index !in p.tracks.indices) return
        if (_ui.value.monitoring) setMonitoring(false)
        _ui.update { it.copy(selected = index) }
    }

    fun openInstrument(index: Int) { select(index); setMode(StudioMode.INSTRUMENT) }
    fun showTrackPicker(show: Boolean) = _ui.update { it.copy(showTrackPicker = show) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
    fun toast(msg: String) = _ui.update { it.copy(message = msg) }
    fun setGrid(g: Grid) = _ui.update { it.copy(grid = g) }

    // ───────────────────────── дорожки ─────────────────────────

    fun addTrack(type: InstrumentType, presetIndex: Int = 0, openInstrument: Boolean = true) {
        val p = _project.value ?: return
        if (p.tracks.size >= NativeEngine.MAX_TRACKS) { toast("Максимум ${NativeEngine.MAX_TRACKS} дорожки"); return }
        val sameType = p.tracks.count { it.type == type }
        val t = Track(
            id = System.nanoTime(),
            name = if (sameType == 0) type.title else "${type.title} ${sameType + 1}",
            type = type,
            presetIndex = presetIndex,
            effects = EngineBridge.defaultEffects(type),
            synthParams = if (type == InstrumentType.SYNTH) SynthPresets.all[presetIndex.coerceIn(0, SynthPresets.all.lastIndex)].params else SynthPresets.all.first().params,
            colorIndex = (type.colorIndex + sameType * 3).mod(8),
        )
        mutate { it.copy(tracks = it.tracks + t) }
        _ui.update { it.copy(selected = p.tracks.size, showTrackPicker = false, mode = if (openInstrument) StudioMode.INSTRUMENT else it.mode) }
    }

    fun deleteTrack(index: Int) {
        mutate { p -> p.copy(tracks = p.tracks.filterIndexed { i, _ -> i != index }) }
        _ui.update { it.copy(selected = (it.selected - if (it.selected >= index) 1 else 0).coerceAtLeast(0)) }
    }

    fun duplicateTrack(index: Int) = mutate { p ->
        val t = p.tracks.getOrNull(index) ?: return@mutate p
        if (p.tracks.size >= NativeEngine.MAX_TRACKS) return@mutate p
        val copy = t.copy(id = System.nanoTime(), name = t.name + " +", regions = t.regions.map { it.copy(id = newId()) }, solo = false)
        p.copy(tracks = p.tracks.toMutableList().apply { add(index + 1, copy) })
    }

    fun moveTrack(from: Int, to: Int) = mutate { p ->
        if (from !in p.tracks.indices || to !in p.tracks.indices) p
        else p.copy(tracks = p.tracks.toMutableList().apply { add(to, removeAt(from)) })
    }.also { _ui.update { it.copy(selected = to) } }

    fun renameTrack(index: Int, name: String) = updateTrack(index) { it.copy(name = name.ifBlank { it.type.title }) }

    fun setPreset(index: Int, presetIndex: Int) = updateTrack(index, "preset-$index") { t ->
        val n = t.type.presets.size
        val pi = presetIndex.mod(n)
        if (t.type == InstrumentType.SYNTH) t.copy(presetIndex = pi, synthParams = SynthPresets.all[pi].params) else t.copy(presetIndex = pi)
    }

    fun setArticulation(index: Int, a: Articulation) = updateTrack(index) { it.copy(articulation = a) }

    fun setSynthParam(index: Int, id: Int, v: Float) = updateTrack(index, "synth-$index-$id") { t ->
        t.copy(synthParams = t.synthParams.toMutableList().also { if (id in it.indices) it[id] = v })
    }

    fun setVolume(index: Int, v: Float) = updateTrack(index, "vol-$index") { it.copy(volume = v) }
    fun setPan(index: Int, v: Float) = updateTrack(index, "pan-$index") { it.copy(pan = v) }
    fun setSend(index: Int, v: Float) = updateTrack(index, "send-$index") { it.copy(reverbSend = v) }
    fun toggleMute(index: Int) = updateTrack(index) { it.copy(mute = !it.mute) }
    fun toggleSolo(index: Int) = updateTrack(index) { it.copy(solo = !it.solo) }
    fun setMasterVolume(v: Float) = mutate("master") { it.copy(masterVolume = v) }
    fun setReverbSize(v: Float) = mutate("reverb") { it.copy(reverbSize = v) }

    // ───────────────────────── эффекты ─────────────────────────

    fun addFx(index: Int, type: Int) = updateTrack(index) { t ->
        if (t.effects.size >= EngineBridge.MAX_FX) t else t.copy(effects = t.effects + EffectSlot(type))
    }

    fun removeFx(index: Int, slot: Int) = updateTrack(index) { t -> t.copy(effects = t.effects.filterIndexed { i, _ -> i != slot }) }

    fun moveFx(index: Int, from: Int, to: Int) = updateTrack(index) { t ->
        if (from !in t.effects.indices || to !in t.effects.indices) t
        else t.copy(effects = t.effects.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun toggleFx(index: Int, slot: Int) = updateTrack(index) { t ->
        t.copy(effects = t.effects.mapIndexed { i, e -> if (i == slot) e.copy(enabled = !e.enabled) else e })
    }

    fun setFxParam(index: Int, slot: Int, pid: Int, v: Float) = updateTrack(index, "fx-$index-$slot-$pid") { t ->
        t.copy(effects = t.effects.mapIndexed { i, e ->
            if (i == slot) e.copy(params = e.params.toMutableList().also { if (pid in it.indices) it[pid] = v }) else e
        })
    }

    fun setEffects(index: Int, effects: List<EffectSlot>) = updateTrack(index) { it.copy(effects = effects.take(EngineBridge.MAX_FX)) }

    // ───────────────────────── регионы ─────────────────────────

    private fun newId() = System.nanoTime()

    fun findRegion(id: Long?): Pair<Int, Region>? {
        if (id == null) return null
        val p = _project.value ?: return null
        p.tracks.forEachIndexed { ti, t -> t.regions.firstOrNull { it.id == id }?.let { return ti to it } }
        return null
    }

    private fun updateRegion(id: Long, key: String? = null, f: (Region) -> Region?) = mutate(key) { p ->
        p.copy(tracks = p.tracks.map { t ->
            if (t.regions.none { it.id == id }) t else t.copy(regions = t.regions.mapNotNull { if (it.id == id) f(it) else it })
        })
    }

    fun selectRegion(id: Long?) = _ui.update { it.copy(selectedRegion = id) }

    fun editRegion(id: Long) {
        val (ti, r) = findRegion(id) ?: return
        if (r.isAudio) return
        _ui.update { it.copy(selected = ti, selectedRegion = id, editingRegion = id, mode = StudioMode.EDITOR) }
    }

    fun createRegion(trackIndex: Int, atTick: Int, bars: Int = 1) {
        val p = _project.value ?: return
        val t = p.tracks.getOrNull(trackIndex) ?: return
        if (!t.type.isMidi) return
        val start = Timing.snapDown(atTick, p.ticksPerBar)
        val r = Region(newId(), start, p.ticksPerBar * bars, name = t.name)
        mutate { pr -> pr.copy(tracks = pr.tracks.mapIndexed { i, tr -> if (i == trackIndex) tr.copy(regions = tr.regions + r) else tr }) }
        _ui.update { it.copy(selectedRegion = r.id, selected = trackIndex) }
    }

    fun moveRegion(id: Long, newStart: Int, newTrack: Int? = null) {
        val (ti, r) = findRegion(id) ?: return
        val p = _project.value ?: return
        val target = newTrack?.takeIf { it in p.tracks.indices } ?: ti
        val compatible = p.tracks[target].type.isMidi == !r.isAudio
        val dest = if (compatible) target else ti
        val moved = r.copy(start = newStart.coerceAtLeast(0))
        mutate("move-$id") { pr ->
            pr.copy(tracks = pr.tracks.mapIndexed { i, t ->
                var regs = t.regions.filter { it.id != id }
                if (i == dest) regs = regs + moved
                if (regs == t.regions) t else t.copy(regions = regs)
            })
        }
    }

    /** Правый край: для MIDI-регионов удлинение за пределы содержимого — повтор (луп). */
    fun resizeRegion(id: Long, newLength: Int, loop: Boolean = false) = updateRegion(id, "resize-$id") { r ->
        val len = newLength.coerceAtLeast(PPQ / 4)
        when {
            r.isAudio -> {
                val info = _clips.value[r.clipFile]
                val maxLen = info?.let { framesToTicks(it.frames - r.clipOffset, it.sampleRate) } ?: Int.MAX_VALUE
                r.copy(length = len.coerceAtMost(maxLen))
            }
            loop -> r.copy(length = len)
            else -> r.copy(length = len, contentLength = len)
        }
    }

    /** Левый край: подрезка начала (сдвигает начало, ноты и смещение клипа). */
    fun trimRegionStart(id: Long, newStart: Int) = updateRegion(id, "trim-$id") { r ->
        val ns = newStart.coerceIn(0, r.end - PPQ / 4)
        val d = ns - r.start
        if (r.isAudio) {
            val bpm = _project.value?.bpm ?: 100f
            val info = _clips.value[r.clipFile]
            val rate = (info?.sampleRate ?: 48000) / 48000.0
            val off = (r.clipOffset + d * Timing.samplesPerTick(bpm) * rate).toLong().coerceAtLeast(0)
            r.copy(start = ns, length = r.end - ns, clipOffset = off)
        } else {
            r.copy(start = ns, length = r.end - ns, contentLength = (r.contentLength - d).coerceAtLeast(PPQ / 4),
                notes = r.notes.map { it.copy(tick = it.tick - d) }.filter { it.tick >= 0 })
        }
    }

    fun toggleRegionLoop(id: Long) = updateRegion(id) { r ->
        if (r.isAudio) r
        else if (r.isLooped) r.copy(length = r.contentLength)
        else r.copy(length = r.contentLength * 4)
    }

    fun deleteRegion(id: Long) {
        updateRegion(id) { null }
        _ui.update { if (it.selectedRegion == id) it.copy(selectedRegion = null) else it }
    }

    fun duplicateRegion(id: Long) {
        val (ti, r) = findRegion(id) ?: return
        val copy = r.copy(id = newId(), start = r.end)
        mutate { p -> p.copy(tracks = p.tracks.mapIndexed { i, t -> if (i == ti) t.copy(regions = t.regions + copy) else t }) }
        _ui.update { it.copy(selectedRegion = copy.id) }
    }

    fun splitRegionAtPlayhead(id: Long) {
        val (ti, r) = findRegion(id) ?: return
        val at = _transport.value.position.toInt()
        if (at <= r.start + PPQ / 8 || at >= r.end - PPQ / 8) { toast("Поставь плейхед внутрь региона"); return }
        val p = _project.value ?: return
        val cut = at - r.start
        val left: Region
        val right: Region
        if (r.isAudio) {
            val rate = (_clips.value[r.clipFile]?.sampleRate ?: 48000) / 48000.0
            left = r.copy(length = cut)
            right = r.copy(id = newId(), start = at, length = r.length - cut,
                clipOffset = r.clipOffset + (cut * Timing.samplesPerTick(p.bpm) * rate).toLong())
        } else {
            val flat = flattenNotes(r)
            left = r.copy(length = cut, contentLength = cut, notes = flat.filter { it.tick < cut }.map { it.copy(length = minOf(it.length, cut - it.tick)) })
            right = r.copy(id = newId(), start = at, length = r.length - cut, contentLength = r.length - cut,
                notes = flat.filter { it.tick >= cut }.map { it.copy(tick = it.tick - cut) })
        }
        mutate { pr -> pr.copy(tracks = pr.tracks.mapIndexed { i, t ->
            if (i == ti) t.copy(regions = t.regions.flatMap { if (it.id == id) listOf(left, right) else listOf(it) }) else t
        }) }
    }

    /** Разворачивает зацикленный регион в явные ноты. */
    fun flattenNotes(r: Region): List<Note> {
        if (!r.isLooped) return r.notes
        val out = ArrayList<Note>()
        var base = 0
        while (base < r.length) {
            r.notes.forEach { n ->
                val t = base + n.tick
                if (n.tick < r.contentLength && t < r.length) out += n.copy(tick = t, length = minOf(n.length, r.length - t))
            }
            base += r.contentLength
        }
        return out
    }

    fun setRegionGain(id: Long, g: Float) = updateRegion(id, "gain-$id") { it.copy(gain = g) }
    fun renameRegion(id: Long, name: String) = updateRegion(id) { it.copy(name = name) }

    // ── ноты (пианоролл) ──
    fun setNotes(regionId: Long, notes: List<Note>, key: String? = null) = updateRegion(regionId, key) { r ->
        val maxEnd = notes.maxOfOrNull { it.tick + it.length } ?: 0
        val grow = maxEnd > r.contentLength && !r.isLooped
        r.copy(notes = notes.sortedBy { it.tick }, contentLength = if (grow) Timing.snapUp(maxEnd, PPQ * 4) else r.contentLength,
            length = if (grow) Timing.snapUp(maxEnd, PPQ * 4) else r.length)
    }

    fun quantizeRegion(id: Long, grid: Grid, strength: Float = 1f, swing: Float = 0f) = updateRegion(id) { r ->
        if (r.isAudio) r else r.copy(notes = Quantize.notes(r.notes, grid.ticks, strength, swing))
    }

    fun transposeRegion(id: Long, semis: Int) = updateRegion(id, "tr-$id") { r ->
        r.copy(notes = r.notes.map { it.copy(key = (it.key + semis).coerceIn(0, 127)) })
    }

    fun addPatternRegion(trackIndex: Int, notes: List<Note>, lengthTicks: Int, name: String) {
        val p = _project.value ?: return
        val at = Timing.snapDown(_transport.value.position.toInt().coerceAtLeast(0), p.ticksPerBar)
        val r = Region(newId(), at, lengthTicks, lengthTicks, notes, name = name)
        mutate { pr -> pr.copy(tracks = pr.tracks.mapIndexed { i, t -> if (i == trackIndex) t.copy(regions = t.regions + r) else t }) }
        toast("Паттерн добавлен в такт ${at / p.ticksPerBar + 1}")
    }

    // ───────────────────────── транспорт ─────────────────────────

    fun togglePlay() = if (_transport.value.playing) stop() else play()

    fun play() {
        if (!ready) return
        engine.stopPreview()
        NativeEngine.nativePlay()
    }

    fun stop() {
        if (!ready) return
        if (recording) stopRecording()
        NativeEngine.nativeStopTransport()
    }

    fun seek(tick: Double) {
        if (!ready) return
        NativeEngine.nativeSeek(tick.coerceAtLeast(0.0))
        _transport.update { it.copy(position = tick.coerceAtLeast(0.0)) }
    }

    fun rewind() {
        val p = _project.value ?: return
        val pos = _transport.value.position
        val bar = p.ticksPerBar.toDouble()
        val target = if (pos < 1 || _transport.value.playing) 0.0 else ((pos - 1) / bar).toInt() * bar
        seek(if (pos - target < bar / 8) 0.0 else target)
    }

    fun setBpm(bpm: Float) = mutate("bpm") { it.copy(bpm = bpm.coerceIn(40f, 240f)) }
    fun setBeatsPerBar(b: Int) = mutate { it.copy(beatsPerBar = b.coerceIn(2, 7)) }
    fun setKey(root: Int, minor: Boolean) = mutate { it.copy(keyRoot = root.mod(12), minor = minor) }
    fun toggleMetronome() = mutate { it.copy(metronome = !it.metronome) }
    fun setCountIn(bars: Int) = mutate { it.copy(countInBars = bars.coerceIn(0, 2)) }
    fun setLengthBars(bars: Int) = mutate { it.copy(lengthBars = bars.coerceIn(1, 999)) }
    fun renameProject(name: String) = mutate { it.copy(name = name.ifBlank { it.name }) }

    fun toggleLoop() = mutate { p ->
        if (p.loopOn) p.copy(loopOn = false)
        else {
            val sel = findRegion(_ui.value.selectedRegion)?.second
            if (sel != null) p.copy(loopOn = true, loopStart = sel.start, loopEnd = sel.end)
            else if (p.loopEnd > p.loopStart) p.copy(loopOn = true)
            else p.copy(loopOn = true, loopStart = 0, loopEnd = p.ticksPerBar * 4)
        }
    }

    fun setLoopRange(start: Int, end: Int) = mutate("loop") { it.copy(loopStart = start.coerceAtLeast(0), loopEnd = max(end, start + PPQ)) }

    // ───────────────────────── живая игра ─────────────────────────

    private fun currentTrack(): Pair<Int, Track>? {
        val p = _project.value ?: return null
        val i = _ui.value.selected
        return p.tracks.getOrNull(i)?.let { i to it }
    }

    fun noteOn(key: Int, velocity: Float, track: Int? = null) {
        if (!ready) return
        val (i, t) = track?.let { idx -> _project.value?.tracks?.getOrNull(idx)?.let { idx to it } } ?: currentTrack() ?: return
        NativeEngine.nativeNoteOn(i, key, velocity.coerceIn(0.05f, 1f), 0f)
        if (t.type == InstrumentType.STRINGS && t.articulation == Articulation.STACCATO) NativeEngine.nativeNoteOff(i, key, 140f)
    }

    fun noteOff(key: Int, track: Int? = null) {
        if (!ready) return
        val (i, t) = track?.let { idx -> _project.value?.tracks?.getOrNull(idx)?.let { idx to it } } ?: currentTrack() ?: return
        if (t.type == InstrumentType.STRINGS && t.articulation == Articulation.STACCATO) return
        NativeEngine.nativeNoteOff(i, key, 0f)
    }

    /** Страм аккорда с разносом струн по времени (вниз/вверх), как на настоящей гитаре. */
    fun strum(keys: List<Int>, velocity: Float, down: Boolean, spacingMs: Float = 14f, muteAfterMs: Float = 0f) {
        if (!ready) return
        val (i, _) = currentTrack() ?: return
        val ordered = if (down) keys else keys.reversed()
        ordered.forEachIndexed { n, k ->
            NativeEngine.nativeNoteOff(i, k, n * spacingMs)
            NativeEngine.nativeNoteOn(i, k, (velocity - n * 0.03f).coerceIn(0.1f, 1f), n * spacingMs + 1f)
            if (muteAfterMs > 0f) NativeEngine.nativeNoteOff(i, k, n * spacingMs + muteAfterMs)
        }
    }

    fun releaseChord(keys: List<Int>) {
        if (!ready) return
        val (i, _) = currentTrack() ?: return
        keys.forEach { NativeEngine.nativeNoteOff(i, it, 0f) }
    }

    fun allNotesOff() {
        if (!ready) return
        currentTrack()?.let { NativeEngine.nativeAllNotesOff(it.first) }
    }

    fun setSustain(on: Boolean) { if (ready) currentTrack()?.let { NativeEngine.nativeSetSustain(it.first, on) } }
    fun setArp(track: Int, on: Boolean, rateTicks: Int, mode: Int, octaves: Int) {
        if (ready) NativeEngine.nativeSetArp(track, on, rateTicks, mode, octaves)
    }

    fun setPitchBend(semis: Float) { if (ready) currentTrack()?.let { NativeEngine.nativeSetPitchBend(it.first, semis) } }

    // ───────────────────────── вход / мониторинг ─────────────────────────

    fun openInput(): Boolean {
        if (!ready) return false
        if (_ui.value.inputOpen) return true
        val ok = NativeEngine.nativeOpenInput()
        _ui.update { it.copy(inputOpen = ok) }
        if (!ok) toast("Не удалось открыть микрофон")
        return ok
    }

    fun closeInput() {
        if (!ready) return
        if (recording) return
        setMonitoring(false)
        NativeEngine.nativeCloseInput()
        _ui.update { it.copy(inputOpen = false) }
    }

    fun setMonitoring(on: Boolean) {
        if (!ready) return
        val (i, _) = currentTrack() ?: return
        if (on && !openInput()) return
        NativeEngine.nativeSetInputMonitor(i, on)
        _ui.update { it.copy(monitoring = on) }
    }

    // ───────────────────────── запись ─────────────────────────

    fun toggleRecord() = if (recording) stopRecording() else startRecording()

    private fun startRecording() {
        if (!ready) return
        val p = _project.value ?: return
        val (i, t) = currentTrack() ?: run { toast("Сначала добавь дорожку"); return }
        engine.stopPreview()
        recTrackIndex = i
        recordedNotes.clear()
        pendingNotes.clear()
        recordStartTick = _transport.value.position.coerceAtLeast(0.0)
        val countIn = if (_transport.value.playing) 0 else p.countInBars
        if (t.type == InstrumentType.AUDIO) {
            if (!openInput()) return
            val f = File(repo.audioDir(p.id), "rec_${System.currentTimeMillis()}.wav")
            recAudioFile = f
            NativeEngine.nativeStartRecording(i, countIn, true, f.path)
        } else {
            recAudioFile = null
            NativeEngine.nativeStartRecording(i, countIn, false, "")
        }
        recording = true
        _transport.update { it.copy(recording = true) }
    }

    private fun pollRecording() {
        val data = NativeEngine.nativePollRecorded()
        var k = 0
        while (k + 4 < data.size) {
            val tick = data[k + 1]; val key = data[k + 2]; val vel = data[k + 3]; val on = data[k + 4] == 1
            if (on) pendingNotes[key] = tick to vel
            else pendingNotes.remove(key)?.let { (st, v) -> recordedNotes += Note(st, max(PPQ / 16, tick - st), key, v) }
            k += 5
        }
        val pos = _transport.value.position
        if (pos >= recordStartTick) {
            val p = _project.value ?: return
            val start = Timing.snapDown(recordStartTick.toInt(), p.ticksPerBar)
            val live = recordedNotes + pendingNotes.map { (key, sv) -> Note(sv.first, max(1, pos.toInt() - sv.first), key, sv.second) }
            _recordingRegion.value = Region(-2, start, max(PPQ, pos.toInt() - start), notes = live.map { it.copy(tick = it.tick - start) },
                clipFile = recAudioFile?.name, name = "● запись")
        }
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        val captureStart = NativeEngine.nativeStopRecording()
        NativeEngine.nativeStopTransport()
        val endPos = _transport.value.position
        pollRecording()
        _recordingRegion.value = null
        _transport.update { it.copy(recording = false) }
        val p = _project.value ?: return
        val ti = recTrackIndex
        val audioFile = recAudioFile
        if (audioFile == null) {
            pendingNotes.forEach { (key, sv) -> recordedNotes += Note(sv.first, max(PPQ / 16, endPos.toInt() - sv.first), key, sv.second) }
            pendingNotes.clear()
            if (recordedNotes.isEmpty()) { toast("Ничего не записано"); return }
            val start = Timing.snapDown(recordStartTick.toInt(), p.ticksPerBar)
            val maxEnd = recordedNotes.maxOf { it.tick + it.length }
            val end = Timing.snapUp(max(maxEnd, start + PPQ), p.ticksPerBar)
            val r = Region(newId(), start, end - start, end - start, recordedNotes.map { it.copy(tick = it.tick - start) }.sortedBy { it.tick },
                name = p.tracks.getOrNull(ti)?.name ?: "")
            mutate { pr -> pr.copy(
                tracks = pr.tracks.mapIndexed { i, t -> if (i == ti) t.copy(regions = t.regions + r) else t },
                lengthBars = max(pr.lengthBars, end / pr.ticksPerBar + 1),
            ) }
            _ui.update { it.copy(selectedRegion = r.id) }
        } else {
            viewModelScope.launch {
                delay(60)
                val id = nextClipId++
                val frames = withContext(Dispatchers.IO) { NativeEngine.nativeLoadClip(id, audioFile.path) }
                if (frames <= 0) { toast("Запись пуста"); audioFile.delete(); return@launch }
                clipIds[audioFile.name] = id
                val spt = Timing.samplesPerTick(p.bpm)
                val latency = ((NativeEngine.nativeOutputLatencyMs() + NativeEngine.nativeInputLatencyMs()) * 48f).toLong()
                val offset = ((recordStartTick - captureStart) * spt).toLong() + latency
                val lenTicks = ((frames - offset) / spt).toInt()
                if (lenTicks < PPQ / 8) { toast("Слишком короткая запись"); return@launch }
                refreshClipInfo(audioFile.name, id, frames)
                val r = Region(newId(), recordStartTick.toInt(), lenTicks, lenTicks, clipFile = audioFile.name, clipOffset = offset.coerceAtLeast(0),
                    name = p.tracks.getOrNull(ti)?.name ?: "Вокал")
                mutate { pr -> pr.copy(
                    tracks = pr.tracks.mapIndexed { i, t -> if (i == ti) t.copy(regions = t.regions + r) else t },
                    lengthBars = max(pr.lengthBars, r.end / pr.ticksPerBar + 1),
                ) }
                _ui.update { it.copy(selectedRegion = r.id) }
            }
        }
    }

    // ───────────────────────── клипы ─────────────────────────

    private fun framesToTicks(frames: Long, sampleRate: Int): Int {
        val bpm = _project.value?.bpm ?: 100f
        return (frames * (48000.0 / sampleRate) / Timing.samplesPerTick(bpm)).toInt()
    }

    private suspend fun loadClips(p: Project) = withContext(Dispatchers.IO) {
        if (!ready) return@withContext
        val dir = repo.audioDir(p.id)
        val files = p.tracks.flatMap { t -> t.regions.mapNotNull { it.clipFile } }.distinct()
        for (name in files) {
            val f = File(dir, name)
            if (!f.exists()) continue
            val id = clipIds.getOrPut(name) { nextClipId++ }
            val frames = NativeEngine.nativeLoadClip(id, f.path)
            if (frames > 0) refreshClipInfo(name, id, frames)
        }
    }

    private fun refreshClipInfo(name: String, id: Int, frames: Long) {
        val sr = NativeEngine.nativeClipSampleRate(id).takeIf { it > 0 } ?: 48000
        val buckets = (frames / (sr / 100)).toInt().coerceIn(16, 60000)
        val peaks = NativeEngine.nativeClipPeaks(id, buckets)
        _clips.update { it + (name to ClipInfo(peaks, frames, sr)) }
    }

    fun importAudio(uri: Uri) {
        val p = _project.value ?: return
        viewModelScope.launch {
            toast("Импортирую…")
            val ctx = getApplication<Application>()
            val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "audio"
            val dest = File(repo.audioDir(p.id), "import_${System.currentTimeMillis()}.wav")
            val ok = AudioImporter.importToWav(ctx, uri, dest)
            if (!ok) { toast("Этот файл не получилось прочитать"); return@launch }
            val id = nextClipId++
            val frames = withContext(Dispatchers.IO) { NativeEngine.nativeLoadClip(id, dest.path) }
            if (frames <= 0) { toast("Файл пустой"); return@launch }
            clipIds[dest.name] = id
            refreshClipInfo(dest.name, id, frames)
            val sr = _clips.value[dest.name]?.sampleRate ?: 48000
            val len = framesToTicks(frames, sr)
            val at = Timing.snapDown(_transport.value.position.toInt().coerceAtLeast(0), p.ticksPerBar)
            val title = display.substringBeforeLast('.')
            val track = Track(System.nanoTime(), title.take(18), InstrumentType.AUDIO, effects = emptyList(),
                regions = listOf(Region(newId(), at, len, len, clipFile = dest.name, name = title)))
            mutate { pr -> pr.copy(tracks = pr.tracks + track, lengthBars = max(pr.lengthBars, (at + len) / pr.ticksPerBar + 1)) }
            _ui.update { it.copy(selected = (_project.value?.tracks?.size ?: 1) - 1) }
            toast("Добавлено: $title")
        }
    }

    // ───────────────────────── библиотека ─────────────────────────

    fun previewLoop(loop: Loop) {
        val p = _project.value ?: return
        if (_transport.value.playing) stop()
        engine.previewLoop(loop, p.bpm, p.keyRoot)
    }

    fun stopPreview() = engine.stopPreview()

    /** Прослушивание паттерна степ-секвенсора на ките этой дорожки. */
    fun previewDrumPattern(track: Track, notes: List<Note>, lengthTicks: Int) {
        val p = _project.value ?: return
        if (_transport.value.playing) stop()
        engine.previewLoop(
            Loop("step", "step", com.yume.studio.data.LoopCategory.DRUMS, track.type, track.presetIndex, lengthTicks / p.ticksPerBar.coerceAtLeast(1), notes),
            p.bpm, 0,
        )
    }

    fun addPatternRegionLooped(trackIndex: Int, notes: List<Note>, contentTicks: Int, bars: Int, name: String) {
        val p = _project.value ?: return
        val at = Timing.snapDown(_transport.value.position.toInt().coerceAtLeast(0), p.ticksPerBar)
        val r = Region(newId(), at, p.ticksPerBar * bars, contentTicks, notes, name = name)
        mutate { pr -> pr.copy(
            tracks = pr.tracks.mapIndexed { i, t -> if (i == trackIndex) t.copy(regions = t.regions + r) else t },
            lengthBars = max(pr.lengthBars, r.end / pr.ticksPerBar + 1),
        ) }
        _ui.update { it.copy(selectedRegion = r.id) }
        toast("$name добавлен с такта ${at / p.ticksPerBar + 1}")
    }

    fun addLoop(loop: Loop) {
        val p = _project.value ?: return
        engine.stopPreview()
        val at = Timing.snapDown(_transport.value.position.toInt().coerceAtLeast(0), p.ticksPerBar)
        val reps = max(1, 4 / loop.bars)
        val region = Region(newId(), at, loop.lengthTicks * reps, loop.lengthTicks, loop.transposed(engine.transposeFor(p.keyRoot)), name = loop.name)
        val sel = p.tracks.getOrNull(_ui.value.selected)
        val reuse = sel != null && sel.type == loop.type && sel.presetIndex == loop.presetIndex && sel.articulation == loop.articulation
        if (reuse) {
            val idx = _ui.value.selected
            mutate { pr -> pr.copy(tracks = pr.tracks.mapIndexed { i, t -> if (i == idx) t.copy(regions = t.regions + region) else t }) }
        } else {
            if (p.tracks.size >= NativeEngine.MAX_TRACKS) { toast("Нет места для новой дорожки"); return }
            val t = Track(
                id = System.nanoTime(), name = loop.name, type = loop.type, presetIndex = loop.presetIndex,
                articulation = loop.articulation, effects = EngineBridge.defaultEffects(loop.type),
                synthParams = if (loop.type == InstrumentType.SYNTH) SynthPresets.all[loop.presetIndex].params else SynthPresets.all.first().params,
                regions = listOf(region), colorIndex = loop.category.ordinal,
            )
            mutate { pr -> pr.copy(tracks = pr.tracks + t, lengthBars = max(pr.lengthBars, region.end / pr.ticksPerBar + 1)) }
            _ui.update { it.copy(selected = p.tracks.size) }
        }
        _ui.update { it.copy(selectedRegion = region.id) }
        toast("«${loop.name}» добавлен")
    }

    // ───────────────────────── экспорт ─────────────────────────

    fun export() {
        val p = _project.value ?: return
        if (!ready || _ui.value.exportProgress != null) return
        stop()
        viewModelScope.launch {
            repo.save(p)
            val bits = settings.value.exportBits
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val safe = p.name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().ifBlank { "yume" }
            val file = File(repo.exportsDir(), "${safe}_$stamp.wav")
            val end = Timing.snapUp(p.endTick, p.ticksPerBar)
            _ui.update { it.copy(exportProgress = 0f) }
            val progressJob = launch {
                while (isActive) {
                    _ui.update { it.copy(exportProgress = NativeEngine.nativeExportProgress()) }
                    delay(80)
                }
            }
            val ok = withContext(Dispatchers.Default) { NativeEngine.nativeExportWav(file.path, end, bits) }
            progressJob.cancel()
            if (ok) ExportStore.saveToMusic(getApplication(), file)
            _ui.update { it.copy(exportProgress = null, lastExport = if (ok) file else null, message = if (ok) "Готово! Трек сохранён в «Музыка/YumeStudio»" else "Экспорт не удался") }
        }
    }

    fun clearLastExport() = _ui.update { it.copy(lastExport = null) }

    // ───────────────────────── тикер ─────────────────────────

    private fun ticker() = viewModelScope.launch {
        val m = FloatArray(NativeEngine.METER_COUNT)
        var frame = 0
        while (isActive) {
            if (ready) {
                val pos = NativeEngine.nativePosition()
                val playing = NativeEngine.nativeIsPlaying()
                _transport.value = TransportState(playing, recording, pos, recording && pos < recordStartTick)
                if (recording) pollRecording()
                NativeEngine.nativeMeters(m)
                _meters.value = m.copyOf()
                if (_ui.value.inputOpen && frame % 2 == 0) {
                    val pk = NativeEngine.nativeInputPeak()
                    _inputWave.value = _inputWave.value.drop(1) + pk
                }
                val p = _project.value
                if (p != null && playing && !recording && !p.loopOn) {
                    val songEnd = max(p.endTick, p.lengthBars * p.ticksPerBar) + p.ticksPerBar
                    if (pos > songEnd) { NativeEngine.nativeStopTransport(); seek(0.0) }
                }
                if (frame % 120 == 0) NativeEngine.nativeCollectGarbage()
            }
            frame++
            delay(16)
        }
    }

    fun onBackground() {
        if (recording) stopRecording()
        if (ready) {
            NativeEngine.nativeStopTransport()
            allNotesOff()
        }
        saveNow()
    }

    override fun onCleared() {
        super.onCleared()
        if (ready) {
            if (recording) NativeEngine.nativeStopRecording()
            NativeEngine.nativeStopTransport()
            NativeEngine.nativeStopPreview()
            NativeEngine.nativeSetInputMonitor(0, false)
            NativeEngine.nativeCloseInput()
            clipIds.values.forEach { NativeEngine.nativeUnloadClip(it) }
            NativeEngine.nativeCollectGarbage()
        }
        _project.value?.let { p -> yume.appScope.launch { repo.save(p) } }
    }
}
