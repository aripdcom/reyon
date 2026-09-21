package com.aripd.reyon

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.ui.ReyonTestSupport
import com.aripd.reyon.ui.reyonLeaveRound
import com.aripd.reyon.ui.reyonOpenMenu
import com.aripd.reyon.ui.about.ABOUT_LANGUAGE_TAG
import com.aripd.reyon.ui.about.ABOUT_SOUND_TAG
import com.aripd.reyon.ui.settings.LANGUAGE_LIST_TAG
import com.aripd.reyon.ui.theme.ReyonTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uygulama kökü: açılışta doğrudan Reyon, üst çubuktaki dişliden ayarlar.
 *
 * Ana menü yok — bu yüzden sınanan şey "oyun listesinden Reyon'a girmek" değil,
 * uygulamanın ilk karesinde dört modun orada olması ve ayarların tek dokunuşla
 * açılması.
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
        rule.reyonOpenMenu()
    }

    @Test
    fun opensStraightIntoReyonWithItsFourModes() {
        launch()
        rule.onNodeWithText(titleOf(R.string.app_name)).assertIsDisplayed()
        for (id in listOf(
            R.string.reyon_kind_puzzle,
            R.string.reyon_kind_audit,
            R.string.reyon_kind_sales,
            R.string.reyon_kind_order,
        )) {
            rule.onNodeWithText(str(id)).assertIsDisplayed()
        }
    }

    @Test
    fun theGearOpensSettingsAndBackReturnsToReyon() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.about_title)).performClick()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.about_version_fmt, "").trim(), substring = true)
            .assertIsDisplayed()
        // Ayarlar ekranının kendi çubuğunda dişli olmamalı; geri oku Reyon'a döner.
        rule.onNodeWithContentDescription(str(R.string.back)).performClick()
        rule.onNodeWithText(str(R.string.reyon_kind_sales)).assertIsDisplayed()
    }

    @Test
    fun theSoundRowFlipsWhatItWillDo() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.about_title)).performClick()
        rule.onNodeWithTag(ABOUT_SOUND_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.sound_off)).performClick()
        rule.onNodeWithContentDescription(str(R.string.sound_on)).assertIsDisplayed()
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
