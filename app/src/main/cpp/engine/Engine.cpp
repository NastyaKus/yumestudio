#include "Engine.h"

#include <chrono>
#include <cmath>

#define TSF_IMPLEMENTATION
#include "../third_party/tsf.h"

namespace yume {

namespace {
constexpr float kTsfGainDb = -3.0f;
constexpr int kTsfVoices = 64;
}  // namespace

Engine::Engine() {
    for (auto& m : meters_) m.store(0.f);
    for (auto& t : tracks_) for (double& o : t.offTick) o = -1.0;
    monRing_.setup(1 << 14);
    recRing_.setup(1 << 19);
    setSampleRate(48000.f);
}

Engine::~Engine() {
    if (writerThread_.joinable()) { writerRun_ = false; writerThread_.join(); }
    collectGarbage();
    for (auto& t : tracks_) {
        if (t.sf) tsf_close(t.sf);
        delete t.synth;
        for (auto* e : t.fx) delete e;
    }
    delete arr_;
    delete preview_;
    for (auto* c : clips_) delete c;
    if (base_) tsf_close(base_);
}

// ───────────────────────────── инициализация ─────────────────────────────

bool Engine::loadSoundFont(const void* data, int size) {
    tsf* f = tsf_load_memory(data, size);
    if (!f) return false;
    tsf_set_output(f, TSF_STEREO_INTERLEAVED, (int)sr_, kTsfGainDb);
    base_ = f;
    return true;
}

void Engine::setSampleRate(float sr) {
    sr_ = sr;
    reverb_.setup(sr);
    reverb_.setRoom(0.72f);
    reverb_.setDamp(0.45f);
    if (base_) tsf_set_output(base_, TSF_STEREO_INTERLEAVED, (int)sr_, kTsfGainDb);
}

int Engine::presetCount() const { return base_ ? tsf_get_presetcount(base_) : 0; }

std::string Engine::presetInfo(int index) const {
    if (!base_ || index < 0 || index >= base_->presetNum) return {};
    const tsf_preset& p = base_->presets[index];
    return std::to_string(p.bank) + "|" + std::to_string(p.preset) + "|" + p.presetName;
}

// ───────────────────────────── очередь команд ─────────────────────────────

void Engine::post(const Cmd& c) {
    std::lock_guard<std::mutex> lk(postMutex_);
    cmds_.push(c);
}

void Engine::trash(int type, void* p) {
    if (!p) return;
    if (!garbage_.push({type, p})) {
        // очередь мусора переполнена — лучше утечь, чем освобождать в аудиопотоке
    }
}

void Engine::freeGarbage(const Garbage& g) {
    switch (g.type) {
        case G_TSF: tsf_close((tsf*)g.ptr); break;
        case G_SYNTH: delete (Synth*)g.ptr; break;
        case G_FX: delete (Effect*)g.ptr; break;
        case G_ARR: delete (Arrangement*)g.ptr; break;
        case G_CLIP: delete (AudioData*)g.ptr; break;
        case G_PREVIEW: delete (PreviewData*)g.ptr; break;
        default: break;
    }
}

void Engine::collectGarbage() {
    std::lock_guard<std::mutex> lk(garbageMutex_);
    Garbage g;
    while (garbage_.pop(g)) freeGarbage(g);
}

void Engine::drainCommands() {
    Cmd c;
    while (cmds_.pop(c)) handle(c);
}

// ───────────────────────────── API дорожек ─────────────────────────────

void Engine::setTrackInstrument(int t, int kind, int bank, int preset) {
    if (t < 0 || t >= kNumTrackSlots) return;
    Cmd c;
    c.track = t;
    if (kind == KIND_SF2 || kind == KIND_DRUMS) {
        if (!base_) return;
        tsf* f = tsf_copy(base_);
        if (!f) return;
        tsf_set_output(f, TSF_STEREO_INTERLEAVED, (int)sr_, kTsfGainDb);
        tsf_set_max_voices(f, kTsfVoices);
        tsf_channel_set_bank_preset(f, 0, kind == KIND_DRUMS ? 128 : bank, preset);
        if (kind == KIND_DRUMS && tsf_channel_get_preset_bank(f, 0) != 128) {
            tsf_channel_set_presetnumber(f, 0, preset, 1);
        }
        tsf_channel_set_pitchrange(f, 0, 2.f);
        c.type = C_SET_TSF;
        c.ptr = f;
        c.a = kind;
    } else if (kind == KIND_SYNTH) {
        c.type = C_SET_SYNTH;
        c.ptr = new Synth(sr_);
        c.a = kind;
    } else {
        c.type = C_SET_KIND;
        c.a = kind;
    }
    post(c);
}

void Engine::removeTrack(int t) {
    Cmd c; c.type = C_SET_KIND; c.track = t; c.a = KIND_NONE; c.flag = true;
    post(c);
}

void Engine::setTrackMix(int t, float vol, float pan, float send, bool mute, bool solo) {
    Cmd c; c.type = C_MIX; c.track = t; c.f = vol; c.g = pan; c.h = send; c.flag = mute; c.flag2 = solo;
    post(c);
}

void Engine::setSynthParam(int t, int id, float v) {
    Cmd c; c.type = C_SYNTH_PARAM; c.track = t; c.a = id; c.f = v;
    post(c);
}

void Engine::setFx(int t, int slot, int type) {
    if (slot < 0 || slot >= kMaxFx) return;
    Cmd c; c.type = C_FX_SET; c.track = t; c.a = slot;
    c.ptr = type >= 0 ? createEffect(type, sr_) : nullptr;
    post(c);
}

void Engine::setFxParam(int t, int slot, int id, float v) {
    Cmd c; c.type = C_FX_PARAM; c.track = t; c.a = slot; c.b = id; c.f = v;
    post(c);
}

void Engine::setFxEnabled(int t, int slot, bool on) {
    Cmd c; c.type = C_FX_ENABLE; c.track = t; c.a = slot; c.flag = on;
    post(c);
}

void Engine::noteOn(int t, int key, float vel, int delayFrames) {
    Cmd c; c.type = C_NOTE_ON; c.track = t; c.a = key; c.b = delayFrames; c.f = vel;
    post(c);
}

void Engine::noteOff(int t, int key, int delayFrames) {
    Cmd c; c.type = C_NOTE_OFF; c.track = t; c.a = key; c.b = delayFrames;
    post(c);
}

void Engine::allNotesOff(int t) { Cmd c; c.type = C_ALL_OFF; c.track = t; post(c); }
void Engine::setSustain(int t, bool on) { Cmd c; c.type = C_SUSTAIN; c.track = t; c.flag = on; post(c); }
void Engine::setPitchBend(int t, float semis) { Cmd c; c.type = C_BEND; c.track = t; c.f = semis; post(c); }

// ───────────────────────────── транспорт ─────────────────────────────

void Engine::setArrangement(Arrangement* arr) { Cmd c; c.type = C_ARRANGEMENT; c.ptr = arr; post(c); }
void Engine::play() { Cmd c; c.type = C_PLAY; post(c); }
void Engine::stop() { Cmd c; c.type = C_STOP; post(c); }
void Engine::seek(double tick) { Cmd c; c.type = C_SEEK; c.d = tick; post(c); pos_.store(tick); }
void Engine::setBpm(float bpm) { Cmd c; c.type = C_BPM; c.f = bpm; post(c); }
void Engine::setTimeSignature(int bpb) { Cmd c; c.type = C_TIMESIG; c.a = bpb; post(c); }
void Engine::setLoop(bool on, int32_t s, int32_t e) { Cmd c; c.type = C_LOOP; c.flag = on; c.a = s; c.b = e; post(c); }
void Engine::setMetronome(bool on, float v) { Cmd c; c.type = C_METRO; c.flag = on; c.f = v; post(c); }
void Engine::setMasterVolume(float v) { Cmd c; c.type = C_MASTER; c.f = v; post(c); }
void Engine::setReverbParams(float size, float damp) { Cmd c; c.type = C_REVERB; c.f = size; c.g = damp; post(c); }

// ───────────────────────────── запись ─────────────────────────────

void Engine::startRecording(int armedTrack, int countInBars, bool audio, const std::string& wavPath) {
    if (writerThread_.joinable()) { writerRun_ = false; writerThread_.join(); }
    if (audio && !wavPath.empty()) {
        recRing_.clear();
        writerRun_ = true;
        const int rate = (int)sr_;
        writerThread_ = std::thread([this, wavPath, rate]() {
            WavWriter w;
            if (!w.open(wavPath, rate, 1, 24)) return;
            std::vector<float> tmp(8192);
            for (;;) {
                const bool run = writerRun_.load();
                size_t got;
                while ((got = recRing_.read(tmp.data(), tmp.size())) > 0) w.write(tmp.data(), (int)got);
                if (!run) break;
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
            w.close();
        });
    }
    Cmd c; c.type = C_REC_START; c.track = armedTrack; c.a = countInBars; c.flag = audio;
    post(c);
}

double Engine::stopRecording() {
    captureOn_.store(false);
    recording_.store(false);
    Cmd c; c.type = C_REC_STOP; post(c);
    if (writerThread_.joinable()) { writerRun_ = false; writerThread_.join(); }
    return captureStartTick_.load();
}

void Engine::setInputMonitor(int track, bool on) {
    Cmd c; c.type = C_MONITOR; c.track = track; c.flag = on;
    post(c);
    monitorOn_.store(on);
    if (!on) monRing_.clear();
}

void Engine::pushInput(const float* mono, int frames) {
    float pk = 0.f;
    for (int i = 0; i < frames; ++i) pk = std::max(pk, std::fabs(mono[i]));
    if (pk > inputPeak_.load(std::memory_order_relaxed)) inputPeak_.store(pk, std::memory_order_relaxed);
    if (captureOn_.load(std::memory_order_acquire)) recRing_.write(mono, frames);
    if (monitorOn_.load(std::memory_order_relaxed)) monRing_.write(mono, frames);
}

// ───────────────────────────── клипы ─────────────────────────────

int64_t Engine::loadClip(int id, const std::string& path) {
    if (id < 0 || id >= kMaxClips) return -1;
    auto* data = new AudioData();
    if (!readWav(path, *data) || data->frames() == 0) { delete data; return -1; }
    const int64_t frames = data->frames();
    {
        std::lock_guard<std::mutex> lk(clipMutex_);
        shadowClips_[id] = data;
    }
    Cmd c; c.type = C_CLIP_SET; c.a = id; c.ptr = data;
    post(c);
    return frames;
}

void Engine::unloadClip(int id) {
    if (id < 0 || id >= kMaxClips) return;
    {
        std::lock_guard<std::mutex> lk(clipMutex_);
        shadowClips_[id] = nullptr;
    }
    Cmd c; c.type = C_CLIP_SET; c.a = id; c.ptr = nullptr;
    post(c);
}

std::vector<float> Engine::clipPeaks(int id, int buckets) {
    std::vector<float> out;
    if (id < 0 || id >= kMaxClips || buckets <= 0) return out;
    std::lock_guard<std::mutex> lk(clipMutex_);
    AudioData* d = shadowClips_[id];
    if (!d) return out;
    out.assign(buckets, 0.f);
    const int64_t frames = d->frames();
    for (int b = 0; b < buckets; ++b) {
        int64_t s = frames * b / buckets, e = std::max(s + 1, frames * (b + 1) / buckets);
        float pk = 0;
        for (int64_t i = s; i < e && i < frames; ++i)
            for (int c = 0; c < d->channels; ++c) pk = std::max(pk, std::fabs(d->samples[i * d->channels + c]));
        out[b] = pk;
    }
    return out;
}

int Engine::clipSampleRate(int id) {
    std::lock_guard<std::mutex> lk(clipMutex_);
    return (id >= 0 && id < kMaxClips && shadowClips_[id]) ? shadowClips_[id]->sampleRate : 0;
}

// ───────────────────────────── превью ─────────────────────────────

void Engine::previewPattern(std::vector<NoteEv> notes, int32_t lengthTicks, float bpm) {
    auto* p = new PreviewData();
    std::sort(notes.begin(), notes.end(), [](const NoteEv& a, const NoteEv& b) { return a.tick < b.tick; });
    p->notes = std::move(notes);
    p->length = std::max<int32_t>(lengthTicks, kPPQ);
    p->bpm = bpm;
    Cmd c; c.type = C_PREVIEW; c.ptr = p;
    post(c);
}

void Engine::previewClip(int clip) { Cmd c; c.type = C_PREVIEW_CLIP; c.a = clip; post(c); }
void Engine::stopPreview() { Cmd c; c.type = C_PREVIEW_STOP; post(c); }

void Engine::takeMeters(float* out) {
    for (int i = 0; i < kMeterCount; ++i) out[i] = meters_[i].exchange(0.f);
}

int Engine::activeVoices(int t) {
    if (t < 0 || t >= kNumTrackSlots) return 0;
    Track& tr = tracks_[t];
    if (tr.sf) return tsf_active_voice_count(tr.sf);
    if (tr.synth) return tr.synth->activeVoices();
    return 0;
}

// ───────────────────────────── обработка команд (аудиопоток) ─────────────────────────────

void Engine::instNoteOn(int t, int key, float vel) {
    Track& tr = tracks_[t];
    if (key < 0 || key > 127) return;
    if (tr.sf) tsf_channel_note_on(tr.sf, 0, key, clampf(vel, 0.01f, 1.f));
    else if (tr.synth) tr.synth->noteOn(key, vel);
}

void Engine::instNoteOff(int t, int key) {
    Track& tr = tracks_[t];
    if (key < 0 || key > 127) return;
    if (tr.kind == KIND_DRUMS) return;  // ударные — one-shot
    if (tr.sf) tsf_channel_note_off(tr.sf, 0, key);
    else if (tr.synth) tr.synth->noteOff(key);
}

void Engine::liveNoteOn(int t, int key, float vel) {
    instNoteOn(t, key, vel);
    if (recording_.load(std::memory_order_relaxed) && t == armed_ && posRt_ >= countInEnd_) {
        recQueue_.push({t, posRt_, (uint8_t)key, (uint8_t)clampf(vel * 127.f, 1.f, 127.f), true});
    }
}

void Engine::liveNoteOff(int t, int key) {
    instNoteOff(t, key);
    if (recording_.load(std::memory_order_relaxed) && t == armed_) {
        recQueue_.push({t, std::max(posRt_, (double)countInEnd_), (uint8_t)key, 0, false});
    }
}

void Engine::releaseSequenced(int t) {
    Track& tr = tracks_[t];
    for (int k = 0; k < 128; ++k) {
        if (tr.offTick[k] >= 0) { instNoteOff(t, k); tr.offTick[k] = -1; }
    }
}

void Engine::releaseAllSequenced() {
    for (int t = 0; t < kMaxTracks; ++t) releaseSequenced(t);
}

void Engine::handle(const Cmd& c) {
    const int t = c.track;
    const bool validTrack = t >= 0 && t < kNumTrackSlots;
    switch (c.type) {
        case C_NOTE_ON:
            if (!validTrack) break;
            if (c.b > 0) {
                Track& tr = tracks_[t];
                if (tr.nDelayed < 64) tr.delayed[tr.nDelayed++] = {c.a, c.f, c.b, true};
            } else {
                liveNoteOn(t, c.a, c.f);
            }
            break;
        case C_NOTE_OFF:
            if (!validTrack) break;
            if (c.b > 0) {
                Track& tr = tracks_[t];
                if (tr.nDelayed < 64) tr.delayed[tr.nDelayed++] = {c.a, 0.f, c.b, false};
            } else {
                liveNoteOff(t, c.a);
            }
            break;
        case C_ALL_OFF:
            if (!validTrack) break;
            if (tracks_[t].sf) { tsf_channel_set_sustain(tracks_[t].sf, 0, 0); tsf_channel_note_off_all(tracks_[t].sf, 0); }
            if (tracks_[t].synth) tracks_[t].synth->allNotesOff();
            tracks_[t].nDelayed = 0;
            break;
        case C_SUSTAIN:
            if (!validTrack) break;
            if (tracks_[t].sf) tsf_channel_set_sustain(tracks_[t].sf, 0, c.flag ? 1 : 0);
            if (tracks_[t].synth) tracks_[t].synth->setSustain(c.flag);
            break;
        case C_BEND:
            if (!validTrack) break;
            if (tracks_[t].sf) tsf_channel_set_pitchwheel(tracks_[t].sf, 0, (int)clampf(8192.f + c.f / 2.f * 8191.f, 0.f, 16383.f));
            if (tracks_[t].synth) tracks_[t].synth->setPitchBend(c.f);
            break;
        case C_SET_TSF: {
            if (!validTrack) { tsf_close((tsf*)c.ptr); break; }
            Track& tr = tracks_[t];
            if (tr.sf) trash(G_TSF, tr.sf);
            if (tr.synth) { trash(G_SYNTH, tr.synth); tr.synth = nullptr; }
            tr.sf = (tsf*)c.ptr;
            tr.kind = c.a;
            for (double& o : tr.offTick) o = -1;
            break;
        }
        case C_SET_SYNTH: {
            if (!validTrack) { delete (Synth*)c.ptr; break; }
            Track& tr = tracks_[t];
            if (tr.sf) { trash(G_TSF, tr.sf); tr.sf = nullptr; }
            if (tr.synth) trash(G_SYNTH, tr.synth);
            tr.synth = (Synth*)c.ptr;
            tr.kind = c.a;
            for (double& o : tr.offTick) o = -1;
            break;
        }
        case C_SET_KIND: {
            if (!validTrack) break;
            Track& tr = tracks_[t];
            if (tr.sf) { trash(G_TSF, tr.sf); tr.sf = nullptr; }
            if (tr.synth) { trash(G_SYNTH, tr.synth); tr.synth = nullptr; }
            tr.kind = c.a;
            if (c.flag) {  // полное удаление дорожки
                for (auto*& e : tr.fx) { trash(G_FX, e); e = nullptr; }
                tr.vol = 0.8f; tr.pan = 0; tr.send = 0.12f; tr.mute = tr.solo = false;
                tr.monitor = false;
                tr.gL = tr.gR = tr.gSend = 0;
            }
            tr.nDelayed = 0;
            for (double& o : tr.offTick) o = -1;
            break;
        }
        case C_SYNTH_PARAM:
            if (validTrack && tracks_[t].synth) tracks_[t].synth->setParam(c.a, c.f);
            break;
        case C_MIX:
            if (!validTrack) break;
            tracks_[t].vol = c.f; tracks_[t].pan = c.g; tracks_[t].send = c.h;
            tracks_[t].mute = c.flag; tracks_[t].solo = c.flag2;
            break;
        case C_FX_SET: {
            if (!validTrack || c.a < 0 || c.a >= kMaxFx) { delete (Effect*)c.ptr; break; }
            Effect*& slot = tracks_[t].fx[c.a];
            trash(G_FX, slot);
            slot = (Effect*)c.ptr;
            break;
        }
        case C_FX_PARAM:
            if (validTrack && c.a >= 0 && c.a < kMaxFx && tracks_[t].fx[c.a]) tracks_[t].fx[c.a]->setParam(c.b, c.f);
            break;
        case C_FX_ENABLE:
            if (validTrack && c.a >= 0 && c.a < kMaxFx && tracks_[t].fx[c.a]) tracks_[t].fx[c.a]->setEnabled(c.flag);
            break;
        case C_ARRANGEMENT:
            trash(G_ARR, arr_);
            arr_ = (Arrangement*)c.ptr;
            break;
        case C_PLAY:
            if (!playRt_) { playRt_ = true; countInEnd_ = INT32_MIN / 2; }
            break;
        case C_STOP:
            playRt_ = false;
            releaseAllSequenced();
            if (recording_.load()) { recording_.store(false); captureOn_.store(false); }
            armed_ = -1;
            break;
        case C_SEEK:
            posRt_ = c.d;
            releaseAllSequenced();
            break;
        case C_BPM: bpm_ = clampf(c.f, 30.f, 300.f); break;
        case C_TIMESIG: beatsPerBar_ = std::max(1, std::min(16, c.a)); break;
        case C_LOOP: loopOn_ = c.flag; loopStart_ = c.a; loopEnd_ = c.b; break;
        case C_METRO: metroOn_ = c.flag; metroVol_ = c.f; break;
        case C_MASTER: masterVol_ = c.f; break;
        case C_REVERB: reverb_.setRoom(c.f); reverb_.setDamp(c.g); break;
        case C_REC_START: {
            armed_ = t;
            const double start = posRt_;
            recStartTick_ = start;
            if (!playRt_) {
                posRt_ = start - (double)c.a * beatsPerBar_ * kPPQ;
                countInEnd_ = (int)std::floor(start);
                releaseAllSequenced();
                playRt_ = true;
            } else {
                countInEnd_ = (int)std::floor(start);
            }
            if (c.flag) {
                captureStartTick_.store(posRt_);
                captureOn_.store(true, std::memory_order_release);
            }
            recording_.store(true);
            break;
        }
        case C_REC_STOP:
            recording_.store(false);
            captureOn_.store(false);
            armed_ = -1;
            break;
        case C_MONITOR:
            if (validTrack) {
                for (auto& tr : tracks_) tr.monitor = false;
                tracks_[t].monitor = c.flag;
            }
            break;
        case C_CLIP_SET:
            if (c.a >= 0 && c.a < kMaxClips) {
                trash(G_CLIP, clips_[c.a]);
                clips_[c.a] = (AudioData*)c.ptr;
                if (previewClip_ == c.a) previewClip_ = -1;
            } else {
                delete (AudioData*)c.ptr;
            }
            break;
        case C_PREVIEW:
            trash(G_PREVIEW, preview_);
            releaseSequenced(kPreviewTrack);
            preview_ = (PreviewData*)c.ptr;
            previewPos_ = 0.0;
            previewOn_ = true;
            previewClip_ = -1;
            break;
        case C_PREVIEW_CLIP:
            previewOn_ = false;
            releaseSequenced(kPreviewTrack);
            previewClip_ = c.a;
            previewClipPos_ = 0.0;
            break;
        case C_PREVIEW_STOP:
            previewOn_ = false;
            previewClip_ = -1;
            releaseSequenced(kPreviewTrack);
            if (tracks_[kPreviewTrack].sf) tsf_channel_note_off_all(tracks_[kPreviewTrack].sf, 0);
            if (tracks_[kPreviewTrack].synth) tracks_[kPreviewTrack].synth->allNotesOff();
            break;
        default: break;
    }
}

// ───────────────────────────── секвенсор ─────────────────────────────

void Engine::fireMidi(int t, const std::vector<MidiRegion>& regs, double t0, double t1) {
    Track& tr = tracks_[t];
    for (const MidiRegion& r : regs) {
        const double rs = r.start, re = (double)r.start + r.length;
        if (re <= t0 || rs >= t1 || r.length <= 0) continue;
        const double l0 = std::max(t0, rs) - rs, l1 = std::min(t1, re) - rs;
        const int32_t cl = r.contentLen > 0 ? r.contentLen : r.length;
        const int64_t k0 = (int64_t)std::floor(l0 / cl);
        const int64_t k1 = (int64_t)std::floor((l1 - 1e-9) / cl);
        for (int64_t k = k0; k <= k1; ++k) {
            const double base = (double)k * cl;
            for (const NoteEv& nt : r.notes) {
                if (nt.tick >= cl) break;
                const double at = base + nt.tick;
                if (at < l0) continue;
                if (at >= l1) break;
                double end = std::min(at + std::max(nt.len, 1), (double)r.length);
                end = std::min(end, base + cl);
                instNoteOn(t, nt.key, nt.vel / 127.f);
                tr.offTick[nt.key] = rs + end;
            }
        }
    }
}

void Engine::fireNoteOffs(int t, double t1) {
    Track& tr = tracks_[t];
    for (int k = 0; k < 128; ++k) {
        if (tr.offTick[k] >= 0 && tr.offTick[k] < t1) {
            instNoteOff(t, k);
            tr.offTick[k] = -1;
        }
    }
}

void Engine::mixAudioRegions(int t, int n, double t0, float* L, float* R) {
    if (!arr_) return;
    const double spt = samplesPerTick();
    const double t1 = t0 + n / spt;
    for (const AudioRegion& r : arr_->audio[t]) {
        const double rs = r.start, re = (double)r.start + r.length;
        if (re <= t0 || rs >= t1 || r.clip < 0 || r.clip >= kMaxClips) continue;
        const AudioData* d = clips_[r.clip];
        if (!d) continue;
        const double rate = (double)d->sampleRate / sr_;
        const int64_t frames = d->frames();
        const int ch = d->channels;
        const double fadeLen = 0.004 * sr_;
        for (int i = 0; i < n; ++i) {
            const double tick = t0 + i / spt;
            if (tick < rs || tick >= re) continue;
            const double fromStart = (tick - rs) * spt;
            const double toEnd = (re - tick) * spt;
            const double cp = (double)r.offset + fromStart * rate;
            if (cp < 0) continue;
            const int64_t i0 = (int64_t)cp;
            if (i0 + 1 >= frames) continue;
            const float fr = (float)(cp - (double)i0);
            float g = r.gain;
            if (fromStart < fadeLen) g *= (float)(fromStart / fadeLen);
            if (toEnd < fadeLen) g *= (float)(toEnd / fadeLen);
            if (ch == 1) {
                float a = d->samples[i0], b = d->samples[i0 + 1];
                float v = (a + (b - a) * fr) * g;
                L[i] += v; R[i] += v;
            } else {
                const float* s0 = &d->samples[i0 * ch];
                const float* s1 = &d->samples[(i0 + 1) * ch];
                L[i] += (s0[0] + (s1[0] - s0[0]) * fr) * g;
                R[i] += (s0[1] + (s1[1] - s0[1]) * fr) * g;
            }
        }
    }
}

void Engine::renderTrack(int t, int n, double t0) {
    Track& tr = tracks_[t];
    std::fill(tL_, tL_ + n, 0.f);
    std::fill(tR_, tR_ + n, 0.f);
    if (tr.sf) {
        tsf_render_float(tr.sf, tsfBuf_, n, 0);
        for (int i = 0; i < n; ++i) { tL_[i] += tsfBuf_[i * 2]; tR_[i] += tsfBuf_[i * 2 + 1]; }
    }
    if (tr.synth) tr.synth->render(tL_, tR_, n);
    if (t < kMaxTracks && playRt_ && arr_) mixAudioRegions(t, n, t0, tL_, tR_);
    if (tr.monitor) {
        for (int i = 0; i < n; ++i) { tL_[i] += inBuf_[i]; tR_[i] += inBuf_[i]; }
    }
    for (Effect* e : tr.fx) if (e) e->run(tL_, tR_, n);
}

void Engine::renderChunk(float* out, int n, bool includeMetronome) {
    std::fill(busL_, busL_ + n, 0.f);
    std::fill(busR_, busR_ + n, 0.f);
    std::fill(sendL_, sendL_ + n, 0.f);
    std::fill(sendR_, sendR_ + n, 0.f);

    // ── вход для мониторинга ──
    std::fill(inBuf_, inBuf_ + n, 0.f);
    if (monitorOn_.load(std::memory_order_relaxed)) {
        size_t avail = monRing_.available();
        if (avail > (size_t)(n + 2048)) monRing_.skip(avail - n - 256);  // не копим задержку
        monRing_.read(inBuf_, n);
    }

    // ── отложенные события (страм гитары и т.п.) ──
    for (int t = 0; t < kNumTrackSlots; ++t) {
        Track& tr = tracks_[t];
        int w = 0;
        for (int i = 0; i < tr.nDelayed; ++i) {
            Delayed d = tr.delayed[i];
            d.frames -= n;
            if (d.frames <= 0) {
                if (d.on) liveNoteOn(t, d.key, d.vel);
                else liveNoteOff(t, d.key);
            } else {
                tr.delayed[w++] = d;
            }
        }
        tr.nDelayed = w;
    }

    // ── секвенсор ──
    const double spt = samplesPerTick();
    const double t0 = posRt_;
    if (playRt_) {
        const double t1 = t0 + n / spt;
        const bool canLoop = loopOn_ && loopEnd_ > loopStart_ && t0 >= countInEnd_;
        if (canLoop && t0 < loopEnd_ && t1 >= loopEnd_) {
            for (int t = 0; t < kMaxTracks; ++t) {
                if (arr_) fireMidi(t, arr_->midi[t], t0, loopEnd_);
            }
            releaseAllSequenced();
            const double nt = loopStart_ + (t1 - loopEnd_);
            for (int t = 0; t < kMaxTracks; ++t) {
                if (arr_) fireMidi(t, arr_->midi[t], loopStart_, nt);
            }
            posRt_ = nt;
        } else {
            for (int t = 0; t < kMaxTracks; ++t) {
                if (arr_) fireMidi(t, arr_->midi[t], t0, t1);
                fireNoteOffs(t, t1);
            }
            posRt_ = t1;
        }
        // метроном: доля внутри блока
        const bool countIn = t0 < countInEnd_;
        if (includeMetronome && (metroOn_ || countIn)) {
            const double beat = std::ceil(t0 / kPPQ) * kPPQ;
            if (beat < t1) {
                const int64_t idx = (int64_t)std::llround(beat / kPPQ);
                const bool down = ((idx % beatsPerBar_) + beatsPerBar_) % beatsPerBar_ == 0;
                clickFreq_ = down ? 2093.f : 1568.f;
                clickEnv_ = down ? 1.f : 0.7f;
                clickPhase_ = 0.f;
            }
        }
    }
    pos_.store(posRt_, std::memory_order_relaxed);

    // ── превью-паттерн ──
    if (previewOn_ && preview_) {
        const double pspt = sr_ * 60.0 / (preview_->bpm * kPPQ);
        const double p0 = previewPos_, p1 = p0 + n / pspt;
        const double len = preview_->length;
        auto fireRange = [&](double a, double b) {
            Track& tr = tracks_[kPreviewTrack];
            for (const NoteEv& nt : preview_->notes) {
                if (nt.tick < a) continue;
                if (nt.tick >= b) break;
                instNoteOn(kPreviewTrack, nt.key, nt.vel / 127.f);
                tr.offTick[nt.key] = std::min((double)nt.tick + std::max(nt.len, 1), len);
            }
        };
        if (p1 >= len) {
            fireRange(p0, len);
            releaseSequenced(kPreviewTrack);
            previewPos_ = p1 - len;
            fireRange(0, previewPos_);
        } else {
            fireRange(p0, p1);
            fireNoteOffs(kPreviewTrack, p1);
            previewPos_ = p1;
        }
    }

    // ── дорожки ──
    bool anySolo = false;
    for (int t = 0; t < kMaxTracks; ++t) anySolo |= (tracks_[t].solo && tracks_[t].kind != KIND_NONE);
    for (int t = 0; t < kNumTrackSlots; ++t) {
        Track& tr = tracks_[t];
        if (tr.kind == KIND_NONE && !tr.monitor) continue;
        if (tr.kind == KIND_AUDIO && !playRt_ && !tr.monitor) {
            // аудиодорожке без воспроизведения нечего рендерить, кроме хвостов эффектов
            bool anyFx = false;
            for (Effect* e : tr.fx) anyFx |= e != nullptr;
            if (!anyFx) continue;
        }
        renderTrack(t, n, t0);
        if (t == kPreviewTrack && previewClip_ >= 0 && clips_[previewClip_]) {
            const AudioData* d = clips_[previewClip_];
            const double rate = (double)d->sampleRate / sr_;
            for (int i = 0; i < n; ++i) {
                int64_t idx = (int64_t)previewClipPos_;
                if (idx + 1 >= d->frames()) { previewClip_ = -1; break; }
                float a = d->samples[idx * d->channels];
                float b = d->samples[idx * d->channels + (d->channels > 1 ? 1 : 0)];
                tL_[i] += a; tR_[i] += b;
                previewClipPos_ += rate;
            }
        }
        const bool audible = t == kPreviewTrack || (!tr.mute && (!anySolo || tr.solo));
        const float ang = (clampf(tr.pan, -1.f, 1.f) + 1.f) * 0.25f * kPi;
        const float targetL = audible ? tr.vol * std::cos(ang) * 1.41421356f : 0.f;
        const float targetR = audible ? tr.vol * std::sin(ang) * 1.41421356f : 0.f;
        const float targetS = audible ? tr.send : 0.f;
        const float dL = (targetL - tr.gL) / n, dR = (targetR - tr.gR) / n, dS = (targetS - tr.gSend) / n;
        float pkL = 0, pkR = 0;
        for (int i = 0; i < n; ++i) {
            tr.gL += dL; tr.gR += dR; tr.gSend += dS;
            const float l = tL_[i] * tr.gL, r = tR_[i] * tr.gR;
            busL_[i] += l; busR_[i] += r;
            sendL_[i] += l * tr.gSend; sendR_[i] += r * tr.gSend;
            pkL = std::max(pkL, std::fabs(l));
            pkR = std::max(pkR, std::fabs(r));
        }
        tr.gL = targetL; tr.gR = targetR; tr.gSend = targetS;
        auto& mL = meters_[t * 2];
        auto& mR = meters_[t * 2 + 1];
        if (pkL > mL.load(std::memory_order_relaxed)) mL.store(pkL, std::memory_order_relaxed);
        if (pkR > mR.load(std::memory_order_relaxed)) mR.store(pkR, std::memory_order_relaxed);
    }

    // ── реверб-шина ──
    for (int i = 0; i < n; ++i) {
        float wl, wr;
        reverb_.process(sendL_[i], sendR_[i], wl, wr);
        busL_[i] += wl * 2.5f;
        busR_[i] += wr * 2.5f;
    }

    // ── мастер: громкость, метроном, лимитер ──
    const float ceilG = 0.966f;  // -0.3 дБ
    const float rel = std::exp(-1.f / (0.12f * sr_));
    float pkL = 0, pkR = 0;
    for (int i = 0; i < n; ++i) {
        float l = busL_[i] * masterVol_, r = busR_[i] * masterVol_;
        const float a = std::max(std::fabs(l), std::fabs(r)) * limGain_;
        if (a > ceilG) limGain_ = ceilG / std::max(std::fabs(l), std::fabs(r));
        else limGain_ = 1.f + (limGain_ - 1.f) * rel;
        l *= limGain_; r *= limGain_;
        if (clickEnv_ > 1e-4f) {
            const float s = std::sin(kTwoPi * clickPhase_) * clickEnv_ * metroVol_ * 0.5f;
            clickPhase_ += clickFreq_ / sr_;
            if (clickPhase_ >= 1.f) clickPhase_ -= 1.f;
            clickEnv_ *= 0.9985f;
            l += s; r += s;
        }
        out[i * 2] = l;
        out[i * 2 + 1] = r;
        pkL = std::max(pkL, std::fabs(l));
        pkR = std::max(pkR, std::fabs(r));
    }
    auto& mL = meters_[kNumTrackSlots * 2];
    auto& mR = meters_[kNumTrackSlots * 2 + 1];
    if (pkL > mL.load(std::memory_order_relaxed)) mL.store(pkL, std::memory_order_relaxed);
    if (pkR > mR.load(std::memory_order_relaxed)) mR.store(pkR, std::memory_order_relaxed);
}

void Engine::render(float* out, int frames) {
    if (offline_.load(std::memory_order_acquire)) {
        std::fill(out, out + frames * 2, 0.f);
        return;
    }
    drainCommands();
    int done = 0;
    while (done < frames) {
        const int n = std::min(kChunk, frames - done);
        renderChunk(out + done * 2, n, true);
        done += n;
    }
    playing_.store(playRt_, std::memory_order_relaxed);
}

ExportResult Engine::renderToFile(const std::string& path, int32_t endTick, int bits,
                                  std::atomic<float>* progress) {
    ExportResult res;
    offline_.store(true);
    drainCommands();
    const bool savedLoop = loopOn_;
    const double savedPos = posRt_;
    loopOn_ = false;
    previewOn_ = false;
    previewClip_ = -1;
    clickEnv_ = 0.f;
    for (int t = 0; t < kNumTrackSlots; ++t) {
        Track& tr = tracks_[t];
        if (tr.sf) tsf_channel_sounds_off_all(tr.sf, 0);
        if (tr.synth) tr.synth->allSoundOff();
        for (Effect* e : tr.fx) if (e) e->reset();
        for (double& o : tr.offTick) o = -1;
        tr.nDelayed = 0;
    }
    reverb_.reset();
    limGain_ = 1.f;
    countInEnd_ = INT32_MIN / 2;
    posRt_ = 0.0;
    playRt_ = true;

    WavWriter w;
    if (!w.open(path, (int)sr_, 2, bits == 24 ? 24 : 16)) {
        offline_.store(false);
        return res;
    }
    const double spt = samplesPerTick();
    const int64_t musicFrames = (int64_t)(endTick * spt);
    const int64_t tailFrames = (int64_t)(2.5 * sr_);
    const int64_t total = musicFrames + tailFrames;
    float buf[kChunk * 2];
    int64_t doneFrames = 0;
    while (doneFrames < total) {
        if (playRt_ && posRt_ >= endTick) { playRt_ = false; releaseAllSequenced(); }
        const int n = (int)std::min<int64_t>(kChunk, total - doneFrames);
        renderChunk(buf, n, false);
        for (int i = 0; i < n * 2; ++i) res.peak = std::max(res.peak, std::fabs(buf[i]));
        w.write(buf, n);
        doneFrames += n;
        if (progress && (doneFrames & 4095) < kChunk) progress->store((float)doneFrames / (float)total);
    }
    w.close();
    if (progress) progress->store(1.f);
    playRt_ = false;
    releaseAllSequenced();
    posRt_ = savedPos;
    pos_.store(posRt_);
    playing_.store(false);
    loopOn_ = savedLoop;
    res.ok = true;
    res.frames = total;
    offline_.store(false);
    return res;
}

}  // namespace yume
