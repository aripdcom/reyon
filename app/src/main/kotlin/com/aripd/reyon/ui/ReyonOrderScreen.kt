package com.aripd.reyon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aripd.reyon.R
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.platform.Sfx
import com.aripd.reyon.platform.ShareContent
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.engine.DaySummary
import com.aripd.reyon.engine.OrderItem
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonOrderState
import com.aripd.reyon.engine.OrderRules
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ScoreCard
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.platform.appString
import com.aripd.reyon.platform.appText

/** Sipariş modu: raf (stok düzeyleri), gün başlığı, ürün başına sipariş adımlayıcısı, gün kapanışı. */
@Composable
internal fun ReyonOrderContent(
    solved: Long,
    onCompleted: () -> Unit,
    onKind: (ReyonKind) -> Unit,
    onExit: () -> Unit,
    viewModel: ReyonOrderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val hinted by viewModel.hinted.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val sound = LocalSound.current
    val res = LocalContext.current.resources

    // Aynı çözüm ikinci kez sayılmasın: döndürmede de, menüye gidip dönüşte
    // de, mod değişiminde de. Sayaç mevcut durumdan başlar, çünkü ViewModel
    // Activity'ye bağlıdır ve sonucu ekrandan çıkınca da taşır. Kaydedilen
    // durum burada yetmez: kökte SaveableStateHolder yok, menüye dönüşte
    // kayıt silinir (doğru örnek: SudokuScreen).
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
    BackHandler { onExit() }

    val st = state
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    // İpucu sonucu: son ipucu hangi ürüne dokundu ya da öneri kalmadı mı; her değişimde silinir.
    var noHint by remember { mutableStateOf(false) }
    LaunchedEffect(version) { if (hinted < 0) noHint = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(title = stringResource(R.string.app_name), onExit = onExit) {
            if (st != null) {
                TextButton(onClick = viewModel::toMenu) {
                    Text(stringResource(R.string.reyon_to_menu))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScoreCard(
                label = stringResource(R.string.reyon_order_day_label),
                value = st?.let { "${minOf(it.day + 1, it.order.days)}/${it.order.days}" } ?: "—",
                modifier = Modifier.weight(1f),
                highlight = true,
            )
            ScoreCard(
                label = stringResource(R.string.reyon_order_profit_label),
                value = st?.profit?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
            ScoreCard(
                label = stringResource(R.string.reyon_order_target_label),
                value = st?.order?.target?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (st != null) {
                val shelfH = shelfHeight(maxWidth, maxHeight, st.order.cols / (st.order.rows * 0.62f))
                Column(modifier = Modifier.fillMaxSize()) {
                    OrderShelfCanvas(state = st, version = version, height = shelfH)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
            when {
                st == null && generating -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_order_generating))
                }
                st == null -> OrderMenuCard(
                    level = level,
                    mode = mode,
                    records = records,
                    bestPercent = viewModel.bestPercent(level),
                    onKind = onKind,
                    onLevel = viewModel::setLevel,
                    onMode = viewModel::setMode,
                    onStart = viewModel::newGame,
                    onExit = onExit,
                )
                summary != null -> DaySummaryCard(state = st, summary = summary!!, onContinue = viewModel::dismissSummary)
                result != null -> OrderResultCard(
                    state = st,
                    result = result!!,
                    onRetry = viewModel::retry,
                    onMenu = viewModel::toMenu,
                    onExit = onExit,
                )
            }
        }

        if (st != null && result == null && summary == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (viewModel.hint() != null) {
                            noHint = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sound?.play(Sfx.CLEAR, volume = 0.6f)
                        } else {
                            noHint = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reyon_hint))
                }
                Button(
                    onClick = {
                        if (viewModel.closeDay() != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sound?.play(Sfx.DROP, volume = 0.6f)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (st.day + 1 >= st.order.days) R.string.reyon_order_close_week else R.string.reyon_order_close_day))
                }
            }
        }
    }
}

