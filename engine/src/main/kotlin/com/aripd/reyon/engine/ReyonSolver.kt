package com.aripd.reyon.engine

import kotlin.math.abs

/** Çıkarım teknikleri (bit maskesi). Oyuncunun gördüğü bilgiyle yapılan akıl yürütme adımları. */
object Tech {
    /** Tekil ipucu: ürünün olası yerlerini doğrudan eler. */
    const val UNARY = 1

    /** İkili ipucu: eşi için hiç uyumlu yer kalmayan yerleri eler. */
    const val BINARY = 2

    /** Örtü: her göz tam bir ürünle dolar (gizli tek, rezerv göz). */
    const val COVER = 4

    /** Kapasite: raf genişliği, kesin yerleşenlerin toplamı. */
    const val CAPACITY = 8

    /** Çoklu ipucu (marka dikey blok): birlikte olası mı diye arama. */
    const val NARY = 16

    const val BASIC = UNARY or BINARY
    const val ALL = 31

    fun names(mask: Int): List<String> = buildList {
        if (mask and UNARY != 0) add("tekil")
        if (mask and BINARY != 0) add("ikili")
        if (mask and COVER != 0) add("örtü")
        if (mask and CAPACITY != 0) add("kapasite")
        if (mask and NARY != 0) add("çoklu")
    }
}

internal class Unary(val p: Int, val ok: (Int) -> Boolean)

internal class Binary(val a: Int, val b: Int, val ok: (Int, Int) -> Boolean)

internal class Nary(val ps: IntArray, val ok: (IntArray) -> Boolean)

/** Çıkarım izi: ürün tek yere indiğinde hangi teknikle indiği. */
class DeductionStep(val product: Int, val idx: Int, val technique: Int)

/**
 * Kısıt yayılımı. Her ürünün alanı, olası başlangıç gözlerinin bit maskesidir.
 * [propagate] verilen tekniklerle sabit noktaya kadar eler; çelişkide false.
 * Çakışmama (iki ürün aynı gözü kaplamaz) yapısal kısıttır ve tüm
 * tekniklerde geçerlidir.
 */
internal class Propagator(val board: Board, val products: List<Product>, clues: List<Clue>) {
    val n = products.size
    val unary = ArrayList<Unary>()
    val binary = ArrayList<Binary>()
    val nary = ArrayList<Nary>()
    private val all = IntArray(n) { board.placements(products[it].facings) }
    private val rowPl = Array(n) { p -> IntArray(board.rows) { r -> board.placementsInRow(r, products[p].facings) } }

    /** Son yayılımda en az bir eleme yapan teknikler. */
    var used = 0
        private set

    init {
        for (c in clues) compile(c)
    }

    fun initial(): IntArray = all.copyOf()

    private fun sm(p: Int, idx: Int): Int = board.mask(idx, products[p].facings)

    private fun row(idx: Int) = board.rowOf(idx)
    private fun col(idx: Int) = board.colOf(idx)
    private fun end(p: Int, idx: Int) = board.colOf(idx) + products[p].facings - 1

