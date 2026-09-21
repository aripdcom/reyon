package com.aripd.reyon.ui.theme

import androidx.compose.ui.graphics.Color
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
 * sessizce bozulmasını engeller.
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

    @Test
    fun `text colours meet WCAG AA contrast`() {
        val c = ReyonColors
        val ciftler = listOf(
            "onBackground/background" to (c.onBackground to c.background),
            "onSurface/surface" to (c.onSurface to c.surface),
            "onSurfaceVariant/surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
            "onSurfaceVariant/background" to (c.onSurfaceVariant to c.background),
            "onPrimary/primary" to (c.onPrimary to c.primary),
            "onSecondary/secondary" to (c.onSecondary to c.secondary),
            "onError/error" to (c.onError to c.error),
            "primary/background" to (c.primary to c.background),
            "primary/surface" to (c.primary to c.surface),
            "error/surface" to (c.error to c.surface),
            "secondary/background" to (c.secondary to c.background),
        )
        for ((ad, cift) in ciftler) {
            val oran = contrast(cift.first, cift.second)
            assertTrue(
                "$ad kontrastı ${"%.2f".format(oran)}:1; WCAG AA en az 4,5:1 ister",
                oran >= 4.5,
            )
        }
    }
}
