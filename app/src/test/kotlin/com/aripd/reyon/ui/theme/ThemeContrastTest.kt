package com.aripd.reyon.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.Category
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Erişilebilirlik: metin ve zemin renkleri okunabilir kontrastta olmalı.
 *
 * WCAG 2.1 AA, normal metin için 4,5:1, büyük metin ve arayüz öğeleri için
 * 3:1 ister. Tema tek yerde tanımlı olduğu için bu, cihaz gerektirmeyen ve
 * hızlı bir CI kontrolüdür; renk paleti değiştirildiğinde okunabilirliğin
 * sessizce bozulmasını engeller. İki tema da aynı eşiklerle ölçülür.
 */
class ThemeContrastTest {

    /** WCAG göreli parlaklık. */
    private fun luminance(color: Color): Double {
        fun kanal(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * kanal(color.red) + 0.7152 * kanal(color.green) + 0.0722 * kanal(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertPairs(tema: String, esik: Double, ciftler: List<Pair<String, Pair<Color, Color>>>) {
        for ((ad, cift) in ciftler) {
            val oran = contrast(cift.first, cift.second)
            assertTrue(
                "$tema · $ad kontrastı ${"%.2f".format(oran)}:1; WCAG AA en az $esik:1 ister",
                oran >= esik,
            )
        }
    }

    /** Material rolleri: metin ve zemin çiftleri. */
    private fun schemePairs(c: ColorScheme) = listOf(
        "onBackground/background" to (c.onBackground to c.background),
        "onSurface/surface" to (c.onSurface to c.surface),
        "onSurfaceVariant/surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
        "onSurfaceVariant/background" to (c.onSurfaceVariant to c.background),
        "onSurfaceVariant/surface" to (c.onSurfaceVariant to c.surface),
        "onPrimary/primary" to (c.onPrimary to c.primary),
        "onPrimaryContainer/primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
        "onSecondary/secondary" to (c.onSecondary to c.secondary),
        "onTertiaryContainer/tertiaryContainer" to (c.onTertiaryContainer to c.tertiaryContainer),
        "onError/error" to (c.onError to c.error),
        "onErrorContainer/errorContainer" to (c.onErrorContainer to c.errorContainer),
        "primary/background" to (c.primary to c.background),
        "primary/surface" to (c.primary to c.surface),
        "tertiary/surface" to (c.tertiary to c.surface),
        "error/surface" to (c.error to c.surface),
        "secondary/background" to (c.secondary to c.background),
    )

    /** Reyon renkleri: durum yazıları, etiket, dikkat sarısı, tonal düğme. */
    private fun tokenPairs(t: ReyonTokens, surface: Color, background: Color) = listOf(
        "ok/surface" to (t.ok to surface),
        "ok/okWeak" to (t.ok to t.okWeak),
        "warn/surface" to (t.warn to surface),
        "bad/surface" to (t.bad to surface),
        "bad/background" to (t.bad to background),
        "onWarnWeak/warnWeak" to (t.onWarnWeak to t.warnWeak),
        "onBadWeak/badWeak" to (t.onBadWeak to t.badWeak),
        "onBrandWeak/brandWeak" to (t.onBrandWeak to t.brandWeak),
        "onAttention/attention" to (t.onAttention to t.attention),
        "onLabel/label" to (t.onLabel to t.label),
        "label/rail" to (t.label to t.rail),
        "labelOk/label" to (t.labelOk to t.label),
        "labelBad/label" to (t.labelBad to t.label),
    ) + Brand.entries.map { b -> "beyaz/ambalaj ${b.name}" to (Color.White to t.pack(b)) }

    @Test
    fun `light theme text colours meet WCAG AA contrast`() {
        assertPairs("açık", 4.5, schemePairs(ReyonLightColors))
        assertPairs("açık", 4.5, tokenPairs(ReyonLightTokens, ReyonLightColors.surface, ReyonLightColors.background))
    }

    @Test
    fun `dark theme text colours meet WCAG AA contrast`() {
        assertPairs("koyu", 4.5, schemePairs(ReyonDarkColors))
        assertPairs("koyu", 4.5, tokenPairs(ReyonDarkTokens, ReyonDarkColors.surface, ReyonDarkColors.background))
    }

    /**
     * Kategori karesi metin değil, arayüz işareti: 3:1 yeter. Etiketin kâğıdında
     * ölçülür, çünkü kare yalnız orada çizilir.
     */
    @Test
    fun `category swatches stand out on the shelf label`() {
        for (tokens in listOf(ReyonLightTokens, ReyonDarkTokens)) {
            val tema = if (tokens.isDark) "koyu" else "açık"
            assertPairs(tema, 3.0, Category.entries.map { c -> "kategori ${c.name}/etiket" to (tokens.category(c) to tokens.label) })
        }
    }
}
