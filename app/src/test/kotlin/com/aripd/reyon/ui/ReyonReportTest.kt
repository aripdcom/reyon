package com.aripd.reyon.ui

import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Raporun kuralları: uyum bandının eşikleri, Sipariş bulguları, günün vakasının
 * formatı ve "aynı vaka" kararı. Saf hesap; cihaz ya da Robolectric gerekmez.
 */
class ReyonReportTest {

    @Test
    fun complianceBandThresholdsAreTheDraftsProposal() {
        assertEquals(ComplianceBand.EXPERT, ComplianceBand.of(100))
        assertEquals(ComplianceBand.EXPERT, ComplianceBand.of(90))
        assertEquals(ComplianceBand.GOOD, ComplianceBand.of(89))
        assertEquals(ComplianceBand.GOOD, ComplianceBand.of(75))
        assertEquals(ComplianceBand.DEVELOPING, ComplianceBand.of(74))
        assertEquals(ComplianceBand.DEVELOPING, ComplianceBand.of(50))
        assertEquals(ComplianceBand.WEAK, ComplianceBand.of(49))
        assertEquals(ComplianceBand.WEAK, ComplianceBand.of(0))
        // Uzmanı geçen sonuç da uzman düzeyi; eksi kâr zayıf.
        assertEquals(ComplianceBand.EXPERT, ComplianceBand.of(130))
        assertEquals(ComplianceBand.WEAK, ComplianceBand.of(-20))
    }

    /** Tasarım taslağındaki hafta: hizmet 70/93, devir 6,3/9,8 → üç bulgu. */
    @Test
    fun theDraftsWeekGivesThreeFindings() {
        val f = orderFindings(service = 70, targetService = 93, turnover = 6.3f, targetTurnover = 9.8f)
        assertEquals(listOf(R.string.report_service_low_fmt, R.string.report_turnover_low_fmt, R.string.report_wrong_mix), f.map { it.text })
        assertEquals(listOf(FindingTone.BAD, FindingTone.WARN, FindingTone.NOTE), f.map { it.tone })
        assertEquals(23, f[0].args.single())
    }

    @Test
    fun aWeekOnParWithTheExpertIsReportedAsSuch() {
        val f = orderFindings(service = 91, targetService = 93, turnover = 9.5f, targetTurnover = 9.8f)
        assertEquals(listOf(R.string.report_service_ok, R.string.report_turnover_ok), f.map { it.text })
        assertTrue(f.all { it.tone == FindingTone.OK })
    }

    /** Stok fazla sıkı: devir uzmandan yüksek ama hizmet düşük. */
    @Test
    fun tightStockIsNamedWhenServiceFalls() {
        val f = orderFindings(service = 72, targetService = 93, turnover = 12f, targetTurnover = 9.8f)
        assertEquals(listOf(R.string.report_service_low_fmt, R.string.report_turnover_tight), f.map { it.text })
        assertFalse(f.any { it.text == R.string.report_wrong_mix })
    }

    @Test
    fun theDailyFormatRotatesThroughAllThreeFormats() {
        val days = (19_000L until 19_003L).map { dailyLevel(it) }
        assertEquals(ReyonLevel.entries.toSet(), days.toSet())
        assertEquals(dailyLevel(19_000L), dailyLevel(19_003L))
        // Eksi gün (epoch öncesi) de geçerli bir format verir.
        assertTrue(dailyLevel(-1L) in ReyonLevel.entries)
    }

    @Test
    fun aDailyCaseIsTheSameOnlyOnTheSameDay() {
        assertTrue(sameCase(ReyonMode.DAILY, 5L, ReyonLevel.ORTA, ReyonMode.DAILY, ReyonLevel.ORTA, 5L))
        assertFalse(sameCase(ReyonMode.DAILY, 4L, ReyonLevel.ORTA, ReyonMode.DAILY, ReyonLevel.ORTA, 5L))
        // Alıştırma gün tanımaz: dünkü yarım alıştırma bugün de sürer.
        assertTrue(sameCase(ReyonMode.FREE, 4L, ReyonLevel.ZOR, ReyonMode.FREE, ReyonLevel.ZOR, 5L))
        assertFalse(sameCase(ReyonMode.FREE, 5L, ReyonLevel.ZOR, ReyonMode.FREE, ReyonLevel.KOLAY, 5L))
        assertFalse(sameCase(ReyonMode.FREE, 5L, ReyonLevel.KOLAY, ReyonMode.DAILY, ReyonLevel.KOLAY, 5L))
    }
}
