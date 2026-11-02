#include "Effects.h"

#include "DelayLine.h"
#include "Filters.h"
#include "Reverb.h"

namespace yume {
namespace {

// ───────────────────────────── Компрессор ─────────────────────────────
// p0 порог, p1 ratio, p2 атака, p3 релиз, p4 makeup, p5 mix
class Compressor : public Effect {
public:
    Compressor(float sr) : Effect(FX_COMPRESSOR, sr) {
        float d[] = {0.55f, 0.35f, 0.2f, 0.35f, 0.3f, 1.f};
        std::copy(d, d + 6, p_);
    }
    void reset() override { env_ = 0; }

protected:
    void update() override {
        thrDb_ = lerpf(-40.f, 0.f, p_[0]);
        ratio_ = expMap(p_[1], 1.f, 20.f);
        att_ = std::exp(-1.f / (0.001f * expMap(p_[2], 0.1f, 80.f) * sr_));
        rel_ = std::exp(-1.f / (0.001f * expMap(p_[3], 20.f, 800.f) * sr_));
        makeup_ = dbToGain(p_[4] * 24.f);
        mix_ = p_[5];
    }
    void process(float* L, float* R, int n) override {
        for (int i = 0; i < n; ++i) {
            float lvl = std::max(std::fabs(L[i]), std::fabs(R[i]));
            float c = lvl > env_ ? att_ : rel_;
            env_ = flushDenormal(lvl + (env_ - lvl) * c);
            float db = gainToDb(env_);
            float gr = 0.f;
            if (db > thrDb_) gr = (db - thrDb_) * (1.f - 1.f / ratio_);
            float g = dbToGain(-gr) * makeup_;
            float wetL = L[i] * g, wetR = R[i] * g;
            L[i] = L[i] + (wetL - L[i]) * mix_;
            R[i] = R[i] + (wetR - R[i]) * mix_;
        }
    }

private:
    float env_ = 0, thrDb_ = -20, ratio_ = 4, att_ = 0.9f, rel_ = 0.99f, makeup_ = 1, mix_ = 1;
};

// ───────────────────────────── Нойз-гейт ─────────────────────────────
// p0 порог, p1 релиз
class Gate : public Effect {
public:
    Gate(float sr) : Effect(FX_GATE, sr) { p_[0] = 0.35f; p_[1] = 0.3f; }
    void reset() override { env_ = 0; gain_ = 0; }

protected:
    void update() override {
        thr_ = dbToGain(lerpf(-80.f, -20.f, p_[0]));
        rel_ = std::exp(-1.f / (0.001f * expMap(p_[1], 10.f, 500.f) * sr_));
        att_ = std::exp(-1.f / (0.0005f * sr_));
        envRel_ = std::exp(-1.f / (0.02f * sr_));
    }
    void process(float* L, float* R, int n) override {
        for (int i = 0; i < n; ++i) {
            float lvl = std::max(std::fabs(L[i]), std::fabs(R[i]));
            env_ = lvl > env_ ? lvl : flushDenormal(lvl + (env_ - lvl) * envRel_);
            float target = env_ > thr_ ? 1.f : 0.f;
            float c = target > gain_ ? att_ : rel_;
            gain_ = flushDenormal(target + (gain_ - target) * c);
            L[i] *= gain_;
            R[i] *= gain_;
        }
    }

private:
    float env_ = 0, gain_ = 0, thr_ = 0.001f, rel_ = 0.99f, att_ = 0.9f, envRel_ = 0.99f;
};

// ─────────────────── Овердрайв / Дисторшн / Фузз ───────────────────
// p0 drive/gain, p1 tone, p2 level
class Drive : public Effect {
public:
    Drive(int type, float sr) : Effect(type, sr) {
        p_[0] = 0.5f; p_[1] = 0.55f; p_[2] = 0.5f;
    }
    void reset() override {
        for (int c = 0; c < 2; ++c) { pre_[c].reset(); tone_[c].reset(); dc_[c].reset(); post_[c].reset(); }
        ff_[0] = ff_[1] = 0;
    }

protected:
    void update() override {
        switch (type_) {
            case FX_OVERDRIVE: gain_ = expMap(p_[0], 1.5f, 60.f); break;
            case FX_DISTORTION: gain_ = expMap(p_[0], 5.f, 300.f); break;
            default: gain_ = expMap(p_[0], 20.f, 1500.f); break;
        }
        for (int c = 0; c < 2; ++c) {
            // «тюбскримерный» горб на средних для овердрайва, срез низа перед клиппингом
            pre_[c].highpass(sr_, type_ == FX_FUZZ ? 60.f : (type_ == FX_OVERDRIVE ? 500.f : 180.f), 0.707f);
            tone_[c].lowpass(sr_, expMap(p_[1], 900.f, 9000.f), 0.707f);
            post_[c].peak(sr_, 900.f, 0.7f, type_ == FX_DISTORTION ? -4.f : 2.f);
        }
        level_ = dbToGain(lerpf(-24.f, 6.f, p_[2]));
        if (type_ == FX_OVERDRIVE) level_ *= 1.6f;
    }
    inline float shape(float x) {
        switch (type_) {
            case FX_OVERDRIVE: return fastTanh(x);
            case FX_DISTORTION: {
                // асимметричный жёсткий клиппинг
                float y = x > 0 ? std::min(x, 1.f) : std::max(x * 1.2f, -0.8f);
                return 0.6f * y + 0.4f * fastTanh(x);
            }
            default: {
                float a = std::fabs(x);
                float y = (1.f - std::exp(-a));
                return x > 0 ? y : -y * 0.9f;
            }
        }
    }
    void process(float* L, float* R, int n) override {
        float* ch[2] = {L, R};
        for (int c = 0; c < 2; ++c) {
            float* x = ch[c];
            for (int i = 0; i < n; ++i) {
                float s = pre_[c].process(x[i]);
                if (type_ == FX_OVERDRIVE) s = s + 0.5f * x[i];  // смешиваем немного низа обратно
                s = shape(s * gain_);
                s = dc_[c].process(s);
                s = post_[c].process(tone_[c].process(s));
                x[i] = s * level_ * 0.5f;
            }
        }
    }

private:
    Biquad pre_[2], tone_[2], post_[2];
    DcBlock dc_[2];
    float gain_ = 10, level_ = 1;
    float ff_[2] = {0, 0};
};

// ───────────────────────────── Вау ─────────────────────────────
// p0 режим (<0.5 ручной, >=0.5 авто), p1 педаль, p2 резонанс, p3 чувствительность, p4 mix
class Wah : public Effect {
public:
    Wah(float sr) : Effect(FX_WAH, sr) {
        float d[] = {1.f, 0.5f, 0.6f, 0.6f, 1.f};
        std::copy(d, d + 5, p_);
        freq_.setup(sr, 15.f);
        freq_.reset(800.f);
    }
    void reset() override { f_[0].reset(); f_[1].reset(); env_ = 0; }

protected:
    void update() override {
        att_ = std::exp(-1.f / (0.004f * sr_));
        rel_ = std::exp(-1.f / (0.12f * sr_));
    }
    void process(float* L, float* R, int n) override {
        const bool autoMode = p_[0] >= 0.5f;
        const float res = lerpf(0.3f, 0.92f, p_[2]);
        const float sens = expMap(p_[3], 1.f, 40.f);
        for (int i = 0; i < n; ++i) {
            float pos;
            if (autoMode) {
                float lvl = std::fabs(L[i] + R[i]) * 0.5f;
                env_ = flushDenormal(lvl + (env_ - lvl) * (lvl > env_ ? att_ : rel_));
                pos = clampf(env_ * sens, 0.f, 1.f);
            } else {
                pos = p_[1];
            }
            freq_.target = expMap(pos, 350.f, 2600.f);
            if ((i & 7) == 0) {
                float fq = freq_.value;
                f_[0].setup(sr_, fq, res);
                f_[1].setup(sr_, fq, res);
            }
            freq_.next();
            float wl = f_[0].process(L[i], 1) * 2.2f;
            float wr = f_[1].process(R[i], 1) * 2.2f;
            L[i] = L[i] + (wl - L[i]) * p_[4];
            R[i] = R[i] + (wr - R[i]) * p_[4];
        }
    }

private:
    Svf f_[2];
    Smoother freq_;
    float env_ = 0, att_ = 0.9f, rel_ = 0.99f;
};

// ─────────────────────────── Хорус / Флэнджер ───────────────────────────
// Хорус: p0 rate, p1 depth, p2 mix.  Флэнджер: p0 rate, p1 depth, p2 feedback, p3 mix
class ModDelay : public Effect {
public:
    ModDelay(int type, float sr) : Effect(type, sr) {
        dl_[0].setup((int)(0.05f * sr));
        dl_[1].setup((int)(0.05f * sr));
        if (type == FX_CHORUS) { p_[0] = 0.3f; p_[1] = 0.5f; p_[2] = 0.5f; }
        else { p_[0] = 0.25f; p_[1] = 0.6f; p_[2] = 0.6f; p_[3] = 0.5f; }
    }
    void reset() override { dl_[0].reset(); dl_[1].reset(); fbL_ = fbR_ = 0; }

protected:
    void process(float* L, float* R, int n) override {
        const bool chorus = type_ == FX_CHORUS;
        const float rate = chorus ? expMap(p_[0], 0.08f, 5.f) : expMap(p_[0], 0.03f, 4.f);
        const float base = chorus ? 0.014f * sr_ : 0.0012f * sr_;
        const float depth = chorus ? p_[1] * 0.007f * sr_ : p_[1] * 0.0045f * sr_;
        const float fb = chorus ? 0.f : lerpf(-0.9f, 0.9f, p_[2]);
        const float mix = chorus ? p_[2] : p_[3];
        const float inc = rate / sr_;
        for (int i = 0; i < n; ++i) {
            phase_ += inc;
            if (phase_ >= 1.f) phase_ -= 1.f;
            float mL = 0.5f + 0.5f * std::sin(kTwoPi * phase_);
            float mR = 0.5f + 0.5f * std::sin(kTwoPi * (phase_ + 0.25f));
            dl_[0].push(L[i] + fbL_ * fb);
            dl_[1].push(R[i] + fbR_ * fb);
            float wl = dl_[0].read(base + depth * mL + 1.f);
            float wr = dl_[1].read(base + depth * mR + 1.f);
            fbL_ = flushDenormal(wl);
            fbR_ = flushDenormal(wr);
            L[i] = L[i] * (1.f - mix * 0.5f) + wl * mix;
            R[i] = R[i] * (1.f - mix * 0.5f) + wr * mix;
        }
    }

private:
    DelayLine dl_[2];
    float phase_ = 0, fbL_ = 0, fbR_ = 0;
};

// ───────────────────────────── Фэйзер ─────────────────────────────
// p0 rate, p1 depth, p2 feedback, p3 mix
class Phaser : public Effect {
public:
    Phaser(float sr) : Effect(FX_PHASER, sr) { p_[0] = 0.3f; p_[1] = 0.7f; p_[2] = 0.5f; p_[3] = 0.5f; }
    void reset() override {
        for (auto& s : z_) for (float& v : s) v = 0;
        fb_[0] = fb_[1] = 0;
    }

protected:
    void process(float* L, float* R, int n) override {
        const float rate = expMap(p_[0], 0.05f, 6.f);
        const float fb = p_[2] * 0.85f;
        float* ch[2] = {L, R};
        for (int i = 0; i < n; ++i) {
            phase_ += rate / sr_;
            if (phase_ >= 1.f) phase_ -= 1.f;
            for (int c = 0; c < 2; ++c) {
                float lfo = 0.5f + 0.5f * std::sin(kTwoPi * (phase_ + c * 0.2f));
                float f = expMap(lfo * p_[1], 200.f, 3000.f);
                float t = std::tan(kPi * f / sr_);
                float a = (t - 1.f) / (t + 1.f);
                float x = ch[c][i] + fb_[c] * fb;
                for (int s = 0; s < 6; ++s) {
                    float y = a * x + z_[c][s];
                    z_[c][s] = flushDenormal(x - a * y);
                    x = y;
                }
                fb_[c] = x;
                ch[c][i] = ch[c][i] * (1.f - p_[3] * 0.5f) + x * p_[3];
            }
        }
    }

private:
    float z_[2][6] = {};
    float fb_[2] = {0, 0};
    float phase_ = 0;
};

// ───────────────────────────── Тремоло ─────────────────────────────
// p0 rate, p1 depth, p2 форма (синус→квадрат), p3 стерео-пан
class Tremolo : public Effect {
public:
    Tremolo(float sr) : Effect(FX_TREMOLO, sr) { p_[0] = 0.45f; p_[1] = 0.6f; p_[2] = 0.2f; p_[3] = 0.f; }

protected:
    void process(float* L, float* R, int n) override {
        const float rate = expMap(p_[0], 0.5f, 16.f);
        for (int i = 0; i < n; ++i) {
            phase_ += rate / sr_;
            if (phase_ >= 1.f) phase_ -= 1.f;
            float s = std::sin(kTwoPi * phase_);
            float sq = s > 0 ? 1.f : -1.f;
            float m = lerpf(s, fastTanh(s * 4.f) * 0.9f + 0.1f * sq, p_[2]);
            float gL = 1.f - p_[1] * (0.5f + 0.5f * m);
            float mR = p_[3] > 0.5f ? -m : m;
            float gR = 1.f - p_[1] * (0.5f + 0.5f * mR);
            L[i] *= gL;
            R[i] *= gR;
        }
    }

private:
    float phase_ = 0;
};

// ───────────────────────────── Октавер ─────────────────────────────
// p0 октава вниз, p1 октава вверх, p2 прямой сигнал
class Octaver : public Effect {
public:
    Octaver(float sr) : Effect(FX_OCTAVER, sr) {
        p_[0] = 0.6f; p_[1] = 0.2f; p_[2] = 0.7f;
        update();
    }
    void reset() override { flip_ = 1; prev_ = 0; env_ = 0; subLp_.reset(); inLp_.reset(); upHp_.reset(); upDc_.reset(); }

protected:
    void update() override {
        subLp_.lowpass(sr_, 400.f, 0.707f);
        inLp_.lowpass(sr_, 900.f, 0.707f);
        upHp_.highpass(sr_, 200.f, 0.707f);
        envC_ = std::exp(-1.f / (0.01f * sr_));
    }
    void process(float* L, float* R, int n) override {
        for (int i = 0; i < n; ++i) {
            float x = 0.5f * (L[i] + R[i]);
            float tracked = inLp_.process(x);
            if (prev_ <= 0.f && tracked > 0.f) flip_ = -flip_;  // делитель частоты на 2
            prev_ = tracked;
            float a = std::fabs(x);
            env_ = flushDenormal(a + (env_ - a) * envC_);
            float sub = subLp_.process(flip_ * env_ * 1.4f);
            float up = upDc_.process(upHp_.process(std::fabs(x) * 1.5f));
            float wet = sub * p_[0] + up * p_[1];
            L[i] = L[i] * p_[2] + wet;
            R[i] = R[i] * p_[2] + wet;
        }
    }

private:
    Biquad subLp_, inLp_, upHp_;
    DcBlock upDc_;
    float flip_ = 1, prev_ = 0, env_ = 0, envC_ = 0.99f;
};

// ───────────────────────────── Дилей ─────────────────────────────
// p0 время, p1 feedback, p2 mix, p3 тон, p4 пинг-понг
class Delay : public Effect {
public:
    Delay(float sr) : Effect(FX_DELAY, sr) {
        dl_[0].setup((int)(1.6f * sr));
        dl_[1].setup((int)(1.6f * sr));
        float d[] = {0.55f, 0.4f, 0.3f, 0.6f, 0.f};
        std::copy(d, d + 5, p_);
        time_.setup(sr, 120.f);
        time_.reset(expMap(p_[0], 0.02f, 1.5f) * sr);
    }
    void reset() override { dl_[0].reset(); dl_[1].reset(); lp_[0].reset(); lp_[1].reset(); }

protected:
    void update() override {
        time_.target = expMap(p_[0], 0.02f, 1.5f) * sr_;
        lp_[0].setLowpass(sr_, expMap(p_[3], 800.f, 14000.f));
        lp_[1].a = lp_[0].a;
    }
    void process(float* L, float* R, int n) override {
        const float fb = p_[1] * 0.92f, mix = p_[2];
        const bool ping = p_[4] >= 0.5f;
        for (int i = 0; i < n; ++i) {
            float d = time_.next();
            float yl = dl_[0].read(d), yr = dl_[1].read(d);
            float fl = lp_[0].lp(yl), fr = lp_[1].lp(yr);
            if (ping) {
                dl_[0].push(0.5f * (L[i] + R[i]) + fr * fb);
                dl_[1].push(fl * fb);
            } else {
                dl_[0].push(L[i] + fl * fb);
                dl_[1].push(R[i] + fr * fb);
            }
            L[i] += yl * mix;
            R[i] += yr * mix;
        }
    }

private:
    DelayLine dl_[2];
    OnePole lp_[2];
    Smoother time_;
};

// ───────────────────────────── Реверб (педаль) ─────────────────────────────
// p0 размер, p1 демпфирование, p2 mix, p3 ширина
class ReverbFx : public Effect {
public:
    ReverbFx(float sr) : Effect(FX_REVERB, sr) {
        rv_.setup(sr);
        p_[0] = 0.6f; p_[1] = 0.45f; p_[2] = 0.3f; p_[3] = 1.f;
    }
    void reset() override { rv_.reset(); }

protected:
    void update() override { rv_.setRoom(p_[0]); rv_.setDamp(p_[1]); rv_.setWidth(p_[3]); }
    void process(float* L, float* R, int n) override {
        for (int i = 0; i < n; ++i) {
            float wl, wr;
            rv_.process(L[i], R[i], wl, wr);
            L[i] = L[i] * (1.f - p_[2] * 0.5f) + wl * p_[2] * 2.f;
            R[i] = R[i] * (1.f - p_[2] * 0.5f) + wr * p_[2] * 2.f;
        }
    }

private:
    Reverb rv_;
};

// ─────────────────────── Lo-Fi Kitty (биткрашер) ───────────────────────
// p0 биты, p1 даунсэмпл, p2 mix, p3 «вау-флаттер» кассеты
class BitCrusher : public Effect {
public:
    BitCrusher(float sr) : Effect(FX_BITCRUSHER, sr) {
        p_[0] = 0.5f; p_[1] = 0.3f; p_[2] = 0.7f; p_[3] = 0.3f;
        wow_.setup((int)(0.02f * sr)); wow2_.setup((int)(0.02f * sr));
    }
    void reset() override { wow_.reset(); wow2_.reset(); }

protected:
    void process(float* L, float* R, int n) override {
        const float bits = lerpf(16.f, 3.f, p_[0]);
        const float steps = std::pow(2.f, bits - 1);
        const float hold = expMap(p_[1], 1.f, 32.f);
        const float wowDepth = p_[3] * 0.003f * sr_;
        for (int i = 0; i < n; ++i) {
            ph_ += 0.6f / sr_;
            if (ph_ >= 1) ph_ -= 1;
            float d = 2.f + wowDepth * (0.5f + 0.5f * std::sin(kTwoPi * ph_));
            wow_.push(L[i]); wow2_.push(R[i]);
            float il = wow_.read(d), ir = wow2_.read(d);
            cnt_ += 1.f;
            if (cnt_ >= hold) {
                cnt_ -= hold;
                hl_ = std::round(il * steps) / steps;
                hr_ = std::round(ir * steps) / steps;
            }
            float hiss = noise_.next() * 0.002f * p_[0];
            L[i] = L[i] + (hl_ + hiss - L[i]) * p_[2];
            R[i] = R[i] + (hr_ + hiss - R[i]) * p_[2];
        }
    }

private:
    DelayLine wow_, wow2_;
    Noise noise_;
    float cnt_ = 0, hl_ = 0, hr_ = 0, ph_ = 0;
};

// ───────────────────────────── 3-полосный EQ ─────────────────────────────
// p0 низ, p1 середина, p2 верх (0.5 = 0 дБ, ±12), p3 частота середины
class Eq3 : public Effect {
public:
    Eq3(float sr) : Effect(FX_EQ, sr) { p_[0] = p_[1] = p_[2] = p_[3] = 0.5f; }
    void reset() override { for (int c = 0; c < 2; ++c) { lo_[c].reset(); mid_[c].reset(); hi_[c].reset(); } }

protected:
    void update() override {
        for (int c = 0; c < 2; ++c) {
            lo_[c].lowShelf(sr_, 120.f, (p_[0] - 0.5f) * 24.f);
            mid_[c].peak(sr_, expMap(p_[3], 250.f, 4000.f), 0.8f, (p_[1] - 0.5f) * 24.f);
            hi_[c].highShelf(sr_, 6000.f, (p_[2] - 0.5f) * 24.f);
        }
    }
    void process(float* L, float* R, int n) override {
        for (int i = 0; i < n; ++i) {
            L[i] = hi_[0].process(mid_[0].process(lo_[0].process(L[i])));
            R[i] = hi_[1].process(mid_[1].process(lo_[1].process(R[i])));
        }
    }

private:
    Biquad lo_[2], mid_[2], hi_[2];
};

// ───────────────────────────── Усилитель + кабинет ─────────────────────────────
// p0 модель (clean/crunch/lead/bass), p1 gain, p2 bass, p3 mid, p4 treble, p5 volume
class Amp : public Effect {
public:
    Amp(float sr) : Effect(FX_AMP, sr) {
        float d[] = {0.34f, 0.45f, 0.55f, 0.5f, 0.55f, 0.5f};
        std::copy(d, d + 6, p_);
    }
    void reset() override {
        for (int c = 0; c < 2; ++c) {
            inHp_[c].reset(); bright_[c].reset(); stackLo_[c].reset(); stackMid_[c].reset(); stackHi_[c].reset();
            cab1_[c].reset(); cab2_[c].reset(); cab3_[c].reset(); cab4_[c].reset(); cabRes_[c].reset(); dc_[c].reset();
        }
    }

protected:
    int model() const { return std::min(3, (int)(p_[0] * 4.f)); }
    void update() override {
        const int m = model();
        static const float gMin[4] = {1.0f, 3.f, 12.f, 1.2f};
        static const float gMax[4] = {6.0f, 60.f, 400.f, 20.f};
        gain_ = expMap(p_[1], gMin[m], gMax[m]);
        const bool bass = m == 3;
        for (int c = 0; c < 2; ++c) {
            inHp_[c].highpass(sr_, bass ? 30.f : 90.f, 0.707f);
            bright_[c].highShelf(sr_, 2500.f, m == 0 ? 4.f : 1.5f);
            stackLo_[c].lowShelf(sr_, bass ? 80.f : 120.f, (p_[2] - 0.5f) * 24.f);
            stackMid_[c].peak(sr_, bass ? 500.f : 750.f, 0.7f, (p_[3] - 0.5f) * 20.f);
            stackHi_[c].highShelf(sr_, bass ? 2000.f : 3200.f, (p_[4] - 0.5f) * 24.f);
            // упрощённый кабинет: резонанс динамика, провал на 400 Гц, срез ВЧ 4-го порядка
            cabRes_[c].peak(sr_, bass ? 80.f : 110.f, 1.2f, 3.5f);
            cab1_[c].peak(sr_, 420.f, 1.0f, -3.f);
            cab2_[c].peak(sr_, bass ? 1200.f : 2300.f, 1.2f, 2.5f);
            cab3_[c].lowpass(sr_, bass ? 3500.f : 5200.f, 0.6f);
            cab4_[c].lowpass(sr_, bass ? 3500.f : 5200.f, 0.9f);
        }
        static const float lvlComp[4] = {0.9f, 0.55f, 0.35f, 0.9f};
        volume_ = dbToGain(lerpf(-24.f, 8.f, p_[5])) * lvlComp[m];
    }
    inline float shape(float x) const {
        switch (model()) {
            case 0: return fastTanh(x * 0.7f) * 1.3f;
            case 1: return fastTanh(x + 0.15f * x * x);  // лёгкая асимметрия
            case 2: { float y = fastTanh(x); return fastTanh(y * 2.5f); }
            default: return fastTanh(x * 0.8f);
        }
    }
    void process(float* L, float* R, int n) override {
        float* ch[2] = {L, R};
        for (int c = 0; c < 2; ++c) {
            float* x = ch[c];
            for (int i = 0; i < n; ++i) {
                float s = inHp_[c].process(x[i]);
                s = bright_[c].process(s);
                s = shape(s * gain_);
                s = dc_[c].process(s);
                s = stackHi_[c].process(stackMid_[c].process(stackLo_[c].process(s)));
                s = cab4_[c].process(cab3_[c].process(cab2_[c].process(cab1_[c].process(cabRes_[c].process(s)))));
                x[i] = s * volume_;
            }
        }
    }

private:
    Biquad inHp_[2], bright_[2], stackLo_[2], stackMid_[2], stackHi_[2];
    Biquad cabRes_[2], cab1_[2], cab2_[2], cab3_[2], cab4_[2];
    DcBlock dc_[2];
    float gain_ = 4, volume_ = 1;
};

// ───────────────────────────── Тюнинг (коррекция высоты) ─────────────────────────────
// p0 скорость/сила, p1 тоника (0..11 → 0..1), p2 лад (<.33 хроматика, <.66 мажор, иначе минор), p3 mix
class PitchCorrect : public Effect {
public:
    PitchCorrect(float sr) : Effect(FX_PITCH, sr) {
        p_[0] = 0.7f; p_[1] = 0.f; p_[2] = 0.5f; p_[3] = 1.f;
        grain_ = (int)(0.04f * sr);
        dl_.setup(grain_ * 3);
        dec_.lowpass(sr, 2500.f, 0.707f);
        decim_ = std::max(1, (int)std::round(sr / 12000.f));
        dsr_ = sr / decim_;
        ana_.assign(kWin * 2, 0.f);
        diff_.assign(kWin, 0.f);
        ratio_.setup(sr, 30.f);
        ratio_.reset(1.f);
    }
    void reset() override { dl_.reset(); dec_.reset(); std::fill(ana_.begin(), ana_.end(), 0.f); ratio_.reset(1.f); }

protected:
    static constexpr int kWin = 512;   // окно анализа на ~12 кГц
    static constexpr int kHop = 128;

