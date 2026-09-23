package com.aripd.reyon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.engine.OrderRules
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReyonScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun leaveRoundAndClear() {
        rule.reyonLeaveRound()
        ReyonTestSupport.clearPrefs()
    }

    @Test
    fun hintsPlaceProductsUndoReturnsThemAndThePuzzleGetsSolved() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.PUZZLE)

        val trayPrefix = str(R.string.reyon_tray_label) + ":"
        fun trayItems() = rule.onAllNodes(hasContentDescription(trayPrefix, substring = true))
        // Bulmaca arka planda üretilir; tepsi gelene dek bekle.
        rule.waitUntil(timeoutMillis = 30_000) { trayItems().fetchSemanticsNodes().isNotEmpty() }
        val before = trayItems().fetchSemanticsNodes().size
        assertTrue("tepside ürün olmalı", before > 0)

        rule.onNodeWithText(str(R.string.reyon_hint)).performClick()
        assertEquals(before - 1, trayItems().fetchSemanticsNodes().size)
        rule.onNodeWithText(str(R.string.undo)).performClick()
        assertEquals(before, trayItems().fetchSemanticsNodes().size)

        var guard = 0
        while (trayItems().fetchSemanticsNodes().isNotEmpty() && guard++ < 20) {
            rule.onNodeWithText(str(R.string.reyon_hint)).performClick()
        }
        rule.onNodeWithText(str(R.string.reyon_done_title)).assertIsDisplayed()

        // Görevler'e dönüş: alıştırma satırı son sonucu (süre) gösterir.
        rule.onNodeWithText(str(R.string.nav_tasks)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithTag(HOME_TAG).fetchSemanticsNodes().isNotEmpty() }
        val row = rule.onNodeWithTag(homePracticeTag(ReyonKind.PUZZLE)).fetchSemanticsNode()
        val texts = row.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        assertTrue("son sonuç süre olarak yazılmalı: $texts", texts.any { Regex("^\\d+:\\d{2}$").matches(it) })
    }

    @Test
    fun leavingAndReenteringASolvedPuzzleDoesNotCountItAgain() {
        // Çözüm görünüm modelinde durur; menüye gidip dönmek (ya da mod çipini
        // değiştirmek) ekranı yeniden kurar. Rekor bir kez gönderilmeli.
        val scores = mutableListOf<Long>()
        var shown by mutableStateOf(true)
        rule.setAppContent { if (shown) ReyonScreen(highScore = 0L, onScore = { scores += it }, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.PUZZLE)

        val trayPrefix = str(R.string.reyon_tray_label) + ":"
        fun trayItems() = rule.onAllNodes(hasContentDescription(trayPrefix, substring = true))
        rule.waitUntil(timeoutMillis = 30_000) { trayItems().fetchSemanticsNodes().isNotEmpty() }
        var guard = 0
        while (trayItems().fetchSemanticsNodes().isNotEmpty() && guard++ < 20) {
            rule.onNodeWithText(str(R.string.reyon_hint)).performClick()
        }
        rule.onNodeWithText(str(R.string.reyon_done_title)).assertIsDisplayed()
        assertEquals(listOf(1L), scores)

        // Ekranı bileşimden çıkarıp geri koymak Görevler'e gidip dönmenin ta kendisi:
        // kökte SaveableStateHolder yok, görünüm modeli Activity'de yaşamaya devam eder.
        rule.runOnIdle { shown = false }
        rule.waitForIdle()
        rule.runOnIdle { shown = true }
        rule.onNodeWithText(str(R.string.reyon_done_title)).assertIsDisplayed()
        assertEquals("yeniden giriş çözümü bir daha saymamalı", listOf(1L), scores)
    }

    @Test
    fun auditHintsRevealEveryDeviation() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.AUDIT)

        val shelfDesc = str(R.string.reyon_audit_board_desc_fmt, 0, 0, 0, 0).substringBefore(' ')
        fun shelf() = rule.onAllNodes(hasContentDescription(shelfDesc, substring = true))
        rule.waitUntil(timeoutMillis = 30_000) { shelf().fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.reyon_audit_plan_label)).assertIsDisplayed()

        // Her kılavuz adımı bir sapma açar; Market'te iki sapma var.
        var guard = 0
        while (rule.onAllNodesWithText(str(R.string.reyon_audit_done_title)).fetchSemanticsNodes().isEmpty() && guard++ < 8) {
            rule.onNodeWithText(str(R.string.reyon_hint)).performClick()
        }
        rule.onNodeWithText(str(R.string.reyon_audit_done_title)).assertIsDisplayed()
    }

    @Test
    fun orderStepperClosesDaysAndTheWeekEnds() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.ORDER)

        val boardPrefix = str(R.string.reyon_order_board_desc_fmt, 0, 0, 0, 0).substringBefore(' ')
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodes(hasContentDescription(boardPrefix, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // İlk ürünün "Artır" düğmesi: sipariş 1 koli olur.
        val morePrefix = str(R.string.reyon_order_more) + ":"
        fun moreButtons() = rule.onAllNodes(hasContentDescription(morePrefix, substring = true))
        assertTrue("artır düğmesi olmalı", moreButtons().fetchSemanticsNodes().isNotEmpty())
        moreButtons()[0].performClick()
        rule.waitForIdle()
        val oneCase = str(R.string.reyon_order_case_fmt, 1, 0).substringBefore(" = ")
        val caseNodes = rule.onAllNodes(hasText(oneCase, substring = true)).fetchSemanticsNodes()
        if (caseNodes.isEmpty()) {
            val first = moreButtons().fetchSemanticsNodes().first()
            val koli = rule.onAllNodes(hasText("koli", substring = true)).fetchSemanticsNodes()
                .map { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }
            val rows = rule.onAllNodes(hasContentDescription(": ", substring = true)).fetchSemanticsNodes()
                .map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
            fail("1 koli görünmeli · düğme ${first.boundsInRoot} devre dışı=${first.config.contains(SemanticsProperties.Disabled)} " +
                "desc=${first.config.getOrNull(SemanticsProperties.ContentDescription)} · koli metinleri=$koli · satırlar=$rows")
        }

        // Günü kapat → döküm kartı → Devam.
        rule.onNodeWithText(str(R.string.reyon_order_close_day)).performClick()
        rule.onNodeWithText(str(R.string.reyon_order_continue)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.reyon_order_continue)).performClick()

        // Kalan günler: kılavuzla (uzmanın önerisi) sipariş ver, kapat; Market beş gün.
        val done = str(R.string.reyon_order_done_title)
        val closeWeek = str(R.string.reyon_order_close_week)
        val closeDay = str(R.string.reyon_order_close_day)
        var guard = 0
        while (rule.onAllNodesWithText(done).fetchSemanticsNodes().isEmpty() && guard++ < 10) {
            repeat(3) { rule.onNodeWithText(str(R.string.reyon_hint)).performClick() }
            if (rule.onAllNodesWithText(closeWeek).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithText(closeWeek).performClick()
            } else {
                rule.onNodeWithText(closeDay).performClick()
            }
            rule.onNodeWithText(str(R.string.reyon_order_continue)).performClick()
        }
        rule.onNodeWithText(done).assertIsDisplayed()

        // Haftalık rapordaki grafik: kapanan her gün ve devir açıklamada olmalı.
        val chartPrefix = str(R.string.reyon_order_chart_desc_fmt, "", "", "").substringBefore(':')
        val chart = rule.onAllNodes(hasContentDescription(chartPrefix, substring = true)).fetchSemanticsNodes()
        assertTrue("hafta grafiği görünmeli", chart.isNotEmpty())
        val desc = chart.first().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
        val lastDay = str(R.string.reyon_order_chart_day_fmt, ReyonLevel.KOLAY.let { OrderRules.days(it) }, 0).substringBeforeLast(' ')
        assertTrue("son gün grafikte olmalı ($desc)", desc.contains(lastDay))
    }

    @Test
    fun salesPlacesAProductByTappingTheShelfAndUndoReturnsIt() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.SALES)

        val trayPrefix = str(R.string.reyon_tray_label) + ":"
        fun trayItems() = rule.onAllNodes(hasContentDescription(trayPrefix, substring = true))
        rule.waitUntil(timeoutMillis = 30_000) { trayItems().fetchSemanticsNodes().isNotEmpty() }
        val before = trayItems().fetchSemanticsNodes().size
        assertTrue("tepside ürün olmalı", before > 0)

        // İlk ürünü seç, rafın ortasına dokun: boş raf, yerleşmeli.
        trayItems()[0].performClick()
        val shelfDesc = str(R.string.reyon_sales_board_desc_fmt, 0, 0, 0, 0).substringBefore(' ')
        rule.onNode(hasContentDescription(shelfDesc, substring = true)).performClick()
        assertEquals(before - 1, trayItems().fetchSemanticsNodes().size)
        rule.onNodeWithText(str(R.string.undo)).performClick()
        assertEquals(before, trayItems().fetchSemanticsNodes().size)
    }
}
