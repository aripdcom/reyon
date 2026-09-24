package com.aripd.reyon.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.ui.common.fitSp
import com.aripd.reyon.ui.theme.ReyonTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Görevler'in dar metinleri 14 dilde, 360 dp telefonda, ölçüm cihazının yazı ölçeğinde
 * (1,1) kırpılmamalı ve kelimenin ortasından bölünmemeli (docs/cihaz-testi.md, 1.1.0):
 *
 * - G2: format seçicideki ad bir dilime sığar ("Supermercado" es/pt, "Supermercato" it
 *   kırpılıyordu). Dilim: (360 − 2 × 12 − 2 × 4 − 2 × 4) / 3 − 2 × 4 ≈ 98,7 dp.
 * - G3: alıştırma satırının alt yazısında her kelime tek satıra sığar ("Wöchentliche
 *   Bestandsentscheidunge·n" bölünüyordu). Yazı sütunu, yanında "301/327" dururken
 *   ~162 dp; pay bırakıp 160 dp.
 */
@RunWith(AndroidJUnit4::class)
class ReyonHomeTextTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var measurer: TextMeasurer
    private lateinit var label: TextStyle
    private lateinit var body: TextStyle
    private var density = 1f

    private fun setUp() {
        rule.setContent {
            ReyonTheme {
                val outer = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(outer.density, FONT_SCALE)) {
                    density = outer.density
                    measurer = rememberTextMeasurer()
                    label = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    body = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun each(ids: List<Int>): List<Pair<String, String>> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return AppLocale.TAGS.flatMap { tag ->
            val config = Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val res = ctx.createConfigurationContext(config).resources
            ids.map { tag to res.getString(it) }
        }
    }

    @Test
    fun formatNamesFitTheNarrowestSegment() {
        setUp()
        val width = (SEGMENT_DP * density).toInt()
        val names = each(ReyonLevel.entries.map { formatName(it) })
        rule.runOnIdle {
            for ((tag, name) in names) {
                val sp = fitSp(measurer, name, label, width, FORMAT_MIN_SP)
                val w = measurer.measure(name, label.copy(fontSize = sp.sp), softWrap = false, maxLines = 1).size.width
                assertTrue("$tag \"$name\": ${sp}sp'de $w px > $width px, kırpılır", w <= width)
            }
        }
    }

    @Test
    fun practiceSubtitlesNeverBreakAWord() {
        setUp()
        val width = (PRACTICE_TEXT_DP * density).toInt()
        val descs = each(listOf(R.string.home_puzzle_desc, R.string.home_audit_desc, R.string.home_sales_desc, R.string.home_order_desc))
        rule.runOnIdle {
            for ((tag, desc) in descs) {
                for (word in desc.split(Regex("\\s+"))) {
                    val w = measurer.measure(word, body, softWrap = false, maxLines = 1).size.width
                    assertTrue("$tag \"$desc\": \"$word\" $w px > $width px, bölünür", w <= width)
                }
            }
        }
    }

    private companion object {
        const val FONT_SCALE = 1.1f
        const val FORMAT_MIN_SP = 11f
        const val SEGMENT_DP = (360f - 2 * 12f - 2 * 4f - 2 * 4f) / 3f - 2 * 4f
        const val PRACTICE_TEXT_DP = 160f
    }
}
