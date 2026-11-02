// JNI-мост для com.yume.studio.audio.NativeEngine
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <jni.h>

#include <atomic>
#include <memory>
#include <string>
#include <vector>

#include "../engine/AudioHost.h"
#include "../engine/Engine.h"

using namespace yume;

namespace {
std::unique_ptr<Engine> gEngine;
std::unique_ptr<AudioHost> gHost;
std::atomic<float> gExportProgress{0.f};

std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

int msToFrames(float ms) { return ms <= 0.f ? 0 : (int)(ms * 0.001f * AudioHost::kSampleRate); }
}  // namespace

#define FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_yume_studio_audio_NativeEngine_##name
#define E if (!gEngine) return

FN(jboolean, nativeCreate)(JNIEnv*, jobject) {
    if (!gEngine) {
        gEngine = std::make_unique<Engine>();
        gEngine->setSampleRate((float)AudioHost::kSampleRate);
        gHost = std::make_unique<AudioHost>(*gEngine);
    }
    return JNI_TRUE;
}

FN(jboolean, nativeLoadSoundFont)(JNIEnv* env, jobject, jobject assets, jstring path) {
    if (!gEngine) return JNI_FALSE;
    if (gEngine->hasSoundFont()) return JNI_TRUE;
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    if (!mgr) return JNI_FALSE;
    std::string p = str(env, path);
    AAsset* a = AAssetManager_open(mgr, p.c_str(), AASSET_MODE_BUFFER);
    if (!a) return JNI_FALSE;
    const void* buf = AAsset_getBuffer(a);
    const off_t len = AAsset_getLength(a);
    bool ok = buf && gEngine->loadSoundFont(buf, (int)len);
    AAsset_close(a);
    return ok ? JNI_TRUE : JNI_FALSE;
}

FN(jboolean, nativeStart)(JNIEnv*, jobject) { return gHost && gHost->start() ? JNI_TRUE : JNI_FALSE; }
FN(void, nativeStop)(JNIEnv*, jobject) { if (gHost) gHost->stop(); }
FN(jint, nativeSampleRate)(JNIEnv*, jobject) { return AudioHost::kSampleRate; }
FN(jfloat, nativeOutputLatencyMs)(JNIEnv*, jobject) { return gHost ? gHost->outputLatencyMs() : 0.f; }
FN(jfloat, nativeInputLatencyMs)(JNIEnv*, jobject) { return gHost ? gHost->inputLatencyMs() : 0.f; }

FN(jobjectArray, nativePresetList)(JNIEnv* env, jobject) {
    jclass sc = env->FindClass("java/lang/String");
    const int n = gEngine ? gEngine->presetCount() : 0;
    jobjectArray arr = env->NewObjectArray(n, sc, nullptr);
    for (int i = 0; i < n; ++i) {
        jstring s = env->NewStringUTF(gEngine->presetInfo(i).c_str());
        env->SetObjectArrayElement(arr, i, s);
        env->DeleteLocalRef(s);
    }
    return arr;
}

FN(void, nativeSetTrackInstrument)(JNIEnv*, jobject, jint t, jint kind, jint bank, jint preset) {
    E; gEngine->setTrackInstrument(t, kind, bank, preset);
}
FN(void, nativeRemoveTrack)(JNIEnv*, jobject, jint t) { E; gEngine->removeTrack(t); }
FN(void, nativeSetTrackMix)(JNIEnv*, jobject, jint t, jfloat vol, jfloat pan, jfloat send, jboolean mute, jboolean solo) {
    E; gEngine->setTrackMix(t, vol, pan, send, mute, solo);
}
FN(void, nativeSetSynthParam)(JNIEnv*, jobject, jint t, jint id, jfloat v) { E; gEngine->setSynthParam(t, id, v); }
FN(void, nativeSetFx)(JNIEnv*, jobject, jint t, jint slot, jint type) { E; gEngine->setFx(t, slot, type); }
FN(void, nativeSetFxParam)(JNIEnv*, jobject, jint t, jint slot, jint id, jfloat v) { E; gEngine->setFxParam(t, slot, id, v); }
FN(void, nativeSetFxEnabled)(JNIEnv*, jobject, jint t, jint slot, jboolean on) { E; gEngine->setFxEnabled(t, slot, on); }

