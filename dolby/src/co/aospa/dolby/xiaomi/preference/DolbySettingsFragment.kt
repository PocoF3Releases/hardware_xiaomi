/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.preference

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import co.aospa.dolby.xiaomi.ui.DolbyPage
import co.aospa.dolby.xiaomi.ui.navigate

/** Retain the legacy class name while routing callers into the reactive Compose UI. */
class DolbySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        navigate(requireActivity(), DolbyPage.MAIN)
    }
}
