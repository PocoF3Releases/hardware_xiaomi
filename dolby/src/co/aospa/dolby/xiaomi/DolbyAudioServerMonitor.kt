/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.Executor

/** AudioManager accepts only one server callback per instance; DAP and FPSOP share it. */
internal class DolbyAudioServerMonitor private constructor(
    private val audioManager: AudioManager,
    private val executor: Executor
) {
    private val lock = Any()
    private val listeners = linkedSetOf<AudioManager.AudioServerStateCallback>()
    private var registered = false
    private val dispatcher = object : AudioManager.AudioServerStateCallback() {
        override fun onAudioServerDown() = dispatch { onAudioServerDown() }
        override fun onAudioServerUp() = dispatch { onAudioServerUp() }
    }

    fun subscribe(listener: AudioManager.AudioServerStateCallback) = synchronized(lock) {
        if (!listeners.add(listener)) return@synchronized
        if (!registered) {
            try {
                audioManager.setAudioServerStateCallback(executor, dispatcher)
                registered = true
            } catch (error: RuntimeException) {
                listeners.remove(listener)
                throw error
            }
        }
    }

    fun unsubscribe(listener: AudioManager.AudioServerStateCallback) = synchronized(lock) {
        if (!listeners.remove(listener)) return@synchronized
        if (listeners.isEmpty() && registered) {
            try {
                audioManager.clearAudioServerStateCallback()
                registered = false
            } catch (error: RuntimeException) {
                listeners.add(listener)
                throw error
            }
        }
    }

    private fun dispatch(event: AudioManager.AudioServerStateCallback.() -> Unit) {
        // No controller/native calls while holding the subscription lock.
        val snapshot = synchronized(lock) { listeners.toList() }
        for (listener in snapshot) {
            try { listener.event() }
            catch (error: RuntimeException) { Log.w("XiaomiDolby", "Server observer failed", error) }
        }
    }

    companion object {
        private val instances = WeakHashMap<AudioManager, WeakReference<DolbyAudioServerMonitor>>()
        fun get(context: Context): DolbyAudioServerMonitor = synchronized(instances) {
            val app = context.applicationContext
            val manager = app.getSystemService(AudioManager::class.java)!!
            instances[manager]?.get() ?: DolbyAudioServerMonitor(manager, app.mainExecutor).also {
                instances[manager] = WeakReference(it)
            }
        }
    }
}
