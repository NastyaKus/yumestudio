#pragma once
// Эффекты (педали, амп, вокальные). Параметры нормированы 0..1, порядок параметров
// и типы ДОЛЖНЫ совпадать с каталогом на стороне Kotlin (audio/EffectCatalog.kt).
#include "DspCommon.h"

namespace yume {

enum FxType : int {
    FX_COMPRESSOR = 0,
    FX_GATE,
    FX_OVERDRIVE,
    FX_DISTORTION,
    FX_FUZZ,
    FX_WAH,
    FX_CHORUS,
    FX_FLANGER,
    FX_PHASER,
    FX_TREMOLO,
    FX_OCTAVER,
    FX_DELAY,
    FX_REVERB,
    FX_BITCRUSHER,
    FX_EQ,
    FX_AMP,
    FX_PITCH,
    FX_COUNT
};

constexpr int kFxMaxParams = 8;
constexpr int kFxMaxBlock = 256;

class Effect {
public:
    explicit Effect(int type, float sampleRate) : type_(type), sr_(sampleRate) {
        for (float& v : p_) v = 0.5f;
    }
    virtual ~Effect() = default;

    int type() const { return type_; }

    void setParam(int id, float v) {
        if (id >= 0 && id < kFxMaxParams) { p_[id] = clampf(v, 0.f, 1.f); dirty_ = true; }
    }
    float param(int id) const { return (id >= 0 && id < kFxMaxParams) ? p_[id] : 0.f; }
    void setEnabled(bool on) { enabled_ = on; }
    bool enabled() const { return enabled_; }

    // Обработка с плавным включением/выключением (без щелчков).
    void run(float* L, float* R, int n) {
        if (!enabled_ && wet_ <= 0.f) return;
        if (dirty_) { update(); dirty_ = false; }
        const bool ramping = (enabled_ && wet_ < 1.f) || (!enabled_ && wet_ > 0.f);
        if (!ramping) { process(L, R, n); return; }
        std::memcpy(dryL_, L, sizeof(float) * n);
        std::memcpy(dryR_, R, sizeof(float) * n);
        process(L, R, n);
        const float step = (enabled_ ? 1.f : -1.f) / (0.01f * sr_);
        for (int i = 0; i < n; ++i) {
            wet_ = clampf(wet_ + step, 0.f, 1.f);
            L[i] = dryL_[i] + (L[i] - dryL_[i]) * wet_;
            R[i] = dryR_[i] + (R[i] - dryR_[i]) * wet_;
        }
        if (!enabled_ && wet_ <= 0.f) reset();
    }

    virtual void reset() {}

protected:
    virtual void update() {}
    virtual void process(float* L, float* R, int n) = 0;

    int type_;
    float sr_;
    float p_[kFxMaxParams];
    bool dirty_ = true;
    bool enabled_ = true;
    float wet_ = 1.f;
    float dryL_[kFxMaxBlock];
    float dryR_[kFxMaxBlock];
};

// Создаёт эффект с параметрами по умолчанию (вызывать НЕ из аудиопотока).
Effect* createEffect(int type, float sampleRate);

}  // namespace yume
