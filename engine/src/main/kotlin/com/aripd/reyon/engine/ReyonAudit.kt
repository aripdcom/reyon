package com.aripd.reyon.engine

import kotlin.random.Random

/** Planogram uyum sapması türleri. */
enum class DeviationKind {
    /** İki ürün yer değiştirmiş. */
    SWAP,

    /** Bir yüz boş kalmış (stok yok). */
    GAP,

    /** Planda olmayan bir ürün konmuş. */
    FOREIGN,

    /** Doğru ürün, yanlış marka. */
    BRAND,

    /** Doğru ürün, yanlış boy. */
    SIZE,

    /** Ürün komşusunun gözüne taşmış. */
    SPILL,
}

/** Raftaki bir ürün bloğu; gerçek rafta nitelikler plandan sapmış olabilir. */
class ShelfItem(val product: Product, val row: Int, val col: Int, val facings: Int) {
    val end: Int get() = col + facings - 1
}

/**
 * Bir sapma: türü, gerçek rafta dokununca sayılacak gözler (bit maskesi) ve
 * ilgili plan ürünleri. [partner] yer değişiminde ikinci ürün, [foreign]
 * yabancı ürünün türü.
 */
class Deviation(
    val kind: DeviationKind,
    val slotMask: Int,
    val products: IntArray,
    val partner: Int = -1,
    val foreign: Kind? = null,
)

/** Denetim: referans plan, sapmalı gerçek raf ve sapmalar. */
class ReyonAudit internal constructor(
    val seed: Long,
    val level: ReyonLevel,
    val plan: List<Product>,
    internal val planAt: IntArray,
    val items: List<ShelfItem>,
    val deviations: List<Deviation>,
) {
    val rows: Int get() = level.rows
    val cols: Int get() = level.cols
    val board = Board(rows, cols)

    /** Plan, blok listesi olarak. */
    val planItems: List<ShelfItem> = plan.map { ShelfItem(it, board.rowOf(planAt[it.id]), board.colOf(planAt[it.id]), it.facings) }

    fun itemAt(row: Int, col: Int): ShelfItem? = items.firstOrNull { it.row == row && col in it.col..it.end }

    fun planItemAt(row: Int, col: Int): ShelfItem? = planItems.firstOrNull { it.row == row && col in it.col..it.end }

    /** Gözü kapsayan sapmanın dizini; yoksa −1. */
    fun deviationAt(row: Int, col: Int): Int {
        val bit = 1 shl board.idx(row, col)
        return deviations.indexOfFirst { it.slotMask and bit != 0 }
    }
}

/**
 * Denetim üretimi: planogram yapısında bir plan örneklenir, sonra seviyeye
 * göre K ayrık sapma uygulanır. Değişmez: plan ile gerçek raf yalnızca
 * sapma gözlerinde ayrışır ve her sapmanın en az bir ayrışan gözü vardır.
 */
object ReyonAuditGenerator {

    const val MAX_ATTEMPTS = 80

    fun count(level: ReyonLevel): Int = when (level) {
        ReyonLevel.KOLAY -> 2
        ReyonLevel.ORTA -> 3
        ReyonLevel.ZOR -> 5
    }

    fun kinds(level: ReyonLevel): List<DeviationKind> = when (level) {
        ReyonLevel.KOLAY -> listOf(DeviationKind.SWAP, DeviationKind.GAP, DeviationKind.FOREIGN)
        ReyonLevel.ORTA -> listOf(DeviationKind.SWAP, DeviationKind.GAP, DeviationKind.FOREIGN, DeviationKind.BRAND, DeviationKind.SPILL)
        ReyonLevel.ZOR -> DeviationKind.entries
    }

    fun dailySeed(epochDay: Long): Long = ReyonGenerator.mix(epochDay, 0x44, 0x45)

    fun generate(seed: Long, level: ReyonLevel): ReyonAudit {
        val rng = Random(ReyonGenerator.mix(seed, level.ordinal + 1, 0x44))
        repeat(MAX_ATTEMPTS) {
            val layout = ReyonGenerator.sampleLayout(rng, level) ?: return@repeat
            val audit = mutate(seed, layout, level, rng) ?: return@repeat
            return audit
        }
        error("Reyon: denetim üretilemedi (tohum $seed, $level)")
    }

