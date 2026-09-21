package com.aripd.reyon.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ZA bilinçli olarak tek görünüme sahip: koyu, yüksek kontrastlı bir oyun teması.
/**
 * Uygulama renk şeması. `internal`: erişilebilirlik kontrast testi
 * (`ThemeContrastTest`) bu değerleri okur — metin/zemin oranlarının WCAG AA
 * eşiğinin altına düşmemesi CI'da korunur. Bkz. docs/oyun-testi.md (E aşaması).
 */
internal val ReyonColors = darkColorScheme(
    primary = Color(0xFF4DE1FF),
    onPrimary = Color(0xFF00293A),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1E1145),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE4EAF5),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE4EAF5),
    surfaceVariant = Color(0xFF1B2437),
    onSurfaceVariant = Color(0xFFAAB4C8),
    error = Color(0xFFF87171),
    onError = Color(0xFF3A0A0A),
)

@Composable
fun ReyonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReyonColors) {
        // Kökte Surface yok; Material yalnızca Surface içinde içerik rengi sağlar.
        // Rengi açıkça verilmeyen her Text aksi hâlde varsayılan siyahla çizilir.
        CompositionLocalProvider(LocalContentColor provides ReyonColors.onBackground, content = content)
    }
}
