package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReyonStateTest {

    private fun fixturePuzzle(): ReyonPuzzle = Fixture.puzzle(
        listOf(Clue.Placed(5, 2, 0), Clue.EyeLevel, Clue.Adjacent(0, 1), Clue.CategoryBlock(Category.ATISTIRMALIK), Clue.SizeFlow(Brand.B)),
    )

    @Test
    fun givensAreLockedAndPlacementRulesHold() {
        val s = ReyonState(fixturePuzzle())
        assertTrue(s.isLocked(5))
        assertEquals(Placement(2, 0), s.placement(5))
        assertFalse("kilitli ürün taşınamaz", s.place(5, 0, 0))
        assertFalse("kilitli ürün kaldırılamaz", s.remove(5))
        assertFalse("çakışma", s.place(6, 2, 2))
        assertFalse("sınır dışı", s.place(0, 0, 3))
        assertFalse("sınır dışı raf", s.place(0, 3, 0))
        assertTrue(s.place(6, 2, 3))
        assertEquals(6, s.occupant(2, 3))
        assertEquals(5, s.occupant(2, 1))
        assertEquals(-1, s.occupant(0, 0))
        assertTrue("taşıma", s.place(6, 0, 0))
        assertEquals(-1, s.occupant(2, 3))
        assertTrue(s.remove(6))
        assertEquals(1, s.placedCount)
    }

    @Test
    fun undoRestoresPreviousPlacements() {
        val s = ReyonState(fixturePuzzle())
        assertFalse(s.undo())
        s.place(0, 0, 0)
        s.place(0, 0, 2)
        s.remove(0)
        assertTrue(s.undo())
        assertEquals(Placement(0, 2), s.placement(0))
        assertTrue(s.undo())
        assertEquals(Placement(0, 0), s.placement(0))
        assertTrue(s.undo())
        assertNull(s.placement(0))
        assertFalse(s.canUndo)
    }

    @Test
    fun clueStatusFollowsPlacements() {
        val s = ReyonState(fixturePuzzle())
        val adj = Clue.Adjacent(0, 1)
        assertEquals(ClueStatus.PENDING, s.status(adj))
        s.place(0, 0, 0)
        assertEquals(ClueStatus.PENDING, s.status(adj))
        s.place(1, 1, 2)
        assertEquals(ClueStatus.VIOLATED, s.status(adj))
        s.place(1, 0, 2)
        assertEquals(ClueStatus.SATISFIED, s.status(adj))
        assertTrue(s.place(2, 2, 3))
        assertEquals(ClueStatus.VIOLATED, s.status(Clue.EyeLevel))
    }

    @Test
    fun solvedWhenEverythingMatchesTheSolution() {
        val p = fixturePuzzle()
        val s = ReyonState(p)
        for (pr in p.products) {
            if (s.isLocked(pr.id)) continue
            val sol = p.solution(pr.id)
            assertTrue(s.place(pr.id, sol.row, sol.col))
        }
        assertTrue(s.isComplete)
        assertTrue(s.isSolved)
        assertTrue(s.mistakes().isEmpty())
        assertNull(s.hint())
    }

    @Test
    fun hintFlagsMistakesThenLeadsToTheSolution() {
        for (seed in 1L..8L) for (level in ReyonLevel.entries) {
            val p = ReyonGenerator.generate(seed, level)
            val s = ReyonState(p)
            // Yanlış bir yerleşim: ipucu önce onu göstermeli.
            val free = p.products.first { !s.isLocked(it.id) }
            val sol = p.solution(free.id)
            val wrongRow = (sol.row + 1) % p.rows
            if (s.place(free.id, wrongRow, sol.col)) {
                val h = s.hint()
                assertTrue("tohum $seed $level yanlış yer bildirilmeli: $h", h is ReyonHint.Wrong && h.product == free.id)
                assertTrue(s.applyHint(h!!))
            }
            var guard = 0
            while (!s.isSolved) {
                val h = s.hint()
                assertNotNull("tohum $seed $level ipucu bitmemeli", h)
                assertTrue(h is ReyonHint.Place)
                assertTrue(s.applyHint(h!!))
                assertTrue("tohum $seed $level ipucu çözümle uyumlu", s.mistakes().isEmpty())
                assertTrue(++guard < 40)
            }
            assertTrue(s.hintsUsed >= p.products.size - p.givens.size)
        }
    }

    @Test
    fun snapshotRoundTrip() {
        val p = ReyonGenerator.generate(4L, ReyonLevel.ORTA)
        val s = ReyonState(p)
        val free = p.products.filter { !s.isLocked(it.id) }.take(3)
        for (pr in free) {
            val sol = p.solution(pr.id)
            s.place(pr.id, sol.row, sol.col)
        }
        val snap = s.snapshot()
        val t = ReyonState(p)
        t.restore(snap, hints = 2)
        assertEquals(s.placedCount, t.placedCount)
        assertEquals(2, t.hintsUsed)
        for (pr in free) assertEquals(s.placement(pr.id), t.placement(pr.id))
        assertFalse(t.canUndo)
    }
}
