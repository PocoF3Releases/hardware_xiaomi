// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <aidl/android/hardware/vibrator/BnVibrator.h>
#include <array>
#include <effect.h>
#include <condition_variable>
#include <mutex>
#include <thread>
namespace aidl::android::hardware::vibrator {
class Vibrator final : public BnVibrator {
  public:
    Vibrator();
    ~Vibrator() override;
    bool ready() const { return mFd >= 0; }
    ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) override;
    ndk::ScopedAStatus off() override;
    ndk::ScopedAStatus on(int32_t timeoutMs,
                          const std::shared_ptr<IVibratorCallback>& callback) override;
    ndk::ScopedAStatus perform(Effect effect, EffectStrength strength,
                               const std::shared_ptr<IVibratorCallback>& callback,
                               int32_t* _aidl_return) override;
    ndk::ScopedAStatus getSupportedEffects(std::vector<Effect>* _aidl_return) override;
    ndk::ScopedAStatus setAmplitude(float amplitude) override;
    ndk::ScopedAStatus setExternalControl(bool enabled) override;
    ndk::ScopedAStatus getCompositionDelayMax(int32_t* maxDelayMs) override;
    ndk::ScopedAStatus getCompositionSizeMax(int32_t* maxSize) override;
    ndk::ScopedAStatus getSupportedPrimitives(std::vector<CompositePrimitive>* supported) override;
    ndk::ScopedAStatus getPrimitiveDuration(CompositePrimitive primitive,
                                            int32_t* durationMs) override;
    ndk::ScopedAStatus compose(const std::vector<CompositeEffect>& composite,
                               const std::shared_ptr<IVibratorCallback>& callback) override;
    ndk::ScopedAStatus getSupportedAlwaysOnEffects(std::vector<Effect>* _aidl_return) override;
    ndk::ScopedAStatus alwaysOnEnable(int32_t id, Effect effect, EffectStrength strength) override;
    ndk::ScopedAStatus alwaysOnDisable(int32_t id) override;
    ndk::ScopedAStatus getResonantFrequency(float* resonantFreqHz) override;
    ndk::ScopedAStatus getQFactor(float* qFactor) override;
    ndk::ScopedAStatus getFrequencyResolution(float* freqResolutionHz) override;
    ndk::ScopedAStatus getFrequencyMinimum(float* freqMinimumHz) override;
    ndk::ScopedAStatus getBandwidthAmplitudeMap(std::vector<float>* _aidl_return) override;
    ndk::ScopedAStatus getPwlePrimitiveDurationMax(int32_t* durationMs) override;
    ndk::ScopedAStatus getPwleCompositionSizeMax(int32_t* maxSize) override;
    ndk::ScopedAStatus getSupportedBraking(std::vector<Braking>* supported) override;
    ndk::ScopedAStatus composePwle(const std::vector<PrimitivePwle>& composite,
                                   const std::shared_ptr<IVibratorCallback>& callback) override;

  private:
    struct Step {
        int effect;
        int duration;
        float scale;
        int delay;
        const effect_stream* stream = nullptr;
    };
    ndk::ScopedAStatus start(std::vector<Step> steps, std::shared_ptr<IVibratorCallback> callback);
    bool writeWave(const effect_stream& stream, float scale, size_t* offset);
    int streamDuration(const effect_stream* stream) const;
    bool upload(int effect, int duration, float scale, int* actualDuration);
    bool stopLocked();
    bool setGain(float amplitude);
    bool event(unsigned short code, int value);
    void run();
    int mFd = -1, mEffect = -1;
    int mGainFd = -1;
    int mWaveFd = -1;
    int mWaveStatusFd = -1;
    int waveActive();
    unsigned int mWavePeriod = 0;
    std::array<const effect_stream*, 7> mEffectStreams{};
    std::array<const effect_stream*, 9> mPrimitiveStreams{};
    bool mDirectRamGain = false;
    static constexpr int kRamEffectCount = 10;
    std::array<int, kRamEffectCount> mDurations{};
    std::mutex mMutex;
    std::condition_variable mCv;
    std::thread mWorker;
    bool mExit = false, mPending = false;
    bool mActiveConstant = false;
    float mAmplitude = 1;
    float mResonantFrequency = 0;
    uint64_t mGeneration = 0;
    std::vector<Step> mSteps;
    std::shared_ptr<IVibratorCallback> mCallback;
};
}  // namespace aidl::android::hardware::vibrator
