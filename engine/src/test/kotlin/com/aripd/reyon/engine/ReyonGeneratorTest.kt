package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReyonGeneratorTest {

    private val seeds = 1L..30L

    private fun signature(p: ReyonPuzzle): String =
        p.products.joinToString { "${it.kind}/${it.brand}/${it.size}/${it.facings}/${it.premium}/${it.heavy}" } +
            "|" + p.clues.joinToString() + "|" + p.solutionIdx.joinToString()

    @Test
    fun sameSeedSamePuzzle() {
        for (level in ReyonLevel.entries) {
            assertEquals(signature(ReyonGenerator.generate(42L, level)), signature(ReyonGenerator.generate(42L, level)))
        }
        assertNotEquals(signature(ReyonGenerator.generate(1L, ReyonLevel.ORTA)), signature(ReyonGenerator.generate(2L, ReyonLevel.ORTA)))
    }

    @Test
    fun everyPuzzleHasExactlyOneSolutionAndAllCluesHold() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val p = ReyonGenerator.generate(seed, level)
            assertEquals("tohum $seed $level", 1, ReyonSolver(Propagator(p.board, p.products, p.clues)).count(3))
            for (c in p.clues) assertTrue("tohum $seed $level ipucu $c çözümde tutmalı", Rules.holds(c, p.board, p.products, p.solutionIdx))
        }
    }

    /** Değişmez: her bulmaca, seviyenin tekniklerine göre tahminsiz çözülür. */
    @Test
    fun everyPuzzleIsSolvableWithoutGuessing() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val p = ReyonGenerator.generate(seed, level)
            assertTrue("tohum $seed $level tahmin gerektirdi", ReyonDeducer(Propagator(p.board, p.products, p.clues)).solves(level.techniques))
        }
    }

    @Test
    fun puzzlesRespectLevelShape() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val p = ReyonGenerator.generate(seed, level)
            assertEquals(level.rows, p.rows)
            assertEquals(level.cols, p.cols)
            assertTrue("ürün sayısı ${p.products.size}", p.products.size in level.products)
            assertEquals(p.board.slots, p.products.sumOf { it.facings })
            assertTrue("verili ${p.givens.size}", p.givens.size in level.givens)
            assertEquals(p.products.size, p.products.map { it.kind }.distinct().size)
            assertTrue("kategori sayısı", p.products.map { it.category }.distinct().size in level.categories)
            assertTrue(p.products.all { it.facings in 1..3 && it.size in 1..3 })
            // Yüksek marjlılar göz hizasında, ağırlar alt rafta: düzen ilkeye uyar.
            for (pr in p.products) {
                if (pr.premium) assertEquals(p.board.eyeRow, p.solution(pr.id).row)
                if (pr.heavy) assertEquals(p.board.bottomRow, p.solution(pr.id).row)
            }
        }
    }

    /** İpucu sayısı okunabilir kalır: brif seviyenin sınırını aşmaz. */
    @Test
    fun briefStaysShort() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val p = ReyonGenerator.generate(seed, level)
            assertTrue("tohum $seed $level brif ${p.brief.size} > ${level.maxClues}", p.brief.size <= level.maxClues)
            assertTrue("brif boş olamaz", p.brief.isNotEmpty())
        }
    }

    /**
     * Üretim bütçesi: iş sayaçları makineden bağımsızdır; sınırlar gözlenen en
     * kötü değerin birkaç katı (bkz. docs/cihaz-testi.md).
     */
    @Test
    fun generationStaysWithinWorkBudget() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val p = ReyonGenerator.generate(seed, level)
            // Gözlenen en kötü: 26 deneme, 56 bin düğüm (40 tohum/zorluk). Sınırlar birkaç katı.
            assertTrue("tohum $seed $level deneme ${p.stats.attempts}", p.stats.attempts <= 80)
            assertTrue("tohum $seed $level düğüm ${p.stats.solverNodes}", p.stats.solverNodes <= 400_000L)
        }
    }

    @Test
    fun clueCandidatesAreAllTrueAndMeaningful() {
        for (seed in 1L..10L) {
            val p = ReyonGenerator.generate(seed, ReyonLevel.ZOR)
            val layout = ReyonGenerator.Layout(p.board, p.products, p.solutionIdx)
            val cands = ReyonGenerator.candidates(layout, kotlin.random.Random(seed))
            for (c in cands) {
                assertTrue("aday $c doğru olmalı", Rules.holds(c, p.board, p.products, p.solutionIdx))
                assertTrue("aday $c en az bir ürünü ilgilendirmeli", Rules.involved(c, p.products).isNotEmpty())
            }
            assertTrue(cands.none { it is Clue.Placed })
        }
    }

    @Test
    fun dailySeedIsStable() {
        assertEquals(ReyonGenerator.dailySeed(20_700L), ReyonGenerator.dailySeed(20_700L))
        assertNotEquals(ReyonGenerator.dailySeed(20_700L), ReyonGenerator.dailySeed(20_701L))
    }
}
