#pragma once
// Стерео-реверб на основе Freeverb (Jezar, public domain), с масштабированием под частоту дискретизации.
#include "DspCommon.h"

namespace yume {

class Reverb {
public:
    void setup(float sampleRate) {
        const float scale = sampleRate / 44100.f;
        static const int combT[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static const int apT[4] = {556, 441, 341, 225};
        const int spread = 23;
        for (int i = 0; i < 8; ++i) {
            combL[i].setup((int)(combT[i] * scale));
            combR[i].setup((int)((combT[i] + spread) * scale));
        }
        for (int i = 0; i < 4; ++i) {
            apL[i].setup((int)(apT[i] * scale));
            apR[i].setup((int)((apT[i] + spread) * scale));
        }
        update();
    }

    void setRoom(float size01) { roomSize = 0.70f + 0.28f * clampf(size01, 0, 1); update(); }
    void setDamp(float d01) { damp = 0.05f + 0.9f * clampf(d01, 0, 1); update(); }
    void setWidth(float w01) { width = clampf(w01, 0, 1); }

    void reset() {
        for (auto& c : combL) c.clear();
        for (auto& c : combR) c.clear();
        for (auto& a : apL) a.clear();
        for (auto& a : apR) a.clear();
    }

    // Выход — только «мокрый» сигнал.
    inline void process(float inL, float inR, float& outL, float& outR) {
        const float input = (inL + inR) * 0.015f;
        float l = 0, r = 0;
        for (int i = 0; i < 8; ++i) { l += combL[i].process(input); r += combR[i].process(input); }
        for (int i = 0; i < 4; ++i) { l = apL[i].process(l); r = apR[i].process(r); }
        const float w1 = width * 0.5f + 0.5f, w2 = (1 - width) * 0.5f;
        outL = l * w1 + r * w2;
        outR = r * w1 + l * w2;
    }

private:
    struct Comb {
        std::vector<float> b; int i = 0; float store = 0, fb = 0.84f, d1 = 0.2f, d2 = 0.8f;
        void setup(int n) { b.assign(std::max(n, 1), 0.f); i = 0; }
        void clear() { std::fill(b.begin(), b.end(), 0.f); store = 0; }
        inline float process(float x) {
            float out = b[i];
            store = flushDenormal(out * d2 + store * d1);
            b[i] = x + store * fb;
            if (++i >= (int)b.size()) i = 0;
            return out;
        }
    };
    struct AllPass {
        std::vector<float> b; int i = 0;
        void setup(int n) { b.assign(std::max(n, 1), 0.f); i = 0; }
        void clear() { std::fill(b.begin(), b.end(), 0.f); }
        inline float process(float x) {
            float bo = b[i];
            float out = -x + bo;
            b[i] = flushDenormal(x + bo * 0.5f);
            if (++i >= (int)b.size()) i = 0;
            return out;
        }
    };
    void update() {
        for (int i = 0; i < 8; ++i) {
            combL[i].fb = combR[i].fb = roomSize;
            combL[i].d1 = combR[i].d1 = damp;
            combL[i].d2 = combR[i].d2 = 1 - damp;
        }
    }
    Comb combL[8], combR[8];
    AllPass apL[4], apR[4];
    float roomSize = 0.84f, damp = 0.4f, width = 1.f;
};

}  // namespace yume
