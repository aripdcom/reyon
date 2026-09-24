package com.aripd.reyon.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aripd.reyon.R
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.ui.theme.Reyon
import com.aripd.reyon.ui.theme.ReyonFonts
import java.util.Locale

fun formatScore(value: Long): String = AppLocale.number(value)

// Süre ayırıcısız; rakam Latin kalsın diye ROOT (bkz. AppLocale.number).
fun formatTime(totalSeconds: Int): String =
    String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

/**
 * Ayarlar/Hakkında ekranını açan geri çağrı.
 *
 * Ayarların duracağı ayrı bir menü yok, simgesi üst çubuğa oturuyor.
 * CompositionLocal olarak veriliyor ki ekranların hiçbiri gezinmeyi bilmek
 * zorunda kalmasın — sağlanmazsa simge çizilmiyor, böylece testler ekranları
 * tek başına çizebiliyor.
 */
val LocalSettingsRoute = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Rakam, kod ve etiket yazısı: IBM Plex Mono, yarı kalın. */
fun monoStyle(size: Float, lineHeight: Float = size * 1.3f): TextStyle =
    TextStyle(fontFamily = ReyonFonts.Mono, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = lineHeight.sp)

/**
 * Ortak üst çubuk: geri oku, başlık ve altında bağlam satırı (format · raf ·
 * vaka türü), ayarlar simgesi, sağda ekrana özel aksiyon. [onExit] null ise
 * geri oku yerine [leading] çizilir (Görevler'de marka işareti).
 */
@Composable
fun GameTopBar(
    title: String,
    onExit: (() -> Unit)?,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    large: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (large) 64.dp else 56.dp)
            .padding(start = if (onExit == null) 16.dp else 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (onExit == null) 10.dp else 4.dp),
    ) {
        if (onExit != null) {
            IconButton(onClick = onExit) {
                Icon(imageVector = ReyonIcons.Back, contentDescription = stringResource(R.string.back))
            }
        } else {
            leading?.invoke()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
                letterSpacing = (-0.01).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LocalSettingsRoute.current?.let { open ->
            IconButton(onClick = open) {
                Icon(imageVector = ReyonIcons.Sliders, contentDescription = stringResource(R.string.about_title))
            }
        }
        trailing()
    }
}

/**
 * Tek satırlık yazı, sığmazsa punto küçülür ([fitSp]); kelimenin ortasından
 * bölünmez. KPI başlıkları için: büyük harfli başlıklar bazı dillerde uzun
 * ("ENCONTRADAS", "FORTJENESTE") ve dar kutuda ikiye bölünüyordu
 * (docs/cihaz-testi.md, v1.0.1 F3).
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minSp: Float = 8f,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val sp = remember(text, style, width, measurer) { fitSp(measurer, text, style, width, minSp) }
        Text(
            text = text,
            style = style.copy(fontSize = sp.sp),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

/** [text]'in [maxWidth] piksele tek satırda sığdığı en büyük punto; 0,5 sp adımlarla [minSp]'ye kadar. */
internal fun fitSp(measurer: TextMeasurer, text: String, style: TextStyle, maxWidth: Int, minSp: Float): Float {
    val top = style.fontSize.value
    if (maxWidth == Constraints.Infinity || maxWidth <= 0) return top
    var sp = top
    while (sp > minSp) {
        val w = measurer.measure(text, style.copy(fontSize = sp.sp), softWrap = false, maxLines = 1).size.width
        if (w <= maxWidth) return sp
        sp -= 0.5f
    }
    return minSp
}

/** KPI başlığının yazısı: küçük, büyük harfli, aralıklı. */
@Composable
fun kpiLabelStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.06.em,
    fontWeight = FontWeight.SemiBold,
)

/**
 * KPI kutusu: büyük harfli başlık, altında eş genişlikli değer ve isteğe bağlı
 * birim ("5" + "/7"). Değerin rengi yalnız anlamı olduğunda verilir (brif tamam).
 */
