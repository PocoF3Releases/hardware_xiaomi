// SPDX-License-Identifier: Apache-2.0
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <cstdlib>
#include "Vibrator.h"
using aidl::android::hardware::vibrator::Vibrator;
int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    ABinderProcess_startThreadPool();
    auto vibrator = ndk::SharedRefBase::make<Vibrator>();
    if (!vibrator->ready()) return EXIT_FAILURE;
    if (AServiceManager_addService(vibrator->asBinder().get(),
                                   "android.hardware.vibrator.IVibrator/default") != STATUS_OK)
        return EXIT_FAILURE;
    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;
}
