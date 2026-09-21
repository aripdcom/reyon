package com.aripd.reyon

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.ui.theme.ReyonTheme
import java.util.Locale

/** Ekranların beklediği tema ve yerel sağlayıcılarla içerik kurar (ses yok). */
fun ComposeContentTestRule.setAppContent(content: @Composable () -> Unit) {
    setContent {
        ReyonTheme {
            val haptics = LocalHapticFeedback.current
            CompositionLocalProvider(LocalHaptics provides haptics, LocalSound provides null) {
                content()
            }
        }
    }
}

/** Uygulama kaynaklarından dize; testler yerel ayardan bağımsız kalır. */
fun str(@StringRes id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

/**
 * GameTopBar başlıkları büyük harfle çizilir. Üretimdeki appLocale() gibi
 * yapılandırmanın yerelini kullanır: qualifiers = "tr" ile koşan bir test
 * Locale.getDefault()'a bakarsa "İ" yerine "I" bekler ve boşa düşer.
 */
fun titleOf(@StringRes id: Int): String = str(id).uppercase(testLocale())

/** Testin koştuğu yapılandırmanın yereli. */
fun testLocale(): Locale =
    ApplicationProvider.getApplicationContext<Context>().resources.configuration.locales[0]
