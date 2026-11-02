// Хост-тест аудиоядра Yume Studio (без Android/Oboe).
// Сборка:  cmake -S tools/host-test -B build/host-test && cmake --build build/host-test
// Запуск:  build/host-test/engine_test app/src/main/assets/soundfonts/GeneralUser-GS.sf2 out_dir
#include <cassert>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

#include "engine/Engine.h"

using namespace yume;

static int failures = 0;
#define CHECK(cond, msg)                                           \
    do {                                                           \
        if (!(cond)) { std::printf("FAIL: %s\n", msg); ++failures; } \
        else std::printf("ok:   %s\n", msg);                       \
    } while (0)

static void testEffects() {
    const float sr = 48000.f;
    for (int type = 0; type < FX_COUNT; ++type) {
        Effect* e = createEffect(type, sr);
        if (!e) { CHECK(false, "effect created"); continue; }
        for (int p = 0; p < kFxMaxParams; ++p) e->setParam(p, 0.8f);
        Noise nz;
        float L[64], R[64];
        bool finite = true;
        float peak = 0.f;
        for (int b = 0; b < 2000; ++b) {
            for (int i = 0; i < 64; ++i) {
                float s = 0.4f * std::sin(kTwoPi * 220.f * (b * 64 + i) / sr) + 0.05f * nz.next();
                L[i] = s; R[i] = s;
            }
            e->run(L, R, 64);
            for (int i = 0; i < 64; ++i) {
                if (!std::isfinite(L[i]) || !std::isfinite(R[i])) finite = false;
                peak = std::max(peak, std::fabs(L[i]));
            }
        }
        char msg[96];
        std::snprintf(msg, sizeof msg, "fx %2d finite, peak=%.3f", type, peak);
        CHECK(finite && peak < 8.f, msg);
        delete e;
    }
}

static void testSynth() {
    Synth s(48000.f);
    float L[256] = {0}, R[256] = {0};
    s.noteOn(60, 0.9f);
    s.noteOn(64, 0.9f);
    float pk = 0;
    for (int b = 0; b < 100; ++b) {
        std::fill(L, L + 256, 0.f); std::fill(R, R + 256, 0.f);
        s.render(L, R, 256);
        for (float v : L) pk = std::max(pk, std::fabs(v));
    }
    CHECK(pk > 0.01f && pk < 2.f, "synth produces sound");
    s.noteOff(60); s.noteOff(64);
    for (int b = 0; b < 2000; ++b) { std::fill(L, L + 256, 0.f); std::fill(R, R + 256, 0.f); s.render(L, R, 256); }
    CHECK(s.activeVoices() == 0, "synth voices released");
}

