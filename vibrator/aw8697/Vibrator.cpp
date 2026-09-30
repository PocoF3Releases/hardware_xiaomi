// SPDX-License-Identifier: Apache-2.0
#include "Vibrator.h"
#include <dirent.h>
#include <fcntl.h>
#include <linux/input.h>
#include <log/log.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>

namespace aidl::android::hardware::vibrator {
namespace {
struct EffectMapping {
    Effect effect;
    int ramId;
};
// Audited against Alioth's RAM bank and MIUI selectors. Filename IDs are
// not Android enum identities: mesh.heavy uses RAM 8 and mesh.light uses RAM 5.
constexpr std::array<EffectMapping, 7> kEffects{{
        {Effect::CLICK, 0},
        {Effect::DOUBLE_CLICK, 0},
        {Effect::TICK, 2},
        {Effect::THUD, 3},
        {Effect::POP, 4},
        {Effect::HEAVY_CLICK, 8},
        {Effect::TEXTURE_TICK, 5},
}};
// The stock driver maps 0..0x3fff to gain 30, then (value-0x3fff)/128,
// with 0x7fff selecting gain 128. Invert that encoding rather than passing
// a linear Android amplitude, which collapses all values <= 0.5 to gain 30.
int encodeAmplitude(float amplitude) {
    int gain = std::clamp(static_cast<int>(std::lround(amplitude * 128)), 30, 128);
    return gain == 128 ? 32767 : 16383 + gain * 128;
}
ndk::ScopedAStatus unsupported() {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}
ndk::ScopedAStatus invalid() {
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
}
ndk::ScopedAStatus failed() {
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
}
}  // namespace
Vibrator::Vibrator() {
    DIR* dir = opendir("/dev/input");
    if (!dir) return;
    while (auto* ent = readdir(dir)) {
        if (strncmp(ent->d_name, "event", 5)) continue;
        std::string path = std::string("/dev/input/") + ent->d_name;
        int fd = open(path.c_str(), O_RDWR | O_CLOEXEC);
        if (fd < 0) continue;
        char name[80]{};
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) >= 0 && !strcmp(name, "aw8697_haptic")) {
            mFd = fd;
            break;
        }
        close(fd);
    }
    closedir(dir);
    if (mFd < 0) return;
    // The kernel publishes a positive period only after RAM programming has
    // completed. Do not register a working vibrator when firmware or its
    // metadata is unavailable. The direct-gain extension remains optional.
    bool ramReady = false;
    for (int attempt = 0; attempt < 100; ++attempt) {
        int metadataFd = open("/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/custom_wave",
                              O_RDONLY | O_CLOEXEC);
        if (metadataFd >= 0) {
            char info[256]{};
            const ssize_t n = TEMP_FAILURE_RETRY(read(metadataFd, info, sizeof(info) - 1));
            close(metadataFd);
            unsigned int period = 0;
            const char* field = n > 0 ? strstr(info, "period_size=") : nullptr;
            if (field && sscanf(field, "period_size=%u;", &period) == 1 && period > 0) {
                mDirectRamGain = strstr(info, "ram_gain_abi=1;") != nullptr;
                ramReady = true;
                break;
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    if (!ramReady) {
        ALOGE("AW8697 RAM firmware readiness could not be confirmed");
        close(mFd);
        mFd = -1;
        return;
    }
    // Query the legacy three-short ABI without starting the motor.
    for (int id = 0; id < kRamEffectCount; ++id) {
        if (!upload(id, 0, 1, &mDurations[id]) || mDurations[id] <= 0 || mDurations[id] > 1000 ||
            !stopLocked()) {
            close(mFd);
            mFd = -1;
            return;
        }
    }
    mGainFd = open("/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/gain", O_WRONLY | O_CLOEXEC);
    // Read the cached boot measurement; reading "f0" starts calibration and
    // would interrupt playback. The stock ABI reports tenths of a hertz.
    if (FILE* f = fopen("/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/f0_value", "re")) {
        unsigned int tenths = 0;
        if (fscanf(f, "%u", &tenths) == 1 && tenths >= 1000 && tenths <= 3000)
            mResonantFrequency = tenths / 10.0f;
        fclose(f);
    }
    mWorker = std::thread(&Vibrator::run, this);
}
Vibrator::~Vibrator() {
    {
        std::lock_guard lock(mMutex);
        mExit = true;
        ++mGeneration;
        mCv.notify_all();
    }
    if (mWorker.joinable()) mWorker.join();
    if (mFd >= 0) {
        stopLocked();
        close(mFd);
    }
    if (mGainFd >= 0) close(mGainFd);
}
bool Vibrator::setGain(float amplitude) {
    if (mGainFd < 0) return event(FF_GAIN, encodeAmplitude(amplitude));
    // Unlike FF_GAIN, this ABI has no minimum-gain plateau and updates the
    // driver's saved gain, which RAM startup/battery compensation reuses.
    char value[16];
    int count = snprintf(value, sizeof(value), "%d",
                         std::clamp(static_cast<int>(std::lround(amplitude * 128)), 1, 128));
    if (lseek(mGainFd, 0, SEEK_SET) < 0) return false;
    return TEMP_FAILURE_RETRY(write(mGainFd, value, count)) == count;
}
bool Vibrator::event(unsigned short code, int value) {
    input_event e{};
    e.type = EV_FF;
    e.code = code;
    e.value = value;
    return TEMP_FAILURE_RETRY(write(mFd, &e, sizeof(e))) == sizeof(e);
}
bool Vibrator::stopLocked() {
    if (mEffect < 0) return true;
    bool stopped = event(mEffect, 0);
    if (ioctl(mFd, EVIOCRMFF, mEffect) < 0) return false;
    mEffect = -1;
    return stopped;
}
bool Vibrator::upload(int id, int duration, float scale, int* actual) {
    ff_effect e{};
    e.id = -1;
    int16_t data[4] = {static_cast<int16_t>(id), 0, 0, 0};
    const bool directGain = mDirectRamGain && !actual && id >= 0 && id < kRamEffectCount;
    if (directGain)
        data[3] = 0x4700 | std::clamp(static_cast<int>(std::lround(scale * 128)), 0, 128);
    if (id >= 0) {
        e.type = FF_PERIODIC;
        e.u.periodic.waveform = FF_CUSTOM;
        e.u.periodic.magnitude = static_cast<int16_t>(encodeAmplitude(scale));
        // Legacy ABI counts bytes, despite the generic FF API's sample count.
        e.u.periodic.custom_len = (directGain ? 4 : 3) * sizeof(int16_t);
        e.u.periodic.custom_data = data;
    } else {
        e.type = FF_CONSTANT;
        e.u.constant.level = static_cast<int16_t>(encodeAmplitude(scale));
        e.replay.length = duration;
    }
    if (ioctl(mFd, EVIOCSFF, &e) < 0) return false;
    mEffect = e.id;
    if (actual) *actual = id >= 0 ? data[1] * 1000 + data[2] : duration;
    return true;
}
ndk::ScopedAStatus Vibrator::start(std::vector<Step> steps, std::shared_ptr<IVibratorCallback> cb) {
    std::unique_lock lock(mMutex);
    if (!ready()) return failed();
    ++mGeneration;
    mPending = false;
    auto previousCallback = std::move(mCallback);
    mCv.notify_all();
    const bool stopped = stopLocked();
    mActiveConstant = false;
    if (stopped) {
        mSteps = std::move(steps);
        mCallback = std::move(cb);
        mPending = true;
        mCv.notify_all();
    }
    // A completion callback reports termination, including cancellation. Never
    // call back while holding the mutex: clients may reenter this service.
    lock.unlock();
    if (previousCallback) previousCallback->onComplete();
    return stopped ? ndk::ScopedAStatus::ok() : failed();
}

void Vibrator::run() {
    std::unique_lock lock(mMutex);
    while (!mExit) {
        mCv.wait(lock, [&] { return mExit || mPending; });
        if (mExit) break;
        auto steps = std::move(mSteps);
        auto generation = mGeneration;
        mPending = false;
        auto wait = [&](int ms) {
            return !mCv.wait_for(lock, std::chrono::milliseconds(ms),
                                 [&] { return mExit || generation != mGeneration; });
        };
        for (auto s : steps) {
            if (s.delay > 0 && !wait(s.delay)) {
                break;
            }
            if (s.scale > 0 && s.duration > 0) {
                mActiveConstant = s.effect < 0;
                if (!upload(s.effect, s.duration, s.scale, nullptr) ||
                    !setGain(s.effect < 0 ? mAmplitude : 1.0f) || !event(mEffect, 1)) {
                    ALOGE("AW8697 effect playback failed: %s", strerror(errno));
                    stopLocked();
                    break;
                }
            }
            if (!wait(s.duration)) {
                break;
            }
            if (!stopLocked()) {
                ALOGE("AW8697 stop failed");
                break;
            }
        }
        if (generation == mGeneration) {
            mActiveConstant = false;
            // onComplete has no success result. Notify after an asynchronous
            // failure as well, so Android need not wait for its timeout.
            auto callback = std::move(mCallback);
            if (!mExit && callback) {
                lock.unlock();
                callback->onComplete();
                lock.lock();
            }
        }
    }
}
ndk::ScopedAStatus Vibrator::getCapabilities(int32_t* out) {
    *out = CAP_ON_CALLBACK | CAP_PERFORM_CALLBACK;
    if (mGainFd >= 0) *out |= CAP_AMPLITUDE_CONTROL;
    if (mResonantFrequency > 0) *out |= CAP_GET_RESONANT_FREQUENCY;
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::off() {
    std::unique_lock lock(mMutex);
    ++mGeneration;
    mPending = false;
    auto callback = std::move(mCallback);
    mCv.notify_all();
    const bool stopped = stopLocked();
    mActiveConstant = false;
    mAmplitude = 1;
    lock.unlock();
    if (callback) callback->onComplete();
    return stopped ? ndk::ScopedAStatus::ok() : failed();
}

ndk::ScopedAStatus Vibrator::on(int32_t ms, const std::shared_ptr<IVibratorCallback>& cb) {
    if (ms <= 0) return invalid();
    // Linux input replay.length is 16-bit; do not truncate long Android requests.
    std::vector<Step> steps;
    while (ms > 0) {
        int duration = std::min(ms, 65535);
        steps.push_back({-1, duration, 1, 0});
        ms -= duration;
    }
    return start(std::move(steps), cb);
}
ndk::ScopedAStatus Vibrator::perform(Effect e, EffectStrength strength,
                                     const std::shared_ptr<IVibratorCallback>& cb, int32_t* out) {
    *out = 0;
    const auto mapping =
            std::find_if(kEffects.begin(), kEffects.end(),
                         [e](const EffectMapping& entry) { return entry.effect == e; });
    if (mapping == kEffects.end()) return unsupported();
    const int id = mapping->ramId;
    float scale;
    switch (strength) {
        // Alioth enables sys.haptic.infinitelevel. Stock VibrateUtils sends
        // 0.375/0.625/1.0 through configStrengthForEffect; this overrides the
        // old 0/0.5/1.0 EffectStrength table in InputFFDevice::playEffect.
        // Use the active stock levels (gains 48/80/128), not that fallback.
        case EffectStrength::LIGHT:
            scale = 0.375f;
            break;
        case EffectStrength::MEDIUM:
            scale = 0.625f;
            break;
        case EffectStrength::STRONG:
            scale = 1;
            break;
        default:
            return unsupported();
    }
    // Alioth's driver effect 1 is a single waveform, not a timed pair.
    // Compose two stock clicks so DOUBLE_CLICK has two distinct onsets.
    if (e == Effect::DOUBLE_CLICK) {
        constexpr int kDoubleClickGapMs = 80;
        auto status = start(
                {{0, mDurations[0], scale, 0}, {0, mDurations[0], scale, kDoubleClickGapMs}}, cb);
        if (status.isOk()) *out = 2 * mDurations[0] + kDoubleClickGapMs;
        return status;
    }
    // TEXTURE_TICK uses MIUI mesh.light (RAM 5, medium gain 80). The
    // waveform already provides light feedback; do not attenuate it again.
    auto status = start({{id, mDurations[id], scale, 0}}, cb);
    if (status.isOk()) *out = mDurations[id];
    return status;
}
ndk::ScopedAStatus Vibrator::getSupportedEffects(std::vector<Effect>* out) {
    out->clear();
    for (const auto& mapping : kEffects) out->push_back(mapping.effect);
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::setAmplitude(float amplitude) {
    if (!std::isfinite(amplitude) || amplitude <= 0 || amplitude > 1) return invalid();
    std::lock_guard lock(mMutex);
    if (mGainFd < 0) return unsupported();
    // setAmplitude applies only to on(), including a future on(). Changing
    // sysfs gain during a predefined effect would corrupt its strength.
    if (mActiveConstant && !setGain(amplitude)) return failed();
    mAmplitude = amplitude;
    return ndk::ScopedAStatus::ok();
}
// Alioth's stock service rejects compose() (ELF offset 0xcb98). Its RAM
// bank and MIUI UI IDs do not contain an Android primitive calibration map.
// Do not advertise generated substitutes as production primitive support.
ndk::ScopedAStatus Vibrator::getCompositionDelayMax(int32_t* out) {
    *out = 0;
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getCompositionSizeMax(int32_t* out) {
    *out = 0;
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getSupportedPrimitives(std::vector<CompositePrimitive>* out) {
    out->clear();
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::getPrimitiveDuration(CompositePrimitive, int32_t* out) {
    *out = 0;
    return unsupported();
}
ndk::ScopedAStatus Vibrator::compose(const std::vector<CompositeEffect>&,
                                     const std::shared_ptr<IVibratorCallback>&) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::setExternalControl(bool) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getSupportedAlwaysOnEffects(std::vector<Effect>*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::alwaysOnEnable(int32_t, Effect, EffectStrength) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::alwaysOnDisable(int32_t) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getResonantFrequency(float* out) {
    if (mResonantFrequency <= 0) return unsupported();
    *out = mResonantFrequency;
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::getQFactor(float*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getFrequencyResolution(float*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getFrequencyMinimum(float*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getBandwidthAmplitudeMap(std::vector<float>*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getPwlePrimitiveDurationMax(int32_t*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getPwleCompositionSizeMax(int32_t*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::getSupportedBraking(std::vector<Braking>*) {
    return unsupported();
}
ndk::ScopedAStatus Vibrator::composePwle(const std::vector<PrimitivePwle>&,
                                         const std::shared_ptr<IVibratorCallback>&) {
    return unsupported();
}
}  // namespace aidl::android::hardware::vibrator