/** Plan rafı; her blokta stok doluluğu (boş kısım karartılır) ve stok/kapasite rozeti. */
@Composable
private fun OrderShelfCanvas(state: ReyonOrderState, version: Int, height: Dp) {
    val order = state.order
    val res = LocalContext.current.resources
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer) { BlockLabeler(textMeasurer) }
    val path = remember { Path() }
    val desc = appString(R.string.reyon_order_board_desc_fmt, order.rows, order.cols, minOf(state.day + 1, order.days), state.profit)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(ReyonPalette.BoardBg)
            .semantics { contentDescription = desc },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = version
        val g = ShelfGeom(size.width, size.height, order.rows, order.cols)
        drawShelfFrame(g)
        for ((i, item) in order.items.withIndex()) {
            val stock = state.stockOf(i)
            val badge = labeler.label("$stock/${item.capacity}", g.cw, (g.h * 0.2f / density).coerceIn(8f, 10f), ReyonPalette.BlockText)
            drawBlock(g, path, labeler, ReyonText.kind(res, item.product.kind), item.product, item.product.facings, item.row, item.col, badge = badge)
            val empty = 1f - stock.toFloat() / item.capacity
            if (empty > 0f) {
                drawRoundRect(
                    ReyonPalette.EmptyShade,
                    topLeft = Offset(g.x(item.col), g.y(item.row)),
                    size = Size(g.w(item.product.facings), g.h * empty),
                    cornerRadius = CornerRadius(g.corner, g.corner),
                )
            }
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
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = appString(R.string.reyon_order_day_title_fmt, ReyonText.dow(res, day), day + 1, order.days),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
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
                style = MaterialTheme.typography.labelSmall,
                color = ReyonPalette.HintRing,
                modifier = Modifier.padding(top = 2.dp),
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
            .padding(top = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((i, item) in state.order.items.withIndex()) {
            OrderRow(state = state, version = version, index = i, item = item, hinted = i == hinted, onAdjust = onAdjust)
        }
    }
}

