/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.dspvolume.xiaomi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.IBinder;
import android.util.Log;

public class VolumeListenerService extends Service {
    private static final String TAG = "DSPVolumeSynchronizer";
    private final VolumeListenerReceiver mReceiver = new VolumeListenerReceiver();
    private AudioManager mAudioManager;
    private boolean mServerCallbackRegistered;
    private boolean mDestroyed;

    private final AudioManager.AudioServerStateCallback mServerCallback =
            new AudioManager.AudioServerStateCallback() {
                @Override
                public void onAudioServerUp() {
                    if (!mDestroyed) VolumeListenerReceiver.syncVolume(mAudioManager);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mAudioManager = getSystemService(AudioManager.class);
        // Once per service lifetime, not once per onStartCommand/sticky restart.
        registerReceiver(mReceiver, new IntentFilter(VolumeListenerReceiver.ACTION_VOLUME_CHANGED),
                Context.RECEIVER_NOT_EXPORTED);
        if (mAudioManager != null) {
            try {
                // This platform-signed service already runs as android.uid.system.
                mAudioManager.setAudioServerStateCallback(getMainExecutor(), mServerCallback);
                mServerCallbackRegistered = true;
            } catch (RuntimeException error) {
                Log.w(TAG, "Cannot monitor audio server; retaining volume broadcasts", error);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        VolumeListenerReceiver.syncVolume(mAudioManager);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        unregisterReceiver(mReceiver);
        if (mServerCallbackRegistered) {
            try {
                mAudioManager.clearAudioServerStateCallback();
            } catch (RuntimeException error) {
                Log.w(TAG, "Cannot unregister audio server callback", error);
            }
            mServerCallbackRegistered = false;
        }
        super.onDestroy();
    }
}