    private fun compile(clue: Clue) {
        val ps = products
        when (clue) {
            is Clue.Placed -> unary += Unary(clue.product) { it == board.idx(clue.row, clue.col) }
            is Clue.OnShelf -> unary += Unary(clue.product) { row(it) == clue.row }
            is Clue.InSlot -> unary += Unary(clue.product) { clue.col in col(it)..end(clue.product, it) }
            is Clue.AtEdge -> unary += Unary(clue.product) {
                if (clue.left) col(it) == 0 else end(clue.product, it) == board.cols - 1
            }
            is Clue.Adjacent -> binary += Binary(clue.a, clue.b) { ia, ib ->
                row(ia) == row(ib) && (end(clue.a, ia) + 1 == col(ib) || end(clue.b, ib) + 1 == col(ia))
            }
            is Clue.LeftOf -> binary += Binary(clue.a, clue.b) { ia, ib -> row(ia) == row(ib) && end(clue.a, ia) < col(ib) }
            is Clue.SameShelf -> binary += Binary(clue.a, clue.b) { ia, ib -> row(ia) == row(ib) }
            is Clue.DifferentShelf -> binary += Binary(clue.a, clue.b) { ia, ib -> row(ia) != row(ib) }
            is Clue.Above -> binary += Binary(clue.a, clue.b) { ia, ib ->
                row(ia) == row(ib) - 1 && Rules.overlaps(col(ia), end(clue.a, ia), col(ib), end(clue.b, ib))
            }
            Clue.EyeLevel -> for (p in ps) if (p.premium) unary += Unary(p.id) { row(it) == board.eyeRow }
            Clue.HeavyBottom -> for (p in ps) if (p.heavy) unary += Unary(p.id) { row(it) == board.bottomRow }
            is Clue.CategoryBlock -> {
                val members = ps.filter { it.category == clue.category }
                val width = members.sumOf { it.facings }
                // Çakışmama ve tam doluluk verildiğinde ikili açıklık sınırı blok kuralına denktir:
                // en soldaki ve en sağdaki çift bloğun tüm açıklığını taşır.
                for (i in members.indices) for (j in i + 1 until members.size) {
                    val a = members[i].id
                    val b = members[j].id
                    binary += Binary(a, b) { ia, ib ->
                        row(ia) == row(ib) && maxOf(end(a, ia), end(b, ib)) - minOf(col(ia), col(ib)) + 1 <= width
                    }
                }
            }
            is Clue.CategoriesApart -> {
                for (x in ps) if (x.category == clue.a) for (y in ps) if (y.category == clue.b) {
                    binary += Binary(x.id, y.id) { ia, ib -> row(ia) != row(ib) }
                }
            }
            is Clue.BrandVertical -> {
                val members = ps.filter { it.brand == clue.brand }.map { it.id }
                if (members.size == 2) {
                    val a = members[0]
                    val b = members[1]
                    binary += Binary(a, b) { ia, ib ->
                        val d = abs(row(ia) - row(ib))
                        d == 0 || (d == 1 && Rules.overlaps(col(ia), end(a, ia), col(ib), end(b, ib)))
                    }
                } else if (members.size > 2) {
                    val span = members.size - 1
                    for (i in members.indices) for (j in i + 1 until members.size) {
                        binary += Binary(members[i], members[j]) { ia, ib -> abs(row(ia) - row(ib)) <= span }
                    }
                    val ids = members.toIntArray()
                    val at = IntArray(n) { -1 }
                    nary += Nary(ids) { assign ->
                        for (k in ids.indices) at[ids[k]] = assign[k]
                        Rules.brandVerticalHolds(members, board, ps, at)
                    }
                }
            }
            is Clue.SizeFlow -> {
                val members = ps.filter { it.brand == clue.brand }
                for (x in members) for (y in members) {
                    if (x.size >= y.size) continue
                    binary += Binary(x.id, y.id) { ia, ib -> row(ia) != row(ib) || end(x.id, ia) < col(ib) }
                }
            }
        }
    }

