package com.aripd.reyon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aripd.reyon.R

/**
 * IBM Plex aileleri (SIL Open Font License 1.1, metni res/raw/license_ofl.txt).
 * Dosyalar değiştirilmeden, yalnız WOFF2 kabından TTF'ye açılarak konuldu:
 * alt küme almak lisansta "değiştirilmiş sürüm" sayılır ve ayrılmış "Plex" adını
 * taşıyamaz.
 *
 * Ağırlıklar bilinçli olarak az: arayüz 400 ve 600 ile yetiniyor, istenen başka
 * ağırlık en yakınına düşer (500 → 400, 700 → 600). Rakamlar eş genişlikli
 * Plex Mono'yla yazılır ki KPI'lar değişirken kıpırdamasın; ambalaj üstündeki
 * ürün adı dar gözlere sığsın diye Plex Sans Condensed.
 */
object ReyonFonts {
    val Sans = FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    )
    val SansArabic = FontFamily(
        Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    )
    val Mono = FontFamily(Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold))
    val Condensed = FontFamily(Font(R.font.ibm_plex_sans_condensed_semibold, FontWeight.SemiBold))
}

/**
 * Material ölçeği, Plex ile. Başlıklar ve etiketler yarı kalın; gövde normal.
 * Punto ve satır aralıkları Material'ınki: ekranların yerleşim testleri bu
 * ölçülerle kalibre edildi.
 */
internal fun reyonTypography(sans: FontFamily): Typography {
    val m = Typography()
    fun TextStyle.plex(weight: FontWeight? = null) = copy(fontFamily = sans, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = m.displayLarge.plex(),
        displayMedium = m.displayMedium.plex(),
        displaySmall = m.displaySmall.plex(),
        headlineLarge = m.headlineLarge.plex(FontWeight.SemiBold),
        headlineMedium = m.headlineMedium.plex(FontWeight.SemiBold),
        headlineSmall = m.headlineSmall.plex(FontWeight.SemiBold),
        titleLarge = m.titleLarge.plex(FontWeight.SemiBold),
        titleMedium = m.titleMedium.plex(FontWeight.SemiBold),
        titleSmall = m.titleSmall.plex(FontWeight.SemiBold),
        bodyLarge = m.bodyLarge.plex(),
        bodyMedium = m.bodyMedium.plex(),
        bodySmall = m.bodySmall.plex(),
        labelLarge = m.labelLarge.plex(FontWeight.SemiBold),
        labelMedium = m.labelMedium.plex(FontWeight.SemiBold),
        labelSmall = m.labelSmall.plex(FontWeight.SemiBold),
    )
}
