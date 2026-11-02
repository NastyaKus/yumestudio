#include "Synth.h"

namespace yume {

namespace {
inline float polyBlep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.f; }
    if (t > 1.f - dt) { t = (t - 1.f) / dt; return t * t + t + t + 1.f; }
    return 0.f;
}
inline float timeToInc(float sr, float sec) { return 1.f / std::max(1.f, sec * sr); }
inline float timeToCoef(float sr, float sec) { return std::exp(-4.6f / std::max(1.f, sec * sr)); }
}  // namespace

Synth::Synth(float sampleRate) : sr_(sampleRate) {
    // пресет по умолчанию — мягкий «kawaii pad»
    const float d[SP_COUNT] = {
        0.5f, 0.75f, 0.35f, 0.52f, 0.55f, 0.25f, 0.35f,
        0.15f, 0.45f, 0.7f, 0.45f,
        0.2f, 0.5f, 0.4f, 0.5f,
        0.3f, 0.05f, 0.1f,
        0.0f, 0.3f, 0.7f, 0.0f};
    std::copy(d, d + SP_COUNT, p_);
    recalc();
}

void Synth::setParam(int id, float v) {
    if (id < 0 || id >= SP_COUNT) return;
    p_[id] = clampf(v, 0.f, 1.f);
    recalc();
}

void Synth::recalc() {
    aInc_ = timeToInc(sr_, expMap(p_[SP_A], 0.001f, 4.f));
    dCoef_ = timeToCoef(sr_, expMap(p_[SP_D], 0.01f, 4.f));
    sLvl_ = p_[SP_S];
    rCoef_ = timeToCoef(sr_, expMap(p_[SP_R], 0.01f, 6.f));
    faInc_ = timeToInc(sr_, expMap(p_[SP_FA], 0.001f, 4.f));
    fdCoef_ = timeToCoef(sr_, expMap(p_[SP_FD], 0.01f, 4.f));
    fsLvl_ = p_[SP_FS];
    frCoef_ = timeToCoef(sr_, expMap(p_[SP_FR], 0.01f, 6.f));
    glideCoef_ = p_[SP_GLIDE] < 0.01f ? 0.f : std::exp(-1.f / (expMap(p_[SP_GLIDE], 0.005f, 1.f) * sr_));
}

float Synth::osc(int wave, float t, float dt) {
    switch (wave) {
        case 0: return std::sin(kTwoPi * t);
        case 1: return 1.f - 4.f * std::fabs(t - 0.5f);
        case 2: return (2.f * t - 1.f) - polyBlep(t, dt);
        case 3: {
            float v = t < 0.5f ? 1.f : -1.f;
            v += polyBlep(t, dt);
            float t2 = t + 0.5f; if (t2 >= 1.f) t2 -= 1.f;
            v -= polyBlep(t2, dt);
            return v * 0.8f;
        }
        default: return noise_.next() * 0.7f;
    }
}

void Synth::noteOn(int key, float vel) {
    const bool mono = p_[SP_MONO] >= 0.5f;
    Voice* v = nullptr;
    if (mono) {
        v = &voices_[0];
        const bool legato = v->active && v->held;
        v->key = key;
        v->vel = vel;
        v->targetFreq = midiToHz((float)key);
        if (!legato || glideCoef_ == 0.f) v->freq = v->targetFreq;
        if (!legato) {
            v->amp.stage = Env::Attack;
            v->filt.stage = Env::Attack;
            v->filt.v = 0;
        }
        v->active = true; v->held = true; v->sustained = false;
        lastKey_ = key;
        return;
    }
    // ищем свободный голос, иначе самый старый
    for (auto& vc : voices_) if (vc.active && vc.key == key) { v = &vc; break; }
    if (!v) for (auto& vc : voices_) if (!vc.active) { v = &vc; break; }
    if (!v) {
        v = &voices_[0];
        for (auto& vc : voices_) if (vc.age < v->age) v = &vc;
    }
    if (!v->active) {
        v->filter[0].reset(); v->filter[1].reset();
        v->amp.v = 0;
        for (float& ph : v->ph1) ph = noise_.next() * 0.5f + 0.5f;
    }
    v->key = key;
    v->vel = vel;
    v->targetFreq = midiToHz((float)key);
    if (glideCoef_ == 0.f || lastKey_ < 0) v->freq = v->targetFreq;
    else v->freq = midiToHz((float)lastKey_);
    v->amp.stage = Env::Attack;
    v->filt.stage = Env::Attack;
    v->filt.v = 0;
    v->active = true; v->held = true; v->sustained = false;
    v->age = ++ageCounter_;
    lastKey_ = key;
}

void Synth::noteOff(int key) {
    for (auto& v : voices_) {
        if (v.active && v.held && v.key == key) {
            v.held = false;
            if (sustain_) { v.sustained = true; continue; }
            v.amp.stage = Env::Release;
            v.filt.stage = Env::Release;
        }
    }
}

