package com.aripd.reyon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.Category
import com.aripd.reyon.platform.appLocale

/**
 * Tema tercihi; ayarlarda saklanır. Varsayılan açık: uygulama mağaza ışığında
 * okunmak üzere tasarlandı, koyu tema bir seçenek. [SYSTEM] telefonun ayarını izler.
 */
enum class ThemeChoice { LIGHT, DARK, SYSTEM }

/** Tercihin o an çizilen karşılığı: koyu mu. */
@Composable
@ReadOnlyComposable
fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
}

/**
 * Açık tema. `internal`: erişilebilirlik kontrast testi (`ThemeContrastTest`) bu
 * değerleri okur — metin/zemin oranlarının WCAG AA eşiğinin altına düşmemesi
 * CI'da korunur. Bkz. docs/cihaz-testi.md (E aşaması).
 */
internal val ReyonLightColors = lightColorScheme(
    primary = Color(0xFF0F4C5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EEF0),
    onPrimaryContainer = Color(0xFF0F4C5C),
    inversePrimary = Color(0xFF7CC4D2),
    secondary = Color(0xFF2E3432),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E3DB),
    onSecondaryContainer = Color(0xFF17201D),
    tertiary = Color(0xFF8A4308),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD23F),
    onTertiaryContainer = Color(0xFF17201D),
    background = Color(0xFFF3F2EE),
    onBackground = Color(0xFF17201D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17201D),
    surfaceVariant = Color(0xFFEEECE6),
    onSurfaceVariant = Color(0xFF56615C),
    surfaceTint = Color(0xFF0F4C5C),
    inverseSurface = Color(0xFF2E3432),
    inverseOnSurface = Color(0xFFF3F2EE),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE7E5),
    onErrorContainer = Color(0xFF8F1C13),
    outline = Color(0xFFC9C5BB),
    outlineVariant = Color(0xFFDDDAD2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E3DB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F7F4),
    surfaceContainer = Color(0xFFF3F2EE),
    surfaceContainerHigh = Color(0xFFEEECE6),
    surfaceContainerHighest = Color(0xFFE6E3DB),
)

/** Koyu tema; açığın aynı rolleri, loş ortam için. */
internal val ReyonDarkColors = darkColorScheme(
    primary = Color(0xFF7CC4D2),
    onPrimary = Color(0xFF0B2A31),
    primaryContainer = Color(0xFF1D3439),
    onPrimaryContainer = Color(0xFF9ED5E0),
    inversePrimary = Color(0xFF0F4C5C),
    secondary = Color(0xFFB8C4BF),
    onSecondary = Color(0xFF17201D),
    secondaryContainer = Color(0xFF242B28),
    onSecondaryContainer = Color(0xFFE6EBE8),
    tertiary = Color(0xFFF0B35E),
    onTertiary = Color(0xFF2A1A05),
    tertiaryContainer = Color(0xFFFFD23F),
    onTertiaryContainer = Color(0xFF17201D),
    background = Color(0xFF111513),
    onBackground = Color(0xFFE6EBE8),
    surface = Color(0xFF1A1F1D),
    onSurface = Color(0xFFE6EBE8),
    surfaceVariant = Color(0xFF242B28),
    onSurfaceVariant = Color(0xFFA3ADA8),
    surfaceTint = Color(0xFF7CC4D2),
    inverseSurface = Color(0xFFE6EBE8),
    inverseOnSurface = Color(0xFF17201D),
    error = Color(0xFFF28B82),
    onError = Color(0xFF3D0B07),
    errorContainer = Color(0xFF3D1D1A),
    onErrorContainer = Color(0xFFF6B8B1),
    outline = Color(0xFF3A4340),
    outlineVariant = Color(0xFF2C3330),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF242B28),
    surfaceDim = Color(0xFF111513),
    surfaceContainerLowest = Color(0xFF0E1110),
    surfaceContainerLow = Color(0xFF161B19),
    surfaceContainer = Color(0xFF1A1F1D),
    surfaceContainerHigh = Color(0xFF1E2422),
    surfaceContainerHighest = Color(0xFF242B28),
)

/**
 * Material şemasında karşılığı olmayan renkler: raf, etiket rayı, dikkat sarısı,
 * durum ve ambalaj renkleri. Görsel dilin kuralları (tasarım taslağı 1.1.0):
 *
 * - Ambalaj rengi markayı taşır ([pack]); kategori raf etiketindeki kodla
 *   ([category]) okunur, ürünün kendisi kategoriye göre boyanmaz.
 * - Dikkat sarısı yalnız dikkat noktasında: seçili ürün, vurgulanan etiket.
 * - Durum renkleri tek başına bilgi taşımaz; hep bir metin ya da işaretle gelir.
 *
 * Etiket zemini iki temada da açık: raf etiketi fiziksel bir kâğıt, kategori
 * renkleri onun üstünde okunur ([ThemeContrastTest] ölçer).
 */
