#pragma once
// Собственный субтрактивный синтезатор «Yume Synth»: 2 осциллятора (+унисон), SVF-фильтр,
// две ADSR-огибающие, LFO, глайд. Параметры нормированы 0..1, индексы совпадают с
// audio/SynthParams.kt.
#include "../dsp/DspCommon.h"
#include "../dsp/Filters.h"

namespace yume {

enum SynthParam : int {
    SP_WAVE1 = 0, SP_WAVE2, SP_MIX2, SP_DETUNE2, SP_CUTOFF, SP_RES, SP_FENV,
    SP_A, SP_D, SP_S, SP_R,
    SP_FA, SP_FD, SP_FS, SP_FR,
    SP_LFO_RATE, SP_LFO_PITCH, SP_LFO_CUTOFF,
    SP_GLIDE, SP_UNISON, SP_VOLUME, SP_MONO,
    SP_COUNT
};

class Synth {
public:
    explicit Synth(float sampleRate);
    void setParam(int id, float v);
    float param(int id) const { return (id >= 0 && id < SP_COUNT) ? p_[id] : 0.f; }
    void noteOn(int key, float vel);
    void noteOff(int key);
    void allNotesOff();
    void allSoundOff();
    void setSustain(bool on);
    void setPitchBend(float semis) { bend_ = semis; }
    // добавляет стерео-сигнал в L/R
    void render(float* L, float* R, int n);
    int activeVoices() const;

private:
    struct Env {
        enum Stage { Idle, Attack, Decay, Sustain, Release } stage = Idle;
        float v = 0;
        inline float next(float a, float d, float s, float r) {
            switch (stage) {
                case Attack: v += a; if (v >= 1.f) { v = 1.f; stage = Decay; } break;
                case Decay: v = s + (v - s) * d; if (v - s < 1e-4f) { v = s; stage = Sustain; } break;
                case Sustain: v = s; break;
                case Release: v *= r; if (v < 1e-5f) { v = 0; stage = Idle; } break;
                default: break;
            }
            return v;
        }
    };
    struct Voice {
        bool active = false, held = false, sustained = false;
        int key = 0;
        float vel = 0, freq = 440.f, targetFreq = 440.f;
        float ph1[3] = {0, 0, 0}, ph2 = 0;
        Env amp, filt;
        Svf filter[2];
        uint32_t age = 0;
    };

    float osc(int wave, float phase, float inc);
    void recalc();

    float sr_;
    float p_[SP_COUNT];
    Voice voices_[12];
    Noise noise_;
    uint32_t ageCounter_ = 0;
    float bend_ = 0;
    float lfoPhase_ = 0;
    bool sustain_ = false;
    // производные коэффициенты
    float aInc_ = 0, dCoef_ = 0, sLvl_ = 1, rCoef_ = 0;
    float faInc_ = 0, fdCoef_ = 0, fsLvl_ = 1, frCoef_ = 0;
    float glideCoef_ = 0;
    int lastKey_ = -1;
};

}  // namespace yume