int main(int argc, char** argv) {
    if (argc < 3) { std::printf("usage: engine_test <sf2> <outdir>\n"); return 2; }
    testEffects();
    testSynth();

    std::ifstream f(argv[1], std::ios::binary);
    std::vector<char> sf((std::istreambuf_iterator<char>(f)), {});
    Engine eng;
    eng.setSampleRate(48000.f);
    CHECK(eng.loadSoundFont(sf.data(), (int)sf.size()), "soundfont loaded");
    std::printf("presets: %d, first: %s\n", eng.presetCount(), eng.presetInfo(0).c_str());

    // 0: рояль, 1: ударные, 2: синт, 3: гитара с педалями, 4: бас
    eng.setTrackInstrument(0, KIND_SF2, 0, 0);
    eng.setTrackInstrument(1, KIND_DRUMS, 128, 0);
    eng.setTrackInstrument(2, KIND_SYNTH, 0, 0);
    eng.setTrackInstrument(3, KIND_SF2, 0, 27);
    eng.setTrackInstrument(4, KIND_SF2, 0, 33);
    eng.setFx(3, 0, FX_OVERDRIVE);
    eng.setFx(3, 1, FX_CHORUS);
    eng.setFx(3, 2, FX_AMP);
    eng.setFx(3, 3, FX_DELAY);
    eng.setTrackMix(2, 0.5f, -0.3f, 0.3f, false, false);
    eng.setBpm(92.f);

    auto* arr = new Arrangement();
    // аккорды на рояле: Cmaj7 – Am7 – Fmaj7 – G
    const int chords[4][4] = {{60, 64, 67, 71}, {57, 60, 64, 67}, {53, 57, 60, 64}, {55, 59, 62, 65}};
    MidiRegion piano; piano.start = 0; piano.length = kPPQ * 16; piano.contentLen = kPPQ * 16;
    for (int c = 0; c < 4; ++c)
        for (int k = 0; k < 4; ++k) piano.notes.push_back({c * kPPQ * 4, kPPQ * 4 - 40, (uint8_t)chords[c][k], 80});
    arr->midi[0].push_back(piano);
    // бит: кик/снейр/хэт, один такт, зацикленный на 4 такта
    MidiRegion drums; drums.start = 0; drums.length = kPPQ * 16; drums.contentLen = kPPQ * 4;
    for (int s = 0; s < 8; ++s) {
        drums.notes.push_back({s * kPPQ / 2, kPPQ / 4, 42, 70});
        if (s % 4 == 0) drums.notes.push_back({s * kPPQ / 2, kPPQ / 4, 36, 110});
        if (s % 4 == 2) drums.notes.push_back({s * kPPQ / 2, kPPQ / 4, 38, 100});
    }
    std::sort(drums.notes.begin(), drums.notes.end(), [](auto& a, auto& b) { return a.tick < b.tick; });
    arr->midi[1].push_back(drums);
    MidiRegion lead; lead.start = kPPQ * 8; lead.length = kPPQ * 8;
    const int mel[8] = {72, 74, 76, 79, 76, 74, 72, 67};
    for (int i = 0; i < 8; ++i) lead.notes.push_back({i * kPPQ, kPPQ - 60, (uint8_t)mel[i], 90});
    arr->midi[2].push_back(lead);
    MidiRegion gtr; gtr.start = 0; gtr.length = kPPQ * 16;
    for (int i = 0; i < 16; ++i) gtr.notes.push_back({i * kPPQ, kPPQ / 2, (uint8_t)(i % 2 ? 52 : 40), 100});
    arr->midi[3].push_back(gtr);
    MidiRegion bass; bass.start = 0; bass.length = kPPQ * 16;
    const int bl[4] = {36, 33, 29, 31};
    for (int i = 0; i < 16; ++i) bass.notes.push_back({i * kPPQ, kPPQ - 100, (uint8_t)bl[i / 4], 100});
    arr->midi[4].push_back(bass);
    eng.setArrangement(arr);

    // живая игра в реальном времени (через render)
    std::vector<float> rt(48000 * 2);
    eng.noteOn(0, 60, 0.8f);
    eng.render(rt.data(), 24000);
    float pk = 0;
    for (int i = 0; i < 48000; ++i) pk = std::max(pk, std::fabs(rt[i]));
    CHECK(pk > 0.01f, "live note audible");
    CHECK(eng.activeVoices(0) > 0, "piano voices active");
    eng.noteOff(0, 60);
    eng.render(rt.data(), 24000);

    // запись MIDI: count-in 1 такт
    eng.startRecording(0, 1, false, "");
    eng.render(rt.data(), 1024);
    for (int i = 0; i < 140; ++i) eng.render(rt.data(), 1024);  // ~3 c — отсчёт (такт при 92 BPM ≈ 2.6 c)
    eng.noteOn(0, 67, 0.9f);
    eng.render(rt.data(), 1024);
    eng.noteOff(0, 67);
    eng.render(rt.data(), 1024);
    eng.stopRecording();
    eng.stop();
    eng.render(rt.data(), 256);
    int rec = 0;
    RecordedNote rn;
    while (eng.pollRecorded(rn)) ++rec;
    CHECK(rec == 2, "recorded note on/off after count-in");

    // экспорт
    eng.collectGarbage();
    std::atomic<float> prog{0};
    std::string out = std::string(argv[2]) + "/render.wav";
    ExportResult r = eng.renderToFile(out, kPPQ * 16, 16, &prog);
    std::printf("export frames=%lld peak=%.3f\n", (long long)r.frames, r.peak);
    CHECK(r.ok && r.peak > 0.05f && r.peak <= 1.0f, "export rendered with sane peak");

    // прочитать обратно и проверить
    AudioData d;
    CHECK(readWav(out, d) && d.channels == 2 && d.frames() == r.frames, "export wav readable");
    bool finite = true;
    for (float v : d.samples) finite &= std::isfinite(v);
    CHECK(finite, "export has no NaN");

    // клип + аудиодорожка
    eng.setTrackInstrument(5, KIND_AUDIO, 0, 0);
    int64_t frames = eng.loadClip(1, out);
    CHECK(frames == r.frames, "clip loaded");
    auto* arr2 = new Arrangement();
    arr2->audio[5].push_back({0, kPPQ * 4, 1, 0, 1.f});
    eng.setArrangement(arr2);
    for (int t = 0; t < 5; ++t) eng.setTrackMix(t, 0.8f, 0, 0, true, false);
    std::string out2 = std::string(argv[2]) + "/clip.wav";
    ExportResult r2 = eng.renderToFile(out2, kPPQ * 4, 24, &prog);
    CHECK(r2.ok && r2.peak > 0.05f, "audio region plays back");
    auto peaks = eng.clipPeaks(1, 100);
    CHECK(peaks.size() == 100, "clip peaks");

    // превью
    eng.setTrackInstrument(kPreviewTrack, KIND_SF2, 0, 10);
    eng.previewPattern({{0, 400, 72, 100}, {480, 400, 76, 100}}, kPPQ, 120.f);
    eng.render(rt.data(), 24000);
    float meters[Engine::kMeterCount];
    eng.takeMeters(meters);
    CHECK(meters[kPreviewTrack * 2] > 0.001f, "preview audible in meters");
    eng.stopPreview();
    eng.render(rt.data(), 1024);
    eng.collectGarbage();

    std::printf(failures ? "\n%d FAILURES\n" : "\nALL OK\n", failures);
    return failures ? 1 : 0;
}
