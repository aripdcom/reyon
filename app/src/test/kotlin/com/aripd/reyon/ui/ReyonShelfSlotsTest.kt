package com.aripd.reyon.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Raf gözleri ekran okuyucuya tek tek açık: her göz konumunu ve içeriğini okur, çift
 * dokunuşu (semantik tıklama) parmak dokunuşuyla aynı işi yapar, raftan kaldırma özel
 * eylemdir (docs/cihaz-testi.md, 1.1.0 E: tuval tek düğümdü, ekran okuyucuyla
 * Diziliş, Satış ve Denetim oynanamıyordu).
 */
@RunWith(AndroidJUnit4::class)
class ReyonShelfSlotsTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun leaveRoundAndClear() {
        rule.reyonLeaveRound()
        ReyonTestSupport.clearPrefs()
    }

    private val empty get() = str(R.string.reyon_slot_empty)

    /** Konum önekiyle başlayan göz düğümleri: "en üst raf, soldan 1. göz: …". */
    private fun slots(): List<SemanticsNode> =
        rule.onAllNodes(SemanticsMatcher("raf gözü") { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(": ") && it.startsWith(str(R.string.reyon_shelf_top)) || it.contains(str(R.string.reyon_shelf_bottom) + ",") || it.contains(str(R.string.reyon_shelf_eye) + ",") } == true
        }).fetchSemanticsNodes()

    private fun desc(n: SemanticsNode) = n.config[SemanticsProperties.ContentDescription].single()

    private fun trayChips(): List<SemanticsNode> =
        rule.onAllNodes(hasContentDescription(str(R.string.reyon_tray_label) + ":", substring = true)).fetchSemanticsNodes()

    private fun click(n: SemanticsNode) {
        rule.runOnIdle { n.config[SemanticsActions.OnClick].action!!.invoke() }
        rule.waitForIdle()
    }

    /** Tepsiden ilk ürünü seçer, ilk boş göze çift dokunur, göz ürünü okur; özel eylemle geri kaldırır. */
    private fun placeAndRemoveThroughSlots(kind: ReyonKind) {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(kind)
        rule.waitUntil(timeoutMillis = 30_000) { trayChips().isNotEmpty() && slots().isNotEmpty() }

        val all = slots()
        assertTrue("göz sayısı 3×4 olmalı: ${all.size}", all.size == 12)
        val before = trayChips().size
        click(trayChips().first())
        val target = slots().first { desc(it).endsWith(": $empty") }
        val where = desc(target).substringBefore(": ")
        click(target)
        rule.waitUntil(timeoutMillis = 5_000) { trayChips().size == before - 1 }
        val filled = slots().first { desc(it).startsWith("$where: ") }
        assertTrue("göz ürünü okumalı: ${desc(filled)}", !desc(filled).endsWith(": $empty"))

        val remove = filled.config[SemanticsActions.CustomActions].single { it.label == str(R.string.reyon_remove) }
        rule.runOnIdle { remove.action() }
        rule.waitUntil(timeoutMillis = 5_000) { trayChips().size == before }
        rule.waitForIdle()
        assertEquals("$where: $empty", desc(slots().first { desc(it).startsWith("$where: ") }))
    }

    @Test
    fun arrangeCanBePlayedThroughSlotNodes() = placeAndRemoveThroughSlots(ReyonKind.PUZZLE)

    @Test
    fun salesCanBePlayedThroughSlotNodes() = placeAndRemoveThroughSlots(ReyonKind.SALES)

    @Test
    fun auditExposesEverySlotOfBothShelves() {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.AUDIT)
        rule.waitUntil(timeoutMillis = 30_000) { slots().size >= 24 }
        // Planogram ve mağaza rafı: ikisi de 3×4, her gözün bir düğümü.
        assertEquals(24, slots().size)
        assertTrue("dolu gözler ürünü, markasını ve boyunu okumalı", slots().any { desc(it).split(", ").size >= 4 })
    }
}