    float detect() {
        // YIN: разностная функция + кумулятивная нормализация
        const int maxTau = std::min(kWin - 2, (int)(dsr_ / 70.f));
        const int minTau = (int)(dsr_ / 1000.f);
        const int W = kWin / 2;
        float energy = 0;
        for (int i = 0; i < W; ++i) energy += ana_[i] * ana_[i];
        if (energy / W < 1e-6f) return 0.f;
        float running = 0;
        diff_[0] = 1.f;
        for (int tau = 1; tau <= maxTau; ++tau) {
            float s = 0;
            for (int i = 0; i < W; ++i) { float d = ana_[i] - ana_[i + tau]; s += d * d; }
            running += s;
            diff_[tau] = running > 0 ? s * tau / running : 1.f;
        }
        int best = -1;
        for (int tau = std::max(2, minTau); tau < maxTau; ++tau) {
            if (diff_[tau] < 0.15f) {
                while (tau + 1 < maxTau && diff_[tau + 1] < diff_[tau]) ++tau;
                best = tau;
                break;
            }
        }
        if (best < 0) return 0.f;
        // параболическая интерполяция
        float a = diff_[best - 1], b = diff_[best], c = diff_[best + 1];
        float denom = a - 2 * b + c;
        float t = best + (std::fabs(denom) > 1e-9f ? 0.5f * (a - c) / denom : 0.f);
        return dsr_ / t;
    }

