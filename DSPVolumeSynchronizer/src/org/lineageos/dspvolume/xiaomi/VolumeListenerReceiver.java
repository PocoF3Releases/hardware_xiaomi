/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.dspvolume.xiaomi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

public class VolumeListenerReceiver extends BroadcastReceiver {
    static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String TAG = "DSPVolumeSynchronizer";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !ACTION_VOLUME_CHANGED.equals(intent.getAction())
                || intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) {
            return;
        }
        syncVolume(context.getSystemService(AudioManager.class));
    }

    static void syncVolume(AudioManager audioManager) {
        if (audioManager == null) return;
        try {
            // Read the current route's index; a queued broadcast may already be stale.
            // A missing EXTRA_VOLUME_STREAM_VALUE must not be interpreted as mute.
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (current < 0) return;
            audioManager.setParameters("volume_change=" + current + ";flags=8");
        } catch (RuntimeException error) {
            // Audio service restart must not crash the persistent helper.
            Log.w(TAG, "Cannot synchronize DSP media volume", error);
        }
    }
}
