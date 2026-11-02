#pragma once
#include "DspCommon.h"

namespace yume {

// RBJ-биквад (Transposed Direct Form II)
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;

    void reset() { z1 = z2 = 0; }

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = flushDenormal(b1 * x - a1 * y + z2);
        z2 = flushDenormal(b2 * x - a2 * y);
        return y;
    }

    void set(float nb0, float nb1, float nb2, float na0, float na1, float na2) {
        const float inv = 1.0f / na0;
        b0 = nb0 * inv; b1 = nb1 * inv; b2 = nb2 * inv; a1 = na1 * inv; a2 = na2 * inv;
    }

    void lowpass(float sr, float f, float q) {
        float w = kTwoPi * clampf(f, 10.f, sr * 0.45f) / sr, c = std::cos(w), a = std::sin(w) / (2 * q);
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a);
    }
    void highpass(float sr, float f, float q) {
        float w = kTwoPi * clampf(f, 10.f, sr * 0.45f) / sr, c = std::cos(w), a = std::sin(w) / (2 * q);
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a);
    }
    void bandpass(float sr, float f, float q) {
        float w = kTwoPi * clampf(f, 10.f, sr * 0.45f) / sr, c = std::cos(w), a = std::sin(w) / (2 * q);
        set(a, 0, -a, 1 + a, -2 * c, 1 - a);
    }
    void peak(float sr, float f, float q, float db) {
        float A = std::pow(10.f, db / 40.f);
        float w = kTwoPi * clampf(f, 10.f, sr * 0.45f) / sr, c = std::cos(w), a = std::sin(w) / (2 * q);
        set(1 + a * A, -2 * c, 1 - a * A, 1 + a / A, -2 * c, 1 - a / A);
    }
    void lowShelf(float sr, float f, float db) {
        float A = std::pow(10.f, db / 40.f);
        float w = kTwoPi * clampf(f, 10.f, sr * 0.45f) / sr, c = std::cos(w), s = std::sin(w);
        float a = s / 2 * std::sqrt(2.f), sq = 2 * std::sqrt(A) * a;
        set(A * ((A + 1) - (A - 1) * c + sq), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sq),
            (A + 1) + (A - 1) * c + sq, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sq);
    }
    void highShelf(float sr, float f, float db) {
        float A = std::pow(10.f, db / 40.f);
        float w = kTwoPi * clampf(f, 10.f, sr * 0.45f) / sr, c = std::cos(w), s = std::sin(w);
        float a = s / 2 * std::sqrt(2.f), sq = 2 * std::sqrt(A) * a;
        set(A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq),
            (A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq);
    }
};

// State-variable filter (Cytomic/Simper), быстрая модуляция среза без артефактов
struct Svf {
    float ic1 = 0, ic2 = 0;
    float g = 0, k = 1, a1 = 0, a2 = 0, a3 = 0;
    void reset() { ic1 = ic2 = 0; }
    void setup(float sr, float cutoff, float res /*0..1*/) {
        g = std::tan(kPi * clampf(cutoff, 20.f, sr * 0.45f) / sr);
        k = 2.f - 1.95f * clampf(res, 0.f, 1.f);
        a1 = 1.f / (1.f + g * (g + k));
        a2 = g * a1;
        a3 = g * a2;
    }
    // режим: 0 lp, 1 bp, 2 hp
    inline float process(float v0, int mode = 0) {
        float v3 = v0 - ic2;
        float v1 = a1 * ic1 + a2 * v3;
        float v2 = ic2 + a2 * ic1 + a3 * v3;
        ic1 = flushDenormal(2 * v1 - ic1);
        ic2 = flushDenormal(2 * v2 - ic2);
        if (mode == 0) return v2;
        if (mode == 1) return v1;
        return v0 - k * v1 - v2;
    }
};

// однополюсные фильтры
struct OnePole {
    float a = 0, z = 0;
    void setLowpass(float sr, float f) { a = std::exp(-kTwoPi * clampf(f, 1.f, sr * 0.49f) / sr); }
    inline float lp(float x) { z = flushDenormal(x + (z - x) * a); return z; }
    inline float hp(float x) { return x - lp(x); }
    void reset() { z = 0; }
};

// DC-блокер
struct DcBlock {
    float x1 = 0, y1 = 0, r = 0.995f;
    inline float process(float x) { float y = x - x1 + r * y1; x1 = x; y1 = flushDenormal(y); return y; }
    void reset() { x1 = y1 = 0; }
};

}  // namespace yume
