package com.aripd.reyon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aripd.reyon.R
import com.aripd.reyon.engine.DaySummary
import com.aripd.reyon.engine.OrderItem
import com.aripd.reyon.engine.OrderRules
import com.aripd.reyon.engine.ReyonOrderState
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.platform.Sfx
import com.aripd.reyon.platform.ShareContent
import com.aripd.reyon.platform.appString
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.platform.appText
import com.aripd.reyon.ui.common.ActionBar
import com.aripd.reyon.ui.common.ButtonKind
import com.aripd.reyon.ui.common.FitText
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.KpiRow
import com.aripd.reyon.ui.common.KpiTile
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ReyonButton
import com.aripd.reyon.ui.common.ReyonCard
import com.aripd.reyon.ui.common.ReyonIcons
import com.aripd.reyon.ui.common.SectionHeader
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.kpiLabelStyle
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.ui.common.monoStyle
import com.aripd.reyon.ui.theme.Reyon
import kotlin.math.abs

/** Sipariş modu: raf (stok düzeyleri), gün başlığı, ürün başına sipariş adımlayıcısı, gün kapanışı. */
@Composable
internal fun ReyonOrderContent(
    onCompleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReyonOrderViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val hinted by viewModel.hinted.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val sound = LocalSound.current

    // Aynı hafta ikinci kez sayılmasın (bkz. ReyonPuzzleContent).
    var countedSeed by remember {
        mutableLongStateOf(state?.order?.seed?.takeIf { result != null } ?: Long.MIN_VALUE)
    }
    val latestCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(result) {
        val seed = state?.order?.seed ?: return@LaunchedEffect
        if (result != null && seed != countedSeed) {
            countedSeed = seed
            latestCompleted()
            sound?.play(Sfx.BIG)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose { viewModel.persistNow() }
    }
    BackHandler { onBack() }

    val st = state
    var howTo by rememberHowTo(ReyonKind.ORDER)
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    // Kılavuz sonucu: son öneri hangi ürüne dokundu ya da öneri kalmadı mı; her değişimde silinir.
    var noHint by remember { mutableStateOf(false) }
    LaunchedEffect(version) { if (hinted < 0) noHint = false }
    val shownLevel = st?.order?.level ?: level
    val daily = mode == ReyonMode.DAILY

    // Hafta bitti ve gün dökümü kapandı: tam sayfa haftalık rapor.
    val done = result
    if (st != null && done != null && summary == null) {
        WeekReport(state = st, result = done, onRetry = viewModel::retry, onBack = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(
            title = stringResource(R.string.reyon_kind_order),
            subtitle = caseSubtitle(shownLevel, daily),
            onExit = onBack,
        ) {
            HowToButton { howTo = true }
        }
        KpiRow {
            KpiTile(
                label = stringResource(R.string.reyon_order_day_label),
                value = st?.let { "${minOf(it.day + 1, it.order.days)}" } ?: "—",
                unit = st?.let { "/${it.order.days}" },
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(R.string.reyon_order_profit_label),
                value = st?.profit?.toString() ?: "—",
                valueColor = if ((st?.profit ?: 0) < 0) Reyon.tokens.bad else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(R.string.reyon_order_target_label),
                value = st?.order?.target?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (st != null) {
                val shelfH = shelfHeight(maxWidth, maxHeight, st.order.cols / (st.order.rows * 0.62f))
                Column(modifier = Modifier.fillMaxSize()) {
                    // Raf yalnız stoğa bağlı; sipariş vermek stoğu değiştirmez (gün kapanınca
                    // değişir). version'a bağlıyken her adımlayıcı dokunuşunda bütün raf yeniden
                    // çiziliyordu (docs/cihaz-testi.md, 1.1.0 B).
                    val stocks = List(st.order.items.size) { st.stockOf(it) }
                    OrderShelfCanvas(state = st, stocks = stocks, day = st.day, profit = st.profit, height = shelfH)
                    DayHeader(state = st, version = version, hinted = hinted, noHint = noHint)
                    OrderList(
                        state = st,
                        version = version,
                        hinted = hinted,
                        onAdjust = { i, d ->
                            if (viewModel.adjust(i, d)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sound?.play(Sfx.POP, volume = 0.4f, rate = if (d > 0) 1.2f else 0.9f)
                            }
                        },
                        modifier = Modifier.weight(1f, fill = false).testTag(REYON_PANEL_TAG),
                    )
                    Text(
                        text = stringResource(R.string.reyon_order_rules),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                    )
                }
            }
            when {
                st == null -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_order_generating))
                }
                summary != null -> DaySummaryCard(state = st, summary = summary!!, onContinue = viewModel::dismissSummary)
                howTo -> HowToCard(ReyonKind.ORDER, shownLevel) { howTo = false }
            }
        }

        if (st != null && result == null && summary == null) {
            ActionBar {
                ReyonButton(
                    text = stringResource(R.string.reyon_hint),
                    onClick = {
                        if (viewModel.hint() != null) {
                            noHint = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sound?.play(Sfx.CLEAR, volume = 0.6f)
                        } else {
                            noHint = true
                        }
                    },
                    kind = ButtonKind.TONAL,
                    modifier = Modifier.weight(1f),
                )
                ReyonButton(
                    text = stringResource(if (st.day + 1 >= st.order.days) R.string.reyon_order_close_week else R.string.reyon_order_close_day),
                    onClick = {
                        if (viewModel.closeDay() != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sound?.play(Sfx.DROP, volume = 0.6f)
                        }
                    },
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Plan rafı; her ambalaj stok doluluğu kadar dolu (kalanı soluk), raf etiketinde
 * stok/kapasite. Stok tükenmişse değer kırmızı.
 */
@Composable
private fun OrderShelfCanvas(state: ReyonOrderState, stocks: List<Int>, day: Int, profit: Int, height: Dp) {
    val order = state.order
    val res = LocalContext.current.resources
    val colors = rememberShelfColors()
    val texts = rememberShelfTexts()
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer, colors.packFamily) { BlockLabeler(textMeasurer, colors.packFamily) }
    val names = remember(order, res) { order.items.map { ReyonText.kind(res, it.product.kind) } }
    val desc = appString(R.string.reyon_order_board_desc_fmt, order.rows, order.cols, minOf(day + 1, order.days), profit)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc },
    ) {
        val g = ShelfGeom(size.width, size.height, order.rows, order.cols, density)
        drawGondola(g, colors)
        for ((i, item) in order.items.withIndex()) {
            val stock = stocks[i]
            val p = item.product
            drawPack(g, labeler, colors, names[i], texts.mark(p), p, p.facings, item.row, item.col, stock = stock.toFloat() / item.capacity)
            drawShelfLabel(
                g, labeler, colors, p, p.facings, item.row, item.col, texts.code(p), texts.badges(p),
                value = "$stock/${item.capacity}",
                valueColor = if (stock == 0) colors.tokens.labelBad else null,
                showDemand = false,
            )
        }
    }
}

/**
 * Güçlü atlama (strong skipping) altında değişken motor durumu aynı nesne kaldığı için
 * satırlar atlanır; [version] her değişimde artar ve yeniden çizimi zorlar.
 */
@Composable
private fun DayHeader(state: ReyonOrderState, version: Int, hinted: Int, noHint: Boolean) {
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val res = LocalContext.current.resources
    val order = state.order
    val day = minOf(state.day, order.days - 1)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = appString(R.string.reyon_order_day_title_fmt, ReyonText.dow(res, day), day + 1, order.days),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val ahead = order.promos.filter { it.day >= state.day }.sortedBy { it.day }
            if (ahead.isNotEmpty()) {
                Text(
                    text = ahead.joinToString(" · ") { appText(res, R.string.reyon_order_promo_ahead_fmt, ReyonText.dow(res, it.day), ReyonText.kind(res, order.items[it.item].product.kind)) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.End,
                )
            }
        }
        val hintText = when {
            hinted >= 0 -> appString(R.string.reyon_order_hint_fmt, ReyonText.kind(res, order.items[hinted].product.kind), state.orderOf(hinted))
            noHint -> stringResource(R.string.reyon_order_hint_none)
            else -> null
        }
        if (hintText != null) {
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelMedium,
                color = Reyon.tokens.onBrandWeak,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(Reyon.tokens.brandWeak, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun OrderList(
    state: ReyonOrderState,
    version: Int,
    hinted: Int,
    onAdjust: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((i, item) in state.order.items.withIndex()) {
            OrderRow(data = orderRowData(state, i), index = i, item = item, hinted = i == hinted, onAdjust = onAdjust)
        }
    }
}

/**
 * Bir sipariş satırının gösterdiği her şey. Satır bunu alır, motor durumunu değil: Compose
 * değeri değişmeyen satırları atlar. `version`'la bütün satırlar her adımlayıcı dokunuşunda
 * yeniden oluşuyordu; kare başına 35–45 ms yeniden oluşturma (docs/cihaz-testi.md, 1.1.0 B).
 * `@Immutable`: alanlar değişmez, ama `List` alanı yüzünden derleyici sınıfı kararsız sayıyor
 * ve güçlü atlamada kimlikle (===) karşılaştırıyordu; her seferinde yeni nesne, satır yine
 * yeniden oluşuyordu. İşaretle içerikle (equals) karşılaştırılır.
 */
@Immutable
private data class OrderRowData(
    val day: Int,
    val stock: Int,
    val cases: Int,
    val canOrder: Boolean,
    val arrival: Int,
    val incoming: List<Pair<Int, Int>>,
    val expiringTonight: Int,
    val expiringTomorrow: Int,
    val promoNow: Boolean,
    val days: Int,
)

private fun orderRowData(state: ReyonOrderState, index: Int): OrderRowData {
    val order = state.order
    val item = order.items[index]
    val day = minOf(state.day, order.days - 1)
    val incoming = (state.day + 1 until minOf(order.days, state.day + OrderRules.MAX_LEAD + 1))
        .map { it to state.incoming(index, it) }
        .filter { it.second > 0 }
    val tonight = if (item.perishable) state.expiring(index, day) else 0
    val tomorrow = if (item.perishable) state.expiring(index, day + 1) - tonight else 0
    return OrderRowData(
        day = day,
        stock = state.stockOf(index),
        cases = state.orderOf(index),
        canOrder = state.canOrder(index),
        arrival = state.arrivalDay(index),
        incoming = incoming,
        expiringTonight = tonight,
        expiringTomorrow = tomorrow,
        promoNow = order.isPromo(day, index),
        days = order.days,
    )
}

@Composable
private fun OrderRow(data: OrderRowData, index: Int, item: OrderItem, hinted: Boolean, onAdjust: (Int, Int) -> Unit) {
    val res = LocalContext.current.resources
    val tokens = Reyon.tokens
    val name = ReyonText.kind(res, item.product.kind)
    val day = data.day
    val stock = data.stock
    val cases = data.cases
    val canOrder = data.canOrder
    val arrival = data.arrival
    val desc = appString(R.string.reyon_order_item_desc_fmt, name, stock, item.capacity, cases)
    val more = stringResource(R.string.reyon_order_more)
    val less = stringResource(R.string.reyon_order_less)

    // Bilgi satırı: bugünkü ve teslimat günü tahmini, gelen teslimat, bozulacak birimler.
    val info = ArrayList<String>()
    info += appString(R.string.reyon_order_today_fmt, item.low(day), item.high(day))
    if (arrival < data.days && arrival != day) info += appString(R.string.reyon_order_forecast_fmt, ReyonText.dow(res, arrival), item.low(arrival), item.high(arrival))
    for ((d, units) in data.incoming) info += appString(R.string.reyon_order_incoming_fmt, ReyonText.dow(res, d), units)
    if (data.expiringTonight > 0) info += appString(R.string.reyon_order_expiring_fmt, data.expiringTonight)
    if (data.expiringTomorrow > 0) info += appString(R.string.reyon_order_expiring_tomorrow_fmt, data.expiringTomorrow)
    val promoNow = data.promoNow

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (hinted) 2.dp else 1.dp, if (hinted) MaterialTheme.colorScheme.primary else tokens.line),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
    ) {
        // Alt pay 10 dp: adımlayıcının 48 dp dokunma alanı kartın kırpma sınırında kesilmesin
        // (cihazda dikey bant 44 dp ölçülmüştü; docs/cihaz-testi.md, Reyon Sipariş C).
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(9.dp + 3.dp * item.product.size)
                        .background(tokens.pack(item.product.brand), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp)),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = appString(R.string.facings_fmt, item.product.facings),
                    style = monoStyle(11.5f, 15f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.product.premium) Icon(ReyonIcons.Eye, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                if (item.product.heavy) Icon(ReyonIcons.Heavy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.weight(1f))
                if (promoNow) {
                    Text(
                        text = stringResource(R.string.reyon_order_promo_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.onAttention,
                        modifier = Modifier
                            .background(tokens.attention, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = appString(R.string.reyon_order_stock_fmt, stock, item.capacity),
                    style = monoStyle(12.5f, 16f),
                    color = if (stock == 0) tokens.bad else MaterialTheme.colorScheme.onSurface,
                )
            }
            StockBar(fraction = stock.toFloat() / item.capacity)
            Text(
                text = info.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (canOrder) {
                    StepButton(label = "−", desc = "$less: $name", enabled = cases > 0) { onAdjust(index, -1) }
                    Text(
                        text = if (cases > 0) appString(R.string.reyon_order_case_fmt, cases, cases * item.caseSize) else stringResource(R.string.reyon_order_no_order),
                        style = if (cases > 0) monoStyle(12.5f, 16f) else MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = if (cases > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    StepButton(label = "+", desc = "$more: $name", enabled = cases < item.maxCases) { onAdjust(index, 1) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = appString(R.string.reyon_order_case_size_fmt, item.caseSize, ReyonText.dow(res, arrival)),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.reyon_order_closed),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (enabled) Reyon.tokens.buttonLine else Reyon.tokens.line),
        modifier = Modifier
            .size(width = 44.dp, height = 32.dp)
            .semantics { contentDescription = desc },
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StockBar(fraction: Float) {
    val tokens = Reyon.tokens
    val fill = when {
        fraction <= 0f -> tokens.bad
        fraction < 0.35f -> tokens.warn
        else -> tokens.ok
    }
    ProgressBar(fraction = fraction, color = fill, height = 5.dp, modifier = Modifier.padding(top = 5.dp))
}

@Composable
private fun DaySummaryCard(state: ReyonOrderState, summary: DaySummary, onContinue: () -> Unit) {
    val res = LocalContext.current.resources
    val tokens = Reyon.tokens
    OverlayCard {
        Text(
            text = appString(R.string.reyon_order_summary_title_fmt, ReyonText.dow(res, summary.day)),
            style = MaterialTheme.typography.titleLarge,
        )
        val lines = ArrayList<Pair<String, Color?>>()
        lines += appString(R.string.reyon_order_sum_sold_fmt, summary.soldUnits, summary.margin) to tokens.ok
        if (summary.lostUnits > 0) lines += appString(R.string.reyon_order_sum_lost_fmt, summary.lostUnits) to tokens.bad
        lines += appString(R.string.reyon_order_sum_holding_fmt, summary.holding) to null
        if (summary.wastedUnits > 0) lines += appString(R.string.reyon_order_sum_waste_fmt, summary.wastedUnits, summary.waste) to tokens.bad
        if (summary.returnedUnits > 0) lines += appString(R.string.reyon_order_sum_returns_fmt, summary.returnedUnits, summary.returns) to tokens.bad
        if (summary.deliveredUnits > 0) lines += appString(R.string.reyon_order_sum_delivered_fmt, summary.deliveredUnits) to null
        for ((text, color) in lines) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = appString(R.string.reyon_order_sum_profit_fmt, summary.profit, state.profit),
            style = monoStyle(16f, 22f),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        ReyonButton(
            text = stringResource(R.string.reyon_order_continue),
            onClick = onContinue,
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun turnoverText(x: Float): String = AppLocale.decimal(x)

/**
 * Haftalık rapor: tam sayfa. Kâr uzmana oranla (uyum bandı), hizmet düzeyi ve
 * stok devri uzmanla yan yana, günlük kâr ve devir grafiği, bulgular.
 */
@Composable
private fun WeekReport(state: ReyonOrderState, result: OrderResult, onRetry: () -> Unit, onBack: () -> Unit) {
    val res = LocalContext.current.resources
    val tokens = Reyon.tokens
    val levelName = ReyonText.level(res, state.order.level)
    val details = appString(R.string.reyon_order_result_fmt, result.score, result.target, result.percent)
    val band = ComplianceBand.of(result.percent)
    val findings = remember(result) { orderFindings(result.service, result.targetService, result.turnover, result.targetTurnover) }
    val share = ShareContent(
        headline = stringResource(R.string.reyon_order_done_title) + " · " + stringResource(band.label),
        details = listOf(levelName, details, appString(R.string.reyon_order_share_fmt, turnoverText(result.turnover)), modeShareLabel(result.daily, result.day)),
        board = orderPainter(state, res),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(
            title = stringResource(R.string.reyon_order_done_title),
            subtitle = appString(R.string.report_order_subtitle_fmt, stringResource(R.string.reyon_kind_order), levelName, state.order.days),
            onExit = onBack,
        ) {
            ShareButton(share, compact = true)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReyonCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    ExpertMetric(
                        label = stringResource(R.string.report_week_profit),
                        value = result.score,
                        expert = result.target,
                        percent = result.percent,
                    )
                    if (result.score > result.target) {
                        Text(
                            text = stringResource(R.string.reyon_order_beat_target),
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.ok,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else if (result.record) {
                        PersonalBestPill(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportStat(
                    label = stringResource(R.string.report_service_level),
                    value = appString(R.string.percent_fmt, result.service),
                    expert = appString(R.string.report_expert_value_fmt, appString(R.string.percent_fmt, result.targetService)),
                    delta = result.service - result.targetService,
                    deltaText = appString(R.string.report_points_fmt, signedMinus(result.service - result.targetService)),
                    modifier = Modifier.weight(1f),
                )
                ReportStat(
                    label = stringResource(R.string.report_turnover),
                    value = turnoverText(result.turnover),
                    expert = appString(R.string.report_expert_value_fmt, turnoverText(result.targetTurnover)),
                    delta = if (result.turnover >= result.targetTurnover - 0.05f) 0 else -1,
                    deltaText = signedDecimal(result.turnover - result.targetTurnover),
                    modifier = Modifier.weight(1f),
                )
            }
            ReyonCard {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
                    SectionHeader(title = stringResource(R.string.report_chart_title))
                    WeekChart(state = state, result = result)
                }
            }
            ReyonCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.report_findings),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
                    )
                    for (f in findings) FindingRow(f, appText(res, f.text, *f.args))
                }
            }
        }
        ActionBar {
            ReyonButton(text = stringResource(R.string.nav_tasks), onClick = onBack, modifier = Modifier.weight(1f))
            ReyonButton(
                text = stringResource(if (result.daily) R.string.report_try_again else R.string.report_new_week),
                onClick = onRetry,
                kind = ButtonKind.PRIMARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Eksi işaretli fark: "−23" (tipografik eksi), artıda "+4". */
private fun signedMinus(v: Int): String = if (v < 0) "−${-v}" else "+$v"

private fun signedDecimal(v: Float): String {
    val magnitude = AppLocale.decimal(abs(v))
    return if (v < -0.05f) "−$magnitude" else if (v > 0.05f) "+$magnitude" else magnitude
}

/** Raporun küçük ölçüsü: başlık, değer, altında uzmanın değeri ve fark. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportStat(label: String, value: String, expert: String, delta: Int, deltaText: String, modifier: Modifier = Modifier) {
    val tokens = Reyon.tokens
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tokens.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
            FitText(text = label.uppercase(appLocale()), style = kpiLabelStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = monoStyle(22f, 30f))
            // Fark sığmazsa bütün olarak alt satıra geçer; satırda sıkışınca "−10 / puan" diye
            // ikiye bölünüyordu (docs/cihaz-testi.md, 1.1.0, G5).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = expert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "· $deltaText",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        delta < -5 -> tokens.bad
                        delta < 0 -> tokens.warn
                        else -> tokens.ok
                    },
                )
            }
        }
    }
}

/**
 * Hafta grafiği: her günün kârı çubuk, biriken stok devri çizgi. Devir, kapanan
 * günlerin satışını ortalama akşam stoğuna bölen aynı hesap olduğu için çizgi
 * hafta sonunda raporun yazdığı sayıya varır; uzmanın devri kesikli çizgi.
 * Akşam stoğu 0.28.1 öncesi kayıtlarda tutulmuyordu; bilinmiyorsa yalnız
 * çubuklar çizilir (bkz. [DaySummary.evening]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekChart(state: ReyonOrderState, result: OrderResult) {
    val history = state.history
    if (history.isEmpty()) return
    val profits = history.map { it.profit }
    val known = history.none { it.evening < 0 }
    val turnovers = if (!known) emptyList() else buildList {
        var sold = 0
        var evening = 0
        history.forEachIndexed { i, s ->
            sold += s.soldUnits
            evening += s.evening
            add(if (evening == 0) 0f else sold / (evening.toFloat() / (i + 1)))
        }
    }
    val top = maxOf(profits.max(), 0)
    val bottom = minOf(profits.min(), 0)
    val span = (top - bottom).coerceAtLeast(1).toFloat()
    val turnTop = maxOf(turnovers.maxOrNull() ?: 0f, result.targetTurnover) * 1.2f

    val tokens = Reyon.tokens
    val barUp = MaterialTheme.colorScheme.primary
    val barDown = tokens.bad
    val line = MaterialTheme.colorScheme.tertiary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = tokens.line
    val res = LocalContext.current.resources
    val desc = appString(
        R.string.reyon_order_chart_desc_fmt,
        history.joinToString(", ") { appText(res, R.string.reyon_order_chart_day_fmt, it.day + 1, it.profit) },
        turnoverText(result.turnover),
        turnoverText(result.targetTurnover),
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { contentDescription = desc }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            val n = profits.size
            val slot = size.width / n
            val zeroY = size.height * (top / span)
            drawLine(grid, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            profits.forEachIndexed { i, p ->
                val h = size.height * (abs(p) / span)
                drawRoundRect(
                    color = if (p >= 0) barUp else barDown,
                    topLeft = Offset(slot * i + slot * 0.28f, if (p >= 0) zeroY - h else zeroY),
                    size = Size(slot * 0.44f, maxOf(h, 1.dp.toPx())),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
            if (known && turnTop > 0f) {
                val expertY = size.height - size.height * (result.targetTurnover / turnTop)
                drawLine(
                    color = axis,
                    start = Offset(0f, expertY),
                    end = Offset(size.width, expertY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                val path = Path()
                turnovers.forEachIndexed { i, t ->
                    val x = slot * i + slot / 2f
                    val y = size.height - size.height * (t / turnTop)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(line, radius = 3.dp.toPx(), center = Offset(x, y))
                }
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (s in history) {
                Text(
                    text = ReyonText.dow(res, s.day).take(3),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    textAlign = TextAlign.Center,
                    color = axis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Sığmayan öğe alt satıra geçer; tek satıra sıkıştırılınca sonuncusu harf harf
        // alt alta diziliyordu ("эксперт 9,0"; docs/cihaz-testi.md, 1.1.0, G4).
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Legend(color = barUp, bar = true, text = stringResource(R.string.reyon_order_chart_profit))
            if (known) {
                Legend(color = line, bar = false, text = stringResource(R.string.reyon_order_chart_turnover))
                Text(
                    text = appString(R.string.reyon_order_chart_expert_fmt, turnoverText(result.targetTurnover)),
                    style = MaterialTheme.typography.bodySmall,
                    color = axis,
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, bar: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(width = if (bar) 10.dp else 14.dp, height = if (bar) 10.dp else 3.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
