/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import co.aospa.dolby.xiaomi.R

/** Resolve built-in labels with the UI locale, not the long-lived engine snapshot. */
@Composable
internal fun profileLabel(key: String, customName: String): String {
    val keys = stringArrayResource(R.array.dolby_profile_values)
    val labels = stringArrayResource(R.array.dolby_profile_entries)
    val index = keys.indexOf(key)
    return if (index >= 0) labels[index] else customName
}
