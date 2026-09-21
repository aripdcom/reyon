package com.aripd.reyon.engine

import kotlin.math.min
import kotlin.random.Random

/**
 * Bulmaca üretimi: planogram yapısında bir çözüm düzeni örneklenir, doğru
 * olan tüm ipucu adayları türetilir, ağırlıklı bir sırayla tek çözüm
 * sağlanana dek ipucu eklenir, sonra çıkarılabilirlik korunarak ipuçları
 * teker teker atılır. Sonuç: tek çözümlü, oyuncunun gördüğü bilgiyle
 * tahminsiz çözülebilen, en küçük ipucu kümesi.
 */
object ReyonGenerator {

    const val MAX_ATTEMPTS = 120

    fun dailySeed(epochDay: Long): Long = mix(epochDay, 0x52, 0x45)

    fun mix(seed: Long, a: Int, b: Int = 0): Long {
        var z = seed xor (a.toLong() shl 32) xor b.toLong() xor -0x61C8864680B583EBL
        z = (z xor (z ushr 30)) * -0x40A7B892E31B1A47L
        z = (z xor (z ushr 27)) * -0x6B2FB644ECCEEE15L
        return z xor (z ushr 31)
    }

    fun generate(seed: Long, level: ReyonLevel): ReyonPuzzle {
        val rng = Random(mix(seed, level.ordinal + 1, 0x52))
        var best: Selection? = null
        var nodes = 0L
        var attempts = 0
        while (attempts < MAX_ATTEMPTS) {
            attempts++
            val layout = sampleLayout(rng, level) ?: continue
            val sel = select(layout, level, rng) ?: continue
            nodes += sel.nodes
            if (best == null || sel.clues.size < best.clues.size) best = sel
            if (sel.clues.size <= level.maxClues) break
        }
        val b = best ?: error("Reyon: $attempts denemede bulmaca üretilemedi (tohum $seed, $level)")
        return ReyonPuzzle(seed, level, b.products, b.clues, b.at, GenStats(attempts, nodes, b.candidates, b.techniques))
    }

    // -----------------------------------------------------------------------
    // Düzen örnekleme
    // -----------------------------------------------------------------------

    internal class Layout(val board: Board, val products: List<Product>, val at: IntArray)

    private class Seg(val row: Int, val col: Int, val width: Int) {
        var category: Category? = null
        var brands: List<Brand> = emptyList()
        val end: Int get() = col + width - 1
    }

    private class Spec(val kind: Kind, val brand: Brand, val size: Int, val facings: Int, val idx: Int)

    internal fun sampleLayout(rng: Random, level: ReyonLevel): Layout? {
        val board = Board(level.rows, level.cols)
        val cats = Category.entries.shuffled(rng).take(level.categories.random(rng))
        val pools = HashMap<Category, ArrayDeque<Kind>>()
        for (c in cats) pools[c] = ArrayDeque(Kind.entries.filter { it.category == c }.shuffled(rng))

        val segs = ArrayList<Seg>()
        for (r in 0 until board.rows) {
            val two = rng.nextFloat() < if (board.cols >= 5) 0.55f else 0.35f
            if (two) {
                val w1 = 2 + rng.nextInt(board.cols - 3)
                segs += Seg(r, 0, w1)
                segs += Seg(r, w1, board.cols - w1)
            } else {
                segs += Seg(r, 0, board.cols)
            }
        }
        if (segs.size < cats.size) return null
        fun above(seg: Seg): Seg? = segs.filter { it.row == seg.row - 1 && Rules.overlaps(it.col, it.end, seg.col, seg.end) }
            .maxByOrNull { min(it.end, seg.end) - maxOf(it.col, seg.col) }

        // Kategori: üstteki kesiti sürdürme eğilimi (dikey bant), her kategori en az bir kez.
        for (seg in segs) {
            val up = above(seg)
            seg.category = if (up != null && rng.nextFloat() < 0.5f) up.category else cats.random(rng)
        }
        for (c in cats) {
            if (segs.any { it.category == c }) continue
            val candidates = segs.filter { s -> segs.count { it.category == s.category } > 1 }
            if (candidates.isEmpty()) return null
            candidates.random(rng).category = c
        }

        val specs = ArrayList<Spec>()
        for (seg in segs) {
            val parts = composition(seg.width, rng, level)
            val up = above(seg)
            val upBrands = up?.brands ?: emptyList()
            val primary = if (upBrands.isNotEmpty() && rng.nextFloat() < 0.6f) upBrands.random(rng) else Brand.entries.random(rng)
            val second = if (parts.size >= 2 && rng.nextFloat() < 0.5f) Brand.entries.filter { it != primary }.random(rng) else null
            val split = if (second != null) 1 + rng.nextInt(parts.size - 1) else parts.size
            seg.brands = listOfNotNull(primary, second)
            var col = seg.col
            var size = 0
            var lastBrand: Brand? = null
            for ((i, f) in parts.withIndex()) {
                val brand = if (i < split) primary else second!!
                size = if (brand != lastBrand) {
                    1 + rng.nextInt(2)
                } else if (rng.nextFloat() < 0.8f) {
                    min(3, size + rng.nextInt(2))
                } else {
                    1 + rng.nextInt(3)
                }
                lastBrand = brand
                val kind = pools[seg.category]!!.removeFirstOrNull() ?: return null
                specs += Spec(kind, brand, size, f, board.idx(seg.row, col))
                col += f
            }
        }
        if (specs.size !in level.products) return null

        // Yüksek marj yalnızca göz hizasında, ağırlık yalnızca alt rafta: ilkeler düzende doğru olsun.
        val eye = specs.indices.filter { board.rowOf(specs[it].idx) == board.eyeRow }
        val premium = HashSet<Int>()
        for (i in eye) if (rng.nextFloat() < 0.5f) premium += i
        if (premium.isEmpty() && eye.isNotEmpty()) premium += eye.random(rng)
        val heavy = HashSet<Int>()
        for (i in specs.indices) {
            val s = specs[i]
            if (board.rowOf(s.idx) == board.bottomRow && s.kind.heavyProne && s.size >= 2 && rng.nextFloat() < 0.7f) heavy += i
        }
        val products = specs.mapIndexed { i, s -> Product(i, s.kind, s.brand, s.size, s.facings, i in premium, i in heavy) }
        return Layout(board, products, IntArray(specs.size) { specs[it].idx })
    }

