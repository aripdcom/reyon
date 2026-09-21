package com.aripd.reyon.engine

import kotlin.math.exp
import kotlin.random.Random

/** Satış modu puanlama kuralları. */
enum class SalesRule {
    /** Konum: talep × yüz × raf çarpanı (göz hizası, ağırlar alta, yüksek marj). */
    POSITION,

    /** Tamamlayıcı ürünler yan yana (cips–sos gibi). */
    COMPLEMENT,

    /** Çakışma: temizlik ürünü gıdanın yanında. */
    CONFLICT,

    /** Kategori bloğu: aynı kategori yan yana. */
    CATEGORY,

    /** Marka bloğu: aynı marka yan yana ya da üst üste. */
    BRAND,
}

/** Puan tabloları; kurallar oyuncuya aynen anlatılır. */
object SalesRules {
    const val COMPLEMENT_SIDE = 6
    const val COMPLEMENT_STACK = 3
    const val CONFLICT_SIDE = -8
    const val CATEGORY_SIDE = 4
    const val BRAND_SIDE = 3
    const val BRAND_STACK = 3

    private val pairs: Set<Set<Kind>> = listOf(
        Kind.CIPS to Kind.SOS, Kind.CIPS to Kind.KOLA, Kind.KRAKER to Kind.PEYNIR, Kind.KRAKER to Kind.AYRAN,
        Kind.KURUYEMIS to Kind.GAZOZ, Kind.CIKOLATA to Kind.SUT, Kind.GOFRET to Kind.SUT, Kind.BISKUVI to Kind.SUT,
        Kind.BAL to Kind.TEREYAGI, Kind.RECEL to Kind.TEREYAGI, Kind.PEYNIR to Kind.ZEYTIN, Kind.YUMURTA to Kind.SUT,
        Kind.DETERJAN to Kind.YUMUSATICI, Kind.DETERJAN to Kind.CAMASIR_SUYU, Kind.BULASIK_DETERJANI to Kind.SUNGER,
        Kind.KAGIT_HAVLU to Kind.COP_POSETI, Kind.SAMPUAN to Kind.SABUN, Kind.TIRAS_KOPUGU to Kind.DEODORANT,
        Kind.LIMONATA to Kind.SODA, Kind.MEYVE_SUYU to Kind.BISKUVI,
    ).map { setOf(it.first, it.second) }.toSet()

    fun complementary(a: Kind, b: Kind): Boolean = a != b && setOf(a, b) in pairs

    /** Temizlik ürünü ile gıda yan yana olmaz. */
    fun conflicting(a: Category, b: Category): Boolean =
        (a == Category.TEMIZLIK && b.isFood) || (b == Category.TEMIZLIK && a.isFood)

    private val Category.isFood: Boolean
        get() = this == Category.ICECEK || this == Category.ATISTIRMALIK || this == Category.KAHVALTILIK

    /** Raf çarpanı: normal ürün göz hizasında 3, alt rafta 1, diğer raflarda 2; ağır yalnız altta 3; yüksek marj yalnız göz hizasında 4. */
    fun rowFactor(p: Product, row: Int, rows: Int): Int {
        val eye = 1
        val bottom = rows - 1
        return when {
            p.heavy -> if (row == bottom) 3 else 1
            p.premium -> if (row == eye) 4 else 1
            row == eye -> 3
            row == bottom -> 1
            else -> 2
        }
    }

    fun position(p: Product, row: Int, rows: Int): Int = p.demand * p.facings * rowFactor(p, row, rows)

    /** Tüm tamamlayıcı çiftler (arayüz açıklaması için). */
    fun complementPairs(): List<Pair<Kind, Kind>> = pairs.map { val l = it.toList(); l[0] to l[1] }
}

/** Puan dökümü: toplam, kural başına ve ürün başına. */
class SalesScore(val total: Int, val byRule: IntArray, val byProduct: IntArray) {
    fun of(rule: SalesRule): Int = byRule[rule.ordinal]
}