    private fun mutate(seed: Long, layout: ReyonGenerator.Layout, level: ReyonLevel, rng: Random): ReyonAudit? {
        val board = layout.board
        val plan = layout.products
        val n = plan.size
        val items = ArrayList<ShelfItem?>(n)
        for (p in plan) items += ShelfItem(p, board.rowOf(layout.at[p.id]), board.colOf(layout.at[p.id]), p.facings)
        val used = BooleanArray(n)
        val deviations = ArrayList<Deviation>()
        val wanted = count(level)
        val order = kinds(level).shuffled(rng)
        var k = 0
        var guard = 0
        while (deviations.size < wanted && guard++ < 60) {
            val kind = order[k++ % order.size]
            val d = apply(kind, board, plan, items, used, rng) ?: continue
            deviations += d
        }
        if (deviations.size < wanted) return null
        val audit = ReyonAudit(seed, level, plan, layout.at, items.filterNotNull(), deviations)
        return if (verify(audit)) audit else null
    }

    private fun block(board: Board, it: ShelfItem): Int = board.mask(board.idx(it.row, it.col), it.facings)

    private fun free(items: List<ShelfItem?>, used: BooleanArray): List<Int> =
        items.indices.filter { !used[it] && items[it] != null }

    private fun apply(kind: DeviationKind, board: Board, plan: List<Product>, items: MutableList<ShelfItem?>, used: BooleanArray, rng: Random): Deviation? {
        val candidates = free(items, used)
        if (candidates.isEmpty()) return null
        when (kind) {
            DeviationKind.SWAP -> {
                val pairs = ArrayList<Pair<Int, Int>>()
                for (i in candidates.indices) for (j in i + 1 until candidates.size) {
                    val a = candidates[i]
                    val b = candidates[j]
                    if (items[a]!!.facings == items[b]!!.facings) pairs += a to b
                }
                if (pairs.isEmpty()) return null
                val (a, b) = pairs.random(rng)
                val ia = items[a]!!
                val ib = items[b]!!
                items[a] = ShelfItem(ia.product, ib.row, ib.col, ia.facings)
                items[b] = ShelfItem(ib.product, ia.row, ia.col, ib.facings)
                used[a] = true
                used[b] = true
                return Deviation(DeviationKind.SWAP, block(board, ia) or block(board, ib), intArrayOf(a, b), partner = b)
            }
            DeviationKind.GAP -> {
                val a = candidates.random(rng)
                val it = items[a]!!
                val mask: Int
                if (it.facings >= 2) {
                    items[a] = ShelfItem(it.product, it.row, it.col, it.facings - 1)
                    mask = 1 shl board.idx(it.row, it.end)
                } else {
                    items[a] = null
                    mask = 1 shl board.idx(it.row, it.col)
                }
                used[a] = true
                return Deviation(DeviationKind.GAP, mask, intArrayOf(a))
            }
            DeviationKind.FOREIGN -> {
                val a = candidates.random(rng)
                val it = items[a]!!
                val p = it.product
                val present = plan.map { it.kind }.toSet()
                val sameCategory = Kind.entries.filter { it.category == p.category && it !in present }
                val pool = if (sameCategory.isNotEmpty() && rng.nextFloat() < 0.7f) sameCategory else Kind.entries.filter { it !in present }
                if (pool.isEmpty()) return null
                val kindNew = pool.random(rng)
                items[a] = ShelfItem(Product(p.id, kindNew, p.brand, p.size, p.facings, p.premium, p.heavy), it.row, it.col, it.facings)
                used[a] = true
                return Deviation(DeviationKind.FOREIGN, block(board, it), intArrayOf(a), foreign = kindNew)
            }
            DeviationKind.BRAND -> {
                val a = candidates.random(rng)
                val it = items[a]!!
                val p = it.product
                val brand = Brand.entries.filter { it != p.brand }.random(rng)
                items[a] = ShelfItem(Product(p.id, p.kind, brand, p.size, p.facings, p.premium, p.heavy), it.row, it.col, it.facings)
                used[a] = true
                return Deviation(DeviationKind.BRAND, block(board, it), intArrayOf(a))
            }
            DeviationKind.SIZE -> {
                val a = candidates.random(rng)
                val it = items[a]!!
                val p = it.product
                val size = (1..3).filter { it != p.size }.random(rng)
                items[a] = ShelfItem(Product(p.id, p.kind, p.brand, size, p.facings, p.premium, p.heavy), it.row, it.col, it.facings)
                used[a] = true
                return Deviation(DeviationKind.SIZE, block(board, it), intArrayOf(a))
            }
            DeviationKind.SPILL -> {
                // (a solda, b sağda) bitişik çiftler; büyüyen taraf rastgele.
                val options = ArrayList<Triple<Int, Int, Boolean>>()
                for (a in candidates) for (b in candidates) {
                    if (a == b) continue
                    val ia = items[a]!!
                    val ib = items[b]!!
                    if (ia.row != ib.row || ia.end + 1 != ib.col) continue
                    if (ib.facings >= 2) options += Triple(a, b, true)
                    if (ia.facings >= 2) options += Triple(a, b, false)
                }
                if (options.isEmpty()) return null
                val (a, b, growLeft) = options.random(rng)
                val ia = items[a]!!
                val ib = items[b]!!
                val mask: Int
                if (growLeft) {
                    items[a] = ShelfItem(ia.product, ia.row, ia.col, ia.facings + 1)
                    items[b] = ShelfItem(ib.product, ib.row, ib.col + 1, ib.facings - 1)
                    mask = (1 shl board.idx(ia.row, ib.col)) or (1 shl board.idx(ia.row, ia.end))
                } else {
                    items[a] = ShelfItem(ia.product, ia.row, ia.col, ia.facings - 1)
                    items[b] = ShelfItem(ib.product, ib.row, ib.col - 1, ib.facings + 1)
                    mask = (1 shl board.idx(ia.row, ia.end)) or (1 shl board.idx(ia.row, ib.col))
                }
                used[a] = true
                used[b] = true
                return Deviation(DeviationKind.SPILL, mask, intArrayOf(a, b), partner = b)
            }
        }
    }

