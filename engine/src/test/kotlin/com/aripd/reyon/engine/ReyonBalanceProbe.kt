package com.aripd.reyon.engine

import org.junit.Test

/**
 * Reyon denge ölçümü — birim testi değil, rapor üretir
 * (bkz. docs/cihaz-testi.md). `./gradlew :engine:probe` ile koşar.
 *
 * Sıra tabanlı bulmaca: soru yetişilebilirlik değil adilliktir. Tek çözüm ve
 * tahminsizlik üreticide garanti; burada ölçülen, zorluk merdiveninin gerçek
 * olup olmadığı (hangi teknikler gerekiyor), brifin uzunluğu ve karışımı,
 * verili sayısı ve üretim bütçesi.
 */
class ReyonBalanceProbe {

    private val seeds = 1L..40L

    private fun kindOf(c: Clue): String = when (c) {
        is Clue.Placed -> "verili"
        is Clue.OnShelf -> "raf"
        is Clue.InSlot -> "göz"
        is Clue.AtEdge -> "kenar"
        is Clue.Adjacent -> "yan yana"
        is Clue.LeftOf -> "solunda"
        is Clue.SameShelf -> "aynı raf"
        is Clue.DifferentShelf -> "farklı raf"
        is Clue.Above -> "üstünde"
        Clue.EyeLevel -> "göz hizası"
        Clue.HeavyBottom -> "ağır alt"
        is Clue.CategoryBlock -> "kategori bloğu"
        is Clue.CategoriesApart -> "kategoriler ayrı"
        is Clue.BrandVertical -> "marka dikey"
        is Clue.SizeFlow -> "boy akışı"
    }

    /** Satış: başlangıç planı ile hedef arası fark, kural payları, iyileştirici tutarlılığı. */
    @Test
    fun salesReport() {
        println("=== Reyon satış ölçümü (${seeds.count()} tohum/zorluk) ===")
        for (level in ReyonLevel.entries) {
            var timeMs = 0L
            val rounds = seeds.map { seed ->
                val t0 = System.nanoTime()
                val r = ReyonSalesGenerator.generate(seed, level)
                timeMs += (System.nanoTime() - t0) / 1_000_000
                r
            }
            val n = rounds.size.toFloat()
            val gain = rounds.map { if (it.baseline > 0) 100f * (it.target - it.baseline) / it.baseline else 0f }
            val shares = FloatArray(SalesRule.entries.size)
            for (r in rounds) {
                val sc = SalesScorer.score(r.board, r.products, r.targetAt)
                for (rule in SalesRule.entries) shares[rule.ordinal] += sc.of(rule).toFloat() / sc.total.coerceAtLeast(1)
            }
            // Tutarlılık: aynı ürün seti, farklı iyileştirici tohumu → hedef ne kadar oynuyor?
            var spread = 0f
            for (r in rounds.take(10)) {
                val alt = SalesOptimizer.optimize(r.board, r.products, r.targetAt, r.seed xor 0x5A5AL, ReyonSalesGenerator.iterations(level), ReyonSalesGenerator.RESTARTS)
                spread += 100f * kotlin.math.abs(alt.score - r.target) / r.target.coerceAtLeast(1)
            }
            println("--- $level (${level.rows}×${level.cols}) ---")
            println("taban ort %.0f · hedef ort %.0f · kazanç ort %%%.0f (en az %%%.0f, en çok %%%.0f)".format(
                rounds.map { it.baseline }.average(), rounds.map { it.target }.average(), gain.average(), gain.min(), gain.max()))
            println("hedefte kural payları: " + SalesRule.entries.joinToString { "${it.name.lowercase()} %%%.0f".format(100f * shares[it.ordinal] / n) })
            println("yeniden koşum sapması ort %%%.1f (10 tur) · değerlendirme %d · süre ort %d ms".format(spread / 10f, rounds[0].evaluations, timeMs / rounds.size))
        }
    }

    /** Denetim: sapma türü karışımı ve görünürlük (sapma başına ayrışan göz). */
    @Test
    fun auditReport() {
        println("=== Reyon denetim ölçümü (${seeds.count()} tohum/zorluk) ===")
        for (level in ReyonLevel.entries) {
            val audits = seeds.map { ReyonAuditGenerator.generate(it, level) }
            val mix = HashMap<DeviationKind, Int>()
            var diffSlots = 0
            var deviations = 0
            for (a in audits) {
                val diff = ReyonAuditGenerator.diffMask(a)
                for (d in a.deviations) {
                    mix[d.kind] = (mix[d.kind] ?: 0) + 1
                    diffSlots += (d.slotMask and diff).countOneBits()
                    deviations++
                }
            }
            val subtle = (mix[DeviationKind.BRAND] ?: 0) + (mix[DeviationKind.SIZE] ?: 0)
            println("--- $level (${level.rows}×${level.cols}) · sapma ${ReyonAuditGenerator.count(level)} ---")
            println("tür karışımı: " + mix.entries.sortedByDescending { it.value }.joinToString { "${it.key} %.0f%%".format(100f * it.value / deviations) })
            println("ince sapma (marka/boy) payı %.0f%% · sapma başına ayrışan göz ort %.2f".format(100f * subtle / deviations, diffSlots.toFloat() / deviations))
        }
    }