/** Kısmi ya da tam yerleşimi puanlar; yerleşmemiş ürün (−1) katkı vermez. */
object SalesScorer {
    fun score(board: Board, products: List<Product>, at: IntArray): SalesScore {
        val n = products.size
        val byRule = IntArray(SalesRule.entries.size)
        val byProduct = IntArray(n)
        fun add(rule: SalesRule, a: Int, b: Int, points: Int) {
            byRule[rule.ordinal] += points
            if (b < 0) {
                byProduct[a] += points
            } else {
                val half = points / 2
                byProduct[a] += half
                byProduct[b] += points - half
            }
        }
        for (p in products) {
            val idx = at[p.id]
            if (idx < 0) continue
            add(SalesRule.POSITION, p.id, -1, SalesRules.position(p, board.rowOf(idx), board.rows))
        }
        for (i in 0 until n) {
            val ia = at[i]
            if (ia < 0) continue
            val a = products[i]
            val ra = board.rowOf(ia)
            val ca = board.colOf(ia)
            val ea = ca + a.facings - 1
            for (j in i + 1 until n) {
                val ib = at[j]
                if (ib < 0) continue
                val b = products[j]
                val rb = board.rowOf(ib)
                val cb = board.colOf(ib)
                val eb = cb + b.facings - 1
                if (ra == rb) {
                    if (ea + 1 == cb || eb + 1 == ca) {
                        if (SalesRules.complementary(a.kind, b.kind)) add(SalesRule.COMPLEMENT, i, j, SalesRules.COMPLEMENT_SIDE)
                        if (SalesRules.conflicting(a.category, b.category)) add(SalesRule.CONFLICT, i, j, SalesRules.CONFLICT_SIDE)
                        if (a.category == b.category) add(SalesRule.CATEGORY, i, j, SalesRules.CATEGORY_SIDE)
                        if (a.brand == b.brand) add(SalesRule.BRAND, i, j, SalesRules.BRAND_SIDE)
                    }
                } else if (ra == rb - 1 || rb == ra - 1) {
                    if (Rules.overlaps(ca, ea, cb, eb)) {
                        if (SalesRules.complementary(a.kind, b.kind)) add(SalesRule.COMPLEMENT, i, j, SalesRules.COMPLEMENT_STACK)
                        if (a.brand == b.brand) add(SalesRule.BRAND, i, j, SalesRules.BRAND_STACK)
                    }
                }
            }
        }
        return SalesScore(byRule.sum(), byRule, byProduct)
    }
}

/** Satış hedefi: tavlamalı yerel arama; deterministik ve iş sayaçlı. */
object SalesOptimizer {
    class Result(val at: IntArray, val score: Int, val evaluations: Long)

    /**
     * Raflar sıralı ürün dizileri olarak tutulur; hamleler tam doluluğu korur:
     * eşit genişlikli iki ürünü değiştirme, raf içinde komşu iki bloğu
     * değiştirme, iki rafı değiştirme, bir ürünü başka raftaki eşit
     * genişlikli bitişik bir koşuyla değiştirme.
     */
    fun optimize(board: Board, products: List<Product>, start: IntArray, seed: Long, iterations: Int, restarts: Int): Result {
        val rng = Random(seed)
        var evaluations = 0L
        fun evaluate(at: IntArray): Int {
            evaluations++
            return SalesScorer.score(board, products, at).total
        }
        val rows = Array(board.rows) { ArrayList<Int>() }
        for (p in products.sortedBy { start[it.id] }) rows[board.rowOf(start[p.id])] += p.id
        fun layout(rs: Array<ArrayList<Int>>): IntArray {
            val at = IntArray(products.size) { -1 }
            for (r in rs.indices) {
                var col = 0
                for (id in rs[r]) {
                    at[id] = board.idx(r, col)
                    col += products[id].facings
                }
            }
            return at
        }
        var bestAt = layout(rows)
        var bestScore = evaluate(bestAt)
        for (restart in 0 until restarts) {
            val cur = Array(board.rows) { ArrayList(rows[it]) }
            if (restart > 0) shuffleRows(cur, products, rng)
            var curScore = evaluate(layout(cur))
            var temperature = 12f
            val cooling = exp(Math.log(0.05 / 12.0) / iterations).toFloat()
            for (it in 0 until iterations) {
                val trial = Array(board.rows) { r -> ArrayList(cur[r]) }
                if (!move(trial, products, rng)) continue
                val score = evaluate(layout(trial))
                val delta = score - curScore
                if (delta >= 0 || rng.nextFloat() < exp(delta / temperature)) {
                    for (r in cur.indices) cur[r] = trial[r]
                    curScore = score
                    if (score > bestScore) {
                        bestScore = score
                        bestAt = layout(cur)
                    }
                }
                temperature *= cooling
            }
        }
        return Result(bestAt, bestScore, evaluations)
    }

