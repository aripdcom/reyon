package com.aripd.reyon.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(selected: String = AppLocale.SYSTEM, onPick: (String) -> Unit = {}) {
        rule.setAppContent {
            LanguageScreen(selected = selected, effective = "en", onPick = onPick, onExit = {})
        }
    }

    @Test
    fun listsEverySupportedLanguageByItsOwnName() {
        // Açık bir seçimle: "telefonun dili" satırı o zaman alt satırda dil adı
        // yazmaz, her ad listede tek geçer.
        show(selected = "en")
        val list = rule.onNodeWithTag(LANGUAGE_LIST_TAG)
        for (tag in AppLocale.TAGS) {
            val name = AppLocale.endonym(tag)
            list.performScrollToNode(hasText(name))
            rule.onNodeWithText(name).assertIsDisplayed()
        }
    }

    @Test
    fun offersThePhoneLanguageAndNamesWhatItResolvedTo() {
        // Seçim "telefonun dili"ndeyken satır hangi dile düşüldüğünü alt satırda
        // yazar: "English" o zaman iki kez geçer (bu satır + listedeki İngilizce).
        show(selected = AppLocale.SYSTEM)
        rule.onNodeWithText(str(R.string.language_system)).assertIsDisplayed()
        assertEquals(2, rule.onAllNodesWithText(AppLocale.endonym("en")).fetchSemanticsNodes().size)
    }

    @Test
    fun anExplicitChoiceDropsTheResolvedLanguageLine() {
        // Türkçe seçiliyken "telefonun dili" satırı alt satır yazmaz: "English"
        // yalnız listedeki İngilizce satırında geçer.
        show(selected = "tr")
        assertEquals(1, rule.onAllNodesWithText(AppLocale.endonym("en")).fetchSemanticsNodes().size)
    }

    @Test
    fun pickingALanguageReportsItsTag() {
        var picked: String? = null
        show(selected = AppLocale.SYSTEM, onPick = { picked = it })
        val list = rule.onNodeWithTag(LANGUAGE_LIST_TAG)
        list.performScrollToNode(hasText(AppLocale.endonym("de")))
        rule.onNodeWithText(AppLocale.endonym("de")).performClick()
        assertEquals("de", picked)
    }
}
