package com.aripd.reyon.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Sipariş modu (stok devri)
// ---------------------------------------------------------------------------
//
// Raf planı sabittir; oyun, hafta boyunca her gün ürün başına kaç koli sipariş
// edileceğidir. Oyuncu talebi aralık olarak (tahmin) görür; gerçekleşen talep
// tohumdan gelir, günlük modda herkes aynı haftayı yaşar.
//
// Gün akışı (gün t, sabah teslimat alınmış hâlde başlar):
//   1. siparişler verilir; [OrderItem.leadTime] gün sonra sabah gelir
//   2. gündüz: gerçekleşen talep raf stoğundan satılır (önce raf ömrü yakın
//      olan); karşılanamayan kısım kayıp satıştır
//   3. akşam: raf ömrü dolan birimler fire; kalan her birime bekleme bedeli
//   4. ertesi sabah: gelen teslimat rafa konur, rafa sığmayan iade edilir
//
// Puan = satış marjı − bekleme − fire − iade. Stok devri = satılan birim /
// ortalama akşam stoğu. Hedef, aynı tahminleri gören uzman politikanın
// (sipariş-üstü düzeyi, bkz. [OrderExpert]) aynı haftadaki kârıdır.

/** Puan kalemleri. */
enum class OrderRule { SALES, HOLDING, WASTE, RETURNS }

/** Sipariş modu sabitleri; ekran bunları oyuncuya aynen anlatır. */
object OrderRules {
    /** Yüz başına raf derinliği (birim). */
    const val DEPTH = 4

    /** Akşam stoğundaki her birime bekleme bedeli. */
    const val HOLDING = 1

    /** Rafa sığmayıp iade edilen her birimin bedeli. */
    const val RETURN_COST = 1

    const val MAX_LEAD = 2
    const val PROMO_FACTOR = 2.5f
    const val WEEKEND_FACTOR = 1.4f

    fun margin(p: Product): Int = if (p.premium) 6 else 4
    fun unitCost(p: Product): Int = if (p.premium) 4 else 2

    /** Raf ömrü (gün); 0 bozulmaz. Kolay'da bozulan ürün yok. */
    fun shelfLife(kind: Kind, level: ReyonLevel): Int = if (level == ReyonLevel.KOLAY) 0 else when (kind) {
        Kind.SUT, Kind.AYRAN -> 2
        Kind.YUMURTA, Kind.PEYNIR -> 3
        Kind.TEREYAGI -> 4
        else -> 0
    }

    fun caseSize(p: Product, capacity: Int): Int = minOf(capacity, if (p.heavy) 4 else intArrayOf(4, 6, 8)[p.kind.ordinal % 3])

    fun leadTime(p: Product, level: ReyonLevel): Int = if (level == ReyonLevel.ZOR && p.heavy) 2 else 1

    fun days(level: ReyonLevel): Int = when (level) {
        ReyonLevel.KOLAY -> 5
        ReyonLevel.ORTA -> 6
        ReyonLevel.ZOR -> 7
    }

    fun spread(level: ReyonLevel): Float = when (level) {
        ReyonLevel.KOLAY -> 0.25f
        ReyonLevel.ORTA -> 0.3f
        ReyonLevel.ZOR -> 0.35f
    }

    fun promoCount(level: ReyonLevel): Int = when (level) {
        ReyonLevel.KOLAY -> 0
        ReyonLevel.ORTA -> 1
        ReyonLevel.ZOR -> 2
    }

    /** Gün 0 pazartesi; cumartesi ve pazar hafta sonu. */
    fun isWeekend(day: Int): Boolean = day >= 5

    /** Hafta sonu talebi artan kategoriler. */
    fun weekendKind(kind: Kind): Boolean = kind.category == Category.ICECEK || kind.category == Category.ATISTIRMALIK
}

