/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import co.aospa.dolby.xiaomi.geq.data.BandGain
import co.aospa.dolby.xiaomi.geq.data.EqualizerGains
import co.aospa.dolby.xiaomi.profiles.ActiveProfileState
import co.aospa.dolby.xiaomi.profiles.DolbyProfiles
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-lifetime audio owner. No native transport is reachable outside a transaction. */
internal class DolbyController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Callbacks carry invalidations, not state snapshots. Keep one pending refresh
    // while the current transaction runs; never cancel native effect work midway.
    private val restoreRequests = Channel<Unit>(Channel.CONFLATED)
    private var delayedRestoreJob: Job? = null
    private val restoreRevision = AtomicLong()
    private val mutex = Mutex()
    private val selectionMutex = Mutex()
    private val pendingEdits = ConcurrentHashMap.newKeySet<CompletableDeferred<Unit>>()
    private val state = MutableStateFlow(ActiveProfileState())
    val activeState = state.asStateFlow()
    val profiles = DolbyProfiles(context)
    private val engine = scope.async(start = CoroutineStart.LAZY) {
        mutex.withLock { DolbyEngine(context, profiles, state, ::requestRestore) }
    }

    init {
        engine.invokeOnCompletion { error ->
            if (error != null) state.value = state.value.copy(loaded = false,
                error = context.getString(R.string.dolby_setting_failed))
        }
        engine.start()
        requestRestore()
        scope.launch {
            val retry = DolbyRetryPolicy()
            var handledRevision = -1L
            for (request in restoreRequests) {
                val revision = restoreRevision.get()
                if (revision != handledRevision) {
                    retry.reset()
                    handledRevision = revision
                }
                // Audio callbacks are invalidations, not stable-state notifications.
                // Cancel only a pending delay; never cancel native effect work in flight.
                delayedRestoreJob?.cancel()
                delayedRestoreJob = null
                val delayMs = try {
                    val quietWindow = transaction(recoverOnFailure = false) { restoreForAudioState() }
                    retry.reset()
                    quietWindow
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: RuntimeException) {
                    publishFailure(error)
                    retry.nextDelay()
                }
                delayMs?.let { retryAfter ->
                    delayedRestoreJob = scope.launch {
                        delay(retryAfter)
                        restoreRequests.trySend(Unit) // Timer does not renew the retry budget.
                    }
                }
            }
        }
    }

    // The lock spans reads, mutations, the full native payload, persistence and StateFlow publication.
    private suspend fun <T> transaction(
        recoverOnFailure: Boolean = true,
        initialize: Boolean = true,
        action: DolbyEngine.() -> T
    ): T = withContext(Dispatchers.IO) {
        val target = engine.await()
        mutex.withLock {
            try {
                if (initialize) target.ensureInitialized()
                target.action()
            } catch (error: RuntimeException) {
                if (recoverOnFailure && error !is CancellationException) requestRestore()
                throw error
            }
        }
    }
    val dsOn: Boolean get() = activeState.value.enabled
    val activeProfileKey: String get() = activeState.value.key
    fun getProfileName(): String = activeState.value.name
    suspend fun awaitReady() { transaction { refreshActiveState() } }
    suspend fun onBootCompleted() = transaction { onBootCompleted() }
    /** Register completed gestures before enqueueing them, so navigation can drain their writes. */
    fun beginEqualizerEdit(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { completion ->
        pendingEdits.add(completion)
        completion.invokeOnCompletion { pendingEdits.remove(completion) }
    }
    suspend fun selectProfile(key: String) = scope.async(start = CoroutineStart.UNDISPATCHED) {
        // Preserve tap order before dispatching native work. Navigating away must not cancel
        // an accepted selection or allow an older request to replace a newer selection.
        selectionMutex.withLock {
            pendingEdits.toList().forEach { it.await() }
            transaction { selectProfile(key) }
        }
    }.await()
    suspend fun saveEqualizer(key: String, value: String) = transaction { saveEqualizer(key, value) }
    suspend fun editEqualizer(key: String, transform: (List<BandGain>) -> List<BandGain>):
        Pair<List<BandGain>, List<BandGain>>? = transaction {
        if (activeProfileKey != key) return@transaction null
        val before = EqualizerGains.decode(getSavedPreset(key))
        val after = transform(before)
        val payload = EqualizerGains.encode(after)
        saveEqualizer(key, payload)
        before to after
    }
    // The engine records Off before initialization/native work. Keeping these
    // actions inside the same mutex preserves gesture order and error reporting.
    suspend fun setDsOnAndPersist(enabled: Boolean) = transaction(initialize = false) {
        setDsOnAndPersist(enabled)
    }
    suspend fun toggleEnabled() = transaction(initialize = false) { setDsOnAndPersist(!dsOn) }
    suspend fun updateSetting(key: String, value: Any) = transaction { updateSetting(key, value) }
    suspend fun toggleSetting(key: String) = transaction {
        refreshActiveState()
        updateSetting(key, !(activeState.value.settings[key] as? Boolean ?: false))
    }
    suspend fun setSpeakerTuning(tuning: DolbyEndpointPolicy.SpeakerTuning) =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            selectionMutex.withLock { transaction { setSpeakerTuning(tuning) } }
        }.await()
    suspend fun resetProfileSpecificSettings() = transaction { resetProfileSpecificSettings() }
    suspend fun resetAllProfiles() = transaction { resetAllProfiles() }
    suspend fun refreshActiveState() = transaction { refreshActiveState() }
    suspend fun deleteNamedProfile(key: String) = transaction { deleteNamedProfile(key) }
    suspend fun createNamedProfile(name: String, base: Int) = transaction { profiles.create(name, base) }
    suspend fun renameNamedProfile(key: String, name: String) = transaction { profiles.rename(key, name) }
    fun requestRefresh() { requestRestore() }
    private fun requestRestore() {
        restoreRevision.incrementAndGet()
        restoreRequests.trySend(Unit)
    }
    private fun publishFailure(error: RuntimeException) {
        Log.w("DolbyController", "Audio transaction failed", error)
        state.value = state.value.copy(
            loaded = false,
            error = context.getString(R.string.dolby_setting_failed),
            runtime = state.value.runtime.copy(nativeEnabled = null,
                frameworkEnabled = null, hasControl = false, acknowledgedTuning = null)
        )
    }
    companion object {
        @Volatile private var instance: DolbyController? = null
        fun getInstance(context: Context): DolbyController = instance ?: synchronized(this) {
            instance ?: DolbyController(context.applicationContext).also { instance = it }
        }
    }
}