@Composable
fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    val tokens = Reyon.tokens
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tokens.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 7.dp)) {
            FitText(
                text = label.uppercase(appLocale()),
                style = kpiLabelStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minSp = 7.5f,
            )
            Row {
                Text(
                    text = value,
                    style = monoStyle(19f, 25f),
                    color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = monoStyle(12f, 16f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

/** Üç KPI kutusu yan yana; ekranın üst çubuğunun altında. */
@Composable
fun KpiRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Bölüm başlığı: solda ad, sağda kısa bilgi (sayım, ipucu). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = Color.Unspecified,
    trailingBold: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() },
        )
        if (trailing != null) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    fontWeight = if (trailingBold) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (trailingColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else trailingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Beyaz kart, ince kenarlıklı. */
@Composable
fun ReyonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Reyon.tokens.line),
        modifier = modifier,
    ) {
        Column(content = content)
    }
}

/**
 * Alt eylem çubuğu: beyaz zemin, üstte ince çizgi. Yan pay 12 dp, düğme aralığı
 * 8 dp: üç düğmeli satırın yazı alanı bu ölçülerle hesaplandı
 * (ReyonActionLabelTest).
 */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val tokens = Reyon.tokens
    Column(modifier = modifier.fillMaxWidth().background(tokens.actionBar)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Düğme türleri: ana eylem, ikincil (kenarlıklı) ve tonal (kılavuz gibi yardım). */
enum class ButtonKind { PRIMARY, SECONDARY, TONAL }

/** Ortak düğme: 48 dp, 10 dp köşe; etiket kelime ortasından bölünmez ([ActionLabel]). */
@Composable
fun ReyonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ActionRowPadding,
) {
    val tokens = Reyon.tokens
    val scheme = MaterialTheme.colorScheme
    val colors = when (kind) {
        ButtonKind.PRIMARY -> ButtonDefaults.buttonColors()
        ButtonKind.TONAL -> ButtonDefaults.buttonColors(containerColor = tokens.brandWeak, contentColor = tokens.onBrandWeak)
        ButtonKind.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
            disabledContainerColor = scheme.surface,
        )
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = colors,
        border = if (kind == ButtonKind.SECONDARY) BorderStroke(1.dp, if (enabled) tokens.buttonLine else tokens.line) else null,
        contentPadding = contentPadding,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        ActionLabel(text)
    }
}

/** Bandın tonu: kılavuz (marka zemini) ya da uyarı (yanlış yerleşimin geri alınması). */
enum class BannerTone { GUIDE, WARN }

/** Kılavuz bandı: simge, kalın başlık ve açıklama; oyun alanının üstünde bir satır. */
@Composable
fun GuideBanner(title: String, body: String, modifier: Modifier = Modifier, tone: BannerTone = BannerTone.GUIDE) {
    val tokens = Reyon.tokens
    val (bg, fg) = when (tone) {
        BannerTone.GUIDE -> tokens.brandWeak to tokens.onBrandWeak
        BannerTone.WARN -> tokens.warnWeak to tokens.onWarnWeak
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = ReyonIcons.Info, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                append(": ")
                append(body)
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
            color = fg,
        )
    }
}

/**
 * Parçalı seçici: seçenekler yan yana, seçili olan beyaz yüzeyde. Her parça
 * ekran okuyucuya radyo düğmesi olarak duyurulur.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    content: @Composable ColumnScope.(option: T, isSelected: Boolean) -> Unit,
) {
    val tokens = Reyon.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.segment, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            val isSel = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSel) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isSel) 1.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSel
                    },
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(min = minHeight)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    content(option, isSel)
                }
            }
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
 * Oyun alanının üstünde açılan kart: bekleme, gün dökümü, sonuç raporu. Kabın
 * yüksekliğine sığmazsa içi kayar: 360×640 dp'de kart ekrandan taşıyor, düğmeye
 * dokunulamıyordu (docs/cihaz-testi.md, Reyon Sipariş bulgu 1). Yükseklik
 * sınırsızsa (kaydırılabilir bir ebeveyn içinde) kaydırma eklenmez; aynı yönde
 * iç içe kaydırma izinli değil.
 */
@Composable
fun OverlayCard(scrollable: Boolean = true, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.padding(16.dp)) {
        val bounded = scrollable && maxHeight != Dp.Infinity
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, Reyon.tokens.line),
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .then(if (bounded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}
