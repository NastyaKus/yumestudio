#pragma once
// Общие мелочи для DSP: константы, быстрые функции, сглаживание параметров.
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

namespace yume {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 2.0f * kPi;

inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
inline float lerpf(float a, float b, float t) { return a + (b - a) * t; }
inline float dbToGain(float db) { return std::pow(10.0f, db * 0.05f); }
inline float gainToDb(float g) { return 20.0f * std::log10(std::max(g, 1e-9f)); }
inline float midiToHz(float note) { return 440.0f * std::pow(2.0f, (note - 69.0f) / 12.0f); }

// экспоненциальное отображение 0..1 → [lo, hi]
inline float expMap(float x, float lo, float hi) { return lo * std::pow(hi / lo, clampf(x, 0.f, 1.f)); }

// мягкое насыщение
inline float fastTanh(float x) {
    x = clampf(x, -3.f, 3.f);
    const float x2 = x * x;
    return x * (27.f + x2) / (27.f + 9.f * x2);
}

inline float flushDenormal(float v) { return std::fabs(v) < 1e-15f ? 0.f : v; }

// однополюсное сглаживание параметра (для «молний» без щелчков)
struct Smoother {
    float value = 0.f, target = 0.f, coeff = 0.999f;
    void setup(float sampleRate, float ms = 20.f) {
        coeff = std::exp(-1.0f / (0.001f * ms * sampleRate));
    }
    void reset(float v) { value = target = v; }
    inline float next() { value = target + (value - target) * coeff; return value; }
};

// простой xorshift-шум (детерминированный, без аллокаций)
struct Noise {
    uint32_t s = 0x12345678u;
    inline float next() {
        s ^= s << 13; s ^= s >> 17; s ^= s << 5;
        return (int32_t)s * (1.0f / 2147483648.0f);
    }
};

}  // namespace yume
