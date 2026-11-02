#include "AudioHost.h"

#include <android/log.h>

#define LOG_TAG "YumeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace yume {

bool AudioHost::start() {
    std::lock_guard<std::mutex> lk(lock_);
    if (out_) return true;
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = b.openStream(out_);
    if (r != oboe::Result::OK) {
        LOGE("open output failed: %s", oboe::convertToText(r));
        out_.reset();
        return false;
    }
    // двойной burst — компромисс между задержкой и устойчивостью
    out_->setBufferSizeInFrames(out_->getFramesPerBurst() * 2);
    r = out_->requestStart();
    if (r != oboe::Result::OK) {
        LOGE("start output failed: %s", oboe::convertToText(r));
        out_->close();
        out_.reset();
        return false;
    }
    LOGI("output: %d Hz, burst %d, buffer %d, exclusive=%d", out_->getSampleRate(), out_->getFramesPerBurst(),
         out_->getBufferSizeInFrames(), out_->getSharingMode() == oboe::SharingMode::Exclusive);
    return true;
}

void AudioHost::stop() {
    std::lock_guard<std::mutex> lk(lock_);
    if (out_) {
        out_->requestStop();
        out_->close();
        out_.reset();
    }
}

bool AudioHost::openInput() {
    std::lock_guard<std::mutex> lk(lock_);
    wantInput_ = true;
    if (in_) return true;
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setInputPreset(oboe::InputPreset::VoicePerformance)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = b.openStream(in_);
    if (r != oboe::Result::OK) {
        LOGE("open input failed: %s", oboe::convertToText(r));
        in_.reset();
        return false;
    }
    r = in_->requestStart();
    if (r != oboe::Result::OK) {
        in_->close();
        in_.reset();
        return false;
    }
    return true;
}

void AudioHost::closeInput() {
    std::lock_guard<std::mutex> lk(lock_);
    wantInput_ = false;
    if (in_) {
        in_->requestStop();
        in_->close();
        in_.reset();
    }
}

float AudioHost::outputLatencyMs() {
    if (!out_) return 0.f;
    auto r = out_->calculateLatencyMillis();
    if (r) return (float)r.value();
    return 1000.f * out_->getBufferSizeInFrames() / (float)kSampleRate;
}

float AudioHost::inputLatencyMs() {
    if (!in_) return 0.f;
    auto r = in_->calculateLatencyMillis();
    if (r) return (float)r.value();
    return 1000.f * in_->getFramesPerBurst() / (float)kSampleRate;
}

oboe::DataCallbackResult AudioHost::onAudioReady(oboe::AudioStream* s, void* data, int32_t frames) {
    if (s->getDirection() == oboe::Direction::Output) {
        engine_.render(static_cast<float*>(data), frames);
    } else {
        const float* src = static_cast<const float*>(data);
        int done = 0;
        while (done < frames) {
            int n = std::min<int>(frames - done, 4096);
            std::copy(src + done, src + done + n, inBuf_);
            engine_.pushInput(inBuf_, n);
            done += n;
        }
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioHost::onErrorAfterClose(oboe::AudioStream* s, oboe::Result error) {
    // смена устройства (наушники, USB-интерфейс) — переоткрываем поток
    LOGI("stream closed: %s, reopening", oboe::convertToText(error));
    if (s->getDirection() == oboe::Direction::Output) {
        {
            std::lock_guard<std::mutex> lk(lock_);
            out_.reset();
        }
        start();
    } else {
        bool want = wantInput_.load();
        {
            std::lock_guard<std::mutex> lk(lock_);
            in_.reset();
        }
        if (want) openInput();
    }
}

}  // namespace yume
