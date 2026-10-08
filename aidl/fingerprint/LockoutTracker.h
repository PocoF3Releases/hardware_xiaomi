/*
 * Copyright (C) 2022 The Android Open Source Project
 *               2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <mutex>
#include <sstream>
#include <stdint.h>
#include <string>

#define LOCKOUT_TIMED_THRESHOLD 5
#define LOCKOUT_TIMED_DURATION 10000
#define LOCKOUT_PERMANENT_THRESHOLD 20

namespace aidl::android::hardware::biometrics::fingerprint {

class LockoutTracker {
  public:
    LockoutTracker() : mFailedCount(0), mLockoutTimedStart(0), mCurrentMode(LockoutMode::kNone) {}
    ~LockoutTracker() {}

    enum class LockoutMode : int8_t { kNone = 0, kTimed, kPermanent };

    void reset(bool clearAttemptCounter);
    LockoutMode getMode();
    void addFailedAttempt();
    int64_t getLockoutTimeLeft();
    struct State { LockoutMode mode; int64_t timeLeft; uint64_t generation; };
    State getState();
    bool expireTimedLockout(uint64_t generation);
    inline std::string toString() const {
        std::lock_guard lock(mMutex);
        std::ostringstream os;
        os << "----- LockoutTracker:: -----" << std::endl;
        os << "LockoutTracker::mFailedCount:" << mFailedCount;
        os << ", LockoutTracker::mCurrentMode:" << (int)mCurrentMode;
        os << std::endl;
        return os.str();
    }

  private:
    mutable std::mutex mMutex;
    uint64_t mGeneration = 1;
    int32_t mFailedCount;
    int64_t mLockoutTimedStart;
    LockoutMode mCurrentMode;
};

}  // namespace aidl::android::hardware::biometrics::fingerprint
