/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "CancellationSignal.h"

namespace aidl::android::hardware::biometrics::fingerprint {

CancellationSignal::CancellationSignal(const std::shared_ptr<Session>& session) : mSession(session) {}

ndk::ScopedAStatus CancellationSignal::cancel() {
    auto session = mSession.lock();
    if (!session || session->isClosed()) return ndk::ScopedAStatus::ok();
    return session->cancel();
}

}  // namespace aidl::android::hardware::biometrics::fingerprint
