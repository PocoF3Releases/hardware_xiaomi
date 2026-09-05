/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.aospa.dolby.xiaomi.geq.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EqualizerViewModel(private val repository: EqualizerRepository) : ViewModel() {
    private val _presets = MutableStateFlow(repository.builtInPresets)
    val presets = _presets.asStateFlow()
    private val _preset = MutableStateFlow(repository.defaultPreset)
    val preset = _preset.asStateFlow()
    private val _undoPreset = MutableStateFlow<Preset?>(null)
    val undoPreset = _undoPreset.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val profileName = repository.activeState.map { it.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.activeState.value.name)
    val profileKey = repository.activeState.map { it.key }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.activeState.value.key)
    val ready = repository.activeState.map { it.loaded && it.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var key = repository.activeState.value.key
    private data class Edit(val key: String, val transform: (Preset) -> Preset,
        val rememberUndo: Boolean, val completion: CompletableDeferred<Unit>)
    private val edits = Channel<Edit>(Channel.UNLIMITED, onUndeliveredElement = { it.completion.complete(Unit) })

    init {
        viewModelScope.launch {
            repository.userPresets.collect { userPresets ->
                _presets.value = userPresets + repository.builtInPresets
                val state = repository.activeState.value
                val current = _preset.value
                if (state.loaded && current.bandGains == state.gains && !current.isMutated) {
                    _preset.value = _presets.value.firstOrNull { it.bandGains == state.gains }
                        ?: Preset(bandGains = state.gains)
                }
            }
        }
        viewModelScope.launch {
            repository.activeState.collect { state ->
                val changedProfile = key != state.key
                if (changedProfile) { key = state.key; _undoPreset.value = null }
                if (state.loaded && (changedProfile || _preset.value.bandGains != state.gains)) {
                    _preset.value = _presets.value.firstOrNull { it.bandGains == state.gains }
                        ?: Preset(bandGains = state.gains)
                }
                _error.value = state.error
            }
        }
        viewModelScope.launch {
            for (edit in edits) {
                try {
                    if (edit.key != repository.activeState.value.key) continue
                    val previous = _preset.value
                    var next = previous
                    val result = repository.editBandGains(edit.key) { currentGains ->
                        next = edit.transform(previous.copy(bandGains = currentGains))
                        next.bandGains
                    } ?: continue
                    if (edit.key != repository.activeState.value.key) continue
                    if (next.isUserDefined) {
                        next = next.copy(isMutated = false)
                        repository.addPreset(next)
                        // Make the saved revision available to the next queued selection,
                        // without waiting for the preference listener to deliver it.
                        _presets.value = listOf(next) + _presets.value.filterNot {
                            it.isUserDefined && it.name == next.name
                        }
                    }
                    // Preset persistence suspends; recheck ownership before publishing UI state.
                    if (edit.key != repository.activeState.value.key) continue
                    if (edit.rememberUndo) _undoPreset.value = previous.copy(bandGains = result.first)
                    _preset.value = next
                    _error.value = null
                } catch (cancel: CancellationException) { throw cancel }
                  catch (_: RuntimeException) {
                    // Keep the last accepted configuration; never crash a gesture coroutine.
                    _error.value = "apply"
                } finally { edit.completion.complete(Unit) }
            }
        }
    }

    private fun edit(rememberUndo: Boolean = true, transform: (Preset) -> Preset) {
        val state = repository.activeState.value
        if (ready.value && state.key == key) {
            val completion = repository.beginEdit()
            if (edits.trySend(Edit(state.key, transform, rememberUndo, completion)).isFailure) completion.complete(Unit)
        }
    }
    override fun onCleared() { edits.cancel(); super.onCleared() }

    private fun Preset.withGains(gains: List<BandGain>) =
        copy(name = if (isUserDefined) name else null, bandGains = gains, isMutated = true)

    fun setGain(index: Int, gain: Int) {
        if (index !in 0 until EqualizerGains.BAND_COUNT || gain !in -100..100) return
        edit { current -> current.withGains(current.bandGains.mapIndexed { i, band ->
            if (i == index) band.copy(gain = gain) else band
        }) }
    }
    fun shiftGains(amount: Int) = edit { current ->
        if (EqualizerGains.canShift(current.bandGains, amount))
            current.withGains(EqualizerGains.shift(current.bandGains, amount)) else current
    }
    fun undoGainEdit() {
        val undo = _undoPreset.value ?: return
        edit(false) { undo }
        _undoPreset.value = null
    }
    fun reset() = edit { current -> current.withGains(repository.defaultPreset.bandGains) }
    fun setPreset(preset: Preset) = edit {
        // A menu may have opened before the preceding edit finished saving.
        if (preset.isUserDefined) _presets.value.firstOrNull { it.isUserDefined && it.name == preset.name } ?: preset
        else preset
    }

    private fun validate(name: String, excluding: String? = null): PresetNameValidationError? =
        when {
            name.isBlank() || name.trim().length > 50 -> PresetNameValidationError.NAME_TOO_LONG
            _presets.value.any { it.name != excluding && it.name.equals(name.trim(), true) } ->
                PresetNameValidationError.NAME_EXISTS
            else -> null
        }
    fun createNewPreset(name: String): PresetNameValidationError? {
        validate(name)?.let { return it }
        edit { it.copy(name = name.trim(), isUserDefined = true, isMutated = false) }
        return null
    }
    fun renamePreset(preset: Preset, name: String): PresetNameValidationError? {
        validate(name, preset.name)?.let { return it }
        viewModelScope.launch {
            try {
                val updated = preset.copy(name = name.trim())
                repository.addPreset(updated)
                if (updated.name != preset.name) repository.removePreset(preset)
                if (_preset.value.name == preset.name) _preset.value = _preset.value.copy(name = updated.name)
            } catch (_: RuntimeException) { _error.value = "apply" }
        }
        return null
    }
    fun deletePreset(preset: Preset, shouldReset: Boolean = true) {
        viewModelScope.launch {
            try { repository.removePreset(preset) }
            catch (_: RuntimeException) { _error.value = "apply" }
        }
        if (shouldReset) setPreset(repository.defaultPreset)
    }
    companion object {
        val Factory = viewModelFactory {
            initializer { EqualizerViewModel(EqualizerRepository(
                this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            )) }
        }
    }
}
