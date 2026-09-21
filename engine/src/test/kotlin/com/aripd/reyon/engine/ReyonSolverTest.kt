package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReyonSolverTest {

    private val b = Fixture.board
    private val ps = Fixture.products

    private fun count(clues: List<Clue>): Int = ReyonSolver(Propagator(b, ps, clues)).count(1_000_000)

    @Test
    fun solverAgreesWithBruteForceOnFixture() {
        val none = emptyList<Clue>()
        assertEquals(Fixture.bruteCount(b, ps, none), count(none))
        val some = listOf(Clue.EyeLevel, Clue.HeavyBottom, Clue.CategoryBlock(Category.ATISTIRMALIK), Clue.SizeFlow(Brand.B))
        assertEquals(Fixture.bruteCount(b, ps, some), count(some))
        val tight = some + listOf(Clue.AtEdge(0, true), Clue.Above(4, 6), Clue.BrandVertical(Brand.C), Clue.Adjacent(2, 3))
        assertEquals(Fixture.bruteCount(b, ps, tight), count(tight))
        assertTrue("sıkı ipuçları çözümü azaltmalı", count(tight) < count(some))
    }

    @Test
    fun solverAgreesWithBruteForceOnGeneratedPuzzles() {
        for (seed in 1L..12L) {
            val puzzle = ReyonGenerator.generate(seed, ReyonLevel.KOLAY)
            val board = puzzle.board
            assertEquals("tohum $seed tek çözüm", 1, Fixture.bruteCount(board, puzzle.products, puzzle.clues))
            // İpuçlarının bir kısmıyla: sayılar yine eşleşmeli.
            val half = puzzle.clues.take(puzzle.clues.size / 2)
            val brute = Fixture.bruteCount(board, puzzle.products, half, limit = 5000)
            val solver = ReyonSolver(Propagator(board, puzzle.products, half)).count(5000)
            assertEquals("tohum $seed yarım ipucu", brute, solver)
        }
    }

    @Test
    fun solveReturnsTheSolution() {
        for (seed in 1L..10L) for (level in ReyonLevel.entries) {
            val puzzle = ReyonGenerator.generate(seed, level)
            val sol = ReyonSolver(Propagator(puzzle.board, puzzle.products, puzzle.clues)).solve()
            assertNotNull(sol)
            assertEquals(puzzle.solutionIdx.toList(), sol!!.toList())
        }
    }

    @Test
    fun propagationNeverDropsTheSolution() {
        val techSets = listOf(Tech.UNARY, Tech.BASIC, Tech.BASIC or Tech.COVER, Tech.BASIC or Tech.CAPACITY, Tech.ALL)
        for (seed in 1L..15L) for (level in ReyonLevel.entries) {
            val puzzle = ReyonGenerator.generate(seed, level)
            val prop = Propagator(puzzle.board, puzzle.products, puzzle.clues)
            for (tech in techSets) {
                val dom = ReyonDeducer(prop).run(tech)
                assertNotNull("tohum $seed $level teknik $tech çelişki verdi", dom)
                for (p in puzzle.products.indices) {
                    assertTrue("tohum $seed $level ürün $p çözümü kaybetti", dom!![p] and (1 shl puzzle.solutionIdx[p]) != 0)
                }
            }
        }
    }

    @Test
    fun deducerSolvesWhatItClaimsAndStopsOtherwise() {
        val puzzle = ReyonGenerator.generate(3L, ReyonLevel.ORTA)
        val prop = Propagator(puzzle.board, puzzle.products, puzzle.clues)
        assertTrue(ReyonDeducer(prop).solves(Tech.ALL))
        // Hiç ipucu olmadan yayılım çözemez.
        val empty = Propagator(puzzle.board, puzzle.products, emptyList())
        assertTrue(!ReyonDeducer(empty).solves(Tech.ALL))
    }

    @Test
    fun randomClueSubsetsAgreeWithBruteForce() {
        val rng = Random(9)
        val puzzle = ReyonGenerator.generate(5L, ReyonLevel.KOLAY)
        val board = puzzle.board
        val cands = ReyonGenerator.candidates(ReyonGenerator.Layout(board, puzzle.products, puzzle.solutionIdx), Random(1))
        repeat(20) {
            val subset = cands.filter { rng.nextFloat() < 0.15f }
            val brute = Fixture.bruteCount(board, puzzle.products, subset, limit = 3000)
            val solver = ReyonSolver(Propagator(board, puzzle.products, subset)).count(3000)
            assertEquals("alt küme $it", brute, solver)
        }
    }
}
