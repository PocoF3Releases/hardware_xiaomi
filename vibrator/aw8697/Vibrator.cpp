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
                unsigned int maximum = 0;
                const char* maxField = strstr(info, "max_size=");
                if (strstr(info, "abi_version=2;") && strstr(info, "playback_active=") &&
                    strstr(info, "custom_wave_id=193;") && maxField &&
                    sscanf(maxField, "max_size=%u;", &maximum) == 1 &&
                    maximum >= 4096 && period > 1 && period <= 2048) {
                    mWavePeriod = period;
                }
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
    if (mWavePeriod) {
        mWaveFd = open("/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/custom_wave",
                       O_WRONLY | O_CLOEXEC);
        mWaveStatusFd = open("/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/custom_wave",
                             O_RDONLY | O_CLOEXEC);
        if (mWaveFd >= 0 && mWaveStatusFd >= 0) {
            for (size_t i = 0; i < mEffectStreams.size(); ++i) {
                const auto* stream = get_effect_stream_exact(static_cast<uint32_t>(kEffects[i].effect));
                if (streamDuration(stream)) mEffectStreams[i] = stream;
            }
            for (size_t i = 1; i < mPrimitiveStreams.size(); ++i) {
                const auto* stream = get_effect_stream_exact((1u << 15) | i);
                if (streamDuration(stream)) mPrimitiveStreams[i] = stream;
            }
        }
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
    if (mWaveFd >= 0) close(mWaveFd);
    if (mWaveStatusFd >= 0) close(mWaveStatusFd);
}
// Bound finite streams to ten seconds. Refills never exceed available ring space.
int Vibrator::streamDuration(const effect_stream* stream) const {
    if (!mWavePeriod || !stream || !stream->data || stream->play_rate_hz != 24000 ||
        stream->length == 0 || stream->length > 240000) return 0;
    // One trailing zero is added only to signal EOF for period-aligned payloads.
    const unsigned int length = stream->length + (stream->length % mWavePeriod == 0);
    return (length + 23) / 24; // Nominal duration; completion follows hardware state.
}
int Vibrator::waveActive() {
    char info[256]{};
    if (lseek(mWaveStatusFd, 0, SEEK_SET) < 0) return -1;
    if (TEMP_FAILURE_RETRY(read(mWaveStatusFd, info, sizeof(info) - 1)) <= 0) return -1;
    const char* field = strstr(info, "playback_active=");
    int active = -1;
    if (field) sscanf(field, "playback_active=%d;", &active);
    return active;
}
bool Vibrator::writeWave(const effect_stream& stream, float scale, size_t* offset) {
    // This worker is the sole producer. The consumer can only increase free space.
    char info[256]{};
    if (lseek(mWaveStatusFd, 0, SEEK_SET) < 0 ||
        TEMP_FAILURE_RETRY(read(mWaveStatusFd, info, sizeof(info) - 1)) <= 0) return false;
    unsigned int available = 0;
    const char* field = strstr(info, "free_size=");
    if (!field || sscanf(field, "free_size=%u;", &available) != 1) return false;
    const size_t total = stream.length + (stream.length % mWavePeriod == 0);
    if (*offset >= total) return *offset == total;
    // A short or unaligned write signals EOF, so intermediate writes are aligned.
    const size_t chunkLimit = (4095 / mWavePeriod) * mWavePeriod;
    size_t count = std::min(total - *offset, chunkLimit);
    if (count > available) count = (available / mWavePeriod) * mWavePeriod;
    if (!count) return true;
    std::vector<int8_t> samples(count, 0);
    for (size_t i = 0; i < count && *offset + i < stream.length; ++i)
        samples[i] = static_cast<int8_t>(std::lround(stream.data[*offset + i] * scale));
    if (lseek(mWaveFd, 0, SEEK_SET) < 0 ||
        TEMP_FAILURE_RETRY(write(mWaveFd, samples.data(), count)) !=
                static_cast<ssize_t>(count)) return false;
    *offset += count;
    return true;
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
            size_t waveOffset = 0;
            if (s.delay > 0 && !wait(s.delay)) {
                break;
            }
            if (s.scale > 0 && s.duration > 0) {
                mActiveConstant = s.effect < 0;
                if (!upload(s.stream ? 193 : s.effect, s.duration, s.stream ? 1 : s.scale, nullptr) ||
                    (s.stream && !writeWave(*s.stream, s.scale, &waveOffset)) ||
                    !setGain(s.effect < 0 ? mAmplitude : 1.0f) || !event(mEffect, 1)) {
                    ALOGE("AW8697 effect playback failed: %s", strerror(errno));
                    stopLocked();
                    break;
                }
            }
            if (s.stream && s.scale > 0) {
                const auto deadline = std::chrono::steady_clock::now() +
                        std::chrono::milliseconds(s.duration + 2000);
                const size_t total = s.stream->length +
                        (s.stream->length % mWavePeriod == 0);
                bool failed = false;
                while (std::chrono::steady_clock::now() < deadline) {
                    const int active = waveActive();
                    if (active < 0 || (!active && waveOffset < total)) {
                        failed = true;
                        break;
                    }
                    if (waveOffset == total && !active) break;
                    if (waveOffset < total && !writeWave(*s.stream, s.scale, &waveOffset)) {
                        failed = true;
                        break;
                    }
                    if (!wait(2)) break;
                }
                if (mExit || generation != mGeneration) break;
                failed |= waveOffset < total || waveActive() != 0;
                if (failed) {
                    ALOGE("AW8697 RTP refill, completion or status failure");
                    stopLocked();
                    break;
                }
            } else if (!wait(s.duration)) {
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
    *out |= CAP_COMPOSE_EFFECTS;
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
    const auto* stream = mEffectStreams[std::distance(kEffects.begin(), mapping)];
    if (stream) {
        const int duration = streamDuration(stream);
        auto status = start({{193, duration, scale, 0, stream}}, cb);
        if (status.isOk()) *out = duration;
        return status;
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
// LOW_TICK uses the stock light tick as a compatibility fallback.
// An exact LOW_TICK file takes precedence; this does not synthesize a new frequency.
namespace {
int primitiveRam(CompositePrimitive primitive) {
    switch (primitive) {
        case CompositePrimitive::CLICK: return 0;
        case CompositePrimitive::LIGHT_TICK:
        case CompositePrimitive::LOW_TICK: return 2;
        case CompositePrimitive::THUD: return 3;
        default: return -1;
    }
}
}
ndk::ScopedAStatus Vibrator::getCompositionDelayMax(int32_t* out) {
    *out = 1000;
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::getCompositionSizeMax(int32_t* out) {
    *out = 256;
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::getSupportedPrimitives(std::vector<CompositePrimitive>* out) {
    out->clear();
    out->push_back(CompositePrimitive::NOOP);
    for (size_t i = 1; i < mPrimitiveStreams.size(); ++i) {
        auto primitive = static_cast<CompositePrimitive>(i);
        if (mPrimitiveStreams[i] || primitiveRam(primitive) >= 0) out->push_back(primitive);
    }
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::getPrimitiveDuration(CompositePrimitive primitive, int32_t* out) {
    *out = 0;
    const int id = static_cast<int>(primitive);
    if (id < 0 || id >= static_cast<int>(mPrimitiveStreams.size())) return unsupported();
    if (primitive == CompositePrimitive::NOOP) return ndk::ScopedAStatus::ok();
    const auto* stream = mPrimitiveStreams[id];
    if (!stream && primitive == CompositePrimitive::LOW_TICK)
        stream = mPrimitiveStreams[static_cast<int>(CompositePrimitive::LIGHT_TICK)];
    if (stream) *out = streamDuration(stream);
    else if (const int ram = primitiveRam(primitive); ram >= 0) *out = mDurations[ram];
    else return unsupported();
    return ndk::ScopedAStatus::ok();
}
ndk::ScopedAStatus Vibrator::compose(const std::vector<CompositeEffect>& composite,
                                     const std::shared_ptr<IVibratorCallback>& callback) {
    if (composite.empty() || composite.size() > 256) return invalid();
    std::vector<Step> steps;
    for (const auto& effect : composite) {
        if (effect.delayMs < 0 || effect.delayMs > 1000 || !std::isfinite(effect.scale) ||
            effect.scale < 0 || effect.scale > 1) return invalid();
        int duration;
        auto status = getPrimitiveDuration(effect.primitive, &duration);
        if (!status.isOk()) return status;
        const auto* stream = mPrimitiveStreams[static_cast<int>(effect.primitive)];
        if (!stream && effect.primitive == CompositePrimitive::LOW_TICK)
            stream = mPrimitiveStreams[static_cast<int>(CompositePrimitive::LIGHT_TICK)];
        steps.push_back({stream ? 193 : primitiveRam(effect.primitive), duration,
                         effect.scale, effect.delayMs, stream});
    }
    return start(std::move(steps), callback);
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
