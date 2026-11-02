#pragma once
// Связка Engine ↔ Oboe: низколатентный выходной поток + опциональный входной (микрофон/линия).
#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

#include "Engine.h"

namespace yume {

class AudioHost : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int kSampleRate = 48000;

    explicit AudioHost(Engine& e) : engine_(e) {}
    ~AudioHost() override { stop(); closeInput(); }

    bool start();
    void stop();
    bool isRunning() const { return out_ != nullptr; }
    bool openInput();
    void closeInput();
    float outputLatencyMs();
    float inputLatencyMs();
    int bufferFrames() const { return out_ ? out_->getBufferSizeInFrames() : 0; }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* s, void* data, int32_t frames) override;
    void onErrorAfterClose(oboe::AudioStream* s, oboe::Result error) override;

private:
    Engine& engine_;
    std::shared_ptr<oboe::AudioStream> out_;
    std::shared_ptr<oboe::AudioStream> in_;
    std::mutex lock_;
    std::atomic<bool> wantInput_{false};
    float inBuf_[4096];
};

}  // namespace yume
