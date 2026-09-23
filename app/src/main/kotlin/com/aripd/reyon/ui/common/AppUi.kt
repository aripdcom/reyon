package com.aripd.reyon.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aripd.reyon.R
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.platform.appLocale
import java.util.Locale

fun formatScore(value: Long): String = AppLocale.number(value)

// Süre ayırıcısız; rakam Latin kalsın diye ROOT (bkz. AppLocale.number).
fun formatTime(totalSeconds: Int): String =
    String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

/**
 * Ayarlar/Hakkında ekranını açan geri çağrı.
 *
 * Uygulama tek ekranlı: ayarların duracağı bir ana menü yok, dişli üst çubuğa
 * oturuyor. CompositionLocal olarak veriliyor ki dört mod ekranının hiçbiri
 * gezinmeyi bilmek zorunda kalmasın — sağlanmazsa dişli çizilmiyor, böylece
 * testler ekranları tek başına çizebiliyor.
 */
val LocalSettingsRoute = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Ortak üst çubuk: geri, başlık, ayarlar dişlisi, sağda ekrana özel aksiyon. */
@Composable
fun GameTopBar(
    title: String,
    onExit: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = title.uppercase(appLocale()),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            modifier = Modifier.weight(1f),
        )
        LocalSettingsRoute.current?.let { open ->
            IconButton(onClick = open) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.about_title),
                )
            }
        }
        trailing()
    }
}

/** Skor/rekor gibi değerler için kart. */
@Composable
fun ScoreCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label.uppercase(appLocale()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Yan yana üç eylem düğmesinin iç payı. Material'ın 24 dp'lik yan payıyla 360 dp'de
 * yazıya ~59 dp kalıyor ve "Tamamla", "Rückgängig", "Подсказка" gibi tek kelimeler
 * ortasından bölünüyordu (docs/cihaz-testi.md, v1.0.1 F1); 8 dp ile ~91 dp kalır.
 */
val ActionRowPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

/**
 * Eylem düğmesinin etiketi: kutunun içinde kalır, kelimenin ortasından bölünmez.
 *
 * Uzun çeviriler yan yana üç düğmeye sığmıyor. İki satıra izin verilir, ama satır
 * yalnız boşlukta kırılmalı: punto, her kelime tek satıra sığana dek
 * [ACTION_LABEL_MIN_SP]'ye kadar küçülür ([actionLabelSp]). Tabanda da sığmazsa
 * üç nokta konur; satır aralığı dar tutulur ki düğme fazla uzamasın.
 */
@Composable
fun ActionLabel(text: String) {
    val base = MaterialTheme.typography.labelLarge
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val sp = remember(text, width, base, measurer) { actionLabelSp(measurer, text, base, width) }
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = actionLabelStyle(base, sp),
        )
    }
}

const val ACTION_LABEL_MIN_SP = 10f
private const val ACTION_LABEL_LINE_HEIGHT = 16f / 14f

internal fun actionLabelStyle(base: TextStyle, sp: Float): TextStyle =
    base.copy(fontSize = sp.sp, lineHeight = (sp * ACTION_LABEL_LINE_HEIGHT).sp)

/**
 * [text]'in [maxWidth] piksele kelime bölünmeden, en çok iki satırda sığdığı en büyük
 * punto (sp): [base]'in puntosundan 0,5 sp adımlarla [ACTION_LABEL_MIN_SP]'ye kadar.
 * Genişlik sınırsızsa ya da tabanda da sığmıyorsa tabanı (ya da temel puntoyu) döndürür.
 */
internal fun actionLabelSp(measurer: TextMeasurer, text: String, base: TextStyle, maxWidth: Int): Float {
    val top = base.fontSize.value
    if (maxWidth == Constraints.Infinity || maxWidth <= 0) return top
    val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    var sp = top
    while (sp > ACTION_LABEL_MIN_SP) {
        if (actionLabelFits(measurer, text, words, actionLabelStyle(base, sp), maxWidth)) return sp
        sp -= 0.5f
    }
    return ACTION_LABEL_MIN_SP
}

private fun actionLabelFits(measurer: TextMeasurer, text: String, words: List<String>, style: TextStyle, maxWidth: Int): Boolean {
    val wordsFit = words.all { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width <= maxWidth }
    if (!wordsFit) return false
    val whole = measurer.measure(text, style, maxLines = 2, constraints = Constraints(maxWidth = maxWidth))
    return !whole.hasVisualOverflow
}

/**
 * Menü, duraklatma ve bitiş kartı. Kabın yüksekliğine sığmazsa içi kayar: 360×640
 * dp'de Reyon menüsü ekrandan taşıyor, "Başla" düğmesine dokunulamıyordu
 * (docs/cihaz-testi.md, Reyon Sipariş bulgu 1). Yükseklik sınırsızsa (kaydırılabilir
 * bir ebeveyn içinde) kaydırma eklenmez; aynı yönde iç içe kaydırma izinli değil.
 * İçeriği kendi kaydıran kartlar (tema listesi, dükkân) [scrollable] = false verir.
 */
@Composable
fun OverlayCard(scrollable: Boolean = true, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.padding(16.dp)) {
        val bounded = scrollable && maxHeight != Dp.Infinity
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            // Saydam yüzey rengi şemadaki hiçbir renkle eşleşmediğinden içerik rengi
            // kendiliğinden türetilemez; başlıklar için açıkça verilir.
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .then(if (bounded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}
