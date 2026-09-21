package com.aripd.reyon.engine

import kotlin.math.max
import kotlin.math.min

/**
 * İpucu değerlendirme. [status] kısmi yerleşimde de çalışır: ilgili ürünlerin
 * yerleşmiş kısmı ipucuyla çelişiyorsa VIOLATED, hepsi yerleşmiş ve tutuyorsa
 * SATISFIED, yoksa PENDING.
 */
object Rules {

    /** İpucunun ilgilendirdiği ürün kimlikleri. */
    fun involved(clue: Clue, products: List<Product>): IntArray {
        fun ids(pred: (Product) -> Boolean): IntArray = products.filter(pred).map { it.id }.toIntArray()
        return when (clue) {
            is Clue.Placed -> intArrayOf(clue.product)
            is Clue.OnShelf -> intArrayOf(clue.product)
            is Clue.InSlot -> intArrayOf(clue.product)
            is Clue.AtEdge -> intArrayOf(clue.product)
            is Clue.Adjacent -> intArrayOf(clue.a, clue.b)
            is Clue.LeftOf -> intArrayOf(clue.a, clue.b)
            is Clue.SameShelf -> intArrayOf(clue.a, clue.b)
            is Clue.DifferentShelf -> intArrayOf(clue.a, clue.b)
            is Clue.Above -> intArrayOf(clue.a, clue.b)
            Clue.EyeLevel -> ids { it.premium }
            Clue.HeavyBottom -> ids { it.heavy }
            is Clue.CategoryBlock -> ids { it.category == clue.category }
            is Clue.CategoriesApart -> ids { it.category == clue.a || it.category == clue.b }
            is Clue.BrandVertical -> ids { it.brand == clue.brand }
            is Clue.SizeFlow -> ids { it.brand == clue.brand }
        }
    }

    /** Tam yerleşimde ipucu tutuyor mu (ilgili ürünlerin hepsi yerleşmiş olmalı). */
    fun holds(clue: Clue, board: Board, products: List<Product>, at: IntArray): Boolean =
        involved(clue, products).all { at[it] >= 0 } && status(clue, board, products, at) == ClueStatus.SATISFIED

    fun overlaps(c1: Int, e1: Int, c2: Int, e2: Int): Boolean = c1 <= e2 && c2 <= e1

