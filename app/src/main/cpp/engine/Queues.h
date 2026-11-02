#pragma once
// Lock-free очереди для обмена с аудиопотоком.
#include <atomic>
#include <cstddef>
#include <vector>

namespace yume {

// Single-producer / single-consumer кольцевая очередь фиксированного размера.
template <typename T>
class SpscQueue {
public:
    explicit SpscQueue(size_t capacityPow2 = 1024) : buf_(capacityPow2), mask_(capacityPow2 - 1) {}

    bool push(const T& v) {
        const size_t w = write_.load(std::memory_order_relaxed);
        const size_t next = (w + 1) & mask_;
        if (next == read_.load(std::memory_order_acquire)) return false;  // полна
        buf_[w] = v;
        write_.store(next, std::memory_order_release);
        return true;
    }
    bool pop(T& out) {
        const size_t r = read_.load(std::memory_order_relaxed);
        if (r == write_.load(std::memory_order_acquire)) return false;
        out = buf_[r];
        read_.store((r + 1) & mask_, std::memory_order_release);
        return true;
    }
    size_t size() const {
        return (write_.load(std::memory_order_acquire) - read_.load(std::memory_order_acquire)) & mask_;
    }

private:
    std::vector<T> buf_;
    size_t mask_;
    std::atomic<size_t> write_{0};
    std::atomic<size_t> read_{0};
};

// Кольцевой буфер float-сэмплов (SPSC), для записи/мониторинга входа.
class SampleRing {
public:
    void setup(size_t capacityPow2) {
        buf_.assign(capacityPow2, 0.f);
        mask_ = capacityPow2 - 1;
        write_.store(0); read_.store(0);
    }
    size_t available() const { return write_.load(std::memory_order_acquire) - read_.load(std::memory_order_acquire); }
    size_t capacity() const { return buf_.size(); }
    size_t write(const float* src, size_t n) {
        const size_t w = write_.load(std::memory_order_relaxed);
        const size_t free = buf_.size() - (w - read_.load(std::memory_order_acquire));
        if (n > free) n = free;
        for (size_t i = 0; i < n; ++i) buf_[(w + i) & mask_] = src[i];
        write_.store(w + n, std::memory_order_release);
        return n;
    }
    size_t read(float* dst, size_t n) {
        const size_t r = read_.load(std::memory_order_relaxed);
        const size_t avail = write_.load(std::memory_order_acquire) - r;
        if (n > avail) n = avail;
        for (size_t i = 0; i < n; ++i) dst[i] = buf_[(r + i) & mask_];
        read_.store(r + n, std::memory_order_release);
        return n;
    }
    void skip(size_t n) {
        const size_t r = read_.load(std::memory_order_relaxed);
        const size_t avail = write_.load(std::memory_order_acquire) - r;
        read_.store(r + (n > avail ? avail : n), std::memory_order_release);
    }
    void clear() { read_.store(write_.load()); }

private:
    std::vector<float> buf_;
    size_t mask_ = 0;
    std::atomic<size_t> write_{0};
    std::atomic<size_t> read_{0};
};

}  // namespace yume
