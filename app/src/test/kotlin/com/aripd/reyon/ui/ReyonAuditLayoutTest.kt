package com.aripd.reyon.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Denetim yerleşimi kısa ekranda da tutarlı kalmalı: plan ve raf aynı genişlikte,
 * ikisi de ekranın içinde, altındaki kılavuz düğmesi görünür; plana dokununca
 * büyütülmüş plan açılır ve dokununca kapanır.
 */
@RunWith(AndroidJUnit4::class)
class ReyonAuditLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun leaveRoundAndClear() {
        rule.reyonLeaveRound()
        ReyonTestSupport.clearPrefs()
    }

    private val level = ReyonLevel.KOLAY
    private val planDesc: String get() = str(R.string.reyon_audit_plan_desc_fmt, level.rows, level.cols)
    private val shelfPrefix: String get() = str(R.string.reyon_audit_board_desc_fmt, 0, 0, 0, 0).substringBefore(' ')
    private val zoomDesc: String get() = str(R.string.reyon_audit_plan_zoom_desc)

    private fun startEasyAudit() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.AUDIT, level)
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodes(hasContentDescription(shelfPrefix, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun plan(): DpRect = rule.onNode(hasContentDescription(planDesc)).getBoundsInRoot()
    private fun shelf(): DpRect = rule.onNode(hasContentDescription(shelfPrefix, substring = true)).getBoundsInRoot()

    private fun assertCanvasesFit() {
        val root = rule.onRoot().getBoundsInRoot()
        val plan = plan()
        val shelf = shelf()
        assertEquals("plan ve raf aynı genişlikte olmalı", plan.width.value, shelf.width.value, 1f)
        assertTrue("plan neredeyse tam genişlikte olmalı: ${plan.width} / ${root.width}", plan.width >= root.width * 0.85f)
        assertTrue("plan ekranın içinde olmalı", plan.left >= root.left && plan.right <= root.right + 1.dp)
        assertTrue("raf ekranın içinde olmalı", shelf.left >= root.left && shelf.right <= root.right + 1.dp)
        assertTrue("raf planın altında olmalı", shelf.top >= plan.bottom)
        assertTrue("raf ekranın altına taşmamalı: ${shelf.bottom} / ${root.bottom}", shelf.bottom <= root.bottom)
        assertTrue("raf plandan daha yüksek olmalı", shelf.height > plan.height)
        rule.onNodeWithText(str(R.string.reyon_hint)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.reyon_audit_plan_label)).assertIsDisplayed()
    }

    /**
     * Kısa ekranda Görevler kayar: en alttaki alıştırma satırı (Sipariş) kaydırınca
     * ekranın içine gelir ve dokunulabilir büyüklükte kalır.
     */
    @Test
    @Config(qualifiers = "+w360dp-h640dp-xhdpi")
    fun theLastPracticeRowIsReachableOnAShortPhone() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonOpenHome()
        val root = rule.onRoot().getBoundsInRoot()
        val row = rule.onNodeWithTag(homePracticeTag(ReyonKind.ORDER)).performScrollTo()
        row.assertIsDisplayed()
        val b = row.getBoundsInRoot()
        assertTrue("Sipariş satırı ekranın içinde olmalı: $b / $root", b.top >= root.top && b.bottom <= root.bottom + 1.dp)
        assertTrue("satır dokunulabilir yükseklikte olmalı: ${b.height}", b.height >= 48.dp)
    }

    /** Mağaza formatları tek satırda, sırayla; her parça adına yetecek genişlikte. */
    @Test
    @Config(qualifiers = "+w360dp-h640dp-xhdpi")
    fun theStoreFormatsShareOneRowOnAShortPhone() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonOpenHome()
        val bounds = ReyonLevel.entries.map { rule.onNodeWithText(str(formatName(it))).performScrollTo().getBoundsInRoot() }
        for (b in bounds) assertEquals("formatlar tek satırda olmalı", bounds.first().top.value, b.top.value, 1f)
        assertTrue("formatlar soldan sağa sıralı olmalı", bounds.zipWithNext().all { (a, b) -> b.left > a.right - 1.dp })
        assertTrue("parça genişliği adına yetmeli: ${bounds.map { it.width }}", bounds.all { it.width >= 60.dp })
    }

    @Test
    fun canvasesShareTheWidthAndFitOnTheDefaultPhone() {
        startEasyAudit()
        assertCanvasesFit()
    }

    @Test
    @Config(qualifiers = "+w360dp-h640dp-xhdpi")
    fun canvasesShareTheWidthAndFitOnAShortPhone() {
        startEasyAudit()
        assertCanvasesFit()
    }

    @Test
    @Config(qualifiers = "+w360dp-h640dp-xhdpi")
    fun tappingThePlanOpensTheEnlargedPlanAndTappingAgainClosesIt() {
        startEasyAudit()
        rule.onNode(hasContentDescription(zoomDesc)).assertDoesNotExist()
        rule.onNode(hasContentDescription(planDesc)).performClick()
        rule.onNode(hasContentDescription(zoomDesc)).assertIsDisplayed()
        // Küçük ve büyük plan birlikte; ikisi de ekranın içinde.
        val rootPx = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val plans = rule.onAllNodes(hasContentDescription(planDesc)).fetchSemanticsNodes()
        assertEquals("küçük ve büyük plan birlikte", 2, plans.size)
        for (node in plans) {
            val b = node.boundsInRoot
            assertTrue("plan ekranın içinde olmalı: $b / $rootPx", b.left >= rootPx.left - 1f && b.right <= rootPx.right + 1f && b.bottom <= rootPx.bottom + 1f)
        }
        // Büyütme, genişlik zaten tamken yüksekliği artırır (raf satırı 0,95'e çıkar).
        assertTrue("büyük plan küçüğünden yüksek olmalı", plans.maxOf { it.boundsInRoot.height } > plans.minOf { it.boundsInRoot.height } * 1.05f)
        rule.onNode(hasContentDescription(zoomDesc)).performClick()
        rule.onNode(hasContentDescription(zoomDesc)).assertDoesNotExist()
        assertEquals(1, rule.onAllNodes(hasContentDescription(planDesc)).fetchSemanticsNodes().size)
    }
}
