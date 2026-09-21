package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReyonRulesTest {

    private val b = Fixture.board
    private val ps = Fixture.products
    private val full = Fixture.at

    private fun st(clue: Clue, at: IntArray = full) = Rules.status(clue, b, ps, at)
    private fun partial(vararg placed: Int): IntArray = IntArray(ps.size) { if (it in placed) full[it] else -1 }

    @Test
    fun unaryClues() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.OnShelf(0, 0)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.OnShelf(0, 1)))
        assertEquals(ClueStatus.PENDING, st(Clue.OnShelf(0, 0), partial()))
        assertEquals(ClueStatus.SATISFIED, st(Clue.InSlot(1, 3)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.InSlot(1, 2)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.InSlot(1, 1)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.AtEdge(0, left = true)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.AtEdge(1, left = false)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.AtEdge(0, left = false)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.Placed(5, 2, 0)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.Placed(5, 2, 1)))
    }

    @Test
    fun binaryClues() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.Adjacent(0, 1)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.Adjacent(1, 0)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.Adjacent(2, 4)))
        assertEquals(ClueStatus.PENDING, st(Clue.Adjacent(2, 4), partial(2)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.LeftOf(2, 4)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.LeftOf(4, 2)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.LeftOf(0, 2)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.SameShelf(2, 3)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.SameShelf(0, 5)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.DifferentShelf(0, 5)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.Above(0, 2)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.Above(1, 2)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.Above(2, 0)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.Above(4, 6)))
    }

    @Test
    fun principleClues() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.EyeLevel))
        assertEquals(ClueStatus.SATISFIED, st(Clue.HeavyBottom))
        assertEquals(ClueStatus.PENDING, st(Clue.EyeLevel, partial(0, 1)))
        // Yüksek marjlı cips üst rafa taşınırsa ilke bozulur.
        val moved = full.copyOf().also { it[2] = b.idx(0, 0) }
        assertEquals(ClueStatus.VIOLATED, st(Clue.EyeLevel, moved))
        val heavyUp = full.copyOf().also { it[5] = b.idx(1, 0) }
        assertEquals(ClueStatus.VIOLATED, st(Clue.HeavyBottom, heavyUp))
        assertEquals(intArrayOf(2).toList(), Rules.involved(Clue.EyeLevel, ps).toList())
        assertEquals(intArrayOf(5).toList(), Rules.involved(Clue.HeavyBottom, ps).toList())
    }

    @Test
    fun categoryBlock() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.CategoryBlock(Category.ATISTIRMALIK)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.CategoryBlock(Category.ICECEK)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.CategoryBlock(Category.TEMIZLIK)))
        assertEquals(ClueStatus.PENDING, st(Clue.CategoryBlock(Category.ATISTIRMALIK), partial(2, 4)))
        // Farklı raf: bozuk.
        val split = partial(2, 4).also { it[4] = b.idx(0, 2) }
        assertEquals(ClueStatus.VIOLATED, st(Clue.CategoryBlock(Category.ATISTIRMALIK), split))
        // Bloğun içinde yabancı ürün: bozuk.
        val foreign = partial(2, 4).also { it[6] = b.idx(1, 1) }
        assertEquals(ClueStatus.VIOLATED, st(Clue.CategoryBlock(Category.ATISTIRMALIK), foreign))
        // Aynı rafta ama arada boşluk kalacak kadar uzak: bozuk (genişlik 4, açıklık 4 → uygun; 5 olsa bozuk).
        val wide = Board(3, 6)
        val at = IntArray(ps.size) { -1 }.also { it[2] = wide.idx(1, 0); it[4] = wide.idx(1, 4) }
        assertEquals(ClueStatus.VIOLATED, Rules.status(Clue.CategoryBlock(Category.ATISTIRMALIK), wide, ps, at))
    }

    @Test
    fun categoriesApart() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.CategoriesApart(Category.ICECEK, Category.TEMIZLIK)))
        val mixed = full.copyOf().also { it[6] = b.idx(0, 1) }
        assertEquals(ClueStatus.VIOLATED, st(Clue.CategoriesApart(Category.ICECEK, Category.TEMIZLIK), mixed))
        assertEquals(ClueStatus.PENDING, st(Clue.CategoriesApart(Category.ICECEK, Category.TEMIZLIK), partial(0, 1)))
    }

    @Test
    fun brandVertical() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.BrandVertical(Brand.A)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.BrandVertical(Brand.C)))
        // C'nin 1. raftaki üyesi en üste çıkarsa raflar ardışık olmaz.
        val gap = full.copyOf().also { it[4] = b.idx(0, 2) }
        assertEquals(ClueStatus.VIOLATED, st(Clue.BrandVertical(Brand.C), gap))
        // Komşu raflardaki açıklıklar kesişmezse bozuk: KRAKER sağ uçta, alt raf yalnızca solda.
        val narrow = Board(3, 6)
        val at = IntArray(ps.size) { -1 }.also {
            it[4] = narrow.idx(1, 4)
            it[5] = narrow.idx(2, 0)
            it[6] = narrow.idx(2, 4)
        }
        assertEquals(ClueStatus.SATISFIED, Rules.status(Clue.BrandVertical(Brand.C), narrow, ps, at))
        at[6] = narrow.idx(2, 3)
        assertEquals(ClueStatus.VIOLATED, Rules.status(Clue.BrandVertical(Brand.C), narrow, ps, at))
        assertEquals(ClueStatus.PENDING, st(Clue.BrandVertical(Brand.C), partial(4, 5)))
    }

    @Test
    fun sizeFlow() {
        assertEquals(ClueStatus.SATISFIED, st(Clue.SizeFlow(Brand.B)))
        assertEquals(ClueStatus.SATISFIED, st(Clue.SizeFlow(Brand.A)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.SizeFlow(Brand.C)))
        assertEquals(ClueStatus.VIOLATED, st(Clue.SizeFlow(Brand.C), partial(5, 6)))
        assertEquals(ClueStatus.PENDING, st(Clue.SizeFlow(Brand.C), partial(5)))
    }

    @Test
    fun holdsRequiresEveryoneAndOverlapsHelper() {
        assertTrue(Rules.holds(Clue.SameShelf(2, 3), b, ps, full))
        assertTrue(!Rules.holds(Clue.SameShelf(2, 3), b, ps, partial(2)))
        assertTrue(Rules.overlaps(0, 1, 1, 3))
        assertTrue(!Rules.overlaps(0, 1, 2, 3))
    }
}