    private fun composition(width: Int, rng: Random, level: ReyonLevel): List<Int> {
        val w1 = if (level == ReyonLevel.KOLAY) 1.0f else 0.8f
        val w3 = when (level) {
            ReyonLevel.KOLAY -> 1.0f
            ReyonLevel.ORTA -> 1.3f
            ReyonLevel.ZOR -> 1.4f
        }
        val out = ArrayList<Int>()
        var rem = width
        while (rem > 0) {
            val weights = floatArrayOf(w1, 1.6f, w3)
            val maxPart = min(3, rem)
            var total = 0f
            for (i in 0 until maxPart) total += weights[i]
            var pick = rng.nextFloat() * total
            var part = 1
            for (i in 0 until maxPart) {
                pick -= weights[i]
                if (pick <= 0f) {
                    part = i + 1
                    break
                }
            }
            out += part
            rem -= part
        }
        return out
    }

    // -----------------------------------------------------------------------
    // İpucu adayları ve seçim
    // -----------------------------------------------------------------------

    internal fun candidates(layout: Layout, rng: Random): List<Clue> {
        val b = layout.board
        val ps = layout.products
        val at = layout.at
        fun row(p: Int) = b.rowOf(at[p])
        fun col(p: Int) = b.colOf(at[p])
        fun end(p: Int) = col(p) + ps[p].facings - 1
        val out = ArrayList<Clue>()
        for (p in ps.indices) {
            out += Clue.OnShelf(p, row(p))
            out += Clue.InSlot(p, col(p) + rng.nextInt(ps[p].facings))
            if (col(p) == 0) out += Clue.AtEdge(p, true)
            if (end(p) == b.cols - 1) out += Clue.AtEdge(p, false)
        }
        for (x in ps.indices) for (y in ps.indices) {
            if (x == y) continue
            if (row(x) == row(y)) {
                if (end(x) + 1 == col(y)) out += Clue.Adjacent(x, y)
                else if (end(x) < col(y)) out += Clue.LeftOf(x, y)
                if (x < y) out += Clue.SameShelf(x, y)
            } else {
                if (x < y && ps[x].category != ps[y].category) out += Clue.DifferentShelf(x, y)
                if (row(x) == row(y) - 1 && Rules.overlaps(col(x), end(x), col(y), end(y))) out += Clue.Above(x, y)
            }
        }
        if (ps.any { it.premium }) out += Clue.EyeLevel
        if (ps.any { it.heavy }) out += Clue.HeavyBottom
        val present = Category.entries.filter { c -> ps.any { it.category == c } }
        for (c in present) {
            val clue = Clue.CategoryBlock(c)
            if (ps.count { it.category == c } >= 2 && Rules.holds(clue, b, ps, at)) out += clue
        }
        for (i in present.indices) for (j in i + 1 until present.size) {
            val clue = Clue.CategoriesApart(present[i], present[j])
            if (Rules.holds(clue, b, ps, at)) out += clue
        }
        for (br in Brand.entries) {
            val members = ps.filter { it.brand == br }
            if (members.size in 2..4 && members.map { row(it.id) }.distinct().size >= 2) {
                val clue = Clue.BrandVertical(br)
                if (Rules.holds(clue, b, ps, at)) out += clue
            }
            if (members.size >= 2) {
                val informative = members.any { x -> members.any { y -> x.id != y.id && row(x.id) == row(y.id) && x.size != y.size } }
                val clue = Clue.SizeFlow(br)
                if (informative && Rules.holds(clue, b, ps, at)) out += clue
            }
        }
        return out
    }

