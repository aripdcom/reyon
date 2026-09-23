package com.aripd.reyon.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aripd.reyon.R
import com.aripd.reyon.engine.OrderRules
import com.aripd.reyon.engine.ReyonAuditGenerator
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.platform.appString
import com.aripd.reyon.ui.common.ButtonKind
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ReyonButton
import com.aripd.reyon.ui.common.ReyonCard
import com.aripd.reyon.ui.common.ReyonIcons
import com.aripd.reyon.ui.common.SectionHeader
import com.aripd.reyon.ui.common.Segmented
import com.aripd.reyon.ui.common.formatTime
import com.aripd.reyon.ui.common.monoStyle
import com.aripd.reyon.ui.theme.Reyon
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Testlerin Görevler ekranını ve satırlarını bulması için. */
const val HOME_TAG = "reyon_home"
fun homeDailyTag(kind: ReyonKind) = "home_daily_" + kind.name
fun homePracticeTag(kind: ReyonKind) = "home_practice_" + kind.name

/**
 * Görevler ekranında bir türün durumu: günün vakası bitti mi ve sonucu, alıştırmada
 * seçili formatın son sonucu. Görevler her açılışta kayıtlardan yeniden okunur.
 */
internal class HomeRow(val kind: ReyonKind, val daily: String?, val practice: String?)

/** Kayıtlardan Görevler'in satırları: günün vakası [dailyLevel] formatında, alıştırma [practice] formatında. */
internal fun homeRows(store: ReyonStore, today: Long, practice: ReyonLevel): List<HomeRow> {
    val daily = dailyLevel(today)
    fun ratio(a: Int, b: Int) = "$a/$b"
    return ReyonKind.entries.map { kind ->
        val dailyResult = when (kind) {
            ReyonKind.PUZZLE -> store.dailyRecord(today, daily)?.let { formatTime(it.time) }
            ReyonKind.AUDIT -> store.auditDailyRecord(today, daily)?.let { formatTime(it.time) }
            ReyonKind.SALES -> store.salesDailyRecord(today, daily)?.let { ratio(it.score, it.target) }
            ReyonKind.ORDER -> store.orderDailyRecord(today, daily)?.let { ratio(it.score, it.target) }
        }
        val last = store.lastPractice(kind, practice)?.let { p ->
            when (kind) {
                ReyonKind.PUZZLE, ReyonKind.AUDIT -> formatTime(p.value)
                ReyonKind.SALES, ReyonKind.ORDER -> ratio(p.value, p.other)
            }
        }
        HomeRow(kind, dailyResult, last)
    }
}

@StringRes
internal fun kindLabel(kind: ReyonKind): Int = when (kind) {
    ReyonKind.PUZZLE -> R.string.reyon_kind_puzzle
    ReyonKind.AUDIT -> R.string.reyon_kind_audit
    ReyonKind.SALES -> R.string.reyon_kind_sales
    ReyonKind.ORDER -> R.string.reyon_kind_order
}

@StringRes
private fun kindDesc(kind: ReyonKind): Int = when (kind) {
    ReyonKind.PUZZLE -> R.string.home_puzzle_desc
    ReyonKind.AUDIT -> R.string.home_audit_desc
    ReyonKind.SALES -> R.string.home_sales_desc
    ReyonKind.ORDER -> R.string.home_order_desc
}

private fun kindIcon(kind: ReyonKind): ImageVector = when (kind) {
    ReyonKind.PUZZLE -> ReyonIcons.Planogram
    ReyonKind.AUDIT -> ReyonIcons.Audit
    ReyonKind.SALES -> ReyonIcons.Sales
    ReyonKind.ORDER -> ReyonIcons.Order
}

/** Mağaza formatının adı: Market, Süpermarket, Hipermarket. */
@StringRes
internal fun formatName(level: ReyonLevel): Int = when (level) {
    ReyonLevel.KOLAY -> R.string.format_market
    ReyonLevel.ORTA -> R.string.format_supermarket
    ReyonLevel.ZOR -> R.string.format_hypermarket
}

/**
 * Görevler: uygulamanın giriş ekranı.
 *
 * Üstte günün vakaları (dört mod, herkes aynı rafla, format günden türer),
 * altında alıştırma: mağaza formatı seçilir, dört moddan biri açılır. Her satır
 * son sonucu gösterir. Ayarlar üst çubuktaki simgeden.
 */
