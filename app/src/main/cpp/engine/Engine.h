#pragma once
// Платформенно-независимое ядро Yume Studio: дорожки, инструменты, FX, секвенсор, микшер.
// Аудиопоток вызывает render(); всё остальное — из UI/JNI-потоков через очередь команд.
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "../dsp/Effects.h"
#include "../dsp/Filters.h"
#include "../dsp/Reverb.h"
#include "../instruments/Synth.h"
#include "Queues.h"
#include "WavIO.h"

struct tsf;

namespace yume {

constexpr int kMaxTracks = 32;
constexpr int kPreviewTrack = kMaxTracks;  // служебная дорожка для прослушивания
constexpr int kNumTrackSlots = kMaxTracks + 1;
constexpr int kMaxFx = 8;
constexpr int kMaxClips = 512;
constexpr int kPPQ = 960;
constexpr int kChunk = 64;

enum InstrumentKind : int { KIND_NONE = 0, KIND_SF2 = 1, KIND_SYNTH = 2, KIND_DRUMS = 3, KIND_AUDIO = 4 };

struct NoteEv { int32_t tick, len; uint8_t key, vel; };
struct MidiRegion { int32_t start = 0, length = 0, contentLen = 0; std::vector<NoteEv> notes; };
struct AudioRegion { int32_t start = 0, length = 0; int clip = -1; int64_t offset = 0; float gain = 1.f; };
struct Arrangement {
    std::vector<MidiRegion> midi[kMaxTracks];
    std::vector<AudioRegion> audio[kMaxTracks];
};

struct RecordedNote { int32_t track; double tick; uint8_t key, vel; bool on; };

struct ExportResult { bool ok = false; int64_t frames = 0; float peak = 0.f; };

class Engine {
public:
    Engine();
    ~Engine();

    // ── инициализация (не из аудиопотока) ──
    bool loadSoundFont(const void* data, int size);
    bool hasSoundFont() const { return base_ != nullptr; }
    void setSampleRate(float sr);
    float sampleRate() const { return sr_; }
    int presetCount() const;
    std::string presetInfo(int index) const;  // "bank|program|name"

    // ── дорожки ──
    void setTrackInstrument(int t, int kind, int bank, int preset);
    void removeTrack(int t);
    void setTrackMix(int t, float vol, float pan, float send, bool mute, bool solo);
    void setSynthParam(int t, int id, float v);
    void setFx(int t, int slot, int type);  // type < 0 — убрать
    void setFxParam(int t, int slot, int id, float v);
    void setFxEnabled(int t, int slot, bool on);

    // ── живая игра ──
    void noteOn(int t, int key, float vel, int delayFrames = 0);
    void noteOff(int t, int key, int delayFrames = 0);
    void allNotesOff(int t);
    void setSustain(int t, bool on);
    void setPitchBend(int t, float semis);
    // арпеджиатор: rateTicks — длина шага, mode 0 вверх / 1 вниз / 2 вверх-вниз / 3 случайно
    void setArp(int t, bool on, int rateTicks, int mode, int octaves);

    // ── транспорт / секвенсор ──
    void setArrangement(Arrangement* arr);  // владение передаётся движку
    void play();
    void stop();
    void seek(double tick);
    void setBpm(float bpm);
    void setTimeSignature(int beatsPerBar);
    void setLoop(bool on, int32_t start, int32_t end);
    void setMetronome(bool on, float volume);
    void setMasterVolume(float v);
    void setReverbParams(float size, float damp);
    double position() const { return pos_.load(std::memory_order_relaxed); }
    bool isPlaying() const { return playing_.load(std::memory_order_relaxed); }
    float bpm() const { return bpm_; }

    // ── запись ──
    // countInBars: такты отсчёта перед стартом; audio: писать вход в wavPath
    void startRecording(int armedTrack, int countInBars, bool audio, const std::string& wavPath);
    // возвращает тик, с которого реально начался захват аудио
    double stopRecording();
    bool isRecording() const { return recording_.load(); }
    bool pollRecorded(RecordedNote& out) { return recQueue_.pop(out); }
    void setInputMonitor(int track, bool on);
    void pushInput(const float* mono, int frames);  // из колбэка входного потока
    float takeInputPeak() { return inputPeak_.exchange(0.f); }

    // ── клипы ──
    int64_t loadClip(int id, const std::string& path);
    void unloadClip(int id);
    std::vector<float> clipPeaks(int id, int buckets);
    int clipSampleRate(int id);

    // ── превью (библиотека звуков) ──
    void previewPattern(std::vector<NoteEv> notes, int32_t lengthTicks, float bpm);
    void previewClip(int clip);
    void stopPreview();

    // ── метры ──
    void takeMeters(float* out /* (kNumTrackSlots+1)*2, мастер последний */);
    static constexpr int kMeterCount = (kNumTrackSlots + 1) * 2;

    // ── рендер ──
    void render(float* interleavedStereo, int frames);
    ExportResult renderToFile(const std::string& path, int32_t endTick, int bits,
                              std::atomic<float>* progress);
    void collectGarbage();