    private fun shuffleRows(rows: Array<ArrayList<Int>>, products: List<Product>, rng: Random) {
        repeat(rows.size * 6) { move(rows, products, rng) }
        for (r in rows) r.shuffle(rng)
    }

    private fun move(rows: Array<ArrayList<Int>>, products: List<Product>, rng: Random): Boolean {
        when (rng.nextInt(4)) {
            0 -> {
                // Eşit genişlikli iki ürün (raflar arası da olabilir).
                val all = rows.flatMap { it }
                val a = all.random(rng)
                val same = all.filter { it != a && products[it].facings == products[a].facings }
                if (same.isEmpty()) return false
                val b = same.random(rng)
                for (r in rows) for (i in r.indices) r[i] = when (r[i]) { a -> b; b -> a; else -> r[i] }
                return true
            }
            1 -> {
                // Raf içinde komşu iki blok.
                val r = rows.filter { it.size >= 2 }.randomOrNull(rng) ?: return false
                val i = rng.nextInt(r.size - 1)
                val t = r[i]
                r[i] = r[i + 1]
                r[i + 1] = t
                return true
            }
            2 -> {
                if (rows.size < 2) return false
                val i = rng.nextInt(rows.size)
                var j = rng.nextInt(rows.size - 1)
                if (j >= i) j++
                val t = rows[i]
                rows[i] = rows[j]
                rows[j] = t
                return true
            }
            else -> {
                // Bir ürün ↔ başka rafta eşit genişlikli bitişik koşu (2–3 ürün).
                if (rows.size < 2) return false
                val r1 = rng.nextInt(rows.size)
                var r2 = rng.nextInt(rows.size - 1)
                if (r2 >= r1) r2++
                val src = rows[r1]
                val dst = rows[r2]
                if (src.isEmpty() || dst.size < 2) return false
                val i = rng.nextInt(src.size)
                val width = products[src[i]].facings
                val options = ArrayList<IntRange>()
                for (s in dst.indices) {
                    var w = 0
                    for (e in s until dst.size) {
                        w += products[dst[e]].facings
                        if (w == width && e > s) options += s..e
                        if (w >= width) break
                    }
                }
                if (options.isEmpty()) return false
                val run = options.random(rng)
                val moved = ArrayList(dst.subList(run.first, run.last + 1))
                val single = src[i]
                for (k in run.last downTo run.first) dst.removeAt(k)
                dst.add(run.first, single)
                src.removeAt(i)
                src.addAll(i, moved)
                return true
            }
        }
    }
}

/** Satış turu: ürünler, hedef puan ve iyileştiricinin bulduğu diziliş. */
class ReyonSales internal constructor(
    val seed: Long,
    val level: ReyonLevel,
    val products: List<Product>,
    /** Örneklenen başlangıç planının puanı (rapor için). */
    val baseline: Int,
    val target: Int,
    internal val targetAt: IntArray,
    val evaluations: Long,
) {
    val rows: Int get() = level.rows
    val cols: Int get() = level.cols
    val board = Board(rows, cols)

    /** Hedefe göre yıldız: ≥ hedef 3, ≥ %90 2, ≥ %75 1. */
    fun stars(score: Int): Int = when {
        score >= target -> 3
        score >= target * 0.9 -> 2
        score >= target * 0.75 -> 1
        else -> 0
    }
}

object ReyonSalesGenerator {

    fun dailySeed(epochDay: Long): Long = ReyonGenerator.mix(epochDay, 0x53, 0x41)

    fun iterations(level: ReyonLevel): Int = when (level) {
        ReyonLevel.KOLAY -> 3000
        ReyonLevel.ORTA -> 6000
        ReyonLevel.ZOR -> 9000
    }

    const val RESTARTS = 4