FN(void, nativeNoteOn)(JNIEnv*, jobject, jint t, jint key, jfloat vel, jfloat delayMs) {
    E; gEngine->noteOn(t, key, vel, msToFrames(delayMs));
}
FN(void, nativeNoteOff)(JNIEnv*, jobject, jint t, jint key, jfloat delayMs) {
    E; gEngine->noteOff(t, key, msToFrames(delayMs));
}
FN(void, nativeAllNotesOff)(JNIEnv*, jobject, jint t) { E; gEngine->allNotesOff(t); }
FN(void, nativeSetSustain)(JNIEnv*, jobject, jint t, jboolean on) { E; gEngine->setSustain(t, on); }
FN(void, nativeSetPitchBend)(JNIEnv*, jobject, jint t, jfloat semis) { E; gEngine->setPitchBend(t, semis); }

// Формат: [nTracks, {track, nMidi, {start,len,contentLen,nNotes,{tick,len,key,vel}*}*,
//                    nAudio, {start,len,clip,offHi,offLo,gainMilli}*}*]
FN(void, nativeSetArrangement)(JNIEnv* env, jobject, jintArray data) {
    E;
    const jsize n = env->GetArrayLength(data);
    std::vector<jint> d(n);
    env->GetIntArrayRegion(data, 0, n, d.data());
    auto* arr = new Arrangement();
    size_t i = 0;
    auto next = [&]() -> int { return i < d.size() ? d[i++] : 0; };
    const int nTracks = next();
    for (int k = 0; k < nTracks && i < d.size(); ++k) {
        const int t = next();
        const bool ok = t >= 0 && t < kMaxTracks;
        const int nMidi = next();
        for (int m = 0; m < nMidi; ++m) {
            MidiRegion r;
            r.start = next(); r.length = next(); r.contentLen = next();
            const int nn = next();
            r.notes.reserve(nn);
            for (int q = 0; q < nn; ++q) {
                NoteEv e{};
                e.tick = next(); e.len = next(); e.key = (uint8_t)next(); e.vel = (uint8_t)next();
                r.notes.push_back(e);
            }
            std::sort(r.notes.begin(), r.notes.end(), [](const NoteEv& a, const NoteEv& b) { return a.tick < b.tick; });
            if (ok) arr->midi[t].push_back(std::move(r));
        }
        const int nAudio = next();
        for (int a = 0; a < nAudio; ++a) {
            AudioRegion r;
            r.start = next(); r.length = next(); r.clip = next();
            const int64_t hi = (uint32_t)next(), lo = (uint32_t)next();
            r.offset = (int64_t)((hi << 32) | lo);
            r.gain = next() / 1000.f;
            if (ok) arr->audio[t].push_back(r);
        }
    }
    gEngine->setArrangement(arr);
}

FN(void, nativePlay)(JNIEnv*, jobject) { E; gEngine->play(); }
FN(void, nativeStopTransport)(JNIEnv*, jobject) { E; gEngine->stop(); }
FN(void, nativeSeek)(JNIEnv*, jobject, jdouble tick) { E; gEngine->seek(tick); }
FN(jdouble, nativePosition)(JNIEnv*, jobject) { return gEngine ? gEngine->position() : 0.0; }
FN(jboolean, nativeIsPlaying)(JNIEnv*, jobject) { return gEngine && gEngine->isPlaying(); }
FN(void, nativeSetBpm)(JNIEnv*, jobject, jfloat bpm) { E; gEngine->setBpm(bpm); }
FN(void, nativeSetTimeSignature)(JNIEnv*, jobject, jint bpb) { E; gEngine->setTimeSignature(bpb); }
FN(void, nativeSetLoop)(JNIEnv*, jobject, jboolean on, jint s, jint e) { E; gEngine->setLoop(on, s, e); }
FN(void, nativeSetMetronome)(JNIEnv*, jobject, jboolean on, jfloat v) { E; gEngine->setMetronome(on, v); }
FN(void, nativeSetMasterVolume)(JNIEnv*, jobject, jfloat v) { E; gEngine->setMasterVolume(v); }
FN(void, nativeSetReverb)(JNIEnv*, jobject, jfloat size, jfloat damp) { E; gEngine->setReverbParams(size, damp); }

