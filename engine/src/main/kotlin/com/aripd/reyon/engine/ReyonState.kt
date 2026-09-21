package com.aripd.reyon.engine

/** İpucu önerisi: yanlış yerleşmiş bir ürün ya da sıradaki mantıklı yerleştirme. */
sealed interface ReyonHint {
    data class Wrong(val product: Int) : ReyonHint

    data class Place(val product: Int, val row: Int, val col: Int, val technique: Int) : ReyonHint
}

/**
 * Oynanış durumu: yerleşimler, verili (kilitli) ürünler, geri alma geçmişi,
 * ipucu durumları ve ipucu üretimi. Ürünler kimliğiyle anılır.
 */
class ReyonState(val puzzle: ReyonPuzzle) {
    private val board = puzzle.board
    private val n = puzzle.products.size
    private val at = IntArray(n) { -1 }
    private val locked = BooleanArray(n)
    private val history = ArrayList<IntArray>()

    var hintsUsed = 0
        private set

    init {
        for (g in puzzle.givens) {
            at[g.product] = board.idx(g.row, g.col)
            locked[g.product] = true
        }
    }

    fun placement(product: Int): Placement? {
        val idx = at[product]
        return if (idx < 0) null else Placement(board.rowOf(idx), board.colOf(idx))
    }

    fun isPlaced(product: Int): Boolean = at[product] >= 0

    fun isLocked(product: Int): Boolean = locked[product]

    /** Gözdeki ürün; boşsa −1. */
    fun occupant(row: Int, col: Int): Int {
        val bit = 1 shl board.idx(row, col)
        for (p in 0 until n) if (at[p] >= 0 && board.mask(at[p], puzzle.products[p].facings) and bit != 0) return p
        return -1
    }

    val placedCount: Int get() = at.count { it >= 0 }
    val isComplete: Boolean get() = placedCount == n
    val isSolved: Boolean get() = isComplete && puzzle.clues.all { status(it) == ClueStatus.SATISFIED }
    val canUndo: Boolean get() = history.isNotEmpty()

    fun canPlace(product: Int, row: Int, col: Int): Boolean {
        if (locked[product] || row !in 0 until board.rows || col < 0) return false
        val f = puzzle.products[product].facings
        if (col + f > board.cols) return false
        val mask = board.mask(board.idx(row, col), f)
        for (q in 0 until n) {
            if (q == product || at[q] < 0) continue
            if (board.mask(at[q], puzzle.products[q].facings) and mask != 0) return false
        }
        return true
    }

    /** Ürünü yerleştirir (yerleşmişse taşır). */
    fun place(product: Int, row: Int, col: Int): Boolean {
        if (!canPlace(product, row, col)) return false
        val idx = board.idx(row, col)
        if (at[product] == idx) return true
        history += intArrayOf(product, at[product], idx)
        at[product] = idx
        return true
    }

    fun remove(product: Int): Boolean {
        if (locked[product] || at[product] < 0) return false
        history += intArrayOf(product, at[product], -1)
        at[product] = -1
        return true
    }

    fun undo(): Boolean {
        val h = history.removeLastOrNull() ?: return false
        at[h[0]] = h[1]
        return true
    }

    fun status(clue: Clue): ClueStatus = Rules.status(clue, board, puzzle.products, at)

    /** Yerleşmiş ama çözümden farklı duran (kilitsiz) ürünler. */
    fun mistakes(): List<Int> = (0 until n).filter { at[it] >= 0 && !locked[it] && at[it] != puzzle.solutionIdx[it] }

    /**
     * İpucu: önce yanlış duran bir ürün, yoksa mevcut yerleşimlerden mantıkla
     * çıkan sıradaki ürün. Tamamlanmış bulmacada null.
     */
    fun hint(): ReyonHint? {
        val wrong = mistakes()
        if (wrong.isNotEmpty()) return ReyonHint.Wrong(wrong.first())
        if (isComplete) return null
        val extra = (0 until n).filter { at[it] >= 0 && !locked[it] }
            .map { Clue.Placed(it, board.rowOf(at[it]), board.colOf(at[it])) }
        val trace = ArrayList<DeductionStep>()
        ReyonDeducer(Propagator(board, puzzle.products, puzzle.clues + extra)).run(Tech.ALL, trace = trace)
        val step = trace.firstOrNull { at[it.product] < 0 }
        if (step != null) return ReyonHint.Place(step.product, board.rowOf(step.idx), board.colOf(step.idx), step.technique)
        val p = (0 until n).first { at[it] < 0 }
        val s = puzzle.solution(p)
        return ReyonHint.Place(p, s.row, s.col, 0)
    }

    /** İpucunu uygular ve sayacı artırır. */
    fun applyHint(hint: ReyonHint): Boolean {
        val ok = when (hint) {
            is ReyonHint.Wrong -> remove(hint.product)
            is ReyonHint.Place -> place(hint.product, hint.row, hint.col)
        }
        if (ok) hintsUsed++
        return ok
    }

    /** Kalıcı kayıt için yerleşimler (ürün → göz indeksi, −1 boş). */
    fun snapshot(): IntArray = at.copyOf()

    fun restore(snapshot: IntArray, hints: Int = 0) {
        if (snapshot.size != n) return
        for (p in 0 until n) if (!locked[p]) at[p] = -1
        for (p in 0 until n) {
            if (locked[p] || snapshot[p] < 0) continue
            val row = board.rowOf(snapshot[p])
            val col = board.colOf(snapshot[p])
            if (canPlace(p, row, col)) at[p] = snapshot[p]
        }
        history.clear()
        hintsUsed = hints
    }
}
