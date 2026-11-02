#pragma once
// Минимальные чтение/запись WAV (PCM 16/24/32 бит и float32).
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace yume {

struct AudioData {
    int sampleRate = 48000;
    int channels = 1;
    std::vector<float> samples;  // interleaved
    int64_t frames() const { return channels ? (int64_t)samples.size() / channels : 0; }
};

inline bool readWav(const std::string& path, AudioData& out) {
    FILE* f = std::fopen(path.c_str(), "rb");
    if (!f) return false;
    auto rd32 = [&](uint32_t& v) { return std::fread(&v, 4, 1, f) == 1; };
    auto rd16 = [&](uint16_t& v) { return std::fread(&v, 2, 1, f) == 1; };
    char id[4];
    uint32_t sz;
    if (std::fread(id, 1, 4, f) != 4 || std::memcmp(id, "RIFF", 4) != 0) { std::fclose(f); return false; }
    rd32(sz);
    if (std::fread(id, 1, 4, f) != 4 || std::memcmp(id, "WAVE", 4) != 0) { std::fclose(f); return false; }
    uint16_t fmt = 1, ch = 1, bits = 16;
    uint32_t rate = 48000;
    bool haveFmt = false;
    while (std::fread(id, 1, 4, f) == 4 && rd32(sz)) {
        if (std::memcmp(id, "fmt ", 4) == 0) {
            uint32_t br; uint16_t ba;
            rd16(fmt); rd16(ch); rd32(rate); rd32(br); rd16(ba); rd16(bits);
            if (fmt == 0xFFFE && sz >= 26) {  // WAVE_FORMAT_EXTENSIBLE
                uint16_t cbSize, valid; uint32_t mask; uint16_t sub;
                rd16(cbSize); rd16(valid); rd32(mask); rd16(sub);
                fmt = sub;
                std::fseek(f, (long)(sz - 26), SEEK_CUR);
            } else if (sz > 16) {
                std::fseek(f, (long)(sz - 16), SEEK_CUR);
            }
            haveFmt = true;
        } else if (std::memcmp(id, "data", 4) == 0 && haveFmt) {
            const int bps = bits / 8;
            if (bps <= 0 || ch == 0) break;
            const size_t count = sz / bps;
            std::vector<uint8_t> raw(count * bps);
            size_t got = std::fread(raw.data(), 1, raw.size(), f);
            const size_t n = got / bps;
            out.samples.resize(n);
            out.channels = ch;
            out.sampleRate = (int)rate;
            for (size_t i = 0; i < n; ++i) {
                const uint8_t* p = &raw[i * bps];
                float v = 0;
                if (fmt == 3 && bits == 32) { std::memcpy(&v, p, 4); }
                else if (bits == 16) { int16_t s; std::memcpy(&s, p, 2); v = s / 32768.f; }
                else if (bits == 24) { int32_t s = (p[0] << 8) | (p[1] << 16) | (p[2] << 24); v = (s >> 8) / 8388608.f; }
                else if (bits == 32) { int32_t s; std::memcpy(&s, p, 4); v = (float)(s / 2147483648.0); }
                else if (bits == 8) { v = (p[0] - 128) / 128.f; }
                out.samples[i] = v;
            }
            std::fclose(f);
            return true;
        } else {
            std::fseek(f, (long)(sz + (sz & 1)), SEEK_CUR);
        }
    }
    std::fclose(f);
    return false;
}

// Потоковая запись WAV: заголовок дописывается при close().
class WavWriter {
public:
    bool open(const std::string& path, int sampleRate, int channels, int bits) {
        f_ = std::fopen(path.c_str(), "wb");
        if (!f_) return false;
        rate_ = sampleRate; ch_ = channels; bits_ = bits; bytes_ = 0;
        uint8_t hdr[44] = {0};
        std::fwrite(hdr, 1, 44, f_);
        return true;
    }
    void write(const float* interleaved, int frames) {
        if (!f_) return;
        const int n = frames * ch_;
        if (bits_ == 16) {
            buf16_.resize(n);
            for (int i = 0; i < n; ++i) {
                float v = interleaved[i];
                v = v > 1.f ? 1.f : (v < -1.f ? -1.f : v);
                buf16_[i] = (int16_t)(v * 32767.f);
            }
            std::fwrite(buf16_.data(), 2, n, f_);
            bytes_ += (uint32_t)n * 2;
        } else {
            buf24_.resize((size_t)n * 3);
            for (int i = 0; i < n; ++i) {
                float v = interleaved[i];
                v = v > 1.f ? 1.f : (v < -1.f ? -1.f : v);
                int32_t s = (int32_t)(v * 8388607.f);
                buf24_[i * 3] = (uint8_t)(s & 0xFF);
                buf24_[i * 3 + 1] = (uint8_t)((s >> 8) & 0xFF);
                buf24_[i * 3 + 2] = (uint8_t)((s >> 16) & 0xFF);
            }
            std::fwrite(buf24_.data(), 1, buf24_.size(), f_);
            bytes_ += (uint32_t)n * 3;
        }
    }
    void close() {
        if (!f_) return;
        std::fseek(f_, 0, SEEK_SET);
        auto w32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f_); };
        auto w16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f_); };
        std::fwrite("RIFF", 1, 4, f_); w32(36 + bytes_);
        std::fwrite("WAVE", 1, 4, f_);
        std::fwrite("fmt ", 1, 4, f_); w32(16); w16(1); w16((uint16_t)ch_); w32((uint32_t)rate_);
        w32((uint32_t)(rate_ * ch_ * bits_ / 8)); w16((uint16_t)(ch_ * bits_ / 8)); w16((uint16_t)bits_);
        std::fwrite("data", 1, 4, f_); w32(bytes_);
        std::fclose(f_);
        f_ = nullptr;
    }
    bool isOpen() const { return f_ != nullptr; }
    ~WavWriter() { close(); }

private:
    FILE* f_ = nullptr;
    int rate_ = 48000, ch_ = 1, bits_ = 16;
    uint32_t bytes_ = 0;
    std::vector<int16_t> buf16_;
    std::vector<uint8_t> buf24_;
};

}  // namespace yume