@Immutable
class ReyonTokens(
    val isDark: Boolean,
    /** Kart ve kutu kenarlığı. */
    val line: Color,
    /** Liste ayırıcısı, ilerleme çubuğu zemini. */
    val divider: Color,
    /** İkincil düğmenin kenarlığı. */
    val buttonLine: Color,
    /** Bölümlü seçimin zemini. */
    val segment: Color,
    /** Alt eylem çubuğunun zemini. */
    val actionBar: Color,
    /** Marka zemini: tonal düğme, kılavuz bandı. */
    val brandWeak: Color,
    val onBrandWeak: Color,
    /** Dikkat sarısı ve üstündeki yazı. */
    val attention: Color,
    val onAttention: Color,
    /** Raf ünitesinin zemini. */
    val gondola: Color,
    /** Raf çıtası. */
    val plank: Color,
    /** Etiket rayı. */
    val rail: Color,
    /** Raf etiketinin kâğıdı ve yazısı. */
    val label: Color,
    val onLabel: Color,
    /** Etiket kâğıdındaki artı ve eksi değerler; kâğıt iki temada da açık. */
    val labelOk: Color,
    val labelBad: Color,
    /** Talep çubuğunun sönük hâli. */
    val demandOff: Color,
    /** Boş gözün kesikli çizgisi. */
    val emptySlot: Color,
    /** Ambalaj renkleri, marka sırasıyla (A–D). */
    private val packs: List<Color>,
    /** Uyumlu. */
    val ok: Color,
    val okWeak: Color,
    /** Sapma. */
    val warn: Color,
    val warnWeak: Color,
    val onWarnWeak: Color,
    /** Eksik, hata. */
    val bad: Color,
    val badWeak: Color,
    val onBadWeak: Color,
) {
    fun pack(brand: Brand): Color = packs[brand.ordinal]

    fun category(category: Category): Color = CategoryColors[category.ordinal]
}

/** Kategori renkleri, [Category] sırasıyla; raf etiketinin kâğıdında iki temada da aynı. */
private val CategoryColors = listOf(
    Color(0xFF2F6FBF), // İçecek
    Color(0xFFD9542B), // Atıştırmalık
    Color(0xFF6F7F22), // Kahvaltılık
    Color(0xFF1A8FA0), // Temizlik
    Color(0xFF9A4FB0), // Bakım
)

internal val ReyonLightTokens = ReyonTokens(
    isDark = false,
    line = Color(0xFFDDDAD2),
    divider = Color(0xFFEEECE6),
    buttonLine = Color(0xFFC9C5BB),
    segment = Color(0xFFE6E3DB),
    actionBar = Color(0xFFFFFFFF),
    brandWeak = Color(0xFFE3EEF0),
    onBrandWeak = Color(0xFF0F4C5C),
    attention = Color(0xFFFFD23F),
    onAttention = Color(0xFF17201D),
    gondola = Color(0xFFE6E3DB),
    plank = Color(0xFFA9A59A),
    rail = Color(0xFF2E3432),
    label = Color(0xFFFFFFFF),
    onLabel = Color(0xFF17201D),
    labelOk = Color(0xFF1E7A4C),
    labelBad = Color(0xFFB42318),
    demandOff = Color(0xFFCFCBC1),
    emptySlot = Color(0xFFBDB8AC),
    packs = listOf(Color(0xFF2C4A7A), Color(0xFF2F6B55), Color(0xFF9C3552), Color(0xFF86591A)),
    ok = Color(0xFF1E7A4C),
    okWeak = Color(0xFFE4F2EA),
    warn = Color(0xFFB45309),
    warnWeak = Color(0xFFFDF0E1),
    onWarnWeak = Color(0xFF8A4308),
    bad = Color(0xFFB42318),
    badWeak = Color(0xFFFBE7E5),
    onBadWeak = Color(0xFF8F1C13),
)

internal val ReyonDarkTokens = ReyonTokens(
    isDark = true,
    line = Color(0xFF2C3330),
    divider = Color(0xFF242B28),
    buttonLine = Color(0xFF3A4340),
    segment = Color(0xFF242B28),
    actionBar = Color(0xFF161B19),
    brandWeak = Color(0xFF1D3439),
    onBrandWeak = Color(0xFF9ED5E0),
    attention = Color(0xFFFFD23F),
    onAttention = Color(0xFF17201D),
    gondola = Color(0xFF1E2422),
    plank = Color(0xFF56605B),
    rail = Color(0xFF0A0D0C),
    label = Color(0xFFF2F1EC),
    onLabel = Color(0xFF17201D),
    labelOk = Color(0xFF1E7A4C),
    labelBad = Color(0xFFB42318),
    demandOff = Color(0xFFCFCBC1),
    emptySlot = Color(0xFF46504C),
    packs = listOf(Color(0xFF3A5E96), Color(0xFF3B7F66), Color(0xFFB2476A), Color(0xFF9C6A22)),
    ok = Color(0xFF6FCF97),
    okWeak = Color(0xFF173A2A),
    warn = Color(0xFFF0A860),
    warnWeak = Color(0xFF3A2A16),
    onWarnWeak = Color(0xFFF5C489),
    bad = Color(0xFFF28B82),
    badWeak = Color(0xFF3D1D1A),
    onBadWeak = Color(0xFFF6B8B1),
)

private val LocalReyonTokens = staticCompositionLocalOf { ReyonLightTokens }

/** Tema dışı renklere erişim: `Reyon.tokens.rail` gibi. */
object Reyon {
    val tokens: ReyonTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalReyonTokens.current
}

@Composable
fun ReyonTheme(choice: ThemeChoice = ThemeChoice.LIGHT, content: @Composable () -> Unit) {
    val dark = choice.isDark()
    val colors = if (dark) ReyonDarkColors else ReyonLightColors
    // Arapça arayüz kendi Plex ailesiyle çizilir; Latin yazılar aynı aileden gelir.
    val arabic = appLocale().language == "ar"
    val typography = remember(arabic) { reyonTypography(if (arabic) ReyonFonts.SansArabic else ReyonFonts.Sans) }
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Kökte Surface yok; Material yalnızca Surface içinde içerik rengi sağlar.
        // Rengi açıkça verilmeyen her Text aksi hâlde varsayılan siyahla çizilir.
        CompositionLocalProvider(
            LocalReyonTokens provides if (dark) ReyonDarkTokens else ReyonLightTokens,
            LocalContentColor provides colors.onBackground,
            content = content,
        )
    }
}
