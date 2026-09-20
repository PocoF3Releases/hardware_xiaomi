/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import kotlinx.coroutines.launch
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import co.aospa.dolby.xiaomi.geq.ui.EqualizerViewModel
import co.aospa.dolby.xiaomi.geq.ui.EqualizerScreen
import co.aospa.dolby.xiaomi.profiles.ProfileManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import co.aospa.dolby.xiaomi.ui.*
import com.android.settingslib.spa.framework.theme.settingsBackground

class DolbySettingsActivity : ComponentActivity() {
    private val equalizer: EqualizerViewModel by viewModels { EqualizerViewModel.Factory }
    private var page by mutableStateOf(DolbyPage.MAIN)
    internal fun showPage(value: DolbyPage) { page = value }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        page = DolbyPage.entries.firstOrNull { it.name == intent.getStringExtra("page") } ?: DolbyPage.MAIN
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page", page.name); super.onSaveInstanceState(outState) }
    private val controller by lazy { DolbyController.getInstance(this) }
    override fun onResume() { super.onResume(); controller.requestRefresh() }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        page = DolbyPage.entries.firstOrNull { it.name == (savedInstanceState?.getString("page") ?: intent.getStringExtra("page")) } ?: DolbyPage.MAIN
        setContent {
            DolbyTheme {
                val windowSize = calculateWindowSizeClass(this@DolbySettingsActivity)
                val expanded = windowSize.widthSizeClass != WindowWidthSizeClass.Compact
                val scope = rememberCoroutineScope()
                var reset by remember { mutableStateOf<Boolean?>(null) }
                var resetMenu by remember { mutableStateOf(false) }
                var failure by remember { mutableStateOf(false) }
                BackHandler(enabled = page != DolbyPage.MAIN) { page = DolbyPage.MAIN }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(title = { Text(stringResource(when (page) {
                            DolbyPage.MAIN -> R.string.dolby_title
                            DolbyPage.EQUALIZER -> R.string.dolby_preset
                            DolbyPage.SETTINGS -> R.string.dolby_nav_settings
                        })) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (page == DolbyPage.MAIN) finish() else page = DolbyPage.MAIN
                                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.dolby_navigate_back)) }
                            },
                            actions = {
                                if (page == DolbyPage.MAIN) {
                                Box {
                                    IconButton(onClick = { resetMenu = true }) {
                                        Icon(Icons.Default.MoreVert, stringResource(R.string.dolby_reset_options))
                                    }
                                    DropdownMenu(expanded = resetMenu, onDismissRequest = { resetMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.dolby_reset_profile)) },
                                            onClick = { resetMenu = false; reset = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.dolby_reset_all)) },
                                            onClick = { resetMenu = false; reset = true }
                                        )
                                    }
                                }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                    },
                    bottomBar = { if (!expanded) DolbyNavigation(this@DolbySettingsActivity, page) }
                ) { padding ->
                    val motion = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    DossierBackdrop(Modifier.matchParentSize())
                    Row(Modifier.fillMaxSize()) {
                    if (expanded) DolbyRail(this@DolbySettingsActivity, page)
                    AnimatedContent(page, modifier = Modifier.weight(1f).fillMaxHeight(),
                        transitionSpec = { fadeIn(motion) togetherWith fadeOut(motion) }, label = "page") { destination ->
                        when (destination) {
                            DolbyPage.MAIN -> MainScreen(controller, Modifier)
                            DolbyPage.EQUALIZER -> EqualizerScreen(equalizer, expanded = expanded)
                            DolbyPage.SETTINGS -> ProfileManager(controller, Modifier) { page = DolbyPage.MAIN }
                        }
                    }
                }
                }
                }
                reset?.let { all ->
                    BackdropBlur()
                    AlertDialog(onDismissRequest = { reset = null },
                        title = { Text(stringResource(if (all) R.string.dolby_reset_all else R.string.dolby_reset_profile)) },
                        text = { Text(stringResource(if (all) R.string.dolby_reset_all_message else R.string.dolby_reset_profile_message)) },
                        confirmButton = {
                            TextButton(onClick = { scope.launch {
                                try {
                                    if (all) controller.resetAllProfiles() else controller.resetProfileSpecificSettings()
                                } catch (_: RuntimeException) { failure = true }
                                reset = null
                            } }) { Text(stringResource(android.R.string.ok)) }
                        },
                        dismissButton = { TextButton(onClick = { reset = null }) { Text(stringResource(android.R.string.cancel)) } })
                }
                if (failure) {
                    BackdropBlur()
                    AlertDialog(onDismissRequest = { failure = false },
                        text = { Text(stringResource(R.string.dolby_setting_failed)) },
                        confirmButton = { TextButton(onClick = { failure = false }) { Text(stringResource(android.R.string.ok)) } })
                }
            }
        }
    }
}
