/*
 * Copyright (C) 2022 The Android Open Source Project
 *               2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "LockoutTracker.h"
#include "util/Util.h"

namespace aidl::android::hardware::biometrics::fingerprint {

void LockoutTracker::reset(bool clearAttemptCounter) {
    std::lock_guard lock(mMutex);
    ++mGeneration;
    if (clearAttemptCounter) {
        mFailedCount = 0;
    }
    mLockoutTimedStart = 0;
    mCurrentMode = LockoutMode::kNone;
}

void LockoutTracker::addFailedAttempt() {
    std::lock_guard lock(mMutex);
    if (mCurrentMode != LockoutMode::kNone) return;
    if (mFailedCount < LOCKOUT_PERMANENT_THRESHOLD) ++mFailedCount;
    if (mFailedCount >= LOCKOUT_PERMANENT_THRESHOLD) {
        mCurrentMode = LockoutMode::kPermanent;
    } else if (mFailedCount >= LOCKOUT_TIMED_THRESHOLD) {
        if (mCurrentMode == LockoutMode::kNone) {
            ++mGeneration;
            mCurrentMode = LockoutMode::kTimed;
            mLockoutTimedStart = Util::getSystemNanoTime();
        }
    }
}

LockoutTracker::State LockoutTracker::getState() {
    std::lock_guard lock(mMutex);
    int64_t remaining = 0;
    if (mCurrentMode == LockoutMode::kTimed) {
        // Round up so the timer cannot wake before the actual deadline.
        const auto elapsed = (Util::getSystemNanoTime() - mLockoutTimedStart) / 1000000LL;
        remaining = LOCKOUT_TIMED_DURATION - elapsed;
        if (remaining <= 0) {
            mCurrentMode = LockoutMode::kNone;
            mLockoutTimedStart = 0;
            remaining = 0;
        }
    }
    return {mCurrentMode, remaining, mGeneration};
}

LockoutTracker::LockoutMode LockoutTracker::getMode() {
    return getState().mode;
}

int64_t LockoutTracker::getLockoutTimeLeft() {
    return getState().timeLeft;
}

bool LockoutTracker::expireTimedLockout(uint64_t generation) {
    std::lock_guard lock(mMutex);
    // Reset, success, or a subsequent timed lockout invalidates older timers.
    // getState may already have observed expiry; still deliver this generation's callback.
    if (generation != mGeneration || mCurrentMode == LockoutMode::kPermanent) return false;
    if (mCurrentMode == LockoutMode::kTimed &&
        Util::getSystemNanoTime() - mLockoutTimedStart <
                LOCKOUT_TIMED_DURATION * 1000000LL) return false;
    mCurrentMode = LockoutMode::kNone;
    mLockoutTimedStart = 0;
    return true;
}

}  // namespace aidl::android::hardware::biometrics::fingerprint
