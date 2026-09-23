package com.aripd.reyon.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.ui.common.ACTION_LABEL_MIN_SP
import com.aripd.reyon.ui.common.actionLabelSp
import com.aripd.reyon.ui.common.actionLabelStyle
import com.aripd.reyon.ui.theme.ReyonTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Üç düğmeli alt satırın (Diziliş: Geri al / Tepsiye al / İpucu, Satış: … / Tamamla)
 * etiketleri 14 dilin hiçbirinde kelimenin ortasından bölünmemeli. v1.0.1 cihaz
 * koşumunda 360 dp'de sekiz dilde bölünüyordu: "Tamaml·a", "Rückgä·ngig",
 * "Подска·зка" (docs/cihaz-testi.md, F1).
 *
 * En dar gerçek düğme 360 dp telefonda: satırın yan payı 2 × 12, aralık 2 × 8,
 * düğmenin iç payı 2 × 8 ([com.aripd.reyon.ui.common.ActionRowPadding]) —
 * (360 − 24 − 16) / 3 − 16 ≈ 90,6 dp yazı alanı. Yazı ölçüleri Robolectric'in
 * yerel grafik kipinde gerçek fontla alınır: uygulamanın teması (IBM Plex Sans)
 * içinde, ekrandaki düğmeyle aynı yazıyla.
 */
@RunWith(AndroidJUnit4::class)
class ReyonActionLabelTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var measurer: TextMeasurer
    private lateinit var base: TextStyle
    private var density = 1f

    private fun setUp(fontScale: Float) {
        rule.setContent {
            ReyonTheme {
                val outer = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(outer.density, fontScale)) {
                    density = outer.density
                    measurer = rememberTextMeasurer()
                    base = MaterialTheme.typography.labelLarge
                }
            }
        }
        rule.waitForIdle()
    }

    private fun labels(): List<Pair<String, String>> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ids = listOf(R.string.undo, R.string.reyon_remove, R.string.reyon_hint, R.string.reyon_sales_finish)
        return AppLocale.TAGS.flatMap { tag ->
            val config = Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val res = ctx.createConfigurationContext(config).resources
            ids.map { tag to res.getString(it) }
        }
    }

    private fun checkNoWordIsBroken(fontScale: Float, minSp: Float) {
        setUp(fontScale)
        val width = (NARROWEST_TEXT_DP * density).toInt()
        val all = labels()
        assertTrue(all.size == AppLocale.TAGS.size * 4)
        rule.runOnIdle {
            for ((tag, text) in all) {
                val sp = actionLabelSp(measurer, text, base, width)
                val style = actionLabelStyle(base, sp)
                for (word in text.split(Regex("\\s+"))) {
                    val w = measurer.measure(word, style, softWrap = false, maxLines = 1).size.width
                    assertTrue("$tag \"$text\": \"$word\" ${sp}sp'de $w px > $width px, bölünür", w <= width)
                }
                val whole = measurer.measure(text, style, maxLines = 2, constraints = Constraints(maxWidth = width))
                assertFalse("$tag \"$text\": iki satıra sığmıyor (${sp}sp)", whole.hasVisualOverflow)
                assertTrue("$tag \"$text\": punto $sp < $minSp", sp >= minSp)
            }
        }
    }

    @Test
    fun noLabelBreaksMidWordOnTheNarrowestPhone() = checkNoWordIsBroken(fontScale = 1f, minSp = 12f)

    /** Ölçüm cihazının yazı ölçeği (SM-A515F, 1,1). */
    @Test
    fun noLabelBreaksMidWordAtTheMeasuredFontScale() = checkNoWordIsBroken(fontScale = 1.1f, minSp = 11f)

    /** Büyük yazı: punto tabana kadar iner ama kelime yine bölünmez. */
    @Test
    fun noLabelBreaksMidWordWithLargeText() = checkNoWordIsBroken(fontScale = 1.3f, minSp = ACTION_LABEL_MIN_SP)

    @Test
    fun shortLabelsKeepTheThemeSize() {
        setUp(1f)
        val width = (NARROWEST_TEXT_DP * density).toInt()
        rule.runOnIdle {
            for (text in listOf("Undo", "Hint", "İpucu", "Geri al")) {
                assertTrue(text, actionLabelSp(measurer, text, base, width) == base.fontSize.value)
            }
        }
    }

    private companion object {
        const val NARROWEST_TEXT_DP = (360f - 2 * 12f - 2 * 8f) / 3f - 2 * 8f
    }
}
