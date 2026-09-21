package com.aripd.reyon.ui.about

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(
        soundOn: Boolean = true,
        hapticsOn: Boolean = true,
        onToggleSound: () -> Unit = {},
        onLanguage: () -> Unit = {},
    ) {
        rule.setAppContent {
            AboutScreen(
                soundOn = soundOn,
                onToggleSound = onToggleSound,
                hapticsOn = hapticsOn,
                onToggleHaptics = {},
                languageLabel = "Türkçe",
                onLanguage = onLanguage,
                onExit = {},
            )
        }
    }

    @Test
    fun showsVersionAndRevealsApacheText() {
        show()
        rule.onNodeWithText(str(R.string.about_version_fmt, "").trim(), substring = true).assertIsDisplayed()
        val reveal = str(R.string.about_license_show)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(reveal))
        rule.onNodeWithText(reveal).performClick()
        rule.onNodeWithText("Apache License", substring = true).assertExists()
    }

    @Test
    fun theSettingsRowsShowTheirNamesAndWhatTappingDoes() {
        show(soundOn = true, hapticsOn = false)
        rule.onNodeWithText(str(R.string.settings_sound)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_haptics)).assertIsDisplayed()
        // Açıkken dokunmak kapatır, kapalıyken açar: okunan metin eylemi söyler.
        rule.onNodeWithTag(ABOUT_SOUND_TAG).assert(hasContentDescription(str(R.string.sound_off)))
        rule.onNodeWithTag(ABOUT_HAPTICS_TAG).assert(hasContentDescription(str(R.string.haptics_on)))
    }

    @Test
    fun theLanguageRowShowsTheCurrentLanguageAndReportsTaps() {
        var opened = 0
        show(onLanguage = { opened++ })
        rule.onNodeWithText("Türkçe").assertIsDisplayed()
        rule.onNodeWithTag(ABOUT_LANGUAGE_TAG).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun tappingTheSoundRowReportsIt() {
        var toggled = 0
        show(onToggleSound = { toggled++ })
        rule.onNodeWithTag(ABOUT_SOUND_TAG).performClick()
        assertEquals(1, toggled)
    }
}