    float snap(float midi) const {
        const int mode = p_[2] < 0.33f ? 0 : (p_[2] < 0.66f ? 1 : 2);
        if (mode == 0) return std::round(midi);
        static const int major[7] = {0, 2, 4, 5, 7, 9, 11};
        static const int minor[7] = {0, 2, 3, 5, 7, 8, 10};
        const int* sc = mode == 1 ? major : minor;
        const int tonic = (int)std::round(p_[1] * 11.f);
        float best = midi, bestD = 1e9f;
        int base = (int)std::floor(midi) - 12;
        for (int n = base; n <= base + 24; ++n) {
            int pc = ((n - tonic) % 12 + 12) % 12;
            bool in = false;
            for (int k = 0; k < 7; ++k) if (sc[k] == pc) in = true;
            if (!in) continue;
            float d = std::fabs(n - midi);
            if (d < bestD) { bestD = d; best = (float)n; }
        }
        return best;
    }

    void process(float* L, float* R, int n) override {
        for (int i = 0; i < n; ++i) {
            float x = 0.5f * (L[i] + R[i]);
            // анализ на пониженной частоте
            float f = dec_.process(x);
            if (++dcnt_ >= decim_) {
                dcnt_ = 0;
                std::memmove(ana_.data(), ana_.data() + 1, sizeof(float) * (kWin - 1));
                ana_[kWin - 1] = f;
                if (++hop_ >= kHop) {
                    hop_ = 0;
                    float hz = detect();
                    if (hz > 60.f && hz < 1100.f) {
                        float m = 69.f + 12.f * std::log2(hz / 440.f);
                        float target = snap(m);
                        float semis = (target - m) * p_[0];
                        ratio_.target = std::pow(2.f, semis / 12.f);
                    } else {
                        ratio_.target = 1.f;
                    }
                }
            }
            // гранулярный сдвиг высоты двумя окнами
            float r = ratio_.next();
            dl_.push(x);
            phase_ += (1.f - r) / (float)grain_;
            if (phase_ >= 1.f) phase_ -= 1.f;
            if (phase_ < 0.f) phase_ += 1.f;
            float ph2 = phase_ + 0.5f;
            if (ph2 >= 1.f) ph2 -= 1.f;
            float d1 = 2.f + phase_ * grain_, d2 = 2.f + ph2 * grain_;
            float w1 = std::sin(kPi * phase_), w2 = std::sin(kPi * ph2);
            float y = dl_.read(d1) * w1 * w1 + dl_.read(d2) * w2 * w2;
            L[i] = L[i] + (y - L[i]) * p_[3];
            R[i] = R[i] + (y - R[i]) * p_[3];
        }
    }

private:
    DelayLine dl_;
    Biquad dec_;
    std::vector<float> ana_, diff_;
    Smoother ratio_;
    int grain_ = 1920, decim_ = 4, dcnt_ = 0, hop_ = 0;
    float dsr_ = 12000.f, phase_ = 0.f;
};

}  // namespace

Effect* createEffect(int type, float sr) {
    switch (type) {
        case FX_COMPRESSOR: return new Compressor(sr);
        case FX_GATE: return new Gate(sr);
        case FX_OVERDRIVE:
        case FX_DISTORTION:
        case FX_FUZZ: return new Drive(type, sr);
        case FX_WAH: return new Wah(sr);
        case FX_CHORUS:
        case FX_FLANGER: return new ModDelay(type, sr);
        case FX_PHASER: return new Phaser(sr);
        case FX_TREMOLO: return new Tremolo(sr);
        case FX_OCTAVER: return new Octaver(sr);
        case FX_DELAY: return new Delay(sr);
        case FX_REVERB: return new ReverbFx(sr);
        case FX_BITCRUSHER: return new BitCrusher(sr);
        case FX_EQ: return new Eq3(sr);
        case FX_AMP: return new Amp(sr);
        case FX_PITCH: return new PitchCorrect(sr);
        default: return nullptr;
    }
}

}  // namespace yume
