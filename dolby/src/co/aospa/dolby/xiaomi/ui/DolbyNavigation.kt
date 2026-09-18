/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.aospa.dolby.xiaomi.DolbySettingsActivity
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.spa.framework.theme.settingsBackground
import co.aospa.dolby.xiaomi.geq.EqualizerActivity
import co.aospa.dolby.xiaomi.profiles.ProfileSettingsActivity

internal enum class DolbyPage { MAIN, EQUALIZER, SETTINGS }

internal fun navigate(activity: Activity, page: DolbyPage) {
    if (activity is DolbySettingsActivity) {
        activity.showPage(page)
        return
    }
    activity.startActivity(Intent(activity, DolbySettingsActivity::class.java)
        .putExtra("page", page.name)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    activity.finish()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DolbyNavigation(activity: Activity, selected: DolbyPage, includeInsets: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.settingsBackground).windowInsetsPadding(
        if (includeInsets) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0))) {
        val cell = maxWidth / 3
        val position by animateDpAsState(cell * selected.ordinal,
            MaterialTheme.motionScheme.fastSpatialSpec(), label = "navigation pill")
        Box(Modifier.offset(x = position + (cell - 56.dp) / 2, y = 7.dp).width(56.dp).height(28.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)))
        val navHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxOf(60.dp, 44.dp + MaterialTheme.typography.labelSmall.lineHeight.toDp())
        }
        Row(Modifier.fillMaxWidth().height(navHeight).selectableGroup()) {
            for (page in DolbyPage.entries) {
                val label = stringResource(when (page) {
                    DolbyPage.MAIN -> R.string.dolby_nav_main
                    DolbyPage.EQUALIZER -> R.string.dolby_nav_equalizer
                    DolbyPage.SETTINGS -> R.string.dolby_nav_settings
                })
                val active = selected == page
                Column(Modifier.weight(1f).fillMaxHeight()
                    .selectable(active, role = Role.Tab) { navigate(activity, page) }
                    .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.height(28.dp).width(56.dp), contentAlignment = Alignment.Center) {
                        Icon(when (page) {
                            DolbyPage.MAIN -> Icons.Default.Home
                            DolbyPage.EQUALIZER -> Icons.Default.Equalizer
                            DolbyPage.SETTINGS -> Icons.Default.Settings
                        }, contentDescription = null, modifier = Modifier.size(22.dp),
                            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun DolbyRail(activity: Activity, selected: DolbyPage) {
    NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.settingsBackground, windowInsets = WindowInsets(0, 0, 0, 0)) {
        for (page in DolbyPage.entries) {
            val label = stringResource(when (page) {
                DolbyPage.MAIN -> R.string.dolby_nav_main
                DolbyPage.EQUALIZER -> R.string.dolby_nav_equalizer
                DolbyPage.SETTINGS -> R.string.dolby_nav_settings
            })
            NavigationRailItem(selected = selected == page, onClick = { navigate(activity, page) },
                icon = { Icon(when (page) {
                    DolbyPage.MAIN -> Icons.Default.Home
                    DolbyPage.EQUALIZER -> Icons.Default.Equalizer
                    DolbyPage.SETTINGS -> Icons.Default.Settings
                }, null) }, label = { Text(label) })
        }
    }
}
