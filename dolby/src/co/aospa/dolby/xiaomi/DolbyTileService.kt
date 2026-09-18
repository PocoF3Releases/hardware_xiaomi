/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class DolbyTileService : TileService() {
    private val controller by lazy { DolbyController.getInstance(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            controller.activeState.collectLatest { configuration ->
                qsTile?.apply {
                    state = if (!configuration.loaded) Tile.STATE_UNAVAILABLE
                        else if (configuration.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    subtitle = configuration.name
                    updateTile()
                }
            }
        }
        controller.requestRefresh()
    }
    override fun onStopListening() { listening?.cancel(); super.onStopListening() }
    override fun onClick() {
        super.onClick()
        scope.launch {
            try { controller.toggleEnabled() }
            catch (_: RuntimeException) { controller.requestRefresh() }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