@Composable
private fun OrderRow(state: ReyonOrderState, version: Int, index: Int, item: OrderItem, hinted: Boolean, onAdjust: (Int, Int) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val res = LocalContext.current.resources
    val order = state.order
    val name = ReyonText.kind(res, item.product.kind)
    val day = minOf(state.day, order.days - 1)
    val stock = state.stockOf(index)
    val cases = state.orderOf(index)
    val canOrder = state.canOrder(index)
    val arrival = state.arrivalDay(index)
    val desc = appString(R.string.reyon_order_item_desc_fmt, name, stock, item.capacity, cases)
    val more = stringResource(R.string.reyon_order_more)
    val less = stringResource(R.string.reyon_order_less)

    // Bilgi satırı: bugünkü ve teslimat günü tahmini, gelen teslimat, bozulacak birimler, promosyon.
    val info = ArrayList<String>()
    info += appString(R.string.reyon_order_today_fmt, item.low(day), item.high(day))
    if (arrival < order.days && arrival != day) info += appString(R.string.reyon_order_forecast_fmt, ReyonText.dow(res, arrival), item.low(arrival), item.high(arrival))
    for (d in state.day + 1 until minOf(order.days, state.day + OrderRules.MAX_LEAD + 1)) {
        val units = state.incoming(index, d)
        if (units > 0) info += appString(R.string.reyon_order_incoming_fmt, ReyonText.dow(res, d), units)
    }
    if (item.perishable) {
        val tonight = state.expiring(index, day)
        val tomorrow = state.expiring(index, day + 1) - tonight
        if (tonight > 0) info += appString(R.string.reyon_order_expiring_fmt, tonight)
        if (tomorrow > 0) info += appString(R.string.reyon_order_expiring_tomorrow_fmt, tomorrow)
    }
    val promoNow = order.isPromo(day, index)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, if (hinted) ReyonPalette.HintRing else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
    ) {
        // Alt pay 10 dp: adımlayıcının 48 dp dokunma alanı kartın kırpma sınırında kesilmesin
        // (cihazda dikey bant 44 dp ölçülmüştü; docs/oyun-testi.md, Reyon Sipariş C).
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(brandColor(item.product.brand)))
                Text(
                    text = buildString {
                        append(name)
                        append(" ×")
                        append(item.product.facings)
                        if (item.product.premium) append(" ★")
                        if (item.product.heavy) append(" ▼")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (promoNow) {
                    Text(
                        text = stringResource(R.string.reyon_order_promo_tag),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = appString(R.string.reyon_order_stock_fmt, stock, item.capacity),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (stock == 0) ReyonPalette.Violated else MaterialTheme.colorScheme.onSurface,
                )
            }
            StockBar(fraction = stock.toFloat() / item.capacity)
            Text(
                text = info.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
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
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (cases > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.width(120.dp),
                    )
                    StepButton(label = "+", desc = "$more: $name", enabled = cases < item.maxCases) { onAdjust(index, 1) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = appString(R.string.reyon_order_case_size_fmt, item.caseSize, ReyonText.dow(res, arrival)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.End,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.reyon_order_closed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
        modifier = Modifier
            .size(width = 44.dp, height = 32.dp)
            .semantics { contentDescription = desc },
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StockBar(fraction: Float) {
    val track = MaterialTheme.colorScheme.surface
    val fill = when {
        fraction <= 0f -> ReyonPalette.Violated
        fraction < 0.35f -> ReyonPalette.HintRing
        else -> ReyonPalette.Satisfied
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(6.dp)
            .clip(CircleShape),
    ) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

@Composable
private fun DaySummaryCard(state: ReyonOrderState, summary: DaySummary, onContinue: () -> Unit) {
    val res = LocalContext.current.resources
    OverlayCard {
        Text(
            text = appString(R.string.reyon_order_summary_title_fmt, ReyonText.dow(res, summary.day)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        val lines = ArrayList<Pair<String, Color?>>()
        lines += appString(R.string.reyon_order_sum_sold_fmt, summary.soldUnits, summary.margin) to ReyonPalette.Satisfied
        if (summary.lostUnits > 0) lines += appString(R.string.reyon_order_sum_lost_fmt, summary.lostUnits) to ReyonPalette.Violated
        lines += appString(R.string.reyon_order_sum_holding_fmt, summary.holding) to null
        if (summary.wastedUnits > 0) lines += appString(R.string.reyon_order_sum_waste_fmt, summary.wastedUnits, summary.waste) to ReyonPalette.Violated
        if (summary.returnedUnits > 0) lines += appString(R.string.reyon_order_sum_returns_fmt, summary.returnedUnits, summary.returns) to ReyonPalette.Violated
        if (summary.deliveredUnits > 0) lines += appString(R.string.reyon_order_sum_delivered_fmt, summary.deliveredUnits) to null
        for ((text, color) in lines) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = color ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
        Text(
            text = appString(R.string.reyon_order_sum_profit_fmt, summary.profit, state.profit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reyon_order_continue))
        }
    }
}

private fun turnoverText(x: Float): String = AppLocale.decimal(x)

@Composable
private fun OrderMenuCard(
    level: ReyonLevel,
    mode: ReyonMode,
    records: Map<ReyonLevel, ReyonStore.OrderRecord>,
    bestPercent: Int,
    onKind: (ReyonKind) -> Unit,
    onLevel: (ReyonLevel) -> Unit,
    onMode: (ReyonMode) -> Unit,
    onStart: () -> Unit,
    onExit: () -> Unit,
) {
    OverlayCard {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        KindChips(kind = ReyonKind.ORDER, onKind = onKind)
        Text(
            text = stringResource(R.string.reyon_order_intro),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        ModeAndLevelChips(mode = mode, level = level, onMode = onMode, onLevel = onLevel)
        Text(
            text = appString(R.string.reyon_order_level_desc_fmt, level.rows, level.cols, OrderRules.days(level), OrderRules.promoCount(level)),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        val record = records[level]
        val info = when {
            mode == ReyonMode.DAILY && record != null -> {
                val stars = if (record.target > 0) when {
                    record.score >= record.target -> 3
                    record.score >= record.target * 0.9 -> 2
                    record.score >= record.target * 0.75 -> 1
                    else -> 0
                } else 0
                appString(R.string.reyon_order_daily_done_fmt, record.score, record.target, starsText(stars))
            }
            mode == ReyonMode.DAILY -> stringResource(R.string.reyon_order_daily_desc)
            bestPercent > 0 -> appString(R.string.reyon_order_best_fmt, bestPercent)
            else -> stringResource(R.string.reyon_order_free_desc)
        }
        Text(
            text = info,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (mode == ReyonMode.DAILY && record != null) R.string.reyon_play_again else R.string.reyon_order_start))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exit_app))
        }
    }
}

/**
 * Hafta grafiği: her günün kârı çubuk, biriken stok devri çizgi. Devir, kapanan
 * günlerin satışını ortalama akşam stoğuna bölen aynı hesap olduğu için çizgi
 * hafta sonunda kartın yazdığı sayıya varır; uzmanın devri kesikli çizgi.
 * Akşam stoğu 0.28.1 öncesi kayıtlarda tutulmuyordu; bilinmiyorsa yalnız
 * çubuklar çizilir (bkz. [DaySummary.evening]).
 */
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

    val barUp = ReyonPalette.Satisfied
    val barDown = ReyonPalette.Violated
    val line = ReyonPalette.HighlightRing
    val axis = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val res = LocalContext.current.resources
    val desc = appString(
        R.string.reyon_order_chart_desc_fmt,
        history.joinToString(", ") { appText(res, R.string.reyon_order_chart_day_fmt, it.day + 1, it.profit) },
        turnoverText(result.turnover),
        turnoverText(result.targetTurnover),
    )

    Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = desc }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.reyon_order_chart_profit),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = barUp,
            )
            if (known) {
                Text(
                    text = stringResource(R.string.reyon_order_chart_turnover),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = line,
                )
                Text(
                    text = appString(R.string.reyon_order_chart_expert_fmt, turnoverText(result.targetTurnover)),
                    style = MaterialTheme.typography.labelSmall,
                    color = axis,
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(top = 4.dp),
        ) {
            val n = profits.size
            val slot = size.width / n
            val zeroY = size.height * (top / span)
            drawLine(axis, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            profits.forEachIndexed { i, p ->
                val h = size.height * (kotlin.math.abs(p) / span)
                drawRect(
                    color = if (p >= 0) barUp else barDown,
                    topLeft = Offset(slot * i + slot * 0.28f, if (p >= 0) zeroY - h else zeroY),
                    size = Size(slot * 0.44f, maxOf(h, 1.dp.toPx())),
                    alpha = 0.85f,
                )
            }
            if (known && turnTop > 0f) {
                val expertY = size.height - size.height * (result.targetTurnover / turnTop)
                drawLine(
                    color = axis,
                    start = Offset(0f, expertY),
                    end = Offset(size.width, expertY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val path = Path()
                turnovers.forEachIndexed { i, t ->
                    val x = slot * i + slot / 2f
                    val y = size.height - size.height * (t / turnTop)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(line, radius = 2.5.dp.toPx(), center = Offset(x, y))
                }
                drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (s in history) {
                Text(
                    text = "${s.day + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = axis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OrderResultCard(
    state: ReyonOrderState,
    result: OrderResult,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    onExit: () -> Unit,
) {
    val res = LocalContext.current.resources
    val details = appString(R.string.reyon_order_result_fmt, result.score, result.target, result.percent)
    val stats = appString(R.string.reyon_order_stats_fmt, turnoverText(result.turnover), turnoverText(result.targetTurnover), result.service, result.targetService)
    val levelName = ReyonText.level(res, state.order.level)
    OverlayCard {
        Text(
            text = stringResource(R.string.reyon_order_done_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = starsText(result.stars),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        if (result.score > result.target) {
            Text(
                text = stringResource(R.string.reyon_order_beat_target),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        } else if (result.record) {
            Text(
                text = stringResource(R.string.new_record),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = "$levelName · $details",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        WeekChart(state = state, result = result)
        Text(
            text = stats,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        ShareButton(
            ShareContent(
                headline = stringResource(R.string.reyon_order_done_title) + " · " + starsText(result.stars),
                details = listOf(levelName, details, appString(R.string.reyon_order_share_fmt, turnoverText(result.turnover)), modeShareLabel(result.daily, result.day)),
                board = orderPainter(state, res),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.same_difficulty))
        }
        TextButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reyon_to_menu))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exit_app))
        }
    }
}
