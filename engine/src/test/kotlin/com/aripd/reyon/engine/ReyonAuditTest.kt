package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReyonAuditTest {

    private val seeds = 1L..40L

    private fun signature(a: ReyonAudit): String =
        a.items.joinToString { "${it.product.kind}/${it.product.brand}/${it.product.size}@${it.row},${it.col}x${it.facings}" } +
            "|" + a.deviations.joinToString { "${it.kind}:${it.slotMask}" }

    @Test
    fun sameSeedSameAudit() {
        for (level in ReyonLevel.entries) {
            assertEquals(signature(ReyonAuditGenerator.generate(7L, level)), signature(ReyonAuditGenerator.generate(7L, level)))
        }
        assertNotEquals(signature(ReyonAuditGenerator.generate(1L, ReyonLevel.ORTA)), signature(ReyonAuditGenerator.generate(2L, ReyonLevel.ORTA)))
        assertNotEquals(ReyonAuditGenerator.dailySeed(20_700L), ReyonAuditGenerator.dailySeed(20_701L))
        assertNotEquals("denetim ve diziliş günlük tohumları farklı", ReyonAuditGenerator.dailySeed(20_700L), ReyonGenerator.dailySeed(20_700L))
    }

    /** Değişmez: sapmalar ayrık, plan ile raf yalnızca sapma gözlerinde ayrışır, her sapma görünür. */
    @Test
    fun deviationsAreDisjointVisibleAndTheOnlyDifferences() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val a = ReyonAuditGenerator.generate(seed, level)
            assertEquals("tohum $seed $level sapma sayısı", ReyonAuditGenerator.count(level), a.deviations.size)
            assertTrue("tohum $seed $level tür", a.deviations.all { it.kind in ReyonAuditGenerator.kinds(level) })
            assertTrue("tohum $seed $level değişmez", ReyonAuditGenerator.verify(a))
            val diff = ReyonAuditGenerator.diffMask(a)
            assertTrue("fark boş olamaz", diff != 0)
            for (d in a.deviations) {
                assertTrue("her sapma ayrışır", d.slotMask and diff != 0)
                assertTrue("ürünler plandan", d.products.all { it in a.plan.indices })
            }
            // Planın kendisi rafı tam doldurur; gerçek raf çakışmasızdır.
            assertEquals(a.board.slots, a.plan.sumOf { it.facings })
        }
    }

    @Test
    fun hardLevelUsesEveryDeviationKind() {
        val seen = HashSet<DeviationKind>()
        for (seed in seeds) seen += ReyonAuditGenerator.generate(seed, ReyonLevel.ZOR).deviations.map { it.kind }
        assertEquals(DeviationKind.entries.toSet(), seen)
        val easy = HashSet<DeviationKind>()
        for (seed in seeds) easy += ReyonAuditGenerator.generate(seed, ReyonLevel.KOLAY).deviations.map { it.kind }
        assertTrue(easy.none { it == DeviationKind.BRAND || it == DeviationKind.SIZE || it == DeviationKind.SPILL })
    }

    @Test
    fun swapAndSpillKeepTheirPartners() {
        for (seed in seeds) {
            val a = ReyonAuditGenerator.generate(seed, ReyonLevel.ZOR)
            for (d in a.deviations) when (d.kind) {
                DeviationKind.SWAP, DeviationKind.SPILL -> {
                    assertEquals(2, d.products.size)
                    assertEquals(d.products[1], d.partner)
                }
                DeviationKind.FOREIGN -> {
                    assertTrue(d.foreign != null && a.plan.none { it.kind == d.foreign })
                    assertEquals(d.foreign, a.items.first { it.product.id == d.products[0] }.product.kind)
                }
                DeviationKind.GAP -> {
                    val item = a.items.firstOrNull { it.product.id == d.products[0] }
                    val planned = a.plan[d.products[0]].facings
                    assertEquals(planned - 1, item?.facings ?: 0)
                }
                DeviationKind.BRAND -> assertNotEquals(a.plan[d.products[0]].brand, a.items.first { it.product.id == d.products[0] }.product.brand)
                DeviationKind.SIZE -> assertNotEquals(a.plan[d.products[0]].size, a.items.first { it.product.id == d.products[0] }.product.size)
            }
        }
    }

    @Test
    fun tappingFindsDeviationsOnceAndCountsMisses() {
        val a = ReyonAuditGenerator.generate(3L, ReyonLevel.ORTA)
        val s = ReyonAuditState(a)
        assertFalse(s.isComplete)
        // Sapma dışı bir göz: hata.
        var missRow = -1
        var missCol = -1
        loop@ for (r in 0 until a.rows) for (c in 0 until a.cols) if (a.deviationAt(r, c) < 0) {
            missRow = r
            missCol = c
            break@loop
        }
        assertEquals(AuditTap.Miss, s.tap(missRow, missCol))
        assertEquals(1, s.mistakes)
        for ((i, d) in a.deviations.withIndex()) {
            val slot = d.slotMask.countTrailingZeroBits()
            val r = a.board.rowOf(slot)
            val c = a.board.colOf(slot)
            assertEquals(AuditTap.Found(i), s.tap(r, c))
            assertEquals(AuditTap.Already, s.tap(r, c))
            assertTrue(s.isFound(i))
        }
        assertTrue(s.isComplete)
        assertEquals(a.deviations.size, s.foundCount)
        assertEquals(a.deviations.indices.toList(), s.foundOrder)
        assertEquals(AuditTap.Already, s.tap(missRow, missCol))
        assertEquals(1, s.mistakes)
        assertNull(s.hint())
    }

    @Test
    fun hintsRevealAndSnapshotRoundTrips() {
        val a = ReyonAuditGenerator.generate(5L, ReyonLevel.ZOR)
        val s = ReyonAuditState(a)
        assertEquals(0, s.hint())
        assertEquals(1, s.hintsUsed)
        s.tap(-1 + 1, 0)
        val snap = s.snapshot()
        val t = ReyonAuditState(a)
        t.restore(snap)
        assertEquals(s.foundCount, t.foundCount)
        assertEquals(s.mistakes, t.mistakes)
        assertEquals(s.hintsUsed, t.hintsUsed)
        assertEquals(s.foundOrder.sorted(), t.foundOrder)
        var guard = 0
        while (!t.isComplete && guard++ < 10) t.hint()
        assertTrue(t.isComplete)
    }
}