    /** Kısmi yerleşimde durum; [at] yerleşmemiş ürün için −1. */
    fun status(clue: Clue, board: Board, products: List<Product>, at: IntArray): ClueStatus {
        fun placed(p: Int) = at[p] >= 0
        fun row(p: Int) = board.rowOf(at[p])
        fun col(p: Int) = board.colOf(at[p])
        fun end(p: Int) = col(p) + products[p].facings - 1
        fun verdict(ok: Boolean) = if (ok) ClueStatus.SATISFIED else ClueStatus.VIOLATED
        fun pair(a: Int, b: Int, ok: () -> Boolean): ClueStatus =
            if (!placed(a) || !placed(b)) ClueStatus.PENDING else verdict(ok())
        fun single(p: Int, ok: () -> Boolean): ClueStatus = if (!placed(p)) ClueStatus.PENDING else verdict(ok())

        return when (clue) {
            is Clue.Placed -> single(clue.product) { at[clue.product] == board.idx(clue.row, clue.col) }
            is Clue.OnShelf -> single(clue.product) { row(clue.product) == clue.row }
            is Clue.InSlot -> single(clue.product) { clue.col in col(clue.product)..end(clue.product) }
            is Clue.AtEdge -> single(clue.product) { if (clue.left) col(clue.product) == 0 else end(clue.product) == board.cols - 1 }
            is Clue.Adjacent -> pair(clue.a, clue.b) {
                row(clue.a) == row(clue.b) && (end(clue.a) + 1 == col(clue.b) || end(clue.b) + 1 == col(clue.a))
            }
            is Clue.LeftOf -> pair(clue.a, clue.b) { row(clue.a) == row(clue.b) && end(clue.a) < col(clue.b) }
            is Clue.SameShelf -> pair(clue.a, clue.b) { row(clue.a) == row(clue.b) }
            is Clue.DifferentShelf -> pair(clue.a, clue.b) { row(clue.a) != row(clue.b) }
            is Clue.Above -> pair(clue.a, clue.b) {
                row(clue.a) == row(clue.b) - 1 && overlaps(col(clue.a), end(clue.a), col(clue.b), end(clue.b))
            }
            Clue.EyeLevel -> classRow(products.filter { it.premium }, board.eyeRow, at, board)
            Clue.HeavyBottom -> classRow(products.filter { it.heavy }, board.bottomRow, at, board)
            is Clue.CategoryBlock -> {
                val members = products.filter { it.category == clue.category }
                val placedMembers = members.filter { placed(it.id) }
                if (placedMembers.isEmpty()) return ClueStatus.PENDING
                val r = row(placedMembers[0].id)
                if (placedMembers.any { row(it.id) != r }) return ClueStatus.VIOLATED
                val width = members.sumOf { it.facings }
                val lo = placedMembers.minOf { col(it.id) }
                val hi = placedMembers.maxOf { end(it.id) }
                if (hi - lo + 1 > width) return ClueStatus.VIOLATED
                // Bloğun içinde yabancı ürün olamaz.
                for (q in products) {
                    if (q.category == clue.category || !placed(q.id) || row(q.id) != r) continue
                    if (overlaps(col(q.id), end(q.id), lo, hi)) return ClueStatus.VIOLATED
                }
                if (placedMembers.size < members.size) return ClueStatus.PENDING
                val sorted = placedMembers.sortedBy { col(it.id) }
                verdict(sorted.zipWithNext().all { (x, y) -> end(x.id) + 1 == col(y.id) })
            }
            is Clue.CategoriesApart -> {
                val xs = products.filter { it.category == clue.a }
                val ys = products.filter { it.category == clue.b }
                for (x in xs) for (y in ys) {
                    if (placed(x.id) && placed(y.id) && row(x.id) == row(y.id)) return ClueStatus.VIOLATED
                }
                if ((xs + ys).all { placed(it.id) }) ClueStatus.SATISFIED else ClueStatus.PENDING
            }
            is Clue.BrandVertical -> {
                val members = products.filter { it.brand == clue.brand }
                val placedMembers = members.filter { placed(it.id) }
                if (placedMembers.isEmpty()) return ClueStatus.PENDING
                val lo = placedMembers.minOf { row(it.id) }
                val hi = placedMembers.maxOf { row(it.id) }
                if (hi - lo > members.size - 1) return ClueStatus.VIOLATED
                if (placedMembers.size < members.size) return ClueStatus.PENDING
                verdict(brandVerticalHolds(members.map { it.id }, board, products, at))
            }
            is Clue.SizeFlow -> {
                val members = products.filter { it.brand == clue.brand }
                for (x in members) for (y in members) {
                    if (x.size >= y.size || !placed(x.id) || !placed(y.id)) continue
                    if (row(x.id) == row(y.id) && end(x.id) >= col(y.id)) return ClueStatus.VIOLATED
                }
                if (members.all { placed(it.id) }) ClueStatus.SATISFIED else ClueStatus.PENDING
            }
        }
    }

    private fun classRow(members: List<Product>, row: Int, at: IntArray, board: Board): ClueStatus {
        var pending = false
        for (m in members) {
            if (at[m.id] < 0) {
                pending = true
                continue
            }
            if (board.rowOf(at[m.id]) != row) return ClueStatus.VIOLATED
        }
        return if (pending) ClueStatus.PENDING else ClueStatus.SATISFIED
    }

    /** Marka dikey blok: raflar ardışık ve komşu raflardaki birleşik açıklıklar kesişir. Hepsi yerleşmiş olmalı. */
    fun brandVerticalHolds(members: List<Int>, board: Board, products: List<Product>, at: IntArray): Boolean {
        if (members.isEmpty()) return true
        val lo = members.minOf { board.rowOf(at[it]) }
        val hi = members.maxOf { board.rowOf(at[it]) }
        val spanLo = IntArray(board.rows) { Int.MAX_VALUE }
        val spanHi = IntArray(board.rows) { Int.MIN_VALUE }
        for (m in members) {
            val r = board.rowOf(at[m])
            val c = board.colOf(at[m])
            spanLo[r] = min(spanLo[r], c)
            spanHi[r] = max(spanHi[r], c + products[m].facings - 1)
        }
        for (r in lo..hi) if (spanLo[r] == Int.MAX_VALUE) return false
        for (r in lo until hi) if (!overlaps(spanLo[r], spanHi[r], spanLo[r + 1], spanHi[r + 1])) return false
        return true
    }
}
