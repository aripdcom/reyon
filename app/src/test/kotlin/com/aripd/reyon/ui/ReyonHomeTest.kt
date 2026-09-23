package com.aripd.reyon.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Görevler: günün vakası günün formatında açılır, alıştırma formatı hatırlanır,
 * "nasıl oynanır" kartı ilk girişte bir kez açılır ve bilgi simgesiyle yeniden.
 */
@RunWith(AndroidJUnit4::class)
class ReyonHomeTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun leaveRoundAndClear() {
        rule.reyonLeaveRound()
        ReyonTestSupport.clearPrefs()
    }

    private val store: ReyonStore get() = ReyonStore(ApplicationProvider.getApplicationContext<Context>())

    private fun trayShown() =
        rule.onAllNodes(hasContentDescription(str(R.string.reyon_tray_label) + ":", substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theDailyCaseOpensInTodaysFormat() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonOpenHome()
        rule.onNodeWithTag(homeDailyTag(ReyonKind.PUZZLE)).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 30_000) { trayShown() }
        val level = dailyLevel(LocalDate.now().toEpochDay())
        val subtitle = str(R.string.case_subtitle_fmt, str(formatName(level)), level.rows, level.cols, str(R.string.mode_daily))
        rule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun thePracticeFormatIsRememberedAndUsed() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.PUZZLE, ReyonLevel.ORTA)
        assertEquals(ReyonLevel.ORTA, store.practiceLevel())
        rule.waitUntil(timeoutMillis = 30_000) { trayShown() }
        val subtitle = str(R.string.case_subtitle_fmt, str(R.string.format_supermarket), 4, 5, str(R.string.mode_free))
        rule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun theHowToCardOpensOnceOnTheFirstVisitAndAgainFromTheInfoButton() {
        ReyonTestSupport.clearPrefs(introsSeen = false)
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.SALES)
        val intro = str(R.string.reyon_sales_intro)
        rule.waitUntil(timeoutMillis = 30_000) { rule.onAllNodesWithText(intro).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.howto_ok)).performScrollTo().performClick()
        rule.onNodeWithText(intro).assertDoesNotExist()
        assertTrue("kart görüldü sayılmalı", store.introSeen(ReyonKind.SALES))
        assertFalse("öbür modların kartı yerinde", store.introSeen(ReyonKind.ORDER))

        rule.onNodeWithContentDescription(str(R.string.howto_title)).performClick()
        rule.onNodeWithText(intro).assertExists()
    }
}