void Synth::setSustain(bool on) {
    sustain_ = on;
    if (!on) {
        for (auto& v : voices_) {
            if (v.active && v.sustained) {
                v.sustained = false;
                v.amp.stage = Env::Release;
                v.filt.stage = Env::Release;
            }
        }
    }
}

void Synth::allNotesOff() {
    sustain_ = false;
    for (auto& v : voices_) {
        if (v.active) { v.held = false; v.sustained = false; v.amp.stage = Env::Release; v.filt.stage = Env::Release; }
    }
}

void Synth::allSoundOff() {
    for (auto& v : voices_) { v.active = false; v.amp.stage = Env::Idle; v.amp.v = 0; }
}

int Synth::activeVoices() const {
    int c = 0;
    for (auto& v : voices_) c += v.active ? 1 : 0;
    return c;
}

void Synth::render(float* L, float* R, int n) {
    const int w1 = std::min(4, (int)(p_[SP_WAVE1] * 4.f + 0.5f));
    const int w2 = std::min(4, (int)(p_[SP_WAVE2] * 4.f + 0.5f));
    const float mix2 = p_[SP_MIX2];
    const float det2 = std::round((p_[SP_DETUNE2] - 0.5f) * 24.f) + (p_[SP_DETUNE2] - 0.5f) * 0.3f;
    const float ratio2 = std::pow(2.f, det2 / 12.f);
    const float cutBase = p_[SP_CUTOFF];
    const float res = p_[SP_RES];
    const float fenv = p_[SP_FENV];
    const float lfoRate = expMap(p_[SP_LFO_RATE], 0.05f, 20.f);
    const float lfoPitch = p_[SP_LFO_PITCH] * p_[SP_LFO_PITCH] * 1.0f;  // в полутонах
    const float lfoCut = p_[SP_LFO_CUTOFF];
    const float uni = p_[SP_UNISON];
    const int uniCount = uni > 0.02f ? 3 : 1;
    const float uniSpread = uni * 0.25f;  // полутона
    const float vol = p_[SP_VOLUME] * p_[SP_VOLUME] * 0.5f;
    const float bendRatio = std::pow(2.f, bend_ / 12.f);

    for (int i = 0; i < n; ++i) {
        lfoPhase_ += lfoRate / sr_;
        if (lfoPhase_ >= 1.f) lfoPhase_ -= 1.f;
        const float lfo = std::sin(kTwoPi * lfoPhase_);
        const float pitchMod = std::pow(2.f, lfo * lfoPitch / 12.f) * bendRatio;
        float outL = 0, outR = 0;
        for (auto& v : voices_) {
            if (!v.active) continue;
            if (glideCoef_ > 0.f) v.freq = v.targetFreq + (v.freq - v.targetFreq) * glideCoef_;
            else v.freq = v.targetFreq;
            const float a = v.amp.next(aInc_, dCoef_, sLvl_, rCoef_);
            const float fe = v.filt.next(faInc_, fdCoef_, fsLvl_, frCoef_);
            if (v.amp.stage == Env::Idle) { v.active = false; continue; }
            const float f = v.freq * pitchMod;
            float s1L = 0, s1R = 0;
            for (int u = 0; u < uniCount; ++u) {
                float detune = uniCount == 1 ? 1.f : std::pow(2.f, (u - 1) * uniSpread / 12.f);
                float dt = f * detune / sr_;
                float s = osc(w1, v.ph1[u], dt);
                v.ph1[u] += dt;
                if (v.ph1[u] >= 1.f) v.ph1[u] -= 1.f;
                if (uniCount == 1) { s1L += s; s1R += s; }
                else if (u == 0) { s1L += s * 0.9f; s1R += s * 0.3f; }
                else if (u == 2) { s1L += s * 0.3f; s1R += s * 0.9f; }
                else { s1L += s * 0.6f; s1R += s * 0.6f; }
            }
            if (uniCount > 1) { s1L *= 0.6f; s1R *= 0.6f; }
            float dt2 = f * ratio2 / sr_;
            float s2 = osc(w2, v.ph2, dt2);
            v.ph2 += dt2;
            if (v.ph2 >= 1.f) v.ph2 -= 1.f;
            float xl = s1L * (1.f - mix2) + s2 * mix2;
            float xr = s1R * (1.f - mix2) + s2 * mix2;
            if ((i & 15) == 0) {
                float cut01 = clampf(cutBase + fenv * fe * 0.6f + lfoCut * lfo * 0.25f + (v.vel - 0.5f) * 0.1f, 0.f, 1.f);
                float cutoff = expMap(cut01, 40.f, 18000.f);
                v.filter[0].setup(sr_, cutoff, res);
                v.filter[1].k = v.filter[0].k; v.filter[1].g = v.filter[0].g;
                v.filter[1].a1 = v.filter[0].a1; v.filter[1].a2 = v.filter[0].a2; v.filter[1].a3 = v.filter[0].a3;
            }
            const float g = a * (0.4f + 0.6f * v.vel);
            outL += v.filter[0].process(xl) * g;
            outR += v.filter[1].process(xr) * g;
        }
        L[i] += outL * vol;
        R[i] += outR * vol;
    }
}

}  // namespace yume
