package com.aripd.reyon.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.engine.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Blok etiketi sığdırma: 35 ürün adı (Türkçe ve İngilizce) en dar gerçek durumda
 * bile kırpılmadan sığmalı. En dar durum: 360 dp telefonda Zor rafının planı,
 * tek yüzlü göz — yazı alanı 45 dp, ad bölgesi 19 dp, 8 sp.
 */
@RunWith(AndroidJUnit4::class)
class ReyonBlockLabelTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var labeler: BlockLabeler
    private var density = 1f

    private fun setUp() {
        rule.setContent {
            val measurer = rememberTextMeasurer()
            density = LocalDensity.current.density
            labeler = remember { BlockLabeler(measurer) }
        }
        rule.waitForIdle()
    }

    private fun names(): List<String> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tr = ctx.resources
        val en = ctx.createConfigurationContext(Configuration(tr.configuration).apply { setLocale(Locale.ENGLISH) }).resources
        return Kind.entries.flatMap { listOf(ReyonText.kind(tr, it), ReyonText.kind(en, it)) }.distinct()
    }

    private fun fit(text: String, widthDp: Float, heightDp: Float, maxSp: Float, minSp: Float = NAME_MIN_SP): BlockLabel =
        rule.runOnIdle { labeler.fit(text, widthDp * density, heightDp * density, maxSp, minSp, Color.Black) }

    @Test
    fun everyProductNameFitsAOneFacingSlotOnAShortPhoneWithoutEllipsis() {
        setUp()
        val names = names()
        assertTrue(names.size >= Kind.entries.size)
        for (name in names) {
            val l = fit(name, 45f, 19f, 8f)
            assertFalse("kırpıldı: $name", l.ellipsized)
            assertTrue("aşırı sıkıştı: $name (${l.scaleX})", l.scaleX >= BlockLabeler.MIN_CONDENSE)
            assertTrue("taştı: $name", l.width <= 45f * density + 1f && l.height <= 19f * density + 1f)
            assertTrue("punto: $name", l.sizeSp >= NAME_MIN_SP)
        }
    }

    @Test
    fun longTwoWordNamesBreakAtTheSpaceAndShortNamesKeepTheLargestSize() {
        setUp()
        // Kolay rafı (360 dp): yazı alanı 70 dp, ad bölgesi 34 dp, 14 sp.
        val long = fit("Bulaşık deterjanı", 70f, 34f, 14f)
        assertEquals(2, long.lines)
        assertFalse(long.ellipsized)
        assertTrue("iki satır büyük puntoda kalmalı: ${long.sizeSp}", long.sizeSp >= 10f)
        val short = fit("Su", 70f, 34f, 14f)
        assertEquals(1, short.lines)
        assertEquals(14f, short.sizeSp, 0.01f)
        // Aynı boyda iki seçenek de sığıyorsa tek satır kazanır.
        val spaced = fit("Meyve suyu", 120f, 34f, 14f)
        assertEquals(1, spaced.lines)
        assertEquals(14f, spaced.sizeSp, 0.01f)
    }

    @Test
    fun aWordTooLongForAnySizeIsCondensedThenEllipsized() {
        setUp()
        val condensed = fit("Yumuşatıcılar", 40f, 19f, 8f)
        assertTrue(condensed.scaleX < 1f && condensed.scaleX >= BlockLabeler.MIN_CONDENSE)
        assertFalse(condensed.ellipsized)
        assertEquals(1, condensed.lines)
        val cut = fit("Çokuzunbirürünadıkesinliklesığmaz", 30f, 19f, 8f)
        assertTrue(cut.ellipsized)
        assertEquals(1f, cut.scaleX, 0.001f)
        assertTrue(cut.width <= 30f * density + 1f)
    }

    @Test
    fun splitPrefersTheSpaceNearestTheMiddle() {
        assertEquals("Bulaşık\ndeterjanı", BlockLabeler.splitForTwoLines("Bulaşık deterjanı"))
        assertEquals("Kâğıt\nhavlu", BlockLabeler.splitForTwoLines("Kâğıt havlu"))
        assertEquals("Personal care\nproducts", BlockLabeler.splitForTwoLines("Personal care products"))
        assertEquals(null, BlockLabeler.splitForTwoLines("Yumuşatıcı"))
    }
}