    private fun signature(item: ShelfItem?): String =
        if (item == null) "" else "${item.product.kind}|${item.product.brand}|${item.product.size}|${item.product.premium}|${item.product.heavy}"

    /** Plan ile gerçek raf arasında ayrışan gözler (bit maskesi). */
    fun diffMask(audit: ReyonAudit): Int {
        var mask = 0
        for (r in 0 until audit.rows) for (c in 0 until audit.cols) {
            if (signature(audit.planItemAt(r, c)) != signature(audit.itemAt(r, c))) mask = mask or (1 shl audit.board.idx(r, c))
        }
        return mask
    }

    /** Değişmezler: bloklar sınırda ve çakışmasız, maskeler ayrık, fark ⊆ maskeler, her maske ayrışıyor. */
    fun verify(audit: ReyonAudit): Boolean {
        val board = audit.board
        var covered = 0
        for (it in audit.items) {
            if (it.facings < 1 || it.col < 0 || it.end >= board.cols || it.row !in 0 until board.rows) return false
            val m = board.mask(board.idx(it.row, it.col), it.facings)
            if (m and covered != 0) return false
            covered = covered or m
        }
        var union = 0
        for (d in audit.deviations) {
            if (d.slotMask == 0 || d.slotMask and union != 0) return false
            union = union or d.slotMask
        }
        val diff = diffMask(audit)
        if (diff and union.inv() != 0) return false
        return audit.deviations.all { it.slotMask and diff != 0 }
    }
}

sealed interface AuditTap {
    data class Found(val index: Int) : AuditTap

    data object Already : AuditTap

    data object Miss : AuditTap
}

/** Denetim oynanışı: bulunan sapmalar, hatalar, ipuçları. */
class ReyonAuditState(val audit: ReyonAudit) {
    private val found = BooleanArray(audit.deviations.size)
    private val order = ArrayList<Int>()

    var mistakes = 0
        private set
    var hintsUsed = 0
        private set

    val foundCount: Int get() = found.count { it }
    val isComplete: Boolean get() = foundCount == found.size

    /** Bulunan sapmaların dizinleri, bulunuş sırasıyla (geri yüklemede dizin sırası). */
    val foundOrder: List<Int> get() = order

    fun isFound(index: Int): Boolean = found[index]

    fun tap(row: Int, col: Int): AuditTap {
        if (isComplete) return AuditTap.Already
        val i = audit.deviationAt(row, col)
        if (i < 0) {
            mistakes++
            return AuditTap.Miss
        }
        if (found[i]) return AuditTap.Already
        mark(i)
        return AuditTap.Found(i)
    }

    /** Bulunmamış ilk sapmayı açar; dizinini döndürür. */
    fun hint(): Int? {
        val i = found.indexOfFirst { !it }
        if (i < 0) return null
        mark(i)
        hintsUsed++
        return i
    }

    private fun mark(i: Int) {
        found[i] = true
        order += i
    }

    fun snapshot(): IntArray {
        var mask = 0
        for (i in found.indices) if (found[i]) mask = mask or (1 shl i)
        return intArrayOf(mask, mistakes, hintsUsed)
    }

    fun restore(snapshot: IntArray) {
        if (snapshot.size < 3) return
        order.clear()
        for (i in found.indices) {
            found[i] = snapshot[0] and (1 shl i) != 0
            if (found[i]) order += i
        }
        mistakes = snapshot[1].coerceAtLeast(0)
        hintsUsed = snapshot[2].coerceAtLeast(0)
    }
}
