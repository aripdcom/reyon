package com.aripd.reyon.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Kısa telefonda (360×640 dp) tuvalin altındaki panel çizilmeli.
 *
 * Diziliş, Satış ve Sipariş aynı iskelete oturuyor: raf tuvali, altında
 * kaydırılabilir panel, en altta tepsi. Tuval yalnız genişlikten ölçülüp
 * sütunda ağırlıksız durduğu için yüksekliği önce o alıyordu; v0.43.1 cihaz
 * koşumunda Diziliş'in planogram brifine ~0 kalmıştı (kural satırı ≈13 dp,
 * ikinci kuralın erişilebilirlik kutusu sıfır, kaydırma da açmıyordu). Brif
 * olmadan bulmaca çözülemez, yani mod o ekranda oynanamaz durumdaydı.
 *
 * Ölçüm kırpılmış kutulara bakıyor (`getBoundsInRoot`), yani cihazın
 * erişilebilirlik ağacında gördüğü değerlere.
 *
 * Yerleşimin garantisi panelin **yüksekliği**: ya tabanını ([PANEL_MIN], yani
 * başlık + üç satır) almış olur, ya da içeriği tabandan kısa olduğu için kendi
 * boyunda durup hiçbir satırı kırpmaz. Panele kaç kural sığdığı buna değil,
 * üretilen ipucu metninin kaç satıra sardığına bağlı; bulmaca her koşumda
 * yeniden üretildiği için satır saymak kararsız olur (bir koşumda tam bu yüzden
 * kırıldı). Kısa ekranda kaç kural okunduğu cihazda ölçülüyor
 * (`docs/cihaz-testi.md`, G1).
 *
 * Yükseklik doğrudan uygulama alanı: Robolectric sistem çubuğu koymadığı için
 * `h640dp` 640 dp'lik bir uygulama alanı demek. Cihazda 360×640 dp bir ekranın
 * uygulama alanı ~568 dp (durum çubuğu 24 + gezinme 48), yani bulgunun geldiği
 * ölçü `h568dp`. `h480dp` daha da darı: tavanlar orada da tutmalı.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+w360dp-h568dp-xhdpi")
class ReyonShortScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun leaveRoundAndClear() {
        rule.reyonLeaveRound()
        ReyonTestSupport.clearPrefs()
    }

    private val trayPrefix: String get() = str(R.string.reyon_tray_label) + ":"

    /**
     * Tabanda kabul edilen pay. Taban sütunda iki iç içe ölçüm geçişinden geçiyor
     * (oyun alanı → panel+tepsi kutusu) ve px/dp yuvarlaması birkaç dp yiyor:
     * Satış'ta 140 dp hedefiyle 138 dp ölçüldü. Pay o yuvarlama için, açlık için
     * değil — bozuk hâlde panel 26 dp'ydi.
     */
    private val PANEL_SLACK = 4.dp

    private fun startRound(kindLabel: String, startLabel: String) {
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonOpenMenu()
        rule.reyonPickKind(kindLabel)
        // Kısa ekranda menü kartı kayar; düğmeler görünür alana getirilerek basılır.
        rule.onNodeWithText(str(R.string.mode_free)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.difficulty_easy)).performScrollTo().performClick()
        rule.onNodeWithText(startLabel).performScrollTo().performClick()
    }

    private fun awaitNodes(prefix: String) = rule.waitUntil(timeoutMillis = 30_000) {
        rule.onAllNodes(hasContentDescription(prefix, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Erişilebilirlik kutuları; aynı ön ekle başlayan her düğüm için biri. */
    private fun describedBounds(prefix: String): List<DpRect> {
        val nodes = rule.onAllNodes(hasContentDescription(prefix, substring = true))
        return List(nodes.fetchSemanticsNodes().size) { nodes[it].getBoundsInRoot() }
    }

    /** Verilen metinlerin kutuları; kaydırma görünümünde dışarıda kalan satır sıfır yükseklikte ölçülür. */
    private fun textBounds(vararg texts: Int): List<DpRect> = texts.map { id ->
        rule.onAllNodesWithText(str(id))[0].getBoundsInRoot()
    }

    /**
     * Yerleşim garantisi: panel açlıktan ölmüyor.
     *
     * Payı [PANEL_MIN], ama panel ile tepsi aynı kalandan besleniyor ve tepsinin de
     * bir tabanı var ([TRAY_MIN]) — kalan ikisine birden yetmezse panele düşen
     * `kalan - TRAY_MIN` oluyor. Beklenen taban o yüzden ikisinin küçüğü. Panel
     * `weight(1f, fill = false)` ile durduğu için içeriğinden de büyümüyor: içerik
     * tabandan kısaysa panel kendi boyunda kalır, o durumda da kırpılan satır
     * olmaz. Bozuk hâlde hiçbiri yoktu: kalan 448 dp iken panel 26 dp'ydi ve
     * satırlar kırpılıyordu.
     */
    private fun assertPanelIsNotStarved(label: String, rows: List<DpRect>) {
        val root = rule.onRoot().getBoundsInRoot()
        val panel = rule.onNodeWithTag(REYON_PANEL_TAG).getBoundsInRoot()
        val tray = rule.onNodeWithTag(REYON_TRAY_TAG).getBoundsInRoot()
        assertTrue("$label: panel ekranın içinde olmalı: $panel / $root", panel.bottom <= root.bottom + 1.dp)
        // Panel ile tepsi kutuyu tepeden aşağı paylaşıyor, yani kalan ikisinin toplamı.
        val rest = panel.height + tray.height
        val floor = minOf(PANEL_MIN, rest - TRAY_MIN)
        val clipped = rows.count { it.height <= 0.dp }
        assertTrue(
            "$label: panel ya en az ${floor - PANEL_SLACK} olmalı ya da hiçbir satırı kırpmamalı; " +
                "ölçülen panel ${panel.height}, tepsi ${tray.height}, kalan $rest, " +
                "kırpılan $clipped/${rows.size} satır",
            panel.height >= floor - PANEL_SLACK || clipped == 0,
        )
    }

    /** Panelin en az [least] satırı [min] yüksekliğinde çizilmiş ve ekranın içinde olmalı. */
    private fun assertRowsAreReadable(label: String, rows: List<DpRect>, least: Int, min: Dp) {
        val root = rule.onRoot().getBoundsInRoot()
        val readable = rows.filter { it.height >= min }
        assertTrue(
            "$label: en az $least satır $min yüksekliğinde olmalı, ölçülen ${rows.map { it.height }}",
            readable.size >= least,
        )
        for (r in readable) {
            assertTrue("$label: satır ekranın içinde olmalı: $r / $root", r.top >= root.top - 1.dp && r.bottom <= root.bottom + 1.dp)
        }
    }

    /** Cihazın bulguyu verdiği ölçü: 360×640 dp ekranın uygulama alanı. */
    @Test
    fun theBriefIsDrawnInADeviceSizedAppArea() = assertTheBriefIsDrawn()

    @Test
    @Config(qualifiers = "+w360dp-h640dp-xhdpi")
    fun theBriefIsDrawnOnAShortPhone() = assertTheBriefIsDrawn()

    /** Daha da darı: tavanlar burada da panele yer bırakmalı. */
    @Test
    @Config(qualifiers = "+w360dp-h480dp-xhdpi")
    fun theBriefIsDrawnOnAVeryShortPhone() = assertTheBriefIsDrawn()

    private fun assertTheBriefIsDrawn() {
        startRound(str(R.string.reyon_kind_puzzle), str(R.string.reyon_start))
        val briefPrefix = str(R.string.reyon_brief_label) + ":"
        awaitNodes(briefPrefix)
        val brief = describedBounds(briefPrefix)
        assertPanelIsNotStarved("brif", brief)
        // Kural satırı bir satırlık bodySmall metni + 3 dp dolgu, yani en az 20 dp;
        // bozukken 13 dp ölçülmüştü.
        assertTrue("brif boş olmamalı", brief.isNotEmpty())
        assertRowsAreReadable("brif", brief, least = 1, min = 20.dp)
        val root = rule.onRoot().getBoundsInRoot()
        val shelfPrefix = str(R.string.reyon_board_desc_fmt, 0, 0, 0).substringBefore(' ')
        val shelf = rule.onNode(hasContentDescription(shelfPrefix, substring = true)).getBoundsInRoot()
        assertTrue("raf neredeyse tam genişlikte olmalı: ${shelf.width} / ${root.width}", shelf.width >= root.width * 0.85f)
        assertTrue("raf ekranın içinde olmalı: $shelf / $root", shelf.bottom <= root.bottom)
        val firstClue = brief.first { it.height >= 20.dp }
        assertTrue("brif rafın altında olmalı: $firstClue / $shelf", firstClue.top >= shelf.bottom)
        // Tepsi de duruyor: ürün seçilemezse bulmaca çözülemez.
        val tray = describedBounds(trayPrefix).filter { it.height >= 20.dp }
        assertTrue("tepside okunur ürün olmalı", tray.isNotEmpty())
        assertTrue("tepsi brifin altında olmalı", tray.all { it.top >= firstClue.top })
    }

    @Test
    fun theSalesRulesPanelIsDrawnOnAShortPhone() {
        startRound(str(R.string.reyon_kind_sales), str(R.string.reyon_sales_start))
        awaitNodes(trayPrefix)
        val rules = textBounds(
            R.string.reyon_sales_rule_position,
            R.string.reyon_sales_rule_complement,
            R.string.reyon_sales_rule_conflict,
            R.string.reyon_sales_rule_category,
            R.string.reyon_sales_rule_brand,
        )
        // Kural adı tek satırlık labelMedium; puan kuralları görünmezse oyuncu neyi
        // topladığını bilmiyor.
        assertPanelIsNotStarved("satış kuralı", rules)
        assertRowsAreReadable("satış kuralı", rules, least = 3, min = 14.dp)
        val tray = describedBounds(trayPrefix).filter { it.height >= 20.dp }
        assertTrue("tepside okunur ürün olmalı", tray.isNotEmpty())
    }

    /**
     * Kırpılan kural gövdesi dokununca açılıyor.
     *
     * Kısa ekranda `Konum` kuralının gövdesi iki satır sınırına takılıp son
     * parçasını ("★ yalnız göz hizasında ×4") üç noktanın arkasında bırakıyor;
     * o çarpan uygulamada başka hiçbir yerde yazılı değil (cihazda ölçüldü,
     * `docs/cihaz-testi.md` G4 doğrulaması).
     *
     * Testin kanıtladığı: satır dokunmayı alıyor, açılan gövde kısalmıyor ve
     * ikinci dokunuş kapatıyor; açılmış hâlde de panel garantisi (bkz.
     * [assertPanelIsNotStarved]) duruyor. Kanıtlayamadığı: gövdenin gerçekten
     * kırpıldığı — Robolectric'in yazı ölçüleri cihazınkinden farklı, kırpma
     * kırpılmıyorsa iki ölçüm eşit çıkar ve test sessiz geçer. Kırpmanın kendisi
     * cihazda ölçülüyor.
     */
    @Test
    fun aClippedSalesRuleOpensOnTap() {
        startRound(str(R.string.reyon_kind_sales), str(R.string.reyon_sales_start))
        awaitNodes(trayPrefix)
        val name = str(R.string.reyon_sales_rule_position)
        val descHeight = { rule.onNodeWithText(str(R.string.reyon_sales_rule_position_desc)).getBoundsInRoot().height }
        val collapsed = descHeight()
        rule.onNodeWithText(name).performClick()
        val opened = descHeight()
        assertTrue("açılan gövde kısalamaz: $collapsed → $opened", opened >= collapsed - 1.dp)
        // Açık satır paneli büyütüyor; garanti bozulmamalı — panel kendi içinde kayar.
        assertPanelIsNotStarved(
            "açık satış kuralı",
            textBounds(
                R.string.reyon_sales_rule_position,
                R.string.reyon_sales_rule_complement,
                R.string.reyon_sales_rule_conflict,
            ),
        )
        rule.onNodeWithText(name).performClick()
        assertTrue("ikinci dokunuş kapatmalı: $opened → ${descHeight()}", descHeight() <= collapsed + 1.dp)
    }

    /**
     * Sipariş'in listesi tuvale değil, üstündeki gün başlığına sıkışıyor: 360×640 dp'de
     * başlık ~180 dp alıyor ve listeye bir satır kalıyor. Tuval tavanı bunu çözmüyor —
     * ayrı bir konu, `docs/cihaz-testi.md`'de açık madde. Burada aranan, listenin
     * kullanılabilir olması: ilk satır tam çizilmiş ve kalanına kaydırmayla ulaşılıyor.
     */
    @Test
    fun theOrderListIsUsableOnAShortPhone() {
        startRound(str(R.string.reyon_kind_order), str(R.string.reyon_order_start))
        val morePrefix = str(R.string.reyon_order_more) + ":"
        awaitNodes(morePrefix)
        // PANEL_MIN garantisi burada aranmıyor: garantiyi tepsi yer vererek
        // sağlıyor, Sipariş'te ise tepsi yok. Liste gün başlığına sıkışıyor.
        // Adımlayıcı 44×32 dp (`StepButton`).
        assertRowsAreReadable("sipariş satırı", describedBounds(morePrefix), least = 1, min = 30.dp)
        val steppers = rule.onAllNodes(hasContentDescription(morePrefix, substring = true))
        val last = steppers.fetchSemanticsNodes().size - 1
        assertTrue("listede birden çok ürün olmalı", last > 0)
        val scrolled = steppers[last].performScrollTo().getBoundsInRoot()
        assertTrue("son ürünün adımlayıcısı kaydırınca tam görünmeli: $scrolled", scrolled.height >= 30.dp)
        val root = rule.onRoot().getBoundsInRoot()
        assertTrue("son satır ekranın içinde olmalı: $scrolled / $root", scrolled.bottom <= root.bottom + 1.dp)
    }
}
