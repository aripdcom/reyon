package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReyonSalesTest {

    private val seeds = 1L..30L

    @Test
    fun scorerFollowsTheTables() {
        val b = Fixture.board
        // Sabit tabloyla elle hesap: 3 raf; göz hizası 1, alt raf 2.
        val ps = listOf(
            Product(0, Kind.CIPS, Brand.A, 1, 2, premium = false, heavy = false, demand = 2),   // raf 1, göz 0-1
            Product(1, Kind.SOS, Brand.A, 1, 1, premium = false, heavy = false, demand = 1),    // raf 1, göz 2
            Product(2, Kind.DETERJAN, Brand.B, 3, 1, premium = false, heavy = true, demand = 3), // raf 1, göz 3 (gıdanın yanında, ağır üstte)
            Product(3, Kind.KOLA, Brand.A, 2, 2, premium = true, heavy = false, demand = 2),    // raf 0, göz 0-1 (yüksek marj göz hizasında değil)
            Product(4, Kind.SU, Brand.C, 2, 2, premium = false, heavy = false, demand = 1),     // raf 0, göz 2-3
            Product(5, Kind.SUNGER, Brand.B, 1, 4, premium = false, heavy = false, demand = 1), // raf 2, göz 0-3
        )
        val at = intArrayOf(b.idx(1, 0), b.idx(1, 2), b.idx(1, 3), b.idx(0, 0), b.idx(0, 2), b.idx(2, 0))
        val s = SalesScorer.score(b, ps, at)
        // Konum: cips 2×2×3=12, sos 1×1×3=3, deterjan ağır üstte 3×1×1=3, kola premium üstte 2×2×1=4, su 1×2×2=4, sünger 1×4×1=4 → 30
        assertEquals(30, s.of(SalesRule.POSITION))
        // Tamamlayıcı: cips–sos yan yana +6; cips–kola üst üste (kola 0-1, cips 0-1) +3 → 9
        assertEquals(9, s.of(SalesRule.COMPLEMENT))
        // Çakışma: sos–deterjan yan yana −8
        assertEquals(-8, s.of(SalesRule.CONFLICT))
        // Kategori: cips–sos +4; kola–su +4 → 8
        assertEquals(8, s.of(SalesRule.CATEGORY))
        // Marka: cips–sos A yan yana +3; kola(A)–cips(A) üst üste +3; deterjan(B)–sünger(B) üst üste (göz 3, sünger 0-3) +3 → 9
        assertEquals(9, s.of(SalesRule.BRAND))
        assertEquals(30 + 9 - 8 + 8 + 9, s.total)
        assertEquals(s.total, s.byProduct.sum())
        // Yerleşmemiş ürün katkı vermez.
        val partial = at.copyOf().also { it[2] = -1 }
        val t = SalesScorer.score(b, ps, partial)
        assertEquals(0, t.of(SalesRule.CONFLICT))
        assertEquals(27, t.of(SalesRule.POSITION))
    }

    @Test
    fun tablesAreSymmetricAndSensible() {
        for ((a, c) in SalesRules.complementPairs()) {
            assertTrue(SalesRules.complementary(a, c))
            assertTrue(SalesRules.complementary(c, a))
        }
        assertFalse(SalesRules.complementary(Kind.KOLA, Kind.KOLA))
        assertTrue(SalesRules.conflicting(Category.TEMIZLIK, Category.ICECEK))
        assertTrue(SalesRules.conflicting(Category.KAHVALTILIK, Category.TEMIZLIK))
        assertFalse(SalesRules.conflicting(Category.TEMIZLIK, Category.BAKIM))
        assertFalse(SalesRules.conflicting(Category.ICECEK, Category.ATISTIRMALIK))
        val normal = Product(0, Kind.KOLA, Brand.A, 1, 1, premium = false, heavy = false)
        assertEquals(3, SalesRules.rowFactor(normal, 1, 4))
        assertEquals(1, SalesRules.rowFactor(normal, 3, 4))
        assertEquals(2, SalesRules.rowFactor(normal, 0, 4))
        val heavy = Product(0, Kind.SU, Brand.A, 3, 1, premium = false, heavy = true)
        assertEquals(3, SalesRules.rowFactor(heavy, 3, 4))
        assertEquals(1, SalesRules.rowFactor(heavy, 1, 4))
        val premium = Product(0, Kind.BAL, Brand.A, 1, 1, premium = true, heavy = false)
        assertEquals(4, SalesRules.rowFactor(premium, 1, 3))
        assertEquals(1, SalesRules.rowFactor(premium, 2, 3))
    }

    @Test
    fun generationIsDeterministicAndTargetBeatsBaseline() {
        for (level in ReyonLevel.entries) {
            val a = ReyonSalesGenerator.generate(9L, level)
            val b = ReyonSalesGenerator.generate(9L, level)
            assertEquals(a.target, b.target)
            assertEquals(a.targetAt.toList(), b.targetAt.toList())
            assertEquals(a.products.map { it.demand }, b.products.map { it.demand })
        }
        assertNotEquals(ReyonSalesGenerator.dailySeed(20_700L), ReyonSalesGenerator.dailySeed(20_701L))
        assertNotEquals(ReyonSalesGenerator.dailySeed(20_700L), ReyonAuditGenerator.dailySeed(20_700L))
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val s = ReyonSalesGenerator.generate(seed, level)
            assertTrue("tohum $seed $level hedef ${s.target} < taban ${s.baseline}", s.target >= s.baseline)
            assertTrue(s.products.all { it.demand in 1..3 })
            assertEquals(s.board.slots, s.products.sumOf { it.facings })
            // Hedef diziliş geçerli bir tam doluluk ve puanı hedefe eşit.
            var covered = 0
            for (p in s.products) {
                val m = s.board.mask(s.targetAt[p.id], p.facings)
                assertEquals("çakışma", 0, m and covered)
                covered = covered or m
            }
            assertEquals((1 shl s.board.slots) - 1, covered)
            assertEquals(s.target, SalesScorer.score(s.board, s.products, s.targetAt).total)
        }
    }

    /** İyileştirici çoğu turda başlangıcı gerçekten aşar; iş bütçesi sabit. */
    @Test
    fun optimizerImprovesAndStaysWithinBudget() {
        var improved = 0
        for (seed in seeds) {
            val s = ReyonSalesGenerator.generate(seed, ReyonLevel.ORTA)
            if (s.target > s.baseline) improved++
            val budget = (ReyonSalesGenerator.iterations(ReyonLevel.ORTA) + 1).toLong() * ReyonSalesGenerator.RESTARTS + 1
            assertTrue("değerlendirme ${s.evaluations}", s.evaluations <= budget)
        }
        assertTrue("iyileşen tur $improved/${seeds.count()}", improved >= seeds.count() * 8 / 10)
    }

    @Test
    fun stateMechanicsAndStars() {
        val s = ReyonSalesGenerator.generate(4L, ReyonLevel.KOLAY)
        val st = ReyonSalesState(s)
        assertEquals(0, st.score().total)
        val p0 = s.products[0]
        assertTrue(st.place(0, 1, 0))
        assertEquals(SalesRules.position(p0, 1, s.rows), st.score().total)
        assertFalse("çakışma", st.place(1, 1, 0))
        assertTrue(st.undo())
        assertFalse(st.isPlaced(0))
        assertFalse("eksikken tamamlanamaz", st.finish())
        st.applyTarget()
        assertTrue(st.isComplete)
        assertEquals(s.target, st.score().total)
        assertEquals(3, s.stars(st.score().total))
        assertEquals(2, s.stars((s.target * 0.9).toInt() + 1))
        assertEquals(1, s.stars((s.target * 0.75).toInt() + 1))
        assertEquals(0, s.stars(0))
        assertTrue(st.finish())
        assertFalse("mühürlüyken taşınamaz", st.place(0, 0, 0))
        st.reopen()
        assertTrue(st.remove(0))
        val snap = st.snapshot()
        val t = ReyonSalesState(s)
        t.restore(snap)
        assertEquals(st.placedCount, t.placedCount)
        assertEquals(st.score().total, t.score().total)
    }
}
