#pragma once
#include "DspCommon.h"

namespace yume {

// Кольцевая линия задержки с дробным чтением. Память выделяется в setup() (не в аудиопотоке).
struct DelayLine {
    std::vector<float> buf;
    int mask = 0, w = 0;

    void setup(int maxSamples) {
        int n = 1;
        while (n < maxSamples + 4) n <<= 1;
        buf.assign(n, 0.f);
        mask = n - 1;
        w = 0;
    }
    void reset() { std::fill(buf.begin(), buf.end(), 0.f); w = 0; }
    inline void push(float x) { buf[w] = x; w = (w + 1) & mask; }
    // d — задержка в сэмплах (>= 1)
    inline float read(float d) const {
        float rp = (float)w - d;
        while (rp < 0) rp += (float)(mask + 1);
        int i0 = (int)rp;
        float fr = rp - (float)i0;
        float a = buf[i0 & mask], b = buf[(i0 + 1) & mask];
        return a + (b - a) * fr;
    }
    inline float readInt(int d) const { return buf[(w - d) & mask]; }
};

}  // namespace yume