FN(jboolean, nativeOpenInput)(JNIEnv*, jobject) { return gHost && gHost->openInput() ? JNI_TRUE : JNI_FALSE; }
FN(void, nativeCloseInput)(JNIEnv*, jobject) { if (gHost) gHost->closeInput(); }
FN(void, nativeSetInputMonitor)(JNIEnv*, jobject, jint t, jboolean on) { E; gEngine->setInputMonitor(t, on); }
FN(jfloat, nativeInputPeak)(JNIEnv*, jobject) { return gEngine ? gEngine->takeInputPeak() : 0.f; }

FN(void, nativeStartRecording)(JNIEnv* env, jobject, jint t, jint countIn, jboolean audio, jstring path) {
    E; gEngine->startRecording(t, countIn, audio, str(env, path));
}
FN(jdouble, nativeStopRecording)(JNIEnv*, jobject) { return gEngine ? gEngine->stopRecording() : 0.0; }
FN(jboolean, nativeIsRecording)(JNIEnv*, jobject) { return gEngine && gEngine->isRecording(); }

// [track, tick, key, vel, on] * n
FN(jintArray, nativePollRecorded)(JNIEnv* env, jobject) {
    std::vector<jint> out;
    if (gEngine) {
        RecordedNote rn;
        while (gEngine->pollRecorded(rn)) {
            out.push_back(rn.track);
            out.push_back((jint)std::llround(rn.tick));
            out.push_back(rn.key);
            out.push_back(rn.vel);
            out.push_back(rn.on ? 1 : 0);
        }
    }
    jintArray a = env->NewIntArray((jsize)out.size());
    if (!out.empty()) env->SetIntArrayRegion(a, 0, (jsize)out.size(), out.data());
    return a;
}

FN(jlong, nativeLoadClip)(JNIEnv* env, jobject, jint id, jstring path) {
    return gEngine ? gEngine->loadClip(id, str(env, path)) : -1;
}
FN(void, nativeUnloadClip)(JNIEnv*, jobject, jint id) { E; gEngine->unloadClip(id); }
FN(jfloatArray, nativeClipPeaks)(JNIEnv* env, jobject, jint id, jint buckets) {
    std::vector<float> p = gEngine ? gEngine->clipPeaks(id, buckets) : std::vector<float>();
    jfloatArray a = env->NewFloatArray((jsize)p.size());
    if (!p.empty()) env->SetFloatArrayRegion(a, 0, (jsize)p.size(), p.data());
    return a;
}
FN(jint, nativeClipSampleRate)(JNIEnv*, jobject, jint id) { return gEngine ? gEngine->clipSampleRate(id) : 0; }

// notes: [tick,len,key,vel]*
FN(void, nativePreviewPattern)(JNIEnv* env, jobject, jintArray notes, jint lengthTicks, jfloat bpm) {
    E;
    const jsize n = env->GetArrayLength(notes);
    std::vector<jint> d(n);
    env->GetIntArrayRegion(notes, 0, n, d.data());
    std::vector<NoteEv> ev;
    for (jsize i = 0; i + 3 < n; i += 4) ev.push_back({d[i], d[i + 1], (uint8_t)d[i + 2], (uint8_t)d[i + 3]});
    gEngine->previewPattern(std::move(ev), lengthTicks, bpm);
}
FN(void, nativePreviewClip)(JNIEnv*, jobject, jint id) { E; gEngine->previewClip(id); }
FN(void, nativeStopPreview)(JNIEnv*, jobject) { E; gEngine->stopPreview(); }

FN(void, nativeMeters)(JNIEnv* env, jobject, jfloatArray out) {
    E;
    float m[Engine::kMeterCount];
    gEngine->takeMeters(m);
    const jsize n = std::min<jsize>(env->GetArrayLength(out), Engine::kMeterCount);
    env->SetFloatArrayRegion(out, 0, n, m);
}

FN(jboolean, nativeExportWav)(JNIEnv* env, jobject, jstring path, jint endTick, jint bits) {
    if (!gEngine) return JNI_FALSE;
    gExportProgress = 0.f;
    const bool wasRunning = gHost && gHost->isRunning();
    if (gHost) gHost->stop();
    ExportResult r = gEngine->renderToFile(str(env, path), endTick, bits, &gExportProgress);
    if (wasRunning && gHost) gHost->start();
    gEngine->collectGarbage();
    return r.ok ? JNI_TRUE : JNI_FALSE;
}
FN(jfloat, nativeExportProgress)(JNIEnv*, jobject) { return gExportProgress.load(); }
FN(void, nativeCollectGarbage)(JNIEnv*, jobject) { E; gEngine->collectGarbage(); }