/** Sipariş modunda bir ürün: plan yeri, kapasite, koli, bedeller, raf ömrü, teslim süresi ve tahmin. */
class OrderItem(
    val product: Product,
    val row: Int,
    val col: Int,
    /** Rafın aldığı birim: yüz × derinlik. */
    val capacity: Int,
    /** Koli büyüklüğü (birim); siparişler koli katıdır ve koli rafa sığar. */
    val caseSize: Int,
    val margin: Int,
    val unitCost: Int,
    /** Raf ömrü (gün); 0 bozulmaz. */
    val shelfLife: Int,
    /** Sipariş kaç gün sonra sabah gelir. */
    val leadTime: Int,
    /** Gün başına beklenen talep (orta değer). */
    val forecast: IntArray,
    /** Tahmin belirsizliği (± oran). */
    val spread: Float,
) {
    val id: Int get() = product.id
    val perishable: Boolean get() = shelfLife > 0
    val maxCases: Int get() = maxOf(1, capacity / caseSize)

    /** Oyuncuya gösterilen tahmin aralığı. */
    fun low(day: Int): Int = floor(forecast[day] * (1f - spread)).toInt().coerceAtLeast(0)
    fun high(day: Int): Int = ceil(forecast[day] * (1f + spread)).toInt()
}

class Promo(val day: Int, val item: Int)

/** Kapanan bir günün dökümü. */
class DaySummary(
    val day: Int,
    val soldUnits: Int,
    val lostUnits: Int,
    val wastedUnits: Int,
    val returnedUnits: Int,
    val deliveredUnits: Int,
    val margin: Int,
    val holding: Int,
    val waste: Int,
    val returns: Int,
    /** Gün kapanışında rafta kalan birim; bekleme bedelini ödeyen stok.
     *  Eski kayıtlardan yüklenen günlerde [UNKNOWN] olur. */
    val evening: Int = UNKNOWN,
) {
    val profit: Int get() = margin - holding - waste - returns

    companion object {
        /** Akşam stoğu kaydedilmemiş (0.28.1 öncesi kayıt). */
        const val UNKNOWN = -1
    }
}

/** Bir sipariş haftası: ürünler, gizli gerçekleşen talep, promosyonlar ve uzman hedefi. */
class ReyonOrder internal constructor(
    val seed: Long,
    val level: ReyonLevel,
    val days: Int,
    val items: List<OrderItem>,
    val promos: List<Promo>,
    /** Gerçekleşen talep [gün][ürün]; oyuncu görmez. */
    internal val demand: Array<IntArray>,
    internal val initial: IntArray,
    val target: Int,
    val targetTurnover: Float,
    val targetService: Int,
) {
    val rows: Int get() = level.rows
    val cols: Int get() = level.cols
    val board = Board(rows, cols)
    val products: List<Product> = items.map { it.product }

    fun isPromo(day: Int, item: Int): Boolean = promos.any { it.day == day && it.item == item }

    /** Hedefe göre yıldız: ≥ hedef 3, ≥ %90 2, ≥ %75 1. */
    fun stars(score: Int): Int = when {
        score >= target -> 3
        target > 0 && score >= target * 0.9 -> 2
        target > 0 && score >= target * 0.75 -> 1
        else -> 0
    }
}

/**
 * Uzman politika: oyuncunun gördüğü bilgiyle (tahmin ortası ve aralık) çalışır.
 * Gelecek teslimatın geleceği gün a için beklenen kalan stok hesaplanır,
 * o günün talebini emniyet payıyla karşılayacak kadar koli istenir; koli
 * beklenen stokla birlikte rafa sığmalıdır (yarım koliye kadar taşma iade
 * bedeline değer). Bozulan ürünlerde emniyet payı ve sipariş eşiği daha
 * temkinlidir.
 */
object OrderExpert {
    fun cases(st: ReyonOrderState, i: Int): Int {
        val o = st.order
        val it = o.items[i]
        val t = st.day
        val a = t + it.leadTime
        if (a >= o.days) return 0
        var position = st.stockOf(i).toFloat()
        for (d in t + 1..a) position += st.incoming(i, d)
        var consume = 0f
        for (d in t until a) consume += it.forecast[d]
        val expected = maxOf(0f, position - consume)
        val safety = if (it.perishable) 0.3f else 0.6f
        val desired = it.forecast[a] * (1f + safety * it.spread)
        val q = desired - expected
        if (q <= 0f) return 0
        val threshold = if (it.perishable) 0.5f else 0.25f
        val cases = ceil(q / it.caseSize - threshold).toInt()
        val fit = floor((it.capacity - expected + 0.5f * it.caseSize) / it.caseSize).toInt()
        return cases.coerceIn(0, minOf(it.maxCases, maxOf(0, fit)))
    }
}

object ReyonOrderGenerator {

    fun dailySeed(epochDay: Long): Long = ReyonGenerator.mix(epochDay, 0x53, 0x50)