@Composable
internal fun ReyonHome(
    practiceLevel: ReyonLevel,
    onPracticeLevel: (ReyonLevel) -> Unit,
    onDaily: (ReyonKind) -> Unit,
    onPractice: (ReyonKind) -> Unit,
    onExit: () -> Unit,
    refresh: Int = 0,
) {
    val context = LocalContext.current
    val store = remember { ReyonStore(context) }
    val today = remember(refresh) { LocalDate.now().toEpochDay() }
    val rows = remember(refresh, practiceLevel, today) { homeRows(store, today, practiceLevel) }
    BackHandler { onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag(HOME_TAG),
    ) {
        GameTopBar(
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.home_subtitle),
            onExit = null,
            large = true,
            leading = { BrandMark() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            DailyCard(today = today, rows = rows, onDaily = onDaily)

            SectionHeader(
                title = stringResource(R.string.home_format_title),
                trailing = stringResource(R.string.home_format_hint),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
            )
            Segmented(
                options = ReyonLevel.entries,
                selected = practiceLevel,
                onSelect = onPracticeLevel,
                minHeight = 48.dp,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { level, selected ->
                Text(
                    text = stringResource(formatName(level)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = appString(R.string.format_shelf_fmt, level.rows, level.cols),
                    style = monoStyle(11f, 14f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            SectionHeader(
                title = stringResource(R.string.home_practice_title),
                trailing = stringResource(R.string.home_last_result),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
            )
            ReyonCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                rows.forEachIndexed { i, row ->
                    PracticeRow(row = row, last = i == rows.lastIndex, onClick = { onPractice(row.kind) })
                }
            }
        }
    }
}

/** Marka işareti: raf izleği, bir ambalaj dikkat sarısında. Tasarım taslağındaki simgenin aynısı. */
@Composable
private fun BrandMark() {
    val attention = Reyon.tokens.attention
    val brand = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(brand, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val u = size.width / 36f
            for (y in listOf(12f, 22f, 32f)) {
                drawLine(ink, Offset(4f * u, y * u), Offset(32f * u, y * u), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
            }
            val packs = listOf(
                floatArrayOf(6f, 4f, 7f, 7f), floatArrayOf(15f, 6f, 6f, 5f), floatArrayOf(23f, 3f, 7f, 8f),
                floatArrayOf(6f, 15f, 9f, 6f), floatArrayOf(24f, 14f, 6f, 7f),
                floatArrayOf(6f, 25f, 6f, 6f), floatArrayOf(14f, 24f, 7f, 7f), floatArrayOf(23f, 26f, 7f, 5f),
            )
            packs.forEachIndexed { i, (x, y, w, h) ->
                drawRoundRect(
                    if (i == 2) attention else ink,
                    topLeft = Offset(x * u, y * u),
                    size = Size(w * u, h * u),
                    cornerRadius = CornerRadius(1.5f * u, 1.5f * u),
                )
            }
        }
    }
}

/**
 * Günün vakaları kartı: marka renginde, dört modun düğmesi. Biten vaka onay
 * işareti ve sonucuyla, bekleyen "Başla" ile. Başlıkta tarih ve ilerleme (1/4).
 */
@Composable
private fun DailyCard(today: Long, rows: List<HomeRow>, onDaily: (ReyonKind) -> Unit) {
    val dark = Reyon.tokens.isDark
    val scheme = MaterialTheme.colorScheme
    val bg = if (dark) Reyon.tokens.brandWeak else scheme.primary
    val ink = if (dark) scheme.onSurface else scheme.onPrimary
    val muted = if (dark) Reyon.tokens.onBrandWeak else Color(0xFFBFD9DE)
    val locale = appLocale()
    val date = remember(today, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMM")
        LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val done = rows.count { it.daily != null }
    val level = dailyLevel(today)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = ink,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_daily_title).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = appString(R.string.home_daily_progress_fmt, date, done, rows.size),
                    style = monoStyle(12f, 16f),
                    color = muted,
                    maxLines = 1,
                )
            }
            Text(
                text = stringResource(R.string.home_daily_desc),
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
            )
            Text(
                text = appString(R.string.home_daily_format_fmt, stringResource(formatName(level)), level.rows, level.cols),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = muted,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp),
            )
            for (pair in rows.chunked(2)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in pair) {
                        DailyButton(row = row, ink = ink, modifier = Modifier.weight(1f), onClick = { onDaily(row.kind) })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DailyButton(row: HomeRow, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    val name = stringResource(kindLabel(row.kind))
    val result = row.daily
    val start = stringResource(R.string.home_start)
    val scheme = MaterialTheme.colorScheme
    // Biten vaka kartın zemininde saydam, bekleyen yüzey renginde: göz bekleyene gider.
    val bg = if (result != null) ink.copy(alpha = 0.12f) else scheme.surface
    val fg = if (result != null) ink else scheme.primary
    val desc = if (result != null) appString(R.string.home_daily_done_desc_fmt, name, result) else "$name · $start"
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .testTag(homeDailyTag(row.kind))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (result != null) {
            Icon(imageVector = ReyonIcons.Check, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(6.dp))
        }
        NameAndResult(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result ?: start,
                style = if (result != null) monoStyle(12.5f, 16f) else MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = fg,
                maxLines = 1,
            )
        }
    }
}

/**
 * Günün vakası düğmesinin adı ve sonucu: sığıyorsa tek satırda (ad solda, sonuç sağda),
 * sığmıyorsa sonuç ikinci satıra iner ve ad bütün genişliği alır. Tek satıra zorlanınca
 * 360 dp'de biten vakanın adı sonucun yanında kırpılıyordu: "Sipa… 402/558", "Раскла… 0:47"
 * (docs/cihaz-testi.md, 1.1.0, G1).
 */
@Composable
private fun NameAndResult(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val result = measurables[1].measure(loose)
        val gap = DAILY_NAME_GAP.roundToPx()
        val nameNatural = measurables[0].maxIntrinsicWidth(constraints.maxHeight.coerceAtLeast(0))
        val oneLine = nameNatural + gap + result.width <= constraints.maxWidth
        val name = measurables[0].measure(
            if (oneLine) loose.copy(maxWidth = constraints.maxWidth - gap - result.width) else loose,
        )
        val width = constraints.maxWidth
        if (oneLine) {
            val height = maxOf(name.height, result.height)
            layout(width, height) {
                name.placeRelative(0, (height - name.height) / 2)
                result.placeRelative(width - result.width, (height - result.height) / 2)
            }
        } else {
            layout(width, name.height + result.height) {
                name.placeRelative(0, 0)
                result.placeRelative(width - result.width, name.height)
            }
        }
    }
}

