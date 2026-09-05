/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

private const val TAG = "XiaomiDolby-Boot"

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val controller = DolbyController.getInstance(context)
                controller.awaitReady()
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) controller.onBootCompleted()
            } catch (error: RuntimeException) {
                Log.e(TAG, "Dolby initialization failed", error)
            } finally { pending.finish() }
        }
    }
}
