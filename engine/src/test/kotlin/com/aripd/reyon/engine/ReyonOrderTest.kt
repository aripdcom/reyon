package com.aripd.reyon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReyonOrderTest {

    private val seeds = 1L..30L

    /** Elle izlenebilir tek ürünlük hafta: kapasite 4, koli 4, raf ömrü 2, teslim 1 gün. */
    private fun tinyWeek(): ReyonOrder {
        val p = Product(0, Kind.SUT, Brand.A, 2, 1, premium = false, heavy = false, demand = 2)
        val item = OrderItem(p, 0, 0, capacity = 4, caseSize = 4, margin = 4, unitCost = 2, shelfLife = 2, leadTime = 1, forecast = intArrayOf(2, 2, 2), spread = 0.25f)
        return ReyonOrder(7L, ReyonLevel.KOLAY, 3, listOf(item), emptyList(), arrayOf(intArrayOf(3), intArrayOf(1), intArrayOf(2)), intArrayOf(2), 0, 0f, 0)
    }

    @Test
    fun dayMechanicsFollowTheRules() {
        val s = ReyonOrderState(tinyWeek())
        assertEquals(2, s.stockOf(0))
        assertTrue(s.canOrder(0))
        assertTrue(s.setOrder(0, 1))
        // Gün 0: talep 3, stok 2 → 2 satış, 1 kayıp; akşam stok 0; sabah 4 gelir, hepsi sığar.
        val d0 = s.closeDay()!!
        assertEquals(2, d0.soldUnits)
        assertEquals(1, d0.lostUnits)
        assertEquals(8, d0.margin)
        assertEquals(0, d0.holding)
        assertEquals(4, d0.deliveredUnits)
        assertEquals(0, d0.returnedUnits)
        assertEquals(1, s.day)
        assertEquals(4, s.stockOf(0))
        // Gün 1: 1 koli daha; talep 1 → stok 3, bekleme 3; sabah 4 gelir, 1 sığar, 3 iade.
        assertTrue(s.setOrder(0, 1))
        val d1 = s.closeDay()!!
        assertEquals(1, d1.soldUnits)
        assertEquals(3, d1.holding)
        assertEquals(1, d1.deliveredUnits)
        assertEquals(3, d1.returnedUnits)
        assertEquals(3, d1.returns)
        assertEquals(4, s.stockOf(0))
        // Gün 2: son gün, sipariş verilemez; talep 2 önce eski partiden; eski partinin kalan 1 birimi fire.
        assertFalse(s.canOrder(0))
        assertFalse(s.setOrder(0, 1))
        assertEquals(1, s.expiring(0, 2) - 2)  // eski parti 3 birim, 2. gün akşamı dolar
        val d2 = s.closeDay()!!
        assertEquals(2, d2.soldUnits)
        assertEquals(1, d2.wastedUnits)
        assertEquals(2, d2.waste)
        assertEquals(1, d2.holding)
        assertTrue(s.isComplete)
        assertNull(s.closeDay())
        assertEquals(5, s.soldUnits)
        assertEquals(20, s.marginPts)
        assertEquals(4, s.holdingPts)
        assertEquals(2, s.wastePts)
        assertEquals(3, s.returnPts)
        assertEquals(11, s.profit)
        assertEquals(11, OrderRule.entries.sumOf { s.of(it) })
        assertEquals(3.75f, s.turnover(), 0.001f)
        assertEquals(83, s.serviceLevel())
    }

    @Test
    fun ordersAreClampedToCasesThatFitTheShelf() {
        val s = ReyonOrderState(tinyWeek())
        assertTrue(s.setOrder(0, 5))
        assertEquals(1, s.orderOf(0))
        assertTrue(s.setOrder(0, -2))
        assertEquals(0, s.orderOf(0))
        val o = ReyonOrderGenerator.generate(3L, ReyonLevel.ZOR)
        for (it in o.items) {
            assertTrue(it.caseSize <= it.capacity)
            assertTrue(it.maxCases * it.caseSize <= it.capacity)
            assertEquals(it.product.facings * OrderRules.DEPTH, it.capacity)
        }
    }

    @Test
    fun sameSeedSameWeek() {
        for (level in ReyonLevel.entries) {
            val a = ReyonOrderGenerator.generate(11L, level)
            val b = ReyonOrderGenerator.generate(11L, level)
            assertEquals(a.target, b.target)
            assertEquals(a.items.map { it.forecast.toList() }, b.items.map { it.forecast.toList() })
            assertEquals(a.demand.map { it.toList() }, b.demand.map { it.toList() })
            val c = ReyonOrderGenerator.generate(12L, level)
            assertTrue(a.demand.map { it.toList() } != c.demand.map { it.toList() } || a.target != c.target)
        }
    }

    @Test
    fun levelsShapeTheWeekAndForecastsBracketDemand() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val o = ReyonOrderGenerator.generate(seed, level)
            assertEquals(OrderRules.days(level), o.days)
            assertEquals(OrderRules.promoCount(level), o.promos.size)
            assertTrue(o.items.size in level.products)
            for ((i, it) in o.items.withIndex()) {
                assertEquals(o.days, it.forecast.size)
                assertEquals(if (level == ReyonLevel.ZOR && it.product.heavy) 2 else 1, it.leadTime)
                if (level == ReyonLevel.KOLAY) assertFalse(it.perishable)
                for (d in 0 until o.days) {
                    val real = o.demand[d][i]
                    assertTrue("$level $seed ürün $i gün $d: $real ∉ ${it.low(d)}..${it.high(d)}", real in it.low(d)..it.high(d))
                    assertTrue(it.forecast[d] >= 1)
                    if (o.isPromo(d, i)) assertTrue(it.forecast[d] > it.forecast[(d + 1) % o.days] || it.forecast[d] >= 2)
                }
                assertTrue(o.initial[i] in 0..it.capacity)
            }
            for (pr in o.promos) {
                assertTrue(pr.day >= 2 && pr.day < o.days)
                assertFalse(o.items[pr.item].perishable)
            }
        }
    }

    @Test
    fun replayingTheExpertReproducesTheTarget() {
        for (level in ReyonLevel.entries) for (seed in seeds) {
            val o = ReyonOrderGenerator.generate(seed, level)
            val s = ReyonOrderState(o)
            while (!s.isComplete) {
                for (i in o.items.indices) s.setOrder(i, OrderExpert.cases(s, i))
                assertNotNull(s.closeDay())
            }
            assertEquals("$level $seed", o.target, s.profit)
            assertEquals(o.targetTurnover, s.turnover(), 0.0001f)
            assertEquals(o.targetService, s.serviceLevel())
            assertEquals(3, o.stars(s.profit))
            assertTrue("$level $seed hedef $o.target", o.target > 0)
        }
    }

    @Test
    fun hintsPullOrdersToTheExpertAndRunOut() {
        val o = ReyonOrderGenerator.generate(5L, ReyonLevel.ORTA)
        val s = ReyonOrderState(o)
        var guard = 0
        while (s.hint() != null && guard++ < 40) Unit
        assertTrue(guard < 40)
        for (i in o.items.indices) if (s.canOrder(i)) assertEquals(OrderExpert.cases(s, i), s.orderOf(i))
        assertTrue(s.hintsUsed >= 1)
        assertNull(s.hint())
    }

    /**
     * Hafta grafiği gün gün akşam stoğunu okur: toplamı motorun devir hesabındaki
     * [ReyonOrderState.eveningSum] ile aynı olmalı, yoksa çizgi kartın yazdığı
     * sayıya varmaz. Akşam stoğu tutulmayan (0.28.1 öncesi) kayıtlar da yüklenebilmeli.
     */
    @Test
    fun historyCarriesEveningStockAndSurvivesOldSnapshots() {
        for (seed in seeds.take(5)) {
            val o = ReyonOrderGenerator.generate(seed, ReyonLevel.ORTA)
            val rng = Random(seed)
            val s = ReyonOrderState(o)
            while (!s.isComplete) {
                for (i in o.items.indices) s.setOrder(i, rng.nextInt(0, o.items[i].maxCases + 1))
                s.closeDay()
            }
            assertEquals(o.days, s.history.size)
            assertTrue(s.history.all { it.evening >= 0 })
            assertEquals(s.eveningSum, s.history.sumOf { it.evening })
            // Grafiğin son noktası kartın yazdığı devirle aynı olmalı.
            val sold = s.history.sumOf { it.soldUnits }
            val avgEvening = s.history.sumOf { it.evening }.toFloat() / s.history.size
            assertEquals(s.turnover(), sold / avgEvening, 0.0001f)

            val snap = s.snapshot()
            val yeniden = ReyonOrderState(o)
            assertTrue(yeniden.restore(snap))
            assertEquals(s.history.map { it.evening }, yeniden.history.map { it.evening })

            // Eski kayıt: akşam stoğu bloğu yok; hafta yine yüklenir, akşam stoğu bilinmez.
            val eski = snap.copyOf(snap.size - o.days)
            val eskiden = ReyonOrderState(o)
            assertTrue(eskiden.restore(eski))
            assertEquals(s.profit, eskiden.profit)
            assertEquals(s.history.size, eskiden.history.size)
            assertTrue(eskiden.history.all { it.evening == DaySummary.UNKNOWN })
        }
    }

    @Test
    fun snapshotRoundTripsMidWeek() {
        for (seed in seeds.take(10)) {
            val o = ReyonOrderGenerator.generate(seed, ReyonLevel.ZOR)
            val rng = Random(seed)
            val a = ReyonOrderState(o)
            repeat(3) {
                for (i in o.items.indices) a.setOrder(i, rng.nextInt(0, o.items[i].maxCases + 1))
                a.closeDay()
            }
            for (i in o.items.indices) a.setOrder(i, rng.nextInt(0, o.items[i].maxCases + 1))
            val snap = a.snapshot()
            val b = ReyonOrderState(o)
            assertTrue(b.restore(snap))
            assertEquals(a.day, b.day)
            assertEquals(a.profit, b.profit)
            assertEquals(a.history.size, b.history.size)
            for (i in o.items.indices) {
                assertEquals(a.stockOf(i), b.stockOf(i))
                assertEquals(a.orderOf(i), b.orderOf(i))
                assertEquals(a.incomingTotal(i), b.incomingTotal(i))
                assertEquals(a.expiring(i, a.day + 1), b.expiring(i, b.day + 1))
            }
            while (!a.isComplete) {
                val cases = IntArray(o.items.size) { rng.nextInt(0, o.items[it].maxCases + 1) }
                for (i in o.items.indices) {
                    a.setOrder(i, cases[i])
                    b.setOrder(i, cases[i])
                }
                a.closeDay()
                b.closeDay()
            }
            assertEquals(a.profit, b.profit)
            assertEquals(a.turnover(), b.turnover(), 0.0001f)
            // Bozuk kayıt: durum başa döner.
            val c = ReyonOrderState(o)
            assertFalse(c.restore(intArrayOf(1, 2, 3)))
            assertEquals(0, c.day)
            assertFalse(c.restore(snap.copyOf(snap.size - 1)))
            assertEquals(0, c.day)
        }
    }
}