private val DAILY_NAME_GAP = 8.dp

@Composable
private fun PracticeRow(row: HomeRow, last: Boolean, onClick: () -> Unit) {
    val tokens = Reyon.tokens
    val name = stringResource(kindLabel(row.kind))
    val desc = stringResource(kindDesc(row.kind))
    val result = row.practice
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(homePracticeTag(row.kind)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tokens.brandWeak, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = kindIcon(row.kind), contentDescription = null, tint = tokens.onBrandWeak, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result != null) {
                Text(text = result, style = monoStyle(13f, 18f))
            }
            Icon(
                imageVector = ReyonIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (!last) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
    }
}

/** Mod ekranının alt başlığı: "Market · 3×4 raf · Alıştırma". */
@Composable
internal fun caseSubtitle(level: ReyonLevel, daily: Boolean): String = appString(
    R.string.case_subtitle_fmt,
    stringResource(formatName(level)),
    level.rows,
    level.cols,
    stringResource(if (daily) R.string.mode_daily else R.string.mode_free),
)

/** Modun nasıl oynandığını anlatan metin. */
@StringRes
private fun introText(kind: ReyonKind): Int = when (kind) {
    ReyonKind.PUZZLE -> R.string.reyon_intro
    ReyonKind.AUDIT -> R.string.reyon_audit_intro
    ReyonKind.SALES -> R.string.reyon_sales_intro
    ReyonKind.ORDER -> R.string.reyon_order_intro
}

/**
 * "Nasıl oynanır" kartının açık olup olmadığı. İlk girişte bir kez kendiliğinden
 * açılır; sonra üst çubuktaki bilgi simgesiyle.
 */
@Composable
internal fun rememberHowTo(kind: ReyonKind): MutableState<Boolean> {
    val context = LocalContext.current
    return remember(kind) { mutableStateOf(!ReyonStore(context).introSeen(kind)) }
}

/** Üst çubuğun bilgi simgesi: nasıl oynanır. */
@Composable
internal fun HowToButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = ReyonIcons.Info, contentDescription = stringResource(R.string.howto_title))
    }
}

/** Modun kuralları ve seçili formatın ölçüsü; kapatınca bir daha kendiliğinden açılmaz. */
@Composable
internal fun HowToCard(kind: ReyonKind, level: ReyonLevel, onClose: () -> Unit) {
    val context = LocalContext.current
    val size = when (kind) {
        ReyonKind.PUZZLE, ReyonKind.SALES ->
            appString(R.string.reyon_level_desc_fmt, level.rows, level.cols, level.products.first, level.products.last)
        ReyonKind.AUDIT ->
            appString(R.string.reyon_audit_level_desc_fmt, level.rows, level.cols, ReyonAuditGenerator.count(level))
        ReyonKind.ORDER ->
            appString(R.string.reyon_order_level_desc_fmt, level.rows, level.cols, OrderRules.days(level), OrderRules.promoCount(level))
    }
    OverlayCard {
        Text(text = stringResource(kindLabel(kind)), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(introText(kind)),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = appString(R.string.howto_format_fmt, stringResource(formatName(level)), size),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReyonButton(
            text = stringResource(R.string.howto_ok),
            onClick = {
                ReyonStore(context).markIntroSeen(kind)
                onClose()
            },
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