    fun generate(seed: Long, level: ReyonLevel): ReyonOrder {
        val rng = Random(ReyonGenerator.mix(seed, level.ordinal + 1, 0x4F))
        var layout: ReyonGenerator.Layout? = null
        var guard = 0
        while (layout == null && guard++ < 200) layout = ReyonGenerator.sampleLayout(rng, level)
        val base = layout ?: error("Reyon: sipariş haftası üretilemedi (tohum $seed, $level)")
        val days = OrderRules.days(level)
        val spread = OrderRules.spread(level)
        val n = base.products.size

        // Talep tabanı: satış modundaki gibi 1–3, yüz sayısıyla ölçeklenir (kapasitenin ~%25–75'i).
        val baseDemand = IntArray(n) { i ->
            val p = base.products[i]
            val dem = if (p.premium) 2 + rng.nextInt(2) else 1 + rng.nextInt(3)
            maxOf(1, (0.7f * dem * p.facings).roundToInt())
        }

        // Promosyonlar: bozulmayan ürün, hazırlanacak zaman kalsın diye 2. günden sonra.
        val promos = ArrayList<Promo>()
        val promoPool = (0 until n).filter { OrderRules.shelfLife(base.products[it].kind, level) == 0 }.shuffled(rng)
        val promoDays = (2 until days).shuffled(rng)
        for (k in 0 until minOf(OrderRules.promoCount(level), promoPool.size, promoDays.size)) promos += Promo(promoDays[k], promoPool[k])

        val items = ArrayList<OrderItem>(n)
        for (i in 0 until n) {
            val p = base.products[i]
            val capacity = p.facings * OrderRules.DEPTH
            val forecast = IntArray(days) { d ->
                var mean = baseDemand[i].toFloat()
                if (OrderRules.isWeekend(d) && OrderRules.weekendKind(p.kind)) mean *= OrderRules.WEEKEND_FACTOR
                if (promos.any { it.day == d && it.item == i }) mean *= OrderRules.PROMO_FACTOR
                maxOf(1, mean.roundToInt())
            }
            items += OrderItem(
                product = p,
                row = base.board.rowOf(base.at[i]),
                col = base.board.colOf(base.at[i]),
                capacity = capacity,
                caseSize = OrderRules.caseSize(p, capacity),
                margin = OrderRules.margin(p),
                unitCost = OrderRules.unitCost(p),
                shelfLife = OrderRules.shelfLife(p.kind, level),
                leadTime = OrderRules.leadTime(p, level),
                forecast = forecast,
                spread = spread,
            )
        }

        // Gerçekleşen talep: tahmin aralığı içinde, tohumdan.
        val demand = Array(days) { d ->
            IntArray(n) { i ->
                val u = rng.nextFloat() * 2f - 1f
                maxOf(0, (items[i].forecast[d] * (1f + spread * u)).roundToInt())
            }
        }

        // Başlangıç stoğu: ilk teslimat gelene dek beklenen talebin biraz üstü.
        val initial = IntArray(n) { i ->
            var need = 0f
            for (d in 0 until minOf(days, items[i].leadTime)) need += items[i].forecast[d]
            minOf(items[i].capacity, ceil(need * 1.1f).toInt())
        }

        val draft = ReyonOrder(seed, level, days, items, promos, demand, initial, 0, 0f, 0)
        val expert = ReyonOrderState(draft)
        while (!expert.isComplete) {
            for (i in 0 until n) expert.setOrder(i, OrderExpert.cases(expert, i))
            expert.closeDay()
        }
        return ReyonOrder(seed, level, days, items, promos, demand, initial, expert.profit, expert.turnover(), expert.serviceLevel())
    }
}

/** Sipariş oynanışı: günlük siparişler, gün kapanışı, ipucu, kayıt. */
class ReyonOrderState(val order: ReyonOrder) {
    private class Batch(var units: Int, val expires: Int)

    private val n = order.items.size
    private val stock = Array(n) { ArrayList<Batch>() }
    private val pipeline = Array(n) { IntArray(order.days + OrderRules.MAX_LEAD + 1) }
    private val orders = IntArray(n)
    private val summaries = ArrayList<DaySummary>()

    /** Geçerli gün (0 tabanlı); [ReyonOrder.days] olunca hafta bitmiştir. */
    var day = 0
        private set
    var hintsUsed = 0
        private set
    var soldUnits = 0
        private set
    var lostUnits = 0
        private set
    var wastedUnits = 0
        private set
    var returnedUnits = 0
        private set
    var marginPts = 0
        private set
    var holdingPts = 0
        private set
    var wastePts = 0
        private set
    var returnPts = 0
        private set

