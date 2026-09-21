package com.aripd.reyon.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Panel ile tepsinin kalan yüksekliği paylaşma kuralı.
 *
 * Kural saf bir işlev olduğu için burada cihaz da Robolectric de gerekmiyor;
 * ölçülen sayılar cihazdan geliyor (`docs/cihaz-testi.md`), sınanan onların
 * aritmetiği. İki kol var: Diziliş'te tepsi önce ölçülür ([trayHeight]),
 * Satış'ta panel önce ölçülür ([panelCap]).
 *
 * Korunan asıl şey: Satış'ın kolu uzun ekranda panele yer açarken kısa ekranın
 * yerleşimini **hiç** değiştirmiyor, ve tavan panelin içeriğine göre değil
 * tepsinin payına göre yazıldığı için dil ya da yazı ölçeği değişince
 * kalibrasyon bozulmuyor.
 */
class ReyonLayoutTest {

    /** Kısa ekran: tavan tabanda kalıyor, yani yerleşim v0.43.2'de ölçüldüğü gibi. */
    @Test
    fun shortScreenPinsThePanelCapToTheFloor() {
        // 360×640 dp'de ölçülen kalan; panel 140 dp tabanını alıyordu.
        assertEquals(PANEL_MIN, panelCap(226.dp))
        // Ayrım noktası: tepsiye TRAY_KEEP kalana kadar tavan kıpırdamıyor.
        assertEquals(PANEL_MIN, panelCap(308.dp))
        assertEquals(PANEL_MIN + 1.dp, panelCap(309.dp))
    }

    /**
     * Ayrım noktasının altında iki kol birebir aynı pay veriyor.
     *
     * Panel önce ölçülünce tavan `min(PANEL_MIN, rest - TRAY_MIN)`e iniyor, ki bu
     * da tepsi önce ölçüldüğünde panele düşen paydı. Yani Satış'ın kolunu
     * çevirmek kısa ekranda **hiçbir şeyi** değiştirmiyor; "etkilenmiyor" burada
     * tahmin değil, aritmetik.
     */
    @Test
    fun belowTheSplitBothArmsGiveTheSameShare() {
        for (rest in 72..308) {
            assertEquals("kalan $rest dp", rest.dp - trayHeight(rest.dp), panelCap(rest.dp))
        }
    }

    /**
     * Tepsinin tabanı tavanın üstünde: çok kısa ekranda panel tepsiyi yemiyor.
     *
     * 480 dp'lik uygulama alanında ölçülen kalan 177,5 dp; belgedeki sayı tepsi
     * 72 dp, panel 105,5 dp. Tavanı yalnız `maxOf` belirlese 140 dp derdi ve
     * tepsiye 37,5 dp kalırdı — ürün seçilemeyen bir tepsi bulmacayı çözülemez
     * yapar.
     */
    @Test
    fun theTrayFloorOutranksThePanelFloor() {
        assertEquals(105.5.dp, panelCap(177.5.dp))
        assertEquals(TRAY_MIN, 177.5.dp - panelCap(177.5.dp))
        // Kalan tepsinin tabanından da azsa tavan sıfırlanıyor (negatif olmuyor).
        assertEquals(0.dp, panelCap(60.dp))
    }

    /** Uzun ekran: tavan yükseliyor, ama tepsinin iki sırası her hâlükârda duruyor. */
    @Test
    fun tallScreenRaisesTheCapButAlwaysKeepsTheTrayTwoRows() {
        // 411 dp'de ölçülen kalan.
        assertEquals(426.dp - TRAY_KEEP, panelCap(426.dp))
        assertEquals(TRAY_KEEP, 426.dp - panelCap(426.dp))
    }

    /**
     * Tavan panelin *istediği* değil, izin verilen en çoğu.
     *
     * Panel ağırlıksız olduğu için içeriği kadar yer alıyor; kısa içerikte artan
     * tepsiye kalıyor. Cihazda beş kural 232,4 dp tuttu, yani 426 dp'lik kalanda
     * tepsiye TRAY_KEEP'ten fazlası düşüyor.
     */
    @Test
    fun aShortPanelLeavesMoreThanTheKeepToTheTray() {
        val rest = 426.dp
        val olculenIcerik = 232.4.dp
        val panel = minOf(olculenIcerik, panelCap(rest))
        assertEquals(olculenIcerik, panel)
        assertTrue("tepsiye kalan ${rest - panel}", rest - panel > TRAY_KEEP)
    }

    /**
     * Uzun bir dilde ya da büyük yazı ölçeğinde içerik tavanı aşarsa panel tavanda
     * duruyor ve tepsi payını koruyor. Sabit bir "istenen boy" olsaydı panel orada
     * kalır ve beşinci kuralın adı yine kırpılırdı.
     */
    @Test
    fun aLongPanelStopsAtTheCapInsteadOfEatingTheTray() {
        val rest = 426.dp
        // Beş kuralın gövdesi ikinci satıra sararsa: 232,4 + 4 × 17,7 dp.
        val uzunIcerik = 303.2.dp
        val panel = minOf(uzunIcerik, panelCap(rest))
        assertEquals(rest - TRAY_KEEP, panel)
        assertEquals(TRAY_KEEP, rest - panel)
    }

    /**
     * Kalan iki tabana yetmediğinde tepsi kazanıyor, panel kalanı alıyor.
     *
     * [PANEL_MIN] bir hedef, dokunulmaz bir taban değil: 100 dp'lik kalanda tepsi
     * 72 dp'sini alıyor ve panele 28 dp düşüyor — eski kolun (`trayHeight`) çok
     * kısa ekranda yaptığının aynısı. Belgedeki ölçülmüş örnek de bu: 480 dp'lik
     * uygulama alanında panel 105,5 dp.
     */
    @Test
    fun whenNeitherFloorFitsTheTrayWins() {
        assertEquals(28.dp, panelCap(100.dp))
        assertEquals(TRAY_MIN, 100.dp - panelCap(100.dp))
    }

    /**
     * Gövdeler yalnız panel tabanına sıkışmışken tek satıra iniyor.
     *
     * Eşik ayrı bir sayı değil, tavanın kendisi: tabana çakılıysa ekran kısadır.
     * 480 dp'lik uygulama alanında (kalan 177,5 dp) tavan tabanın altında, orada
     * da sıkışma var. 411 dp'de (kalan 426 dp) tavan 258 dp, gövdeler iki satır.
     */
    @Test
    fun rulesGoOneLineOnlyWhenThePanelIsAtItsFloor() {
        assertTrue("177,5 dp: sıkışmalı", rulesAreCompact(177.5.dp))
        assertTrue("226 dp: sıkışmalı", rulesAreCompact(226.dp))
        assertTrue("308 dp (ayrım): sıkışmalı", rulesAreCompact(308.dp))
        assertTrue("309 dp: sıkışma bitmeli", !rulesAreCompact(309.dp))
        assertTrue("426 dp: sıkışma olmamalı", !rulesAreCompact(426.dp))
    }

    /** Diziliş'in kolu (tepsi önce ölçülür) değişmedi. */
    @Test
    fun theBriefArmIsUnchanged() {
        for (rest in listOf(100, 226, 308, 426, 700)) {
            assertEquals("kalan $rest dp", (rest.dp - PANEL_MIN).coerceAtLeast(TRAY_MIN), trayHeight(rest.dp))
        }
    }
}