    internal fun weight(clue: Clue, level: ReyonLevel): Float = when (level) {
        ReyonLevel.KOLAY -> when (clue) {
            is Clue.OnShelf -> 3f
            is Clue.InSlot -> 2.5f
            is Clue.AtEdge -> 2f
            is Clue.Adjacent -> 2f
            is Clue.Above -> 1.5f
            is Clue.LeftOf -> 1.2f
            is Clue.SameShelf -> 1f
            is Clue.DifferentShelf -> 0.4f
            is Clue.Placed -> 0f
            else -> 1f
        }
        ReyonLevel.ORTA -> when (clue) {
            is Clue.OnShelf -> 1f
            is Clue.InSlot -> 0.8f
            is Clue.AtEdge -> 1.2f
            is Clue.Adjacent -> 2.2f
            is Clue.Above -> 1f
            is Clue.LeftOf -> 1.6f
            is Clue.SameShelf -> 1.2f
            is Clue.DifferentShelf -> 1f
            is Clue.Placed -> 0f
            else -> 3.5f
        }
        ReyonLevel.ZOR -> when (clue) {
            is Clue.OnShelf -> 0.5f
            is Clue.InSlot -> 0.3f
            is Clue.AtEdge -> 1f
            is Clue.Adjacent -> 2f
            is Clue.Above -> 0.7f
            is Clue.LeftOf -> 1.6f
            is Clue.SameShelf -> 1.2f
            is Clue.DifferentShelf -> 1.2f
            is Clue.Placed -> 0f
            else -> 4f
        }
    }

    /** Ağırlıkla orantılı olasılıkla, yerine koymadan sıralama. */
    private fun weightedOrder(items: List<Clue>, level: ReyonLevel, rng: Random): List<Clue> {
        val pool = items.toMutableList()
        val out = ArrayList<Clue>(items.size)
        while (pool.isNotEmpty()) {
            var total = 0f
            for (c in pool) total += weight(c, level)
            var pick = rng.nextFloat() * total
            var chosen = pool.size - 1
            for (i in pool.indices) {
                pick -= weight(pool[i], level)
                if (pick <= 0f) {
                    chosen = i
                    break
                }
            }
            out += pool.removeAt(chosen)
        }
        return out
    }

    internal class Selection(
        val products: List<Product>,
        val at: IntArray,
        val clues: List<Clue>,
        val nodes: Long,
        val candidates: Int,
        val techniques: Int,
    )

    internal fun select(layout: Layout, level: ReyonLevel, rng: Random): Selection? {
        val board = layout.board
        val products = layout.products
        val cands = candidates(layout, rng)
        val order = weightedOrder(cands, level, rng)
        val givens = products.indices.shuffled(rng).take(level.givens.random(rng))
            .map { Clue.Placed(it, board.rowOf(layout.at[it]), board.colOf(layout.at[it])) }
        var nodes = 0L

        fun unique(clues: List<Clue>): Boolean {
            val solver = ReyonSolver(Propagator(board, products, clues))
            val c = solver.count(2)
            nodes += solver.nodes
            return c == 1
        }

        fun deducible(clues: List<Clue>): Boolean =
            ReyonDeducer(Propagator(board, products, clues)).solves(level.techniques)

        val chosen = ArrayList<Clue>()
        var i = 0
        while (!unique(givens + chosen)) {
            if (i == order.size) return null
            chosen += order[i++]
        }
        while (!deducible(givens + chosen)) {
            if (i == order.size) return null
            chosen += order[i++]
        }
        // Kaldırma sırası: en az istenen önce; eşitlikte rastgele (anahtar bir kez hesaplanır).
        val keys = chosen.associateWith { weight(it, level) + rng.nextFloat() * 0.3f }
        val removal = chosen.sortedBy { keys[it]!! }
        for (c in removal) {
            val trial = chosen.filter { it !== c }
            if (unique(givens + trial) && deducible(givens + trial)) chosen.remove(c)
        }
        val all = givens + chosen
        val deducer = ReyonDeducer(Propagator(board, products, all))
        deducer.solves(level.techniques)
        return Selection(products, layout.at, all, nodes, cands.size, deducer.used)
    }
}