    /** Kapanan günlerin akşam stok toplamı (stok devri için). */
    var eveningSum = 0
        private set

    init {
        reset()
    }

    private fun reset() {
        day = 0
        hintsUsed = 0
        soldUnits = 0
        lostUnits = 0
        wastedUnits = 0
        returnedUnits = 0
        marginPts = 0
        holdingPts = 0
        wastePts = 0
        returnPts = 0
        eveningSum = 0
        summaries.clear()
        for (i in 0 until n) {
            stock[i].clear()
            pipeline[i].fill(0)
            orders[i] = 0
            if (order.initial[i] > 0) stock[i] += Batch(order.initial[i], expiry(i, 0))
        }
    }

    private fun expiry(i: Int, receivedDay: Int): Int =
        if (order.items[i].shelfLife > 0) receivedDay + order.items[i].shelfLife - 1 else Int.MAX_VALUE

    val isComplete: Boolean get() = day >= order.days
    val profit: Int get() = marginPts - holdingPts - wastePts - returnPts
    val history: List<DaySummary> get() = summaries
    val lastSummary: DaySummary? get() = summaries.lastOrNull()

    fun of(rule: OrderRule): Int = when (rule) {
        OrderRule.SALES -> marginPts
        OrderRule.HOLDING -> -holdingPts
        OrderRule.WASTE -> -wastePts
        OrderRule.RETURNS -> -returnPts
    }

    fun stockOf(i: Int): Int = stock[i].sumOf { it.units }

    /** [byDay] günü akşamına kadar raf ömrü dolacak birimler. */
    fun expiring(i: Int, byDay: Int): Int = stock[i].filter { it.expires <= byDay }.sumOf { it.units }

    /** [d] günü sabahı gelecek birimler. */
    fun incoming(i: Int, d: Int): Int = if (d in pipeline[i].indices) pipeline[i][d] else 0

    fun incomingTotal(i: Int): Int {
        var s = 0
        for (d in day + 1 until pipeline[i].size) s += pipeline[i][d]
        return s
    }

    fun orderOf(i: Int): Int = orders[i]
    fun arrivalDay(i: Int): Int = day + order.items[i].leadTime

    /** Sipariş, hafta içinde gelecekse verilebilir. */
    fun canOrder(i: Int): Boolean = !isComplete && arrivalDay(i) < order.days

    fun setOrder(i: Int, cases: Int): Boolean {
        if (!canOrder(i)) return false
        orders[i] = cases.coerceIn(0, order.items[i].maxCases)
        return true
    }

    /** Uzman önerisinden ayrılan ilk siparişi uzmanınkine çeker; ürün dizinini döndürür. */
    fun hint(): Int? {
        if (isComplete) return null
        for (i in 0 until n) {
            if (!canOrder(i)) continue
            val e = OrderExpert.cases(this, i)
            if (e != orders[i]) {
                orders[i] = e
                hintsUsed++
                return i
            }
        }
        return null
    }

    /** Günü kapatır: siparişler yola çıkar, satış, fire ve bekleme; ertesi sabah teslimat. */
    fun closeDay(): DaySummary? {
        if (isComplete) return null
        val t = day
        for (i in 0 until n) {
            if (orders[i] > 0 && canOrder(i)) pipeline[i][t + order.items[i].leadTime] += orders[i] * order.items[i].caseSize
            orders[i] = 0
        }
        var sold = 0
        var lost = 0
        var margin = 0
        var wasted = 0
        var waste = 0
        var holding = 0
        var evening = 0
        for (i in 0 until n) {
            val it = order.items[i]
            var d = order.demand[t][i]
            val batches = stock[i]
            batches.sortBy { b -> b.expires }
            var k = 0
            while (d > 0 && k < batches.size) {
                val take = minOf(d, batches[k].units)
                batches[k].units -= take
                d -= take
                sold += take
                margin += take * it.margin
                if (batches[k].units == 0) batches.removeAt(k) else k++
            }
            lost += d
            val iter = batches.iterator()
            while (iter.hasNext()) {
                val b = iter.next()
                if (b.expires <= t) {
                    wasted += b.units
                    waste += b.units * it.unitCost
                    iter.remove()
                }
            }
            holding += stockOf(i) * OrderRules.HOLDING
            evening += stockOf(i)
        }
        eveningSum += evening
        day = t + 1
        var delivered = 0
        var returned = 0
        if (day < order.days) {
            for (i in 0 until n) {
                val arriving = pipeline[i][day]
                if (arriving <= 0) continue
                val space = order.items[i].capacity - stockOf(i)
                val put = minOf(space, arriving).coerceAtLeast(0)
                if (put > 0) stock[i] += Batch(put, expiry(i, day))
                delivered += put
                returned += arriving - put
            }
        }
        val returns = returned * OrderRules.RETURN_COST
        soldUnits += sold
        lostUnits += lost
        wastedUnits += wasted
        returnedUnits += returned
        marginPts += margin
        holdingPts += holding
        wastePts += waste
        returnPts += returns
        val summary = DaySummary(t, sold, lost, wasted, returned, delivered, margin, holding, waste, returns, evening)
        summaries += summary
        return summary
    }

