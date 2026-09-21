package com.aripd.reyon.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * [enabled] her titreşimde okunur; Ayarlar ekranındaki düğme böylece anında
 * etki eder (bkz. [SoundPlayer]).
 */
private class GatedHaptics(
    private val delegate: HapticFeedback,
    private val enabled: () -> Boolean,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (enabled()) delegate.performHapticFeedback(hapticFeedbackType)
    }
}

fun HapticFeedback.gatedBy(enabled: () -> Boolean): HapticFeedback = GatedHaptics(this, enabled)

/** Ekranların titreşim için eriştiği yer; kapatılınca sessizce yutar. */
val LocalHaptics = staticCompositionLocalOf<HapticFeedback> {
    error("LocalHaptics sağlanmadı")
}
