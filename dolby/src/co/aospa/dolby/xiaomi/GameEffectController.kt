/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioSystem
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemProperties
import android.util.Log
import android.view.Display
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class GameEffectTuning(
    val lowFrequency: Int,
    val vocal: Int,
    val footstep: Int,
    val soundField: Int
) {
    init {
        require(listOf(lowFrequency, vocal, footstep, soundField).all { it in 0..100 }) {
            "Game effect values must be in the HyperOS 0..100 range"
        }
    }

    fun encode(): String = listOf(lowFrequency, vocal, footstep, soundField).joinToString(",")

    companion object {
        fun decode(value: String?): GameEffectTuning? {
            val values = value?.split(",")?.mapNotNull { it.toIntOrNull() } ?: return null
            if (values.size != 4 || values.any { it !in 0..100 }) return null
            return GameEffectTuning(values[0], values[1], values[2], values[3])
        }
    }
}

internal data class GameEffectState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val games: Set<String> = emptySet(),
    val activePackage: String? = null,
    val error: Boolean = false
)

internal class GameEffectController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)!!
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val supported = SystemProperties.getBoolean(PROP_GAME_EFFECT, false)

    private val worker = HandlerThread("XiaomiGameEffect").apply { start() }
    private val handler = Handler(worker.looper)
    private val started = AtomicBoolean(false)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private val _state = MutableStateFlow(GameEffectState())
    val state = _state.asStateFlow()

    private var appliedPackage: String? = null
    private var appliedTuning: GameEffectTuning? = null

    private val taskListener = object : TaskStackListener() {
        override fun onTaskStackChanged() = scheduleReconcile(SETTLE_DELAY_MS)
        override fun onTaskMovedToFront(taskInfo: android.app.ActivityManager.RunningTaskInfo) =
            scheduleReconcile(SETTLE_DELAY_MS)
        override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
            if (focused) scheduleReconcile(SETTLE_DELAY_MS)
        }
    }

    private val routeListener = AudioManager.OnDevicesForAttributesChangedListener { _, _ ->
        scheduleReconcile(SETTLE_DELAY_MS)
    }

    private val audioServerCallback = object : AudioManager.AudioServerStateCallback() {
        override fun onAudioServerDown() {
            handler.post {
                appliedPackage = null
                appliedTuning = null
                publish(error = false)
            }
        }

        override fun onAudioServerUp() {
            scheduleReconcile(AUDIO_SERVER_RECOVERY_DELAY_MS)
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        handler.post {
            publish(error = false)
            if (!supported) return@post

            try {
                ActivityTaskManager.getService().registerTaskStackListener(taskListener)
            } catch (error: Exception) {
                Log.e(TAG, "Cannot register Android 17 task listener", error)
                publish(error = true)
                return@post
            }

            try {
                audioManager.addOnDevicesForAttributesChangedListener(
                    mediaAttributes, appContext.mainExecutor, routeListener)
            } catch (error: RuntimeException) {
                Log.e(TAG, "Cannot monitor routed media devices", error)
                runCatching {
                    ActivityTaskManager.getService().unregisterTaskStackListener(taskListener)
                }
                publish(error = true)
                return@post
            }

            try {
                audioManager.setAudioServerStateCallback(
                    appContext.mainExecutor, audioServerCallback)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Cannot monitor audio-server restarts", error)
            }

            scheduleReconcile(SETTLE_DELAY_MS)
        }
    }

    fun stockGames(): Set<String> = STOCK_TUNINGS.keys

    fun games(): Set<String> =
        prefs.getStringSet(KEY_GAMES, null)?.toSet() ?: STOCK_TUNINGS.keys

    fun isEnabled(): Boolean = supported && prefs.getBoolean(KEY_ENABLED, true)

    fun tuning(packageName: String): GameEffectTuning =
        GameEffectTuning.decode(prefs.getString(tuningKey(packageName), null))
            ?: STOCK_TUNINGS[packageName]
            ?: DEFAULT_GAME_TUNING

    fun defaultTuning(packageName: String): GameEffectTuning =
        STOCK_TUNINGS[packageName] ?: DEFAULT_GAME_TUNING

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        handler.post {
            if (!supported) {
                publish(error = false)
                return@post
            }
            if (!value) {
                handler.removeCallbacks(reconcileRunnable)
                clearEffect(force = true)
            } else {
                scheduleReconcile(0)
            }
            publish(error = false)
        }
    }

    fun setGameEnabled(packageName: String, value: Boolean) {
        require(PACKAGE_PATTERN.matches(packageName)) { "Invalid package name" }
        val games = games().toMutableSet()
        if (value) games.add(packageName) else games.remove(packageName)
        prefs.edit().putStringSet(KEY_GAMES, games).apply()
        scheduleReconcile(0)
        publish(error = false)
    }

    fun setTuning(packageName: String, tuning: GameEffectTuning) {
        require(PACKAGE_PATTERN.matches(packageName)) { "Invalid package name" }
        prefs.edit().putString(tuningKey(packageName), tuning.encode()).apply()
        scheduleReconcile(0)
        publish(error = false)
    }

    fun resetTuning(packageName: String) {
        prefs.edit().remove(tuningKey(packageName)).apply()
        scheduleReconcile(0)
        publish(error = false)
    }

    private fun scheduleReconcile(delayMs: Long) {
        if (!supported) return
        handler.removeCallbacks(reconcileRunnable)
        handler.postDelayed(reconcileRunnable, delayMs)
    }

    private val reconcileRunnable = Runnable {
        val foreground = foregroundPackage()
        val target = foreground?.takeIf {
            isEnabled() && it in games() && isSupportedRoute()
        }
        if (target == null) {
            clearEffect(force = appliedPackage != null)
            publish(error = false)
            return@Runnable
        }

        val tuning = tuning(target)
        if (appliedPackage == target && appliedTuning == tuning) {
            publish(error = false)
            return@Runnable
        }

        try {
            applyTuning(tuning)
            appliedPackage = target
            appliedTuning = tuning
            publish(error = false)
            Log.i(TAG, "Applied HyperOS game audio for $target: $tuning")
        } catch (error: RuntimeException) {
            Log.e(TAG, "Cannot apply HyperOS game audio for $target", error)
            clearEffect(force = true)
            publish(error = true)
        }
    }

    private fun foregroundPackage(): String? = try {
        ActivityTaskManager.getService()
            .getTasks(1, false, false, Display.DEFAULT_DISPLAY)
            .firstOrNull()
            ?.let { it.topActivity ?: it.baseActivity }
            ?.packageName
    } catch (error: Exception) {
        Log.w(TAG, "Cannot resolve foreground task", error)
        null
    }

    private fun isSupportedRoute(): Boolean {
        val devices = try {
            audioManager.getAudioDevicesForAttributes(mediaAttributes)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot query routed media output", error)
            return false
        }
        return devices.any { it.type in SUPPORTED_DEVICE_TYPES }
    }

    private fun applyTuning(tuning: GameEffectTuning) {
        val values = intArrayOf(
            tuning.lowFrequency, tuning.vocal, tuning.footstep, tuning.soundField)
        try {
            PARAMETER_NAMES.forEachIndexed { index, key ->
                writeParameter(key, values[index])
            }
        } catch (failure: RuntimeException) {
            try {
                clearParameters()
            } catch (cleanup: RuntimeException) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    private fun clearEffect(force: Boolean) {
        if (!force && appliedPackage == null && appliedTuning == null) return
        try {
            clearParameters()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot clear all HyperOS game-audio parameters", error)
        } finally {
            appliedPackage = null
            appliedTuning = null
        }
    }

    private fun clearParameters() {
        var failure: RuntimeException? = null
        for (key in PARAMETER_NAMES) {
            try {
                writeParameter(key, 0)
            } catch (error: RuntimeException) {
                val first = failure
                if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun writeParameter(key: String, value: Int) {
        require(value in 0..100)
        val command = "AURISYS_SET_PARAM,HAL,ALL,FPSOP,KEY_VALUE,$key,$value=SET"
        val status = AudioSystem.setParameters(command)
        check(status == AudioSystem.AUDIO_STATUS_OK) {
            "AudioSystem rejected $key=$value with status $status"
        }
    }

    private fun publish(error: Boolean) {
        _state.value = GameEffectState(
            supported = supported,
            enabled = isEnabled(),
            games = games(),
            activePackage = appliedPackage,
            error = error
        )
    }

    private fun tuningKey(packageName: String) = "tuning_$packageName"

    companion object {
        private const val TAG = "XiaomiGameEffect"
        private const val PROP_GAME_EFFECT = "ro.vendor.audio.game.effect"
        private const val PREFS = "game_effect"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_GAMES = "games"
        private const val SETTLE_DELAY_MS = 2_000L
        private const val AUDIO_SERVER_RECOVERY_DELAY_MS = 1_000L

        private val PACKAGE_PATTERN =
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        private val PARAMETER_NAMES = arrayOf(
            "FpsOpLowFreqBoost",
            "FpsOpVocalBoost",
            "FpsOpFootStepBoost",
            "FpsOpSoundFieldBoost"
        )
        private val SUPPORTED_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )

        private val DEFAULT_GAME_TUNING = GameEffectTuning(100, 0, 100, 100)
        private val STOCK_TUNINGS = linkedMapOf(
            "com.tencent.tmgp.pubgmhd" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.tmgp.cf" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.mf.uam" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.toaa" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.tmgp.cod" to GameEffectTuning(100, 0, 100, 100),
            "com.miHoYo.ys.mi" to GameEffectTuning(100, 100, 0, 0),
            "com.tencent.tmgp.sgame" to GameEffectTuning(100, 100, 0, 0)
        )

        @Volatile private var instance: GameEffectController? = null

        fun getInstance(context: Context): GameEffectController =
            instance ?: synchronized(this) {
                instance ?: GameEffectController(context).also { instance = it }
            }
    }
}