    /** Stok devri: satılan birim / ortalama akşam stoğu (kapanan günler). */
    fun turnover(): Float {
        if (day == 0 || eveningSum == 0) return 0f
        return soldUnits / (eveningSum.toFloat() / day)
    }

    /** Hizmet düzeyi: karşılanan talep yüzdesi. */
    fun serviceLevel(): Int = if (soldUnits + lostUnits == 0) 100 else 100 * soldUnits / (soldUnits + lostUnits)

    fun snapshot(): IntArray {
        val out = ArrayList<Int>()
        out += day
        out += hintsUsed
        out += soldUnits
        out += lostUnits
        out += wastedUnits
        out += returnedUnits
        out += marginPts
        out += holdingPts
        out += wastePts
        out += returnPts
        out += eveningSum
        out += summaries.size
        for (s in summaries) {
            out += s.day
            out += s.soldUnits
            out += s.lostUnits
            out += s.wastedUnits
            out += s.returnedUnits
            out += s.deliveredUnits
            out += s.margin
            out += s.holding
            out += s.waste
            out += s.returns
        }
        for (i in 0 until n) {
            out += orders[i]
            out += stock[i].size
            for (b in stock[i]) {
                out += b.units
                out += if (b.expires == Int.MAX_VALUE) -1 else b.expires
            }
            for (v in pipeline[i]) out += v
        }
        // Akşam stokları en sonda: eski kayıtlarda bu blok yok ve okuma onsuz da tutar.
        for (s in summaries) out += s.evening
        return out.toIntArray()
    }

    /** Kaydı yükler; tutarsızsa false döner ve durum başa alınır. */
    fun restore(snapshot: IntArray): Boolean {
        reset()
        var p = 0
        fun next(): Int = snapshot[p++]
        try {
            val d = next()
            if (d !in 0..order.days) return fail()
            day = d
            hintsUsed = next().coerceAtLeast(0)
            soldUnits = next()
            lostUnits = next()
            wastedUnits = next()
            returnedUnits = next()
            marginPts = next()
            holdingPts = next()
            wastePts = next()
            returnPts = next()
            eveningSum = next()
            val count = next()
            if (count != d) return fail()
            repeat(count) {
                summaries += DaySummary(next(), next(), next(), next(), next(), next(), next(), next(), next(), next())
            }
            val fromDay = summaries.size
            for (i in 0 until n) {
                stock[i].clear()
                orders[i] = next().coerceIn(0, order.items[i].maxCases)
                val k = next()
                if (k < 0 || k > 64) return fail()
                repeat(k) {
                    val units = next()
                    val exp = next()
                    if (units > 0) stock[i] += Batch(units, if (exp < 0) Int.MAX_VALUE else exp)
                }
                for (j in pipeline[i].indices) pipeline[i][j] = next().coerceAtLeast(0)
            }
            if (p < snapshot.size) {
                for (k in 0 until fromDay) {
                    val e = next().coerceAtLeast(0)
                    val old = summaries[k]
                    summaries[k] = DaySummary(old.day, old.soldUnits, old.lostUnits, old.wastedUnits,
                        old.returnedUnits, old.deliveredUnits, old.margin, old.holding, old.waste, old.returns, e)
                }
            }
            if (p != snapshot.size) return fail()
        } catch (e: IndexOutOfBoundsException) {
            return fail()
        }
        return true
    }

    private fun fail(): Boolean {
        reset()
        return false
    }
}