    /**
     * Sabit noktaya kadar yayılım. [trace] verilirse tek yere inen ürünler
     * sırayla kaydedilir. Dönüş: çelişki yoksa true.
     */
    fun propagate(dom: IntArray, tech: Int, trace: MutableList<DeductionStep>? = null): Boolean {
        used = 0
        var changed = true
        var wiped = false

        fun update(p: Int, nd: Int, technique: Int) {
            if (nd == dom[p]) return
            dom[p] = nd
            changed = true
            used = used or technique
            if (nd == 0) {
                wiped = true
            } else if (trace != null && nd and (nd - 1) == 0) {
                trace += DeductionStep(p, nd.countTrailingZeroBits(), technique)
            }
        }

        fun revise(x: Int, y: Int, ok: (Int, Int) -> Boolean): Int {
            var keep = 0
            var mx = dom[x]
            while (mx != 0) {
                val ix = mx.countTrailingZeroBits()
                mx = mx and (mx - 1)
                val smx = sm(x, ix)
                var my = dom[y]
                while (my != 0) {
                    val iy = my.countTrailingZeroBits()
                    my = my and (my - 1)
                    if (sm(y, iy) and smx == 0 && ok(ix, iy)) {
                        keep = keep or (1 shl ix)
                        break
                    }
                }
            }
            return keep
        }

        if (trace != null) for (p in 0 until n) if (dom[p] != 0 && dom[p] and (dom[p] - 1) == 0) {
            trace += DeductionStep(p, dom[p].countTrailingZeroBits(), 0)
        }

        while (changed && !wiped) {
            changed = false

            if (tech and Tech.UNARY != 0) for (u in unary) {
                var keep = 0
                var m = dom[u.p]
                while (m != 0) {
                    val i = m.countTrailingZeroBits()
                    m = m and (m - 1)
                    if (u.ok(i)) keep = keep or (1 shl i)
                }
                update(u.p, keep, Tech.UNARY)
                if (wiped) return false
            }

            if (tech and Tech.BINARY != 0) for (b in binary) {
                update(b.a, revise(b.a, b.b) { ia, ib -> b.ok(ia, ib) }, Tech.BINARY)
                if (wiped) return false
                update(b.b, revise(b.b, b.a) { ib, ia -> b.ok(ia, ib) }, Tech.BINARY)
                if (wiped) return false
            }

            if (tech and Tech.COVER != 0) {
                val union = IntArray(n)
                val certain = IntArray(n)
                for (p in 0 until n) {
                    var u = 0
                    var c = -1
                    var m = dom[p]
                    while (m != 0) {
                        val i = m.countTrailingZeroBits()
                        m = m and (m - 1)
                        val s = sm(p, i)
                        u = u or s
                        c = c and s
                    }
                    union[p] = u
                    certain[p] = if (dom[p] == 0) 0 else c
                }
                for (s in 0 until board.slots) {
                    val bit = 1 shl s
                    var count = 0
                    var only = -1
                    for (p in 0 until n) if (union[p] and bit != 0) {
                        count++
                        only = p
                    }
                    if (count == 0) return false
                    if (count == 1) {
                        var keep = 0
                        var m = dom[only]
                        while (m != 0) {
                            val i = m.countTrailingZeroBits()
                            m = m and (m - 1)
                            if (sm(only, i) and bit != 0) keep = keep or (1 shl i)
                        }
                        update(only, keep, Tech.COVER)
                        if (wiped) return false
                    }
                }
                for (p in 0 until n) {
                    if (certain[p] == 0) continue
                    for (q in 0 until n) {
                        if (q == p) continue
                        var keep = 0
                        var m = dom[q]
                        while (m != 0) {
                            val i = m.countTrailingZeroBits()
                            m = m and (m - 1)
                            if (sm(q, i) and certain[p] == 0) keep = keep or (1 shl i)
                        }
                        update(q, keep, Tech.COVER)
                        if (wiped) return false
                    }
                }
            }

            if (tech and Tech.CAPACITY != 0) for (r in 0 until board.rows) {
                var certainSum = 0
                var possibleSum = 0
                for (p in 0 until n) {
                    val inRow = dom[p] and rowPl[p][r]
                    if (inRow == 0) continue
                    possibleSum += products[p].facings
                    if (dom[p] == inRow) certainSum += products[p].facings
                }
                if (certainSum > board.cols || possibleSum < board.cols) return false
                val free = board.cols - certainSum
                for (p in 0 until n) {
                    val inRow = dom[p] and rowPl[p][r]
                    if (inRow == 0 || dom[p] == inRow) continue
                    if (products[p].facings > free) {
                        update(p, dom[p] and rowPl[p][r].inv(), Tech.CAPACITY)
                        if (wiped) return false
                    }
                }
            }

            if (tech and Tech.NARY != 0 || nary.isNotEmpty()) for (c in nary) {
                if (!gac(c, dom, tech and Tech.NARY != 0, ::update)) return false
                if (wiped) return false
            }
        }
        return !wiped
    }

    /**
     * Çoklu kısıt: tüm üyeler tek yerdeyse tam denetim; [search] açıksa ve
     * alanlar küçükse her yer için "diğerleriyle birlikte olası mı" araması.
     */
    private fun gac(c: Nary, dom: IntArray, search: Boolean, update: (Int, Int, Int) -> Unit): Boolean {
        val k = c.ps.size
        val assign = IntArray(k)
        var allSingle = true
        var product = 1L
        for (i in 0 until k) {
            val d = dom[c.ps[i]]
            if (d == 0) return false
            val size = d.countOneBits()
            if (size != 1) allSingle = false
            product *= size
            assign[i] = d.countTrailingZeroBits()
        }
        if (allSingle) return c.ok(assign)
        if (!search || product > GAC_BUDGET) return true
        for (i in 0 until k) {
            var keep = 0
            var m = dom[c.ps[i]]
            while (m != 0) {
                val idx = m.countTrailingZeroBits()
                m = m and (m - 1)
                assign[i] = idx
                if (exists(c, dom, assign, i, 0, sm(c.ps[i], idx))) keep = keep or (1 shl idx)
            }
            update(c.ps[i], keep, Tech.NARY)
            if (keep == 0) return false
        }
        return true
    }

