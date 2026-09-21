package com.aripd.reyon.engine

/** Testler için elle kurulmuş 3×4 düzen ve kaba kuvvet sayıcı. */
internal object Fixture {
    val board = Board(3, 4)

    // Satır 0: KOLA(A,b1,f2)@0  SU(A,b2,f2)@2
    // Satır 1: CİPS(B,b1,f1,★)@0  SOS(B,b2,f1)@1  KRAKER(C,b2,f2)@2
    // Satır 2: DETERJAN(C,b3,f3,ağır)@0  SÜNGER(C,b1,f1)@3
    val products = listOf(
        Product(0, Kind.KOLA, Brand.A, 1, 2, premium = false, heavy = false),
        Product(1, Kind.SU, Brand.A, 2, 2, premium = false, heavy = false),
        Product(2, Kind.CIPS, Brand.B, 1, 1, premium = true, heavy = false),
        Product(3, Kind.SOS, Brand.B, 2, 1, premium = false, heavy = false),
        Product(4, Kind.KRAKER, Brand.C, 2, 2, premium = false, heavy = false),
        Product(5, Kind.DETERJAN, Brand.C, 3, 3, premium = false, heavy = true),
        Product(6, Kind.SUNGER, Brand.C, 1, 1, premium = false, heavy = false),
    )
    val at = intArrayOf(board.idx(0, 0), board.idx(0, 2), board.idx(1, 0), board.idx(1, 1), board.idx(1, 2), board.idx(2, 0), board.idx(2, 3))

    fun puzzle(clues: List<Clue>): ReyonPuzzle =
        ReyonPuzzle(0L, ReyonLevel.KOLAY, products, clues, at, GenStats(1, 0, clues.size, 0))

    /** Tüm tam yerleşimleri sayar; yayılım yok, yalnızca çakışmama ve ipucu denetimi. */
    fun bruteCount(board: Board, products: List<Product>, clues: List<Clue>, limit: Int = Int.MAX_VALUE): Int {
        val at = IntArray(products.size) { -1 }
        var count = 0
        fun rec(p: Int, covered: Int) {
            if (count >= limit) return
            if (p == products.size) {
                if (clues.all { Rules.holds(it, board, products, at) }) count++
                return
            }
            var m = board.placements(products[p].facings)
            while (m != 0) {
                val idx = m.countTrailingZeroBits()
                m = m and (m - 1)
                val s = board.mask(idx, products[p].facings)
                if (s and covered != 0) continue
                at[p] = idx
                rec(p + 1, covered or s)
                at[p] = -1
            }
        }
        rec(0, 0)
        return count
    }
}
