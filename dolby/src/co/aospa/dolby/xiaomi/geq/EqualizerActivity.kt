/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq

import android.os.Bundle
import androidx.activity.ComponentActivity
import co.aospa.dolby.xiaomi.ui.DolbyPage
import co.aospa.dolby.xiaomi.ui.navigate

class EqualizerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigate(this, DolbyPage.EQUALIZER)
    }
}
