package com.aripd.reyon

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.platform.SettingsStore
import com.aripd.reyon.ui.HOME_TAG
import com.aripd.reyon.ui.ReyonKind
import com.aripd.reyon.ui.ReyonTestSupport
import com.aripd.reyon.ui.homeDailyTag
import com.aripd.reyon.ui.homePracticeTag
import com.aripd.reyon.ui.reyonLeaveRound
import com.aripd.reyon.ui.reyonOpenHome
import com.aripd.reyon.ui.about.ABOUT_LANGUAGE_TAG
import com.aripd.reyon.ui.about.ABOUT_SOUND_TAG
import com.aripd.reyon.ui.about.ABOUT_THEME_TAG
import com.aripd.reyon.ui.settings.LANGUAGE_LIST_TAG
import com.aripd.reyon.ui.theme.ReyonTheme
import com.aripd.reyon.ui.theme.ThemeChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uygulama kökü: açılışta Görevler, üst çubuktaki ayar simgesinden ayarlar.
 *
 * Sınanan şey uygulamanın ilk karesinde dört modun (günün vakaları ve
 * alıştırma) orada olması ve ayarların tek dokunuşla açılması.
 */
@RunWith(AndroidJUnit4::class)
class ReyonAppTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun leaveRoundAndClear() {
        rule.reyonLeaveRound()
        ReyonTestSupport.clearPrefs()
    }

    private fun launch() {
        rule.setContent { ReyonTheme { ReyonApp() } }
        rule.reyonOpenHome()
    }

    @Test
    fun opensOnTheTaskListWithItsFourModes() {
        launch()
        rule.onNodeWithText(str(R.string.home_title)).assertIsDisplayed()
        for (kind in ReyonKind.entries) {
            rule.onNodeWithTag(homeDailyTag(kind)).performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag(homePracticeTag(kind)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theGearOpensSettingsAndBackReturnsToReyon() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.about_title)).performClick()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.about_version_fmt, "").trim(), substring = true)
            .assertIsDisplayed()
        // Ayarlar ekranının kendi çubuğunda ayar simgesi olmamalı; geri oku Görevler'e döner.
        rule.onNodeWithContentDescription(str(R.string.back)).performClick()
        rule.onNodeWithTag(HOME_TAG).assertIsDisplayed()
    }

    /**
     * Ses satırı dokununca ne olacağını söyler ve dokunuşla tersine döner. Ses
     * 1.1.0'dan beri varsayılan kapalı; ayar dosyası testler arasında
     * paylaşılabildiği için başlangıç durumu okunur, varsayılmaz.
     */
    @Test
    fun theSoundRowFlipsWhatItWillDo() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.about_title)).performClick()
        rule.onNodeWithTag(ABOUT_SOUND_TAG).assertIsDisplayed()
        val on = str(R.string.sound_on)
        val off = str(R.string.sound_off)
        val wasOff = rule.onAllNodesWithContentDescription(on).fetchSemanticsNodes().isNotEmpty()
        rule.onNodeWithContentDescription(if (wasOff) on else off).performClick()
        rule.onNodeWithContentDescription(if (wasOff) off else on).assertIsDisplayed()
    }

    @Test
    fun soundIsOffByDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("reyon_settings", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(SettingsStore(context).soundEnabled)
    }

    @Test
    fun theThemeRowSwitchesToDarkAndIsRemembered() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.about_title)).performClick()
        rule.onNodeWithTag(ABOUT_THEME_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.theme_dark)).performClick()
        rule.onNode(hasText(str(R.string.theme_dark)) and isSelected()).assertExists()
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(ThemeChoice.DARK, SettingsStore(context).theme)
        rule.onNodeWithText(str(R.string.theme_light)).performClick()
        assertEquals(ThemeChoice.LIGHT, SettingsStore(context).theme)
    }

    @Test
    fun theLanguageRowOpensTheList() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.about_title)).performClick()
        rule.onNodeWithTag(ABOUT_LANGUAGE_TAG).performClick()
        rule.onNodeWithTag(LANGUAGE_LIST_TAG).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.language_system)).assertIsDisplayed()
    }
}