    private fun exists(c: Nary, dom: IntArray, assign: IntArray, fixed: Int, pos: Int, covered: Int): Boolean {
        if (pos == c.ps.size) return c.ok(assign)
        if (pos == fixed) return exists(c, dom, assign, fixed, pos + 1, covered)
        var m = dom[c.ps[pos]]
        while (m != 0) {
            val idx = m.countTrailingZeroBits()
            m = m and (m - 1)
            val s = sm(c.ps[pos], idx)
            if (s and covered != 0) continue
            assign[pos] = idx
            if (exists(c, dom, assign, fixed, pos + 1, covered or s)) return true
        }
        return false
    }

    companion object {
        const val GAC_BUDGET = 40_000L
    }
}

/** Geri izlemeli çözücü: çözüm sayar (üst sınıra kadar) ve iş sayacı tutar. */
internal class ReyonSolver(private val prop: Propagator) {
    var nodes = 0L
        private set

    fun count(limit: Int = 2, dom0: IntArray = prop.initial()): Int = search(dom0.copyOf(), limit)

    fun solve(dom0: IntArray = prop.initial()): IntArray? {
        val out = arrayOfNulls<IntArray>(1)
        first(dom0.copyOf(), out)
        return out[0]
    }

    private fun search(dom: IntArray, limit: Int): Int {
        nodes++
        if (!prop.propagate(dom, Tech.ALL)) return 0
        val v = pick(dom)
        if (v < 0) return 1
        var total = 0
        var m = dom[v]
        while (m != 0) {
            val idx = m.countTrailingZeroBits()
            m = m and (m - 1)
            val d = dom.copyOf()
            d[v] = 1 shl idx
            total += search(d, limit - total)
            if (total >= limit) return total
        }
        return total
    }

    private fun first(dom: IntArray, out: Array<IntArray?>): Boolean {
        nodes++
        if (!prop.propagate(dom, Tech.ALL)) return false
        val v = pick(dom)
        if (v < 0) {
            out[0] = IntArray(dom.size) { dom[it].countTrailingZeroBits() }
            return true
        }
        var m = dom[v]
        while (m != 0) {
            val idx = m.countTrailingZeroBits()
            m = m and (m - 1)
            val d = dom.copyOf()
            d[v] = 1 shl idx
            if (first(d, out)) return true
        }
        return false
    }

    /** En dar alanlı çözülmemiş ürün; hepsi tekse −1. */
    private fun pick(dom: IntArray): Int {
        var best = -1
        var bestSize = Int.MAX_VALUE
        for (p in dom.indices) {
            val s = dom[p].countOneBits()
            if (s > 1 && s < bestSize) {
                best = p
                bestSize = s
            }
        }
        return best
    }
}

/**
 * Oyuncunun gördüğü bilgiyle çalışan çıkarım çözücüsü: yalnızca yayılım,
 * arama yok. Sonuna kadar gidiyorsa bulmaca tahminsiz çözülebilir.
 */
internal class ReyonDeducer(private val prop: Propagator) {
    var used = 0
        private set

    /** Sabit noktadaki alanlar; çelişkide null. */
    fun run(tech: Int, dom0: IntArray = prop.initial(), trace: MutableList<DeductionStep>? = null): IntArray? {
        val dom = dom0.copyOf()
        val ok = prop.propagate(dom, tech, trace)
        used = prop.used
        return if (ok) dom else null
    }

    fun solves(tech: Int, dom0: IntArray = prop.initial()): Boolean {
        val dom = run(tech, dom0) ?: return false
        return dom.all { it != 0 && it and (it - 1) == 0 }
    }
}