    @Test
    fun report() {
        println("=== Reyon denge ölçümü (${seeds.count()} tohum/zorluk) ===")
        for (level in ReyonLevel.entries) {
            val puzzles = ArrayList<ReyonPuzzle>()
            var timeMs = 0L
            var worstMs = 0L
            for (seed in seeds) {
                val t0 = System.nanoTime()
                puzzles += ReyonGenerator.generate(seed, level)
                val ms = (System.nanoTime() - t0) / 1_000_000
                timeMs += ms
                worstMs = maxOf(worstMs, ms)
            }
            val n = puzzles.size.toFloat()
            val mix = HashMap<String, Int>()
            for (p in puzzles) for (c in p.clues) mix[kindOf(c)] = (mix[kindOf(c)] ?: 0) + 1
            fun pct(pred: (ReyonPuzzle) -> Boolean) = "%.0f%%".format(100f * puzzles.count(pred) / n)
            fun solvable(p: ReyonPuzzle, tech: Int) = ReyonDeducer(Propagator(p.board, p.products, p.clues)).solves(tech)
            val basic = pct { solvable(it, Tech.BASIC) }
            val cover = pct { solvable(it, Tech.BASIC or Tech.COVER) }
            val capacity = pct { solvable(it, Tech.BASIC or Tech.COVER or Tech.CAPACITY) }
            val all = pct { solvable(it, Tech.ALL) }
            println("--- $level (${level.rows}×${level.cols}) ---")
            println("ürün ort %.1f (%d–%d) · brif ort %.1f (en çok %d, sınır %d) · verili ort %.1f".format(
                puzzles.map { it.products.size }.average(), puzzles.minOf { it.products.size }, puzzles.maxOf { it.products.size },
                puzzles.map { it.brief.size }.average(), puzzles.maxOf { it.brief.size }, level.maxClues,
                puzzles.map { it.givens.size }.average()))
            println("tahminsiz çözüm: yalnız tekil+ikili $basic · +örtü $cover · +kapasite $capacity · tüm teknikler $all")
            val techs = HashMap<String, Int>()
            for (p in puzzles) for (t in Tech.names(p.stats.techniques)) techs[t] = (techs[t] ?: 0) + 1
            println("kullanılan teknikler: " + techs.entries.sortedByDescending { it.value }.joinToString { "${it.key} %.0f%%".format(100f * it.value / n) })
            println("ipucu karışımı: " + mix.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" })
            println("üretim: deneme ort %.1f (en çok %d) · düğüm ort %.0f (en çok %d) · aday ort %.0f · süre ort %d ms (en kötü %d ms, yalnız rapor)".format(
                puzzles.map { it.stats.attempts }.average(), puzzles.maxOf { it.stats.attempts },
                puzzles.map { it.stats.solverNodes }.average(), puzzles.maxOf { it.stats.solverNodes },
                puzzles.map { it.stats.candidates }.average(), timeMs / puzzles.size, worstMs))
            val sample = puzzles.first()
            println("örnek (tohum ${sample.seed}): " + sample.products.joinToString { "${it.kind.name.lowercase()}×${it.facings}" })
            println("  brif: " + sample.brief.joinToString(" · ") { kindOf(it) + " " + it.toString().substringAfter('(').substringBefore(')') })
        }
    }

    // -----------------------------------------------------------------------
    // Sipariş
    // -----------------------------------------------------------------------

    private fun runWeek(o: ReyonOrder, policy: (ReyonOrderState, Int) -> Int): ReyonOrderState {
        val s = ReyonOrderState(o)
        while (!s.isComplete) {
            for (i in o.items.indices) s.setOrder(i, policy(s, i))
            s.closeDay()
        }
        return s
    }

    /** Naif: tahmin ortasını emniyetsiz karşıla, sığmaya bakma. */
    private fun naiveCases(s: ReyonOrderState, i: Int): Int {
        val o = s.order
        val it = o.items[i]
        val t = s.day
        val a = t + it.leadTime
        if (a >= o.days) return 0
        var position = s.stockOf(i).toFloat()
        for (d in t + 1..a) position += s.incoming(i, d)
        var consume = 0f
        for (d in t until a) consume += it.forecast[d]
        val q = it.forecast[a] - maxOf(0f, position - consume)
        if (q <= 0f) return 0
        return kotlin.math.ceil(q / it.caseSize).toInt().coerceIn(0, it.maxCases)
    }

    /** Kâhin: gerçekleşen talebi bilir (oyuncu bilmez); uzmanın üst sınırı olarak. */
    private fun oracleCases(s: ReyonOrderState, i: Int): Int {
        val o = s.order
        val it = o.items[i]
        val t = s.day
        val a = t + it.leadTime
        if (a >= o.days) return 0
        var position = s.stockOf(i).toFloat()
        for (d in t + 1..a) position += s.incoming(i, d)
        var consume = 0f
        for (d in t until a) consume += o.demand[d][i]
        val expected = maxOf(0f, position - consume)
        val q = o.demand[a][i] - expected
        if (q <= 0f) return 0
        val cases = kotlin.math.ceil(q / it.caseSize).toInt()
        val fit = kotlin.math.floor((it.capacity - expected + 0.5f * it.caseSize) / it.caseSize).toInt()
        return cases.coerceIn(0, minOf(it.maxCases, maxOf(0, fit)))
    }

    /** Sipariş: uzman hedefi, kâhin ve naif politikalarla karşılaştırılır; hizmet, fire, iade, devir. */
    @Test
    fun orderReport() {
        println("=== Reyon sipariş ölçümü (${seeds.count()} tohum/zorluk) ===")
        for (level in ReyonLevel.entries) {
            var timeMs = 0L
            val weeks = seeds.map { seed ->
                val t0 = System.nanoTime()
                val w = ReyonOrderGenerator.generate(seed, level)
                timeMs += (System.nanoTime() - t0) / 1_000_000
                w
            }
            val none = weeks.map { runWeek(it) { _, _ -> 0 } }
            val naive = weeks.map { runWeek(it) { s, i -> naiveCases(s, i) } }
            val expert = weeks.map { runWeek(it) { s, i -> OrderExpert.cases(s, i) } }
            val oracle = weeks.map { runWeek(it) { s, i -> oracleCases(s, i) } }
            fun avg(xs: List<Int>) = xs.average()
            fun pct(a: List<ReyonOrderState>, b: List<ReyonOrderState>) = 100.0 * a.map { it.profit }.average() / b.map { it.profit }.average()
            fun lostShare(xs: List<ReyonOrderState>) = 100.0 * xs.sumOf { it.lostUnits } / xs.sumOf { it.soldUnits + it.lostUnits }
            fun wasteShare(xs: List<ReyonOrderState>) = 100.0 * xs.sumOf { it.wastedUnits } / xs.sumOf { it.soldUnits + it.wastedUnits }.coerceAtLeast(1)
            val sales = expert.map { it.marginPts }
            println("--- $level (${level.rows}×${level.cols}) · ${weeks[0].days} gün · ürün ort %.1f · promosyon ${weeks[0].promos.size} ---".format(weeks.map { it.items.size }.average()))
            println("kâr ort: sipariş yok %.0f · naif %.0f · uzman %.0f · kâhin %.0f → uzman/kâhin %%%.0f, naif/uzman %%%.0f".format(
                avg(none.map { it.profit }), avg(naive.map { it.profit }), avg(expert.map { it.profit }), avg(oracle.map { it.profit }), pct(expert, oracle), pct(naive, expert)))
            println("uzman: satış marjı ort %.0f · bekleme %.0f · fire %.0f · iade %.0f · kayıp satış %%%.1f · fire payı %%%.1f · hizmet %%%.0f · devir %.2f (kâhin %.2f)".format(
                avg(sales), avg(expert.map { it.holdingPts }), avg(expert.map { it.wastePts }), avg(expert.map { it.returnPts }),
                lostShare(expert), wasteShare(expert), expert.map { it.serviceLevel() }.average(), expert.map { it.turnover() }.average(), oracle.map { it.turnover() }.average()))
            println("naif: kayıp satış %%%.1f · iade ort %.0f · devir %.2f · süre ort %d ms".format(lostShare(naive), avg(naive.map { it.returnPts }), naive.map { it.turnover() }.average(), timeMs / weeks.size))
            val heavyLead = weeks.sumOf { w -> w.items.count { it.leadTime == 2 } }
            val perishable = weeks.sumOf { w -> w.items.count { it.perishable } }
            println("teslim 2 gün ürün toplam %d · bozulan ürün toplam %d · hedef en az %d en çok %d".format(heavyLead, perishable, weeks.minOf { it.target }, weeks.maxOf { it.target }))
        }
    }
}