    fun generate(seed: Long, level: ReyonLevel): ReyonSales {
        val rng = Random(ReyonGenerator.mix(seed, level.ordinal + 1, 0x53))
        var layout: ReyonGenerator.Layout? = null
        var guard = 0
        while (layout == null && guard++ < 200) layout = ReyonGenerator.sampleLayout(rng, level)
        val base = layout ?: error("Reyon: satış turu üretilemedi (tohum $seed, $level)")
        // Talep: rastgele 1–3; yüksek marjlılar talebi yüksek olmaya eğilimli.
        val products = base.products.map { p ->
            val demand = if (p.premium) 2 + rng.nextInt(2) else 1 + rng.nextInt(3)
            Product(p.id, p.kind, p.brand, p.size, p.facings, p.premium, p.heavy, demand)
        }
        val baseline = SalesScorer.score(base.board, products, base.at).total
        val opt = SalesOptimizer.optimize(base.board, products, base.at, rng.nextLong(), iterations(level), RESTARTS)
        return ReyonSales(seed, level, products, baseline, opt.score, opt.at, opt.evaluations)
    }
}

/** Satış oynanışı: serbest yerleştirme, puan, tamamlama. */
class ReyonSalesState(val sales: ReyonSales) {
    private val board = sales.board
    private val n = sales.products.size
    private val at = IntArray(n) { -1 }
    private val history = ArrayList<IntArray>()

    var finished = false
        private set

    fun placement(product: Int): Placement? {
        val idx = at[product]
        return if (idx < 0) null else Placement(board.rowOf(idx), board.colOf(idx))
    }

    fun isPlaced(product: Int): Boolean = at[product] >= 0

    fun occupant(row: Int, col: Int): Int {
        val bit = 1 shl board.idx(row, col)
        for (p in 0 until n) if (at[p] >= 0 && board.mask(at[p], sales.products[p].facings) and bit != 0) return p
        return -1
    }

    val placedCount: Int get() = at.count { it >= 0 }
    val isComplete: Boolean get() = placedCount == n
    val canUndo: Boolean get() = history.isNotEmpty()

    fun score(): SalesScore = SalesScorer.score(board, sales.products, at)

    fun canPlace(product: Int, row: Int, col: Int): Boolean {
        if (finished || row !in 0 until board.rows || col < 0) return false
        val f = sales.products[product].facings
        if (col + f > board.cols) return false
        val mask = board.mask(board.idx(row, col), f)
        for (q in 0 until n) {
            if (q == product || at[q] < 0) continue
            if (board.mask(at[q], sales.products[q].facings) and mask != 0) return false
        }
        return true
    }

    fun place(product: Int, row: Int, col: Int): Boolean {
        if (!canPlace(product, row, col)) return false
        val idx = board.idx(row, col)
        if (at[product] == idx) return true
        history += intArrayOf(product, at[product], idx)
        at[product] = idx
        return true
    }

    fun remove(product: Int): Boolean {
        if (finished || at[product] < 0) return false
        history += intArrayOf(product, at[product], -1)
        at[product] = -1
        return true
    }

    fun undo(): Boolean {
        if (finished) return false
        val h = history.removeLastOrNull() ?: return false
        at[h[0]] = h[1]
        return true
    }

    /** Tamamla: tam yerleşimde puanı mühürler. */
    fun finish(): Boolean {
        if (!isComplete) return false
        finished = true
        return true
    }

    /** Düzenlemeye geri dön. */
    fun reopen() {
        finished = false
    }

    /** Hedef dizilişi yükler (çözümü göster). */
    fun applyTarget() {
        for (p in 0 until n) at[p] = sales.targetAt[p]
        history.clear()
    }

    fun snapshot(): IntArray = at.copyOf()

    fun restore(snapshot: IntArray, finished: Boolean = false) {
        if (snapshot.size != n) return
        for (p in 0 until n) at[p] = -1
        for (p in 0 until n) {
            if (snapshot[p] < 0) continue
            val row = board.rowOf(snapshot[p])
            val col = board.colOf(snapshot[p])
            if (canPlace(p, row, col)) at[p] = snapshot[p]
        }
        history.clear()
        this.finished = finished && isComplete
    }
}