    // для тестов
    int activeVoices(int t);

private:
    enum CmdType : int {
        C_NOTE_ON, C_NOTE_OFF, C_ALL_OFF, C_SUSTAIN, C_BEND, C_PRESET, C_SET_TSF, C_SET_SYNTH,
        C_SET_KIND, C_SYNTH_PARAM, C_MIX, C_FX_SET, C_FX_PARAM, C_FX_ENABLE, C_ARRANGEMENT,
        C_PLAY, C_STOP, C_SEEK, C_BPM, C_TIMESIG, C_LOOP, C_METRO, C_REC_START, C_REC_STOP,
        C_MONITOR, C_CLIP_SET, C_MASTER, C_PREVIEW, C_PREVIEW_CLIP, C_PREVIEW_STOP, C_REVERB, C_ARP
    };
    struct Cmd {
        int type = 0, track = 0, a = 0, b = 0;
        float f = 0, g = 0, h = 0;
        bool flag = false, flag2 = false;
        void* ptr = nullptr;
        double d = 0;
    };
    enum GarbageType : int { G_TSF, G_SYNTH, G_FX, G_ARR, G_CLIP, G_PREVIEW };
    struct Garbage { int type; void* ptr; };

    struct Delayed { int key; float vel; int frames; bool on; };
    struct Track {
        int kind = KIND_NONE;
        tsf* sf = nullptr;
        Synth* synth = nullptr;
        Effect* fx[kMaxFx] = {};
        float vol = 0.8f, pan = 0.f, send = 0.12f;
        bool mute = false, solo = false;
        float gL = 0.f, gR = 0.f, gSend = 0.f;  // сглаженные гейны
        double offTick[128];
        Delayed delayed[64];
        int nDelayed = 0;
        bool sustain = false;
        bool monitor = false;
        // арпеджиатор
        bool arpOn = false;
        int arpRate = kPPQ / 4, arpMode = 0, arpOct = 1;
        int held[16];
        int nHeld = 0;
        float arpVel = 0.8f;
        double arpAcc = 0.0;
        int arpIdx = 0, arpKey = -1;
        bool arpGateClosed = true;
    };
    struct PreviewData { std::vector<NoteEv> notes; int32_t length = 0; float bpm = 100.f; };

    void post(const Cmd& c);
    void drainCommands();
    void handle(const Cmd& c);
    void trash(int type, void* p);
    void freeGarbage(const Garbage& g);

    void instNoteOn(int t, int key, float vel);
    void instNoteOff(int t, int key);
    void liveNoteOn(int t, int key, float vel);
    void liveNoteOff(int t, int key);
    void releaseSequenced(int t);
    void arpPress(int t, int key, float vel);
    void arpRelease(int t, int key);
    void arpTick(int t, int n);
    void releaseAllSequenced();
    void fireMidi(int t, const std::vector<MidiRegion>& regs, double t0, double t1);
    void fireNoteOffs(int t, double t1);
    void renderChunk(float* out, int n, bool includeMetronome);
    void renderTrack(int t, int n, double t0);
    void mixAudioRegions(int t, int n, double t0, float* L, float* R);
    double samplesPerTick() const { return sr_ * 60.0 / (bpm_ * kPPQ); }

    float sr_ = 48000.f;
    tsf* base_ = nullptr;
    std::vector<uint8_t> sfData_;

    Track tracks_[kNumTrackSlots];
    Arrangement* arr_ = nullptr;
    AudioData* clips_[kMaxClips] = {};      // принадлежит аудиопотоку
    AudioData* shadowClips_[kMaxClips] = {};  // копия указателей для UI-потоков
    std::mutex clipMutex_;

    SpscQueue<Cmd> cmds_{4096};
    std::mutex postMutex_;
    SpscQueue<Garbage> garbage_{1024};
    std::mutex garbageMutex_;
    SpscQueue<RecordedNote> recQueue_{4096};

    // транспорт
    std::atomic<double> pos_{0.0};
    std::atomic<bool> playing_{false};
    std::atomic<bool> recording_{false};
    double posRt_ = 0.0;
    bool playRt_ = false;
    float bpm_ = 100.f;
    int beatsPerBar_ = 4;
    bool loopOn_ = false;
    int32_t loopStart_ = 0, loopEnd_ = kPPQ * 16;
    bool metroOn_ = true;
    float metroVol_ = 0.6f;
    float masterVol_ = 0.9f;
    int armed_ = -1;
    double recStartTick_ = 0.0;
    int countInEnd_ = 0;  // тик, после которого начинается запись

    Noise arpNoise_;
    // клик метронома
    float clickPhase_ = 0.f, clickFreq_ = 1500.f, clickEnv_ = 0.f;

    // шины
    Reverb reverb_;
    float limGain_ = 1.f;
    float busL_[kChunk], busR_[kChunk], sendL_[kChunk], sendR_[kChunk];
    float tL_[kChunk], tR_[kChunk];
    float tsfBuf_[kChunk * 2];
    float inBuf_[kChunk];

    // превью
    PreviewData* preview_ = nullptr;
    double previewPos_ = 0.0;
    bool previewOn_ = false;
    int previewClip_ = -1;
    double previewClipPos_ = 0.0;

    // метры
    std::atomic<float> meters_[(kNumTrackSlots + 1) * 2];  // + мастер
    std::atomic<float> inputPeak_{0.f};

    // вход: мониторинг и запись
    SampleRing monRing_;
    SampleRing recRing_;
    std::atomic<bool> captureOn_{false};
    std::atomic<bool> monitorOn_{false};
    std::atomic<double> captureStartTick_{0.0};
    std::thread writerThread_;
    std::atomic<bool> writerRun_{false};

    std::atomic<bool> offline_{false};
};

}  // namespace yume
