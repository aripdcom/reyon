package com.aripd.reyon.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aripd.reyon.R
import com.aripd.reyon.engine.AuditTap
import com.aripd.reyon.engine.Board
import com.aripd.reyon.engine.DeviationKind
import com.aripd.reyon.engine.ReyonAuditState
import com.aripd.reyon.engine.ShelfItem
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.platform.Sfx
import com.aripd.reyon.platform.ShareContent
import com.aripd.reyon.platform.appString
import com.aripd.reyon.ui.common.ActionBar
import com.aripd.reyon.ui.common.ButtonKind
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.KpiRow
import com.aripd.reyon.ui.common.KpiTile
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ReyonButton
import com.aripd.reyon.ui.common.ReyonIcons
import com.aripd.reyon.ui.common.SectionHeader
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.formatTime
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.ui.common.monoStyle
import com.aripd.reyon.ui.theme.Reyon
import kotlinx.coroutines.delay

/** Denetim modu: üstte planogram, altında sapmalı mağaza rafı; sapmalara dokunulur. */
@Composable
internal fun ReyonAuditContent(
    onCompleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReyonAuditViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val missSlot by viewModel.missSlot.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val sound = LocalSound.current

    // Aynı denetim ikinci kez sayılmasın (bkz. ReyonPuzzleContent).
    var countedSeed by remember {
        mutableLongStateOf(state?.audit?.seed?.takeIf { result != null } ?: Long.MIN_VALUE)
    }
    val latestCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(result) {
        val seed = state?.audit?.seed ?: return@LaunchedEffect
        if (result != null && seed != countedSeed) {
            countedSeed = seed
            latestCompleted()
            sound?.play(Sfx.BIG)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(missSlot) {
        if (missSlot >= 0) {
            delay(700L)
            viewModel.clearMiss()
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.setPaused(false)
        onPauseOrDispose { viewModel.setPaused(true) }
    }

    val st = state
    var howTo by rememberHowTo(ReyonKind.AUDIT)
    // Plan büyütme: küçük ekranda planı okumak için; geri tuşu önce bunu kapatır.
    var planZoom by remember { mutableStateOf(false) }
    LaunchedEffect(st, result) { planZoom = false }
    BackHandler {
        if (planZoom) planZoom = false else onBack()
    }
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val shownLevel = st?.audit?.level ?: level

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(
            title = stringResource(R.string.reyon_kind_audit),
            subtitle = caseSubtitle(shownLevel, mode == ReyonMode.DAILY),
            onExit = onBack,
        ) {
            HowToButton { howTo = true }
        }
        KpiRow {
            KpiTile(
                label = stringResource(R.string.reyon_audit_found_label),
                value = st?.foundCount?.toString() ?: "—",
                unit = st?.let { "/${it.audit.deviations.size}" },
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(R.string.reyon_audit_mistakes_label),
                value = st?.mistakes?.toString() ?: "—",
                valueColor = if ((st?.mistakes ?: 0) > 0) Reyon.tokens.warn else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(R.string.time_label),
                value = formatTime(elapsed),
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (st != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val layout = auditLayout(maxWidth, maxHeight, st.audit.rows, st.audit.cols)
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        SectionHeader(
                            title = stringResource(R.string.reyon_audit_plan_label),
                            trailing = stringResource(R.string.reyon_audit_plan_zoom_hint),
                            modifier = Modifier.width(layout.width).padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
                        )
                        PlanCanvas(state = st, width = layout.width, height = layout.planHeight, onTap = { planZoom = true })
                        SectionHeader(
                            title = stringResource(R.string.reyon_audit_shelf_label),
                            trailing = stringResource(R.string.reyon_audit_shelf_hint),
                            modifier = Modifier.width(layout.width).padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                        )
                        AuditCanvas(
                            state = st,
                            version = version,
                            missSlot = missSlot,
                            width = layout.width,
                            height = layout.shelfHeight,
                            onTap = { row, col ->
                                when (viewModel.tap(row, col)) {
                                    is AuditTap.Found -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sound?.play(Sfx.POP, volume = 0.7f, rate = 1.2f)
                                    }
                                    AuditTap.Miss -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sound?.play(Sfx.DROP, volume = 0.5f, rate = 0.8f)
                                    }
                                    AuditTap.Already -> Unit
                                }
                            },
                        )
                        FoundList(state = st, version = version, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (planZoom && result == null) {
                        PlanZoom(state = st, maxWidth = maxWidth, maxHeight = maxHeight, onClose = { planZoom = false })
                    }
                }
            }
            when {
                st == null -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_audit_generating))
                }
                result != null -> AuditDoneCard(
                    state = st,
                    result = result!!,
                    onRetry = viewModel::retry,
                    onBack = onBack,
                )
                howTo -> HowToCard(ReyonKind.AUDIT, shownLevel) { howTo = false }
            }
        }

        if (st != null && result == null) {
            ActionBar {
                val left = st.audit.deviations.size - st.foundCount
                Text(
                    text = appString(R.string.reyon_audit_remaining_fmt, left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                ReyonButton(
                    text = stringResource(R.string.reyon_hint),
                    onClick = {
                        if (viewModel.hint() != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sound?.play(Sfx.CLEAR, volume = 0.6f)
                        }
                    },
                    kind = ButtonKind.TONAL,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Plan ve raf tuvallerinin boyutları (dp). */
internal class AuditLayout(val width: Dp, val planHeight: Dp, val shelfHeight: Dp)

/**
 * Denetim yerleşimi: iki tuval aynı genişlikte (gözler alt alta hizalı kalsın),
 * raf satırı göz genişliğinin [SHELF_ROW_MIN]–[SHELF_ROW_MAX] katı, plan satırı
 * rafınkinin [PLAN_RATIO] katı. Yükseklik bütçesi (başlıklar ve bulunanlar için
 * bir satır düşüldükten sonra) önce satır katsayısını belirler; taban katsayıda
 * bile sığmıyorsa tuvaller daraltılır ve ortalanır. Böylece kısa ekranlarda raf
 * daralıp sola yaslanmaz, bulunanlar listesi kaybolmaz; uzun ekranlarda bloklar
 * büyür.
 */
internal fun auditLayout(maxWidth: Dp, maxHeight: Dp, rows: Int, cols: Int): AuditLayout {
    val budget = (maxHeight - SECTION_LABEL_HEIGHT * 2 - FOUND_LIST_MIN).coerceAtLeast(96.dp)
    val unit = maxWidth * rows / cols // katsayı 1'de bir tuvalin boyu
    val f = (budget / (unit * (1f + PLAN_RATIO))).coerceIn(SHELF_ROW_MIN, SHELF_ROW_MAX)
    val need = unit * (1f + PLAN_RATIO) * f
    val width = if (need > budget) maxWidth * (budget / need) else maxWidth
    val row = width / cols
    return AuditLayout(width, row * rows * f * PLAN_RATIO, row * rows * f)
}

internal const val SHELF_ROW_MIN = 0.62f
internal const val SHELF_ROW_MAX = 0.9f
internal const val PLAN_RATIO = 0.85f
internal val SECTION_LABEL_HEIGHT = 28.dp
internal val FOUND_LIST_MIN = 26.dp

/** Büyütülmüş plan: içerik alanını kaplayan karartma, ortada geniş plan; dokununca kapanır. */
@Composable
private fun PlanZoom(state: ReyonAuditState, maxWidth: Dp, maxHeight: Dp, onClose: () -> Unit) {
    val audit = state.audit
    val desc = stringResource(R.string.reyon_audit_plan_zoom_desc)
    val room = (maxHeight - 64.dp).coerceAtLeast(96.dp)
    val unit = maxWidth * audit.rows / audit.cols
    val f = (room / unit).coerceIn(SHELF_ROW_MIN, 0.95f)
    val width = if (unit * f > room) maxWidth * (room / (unit * f)) else maxWidth
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.reyon_audit_plan_label),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .width(width)
                    .padding(start = 4.dp, bottom = 4.dp),
            )
            PlanCanvas(state = state, width = width, height = width * audit.rows / audit.cols * f, onTap = null)
            Text(
                text = stringResource(R.string.reyon_audit_plan_zoom_close),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Planogram: etiketsiz, raptiyesiz; ürünlerin olması gereken yeri. */
@Composable
private fun PlanCanvas(state: ReyonAuditState, width: Dp, height: Dp, onTap: (() -> Unit)?) {
    val audit = state.audit
    val res = LocalContext.current.resources
    val colors = rememberShelfColors()
    val texts = rememberShelfTexts()
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer, colors.packFamily) { BlockLabeler(textMeasurer, colors.packFamily) }
    val currentTap by rememberUpdatedState(onTap)
    val desc = appString(R.string.reyon_audit_plan_desc_fmt, audit.rows, audit.cols)
    Canvas(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc }
            .then(if (onTap != null) Modifier.pointerInput(Unit) { detectTapGestures { currentTap?.invoke() } } else Modifier),
    ) {
        val g = ShelfGeom(size.width, size.height, audit.rows, audit.cols, density, rail = false)
        drawGondola(g, colors)
        for (it in audit.planItems) {
            drawPack(g, labeler, colors, ReyonText.kind(res, it.product.kind), texts.mark(it.product), it.product, it.facings, it.row, it.col)
        }
    }
}

/** Mağaza rafı: sapmalar dokunulana dek çizimde saklı; bulunanlar kırmızı çerçeve ve türüyle. */
@Composable
private fun AuditCanvas(state: ReyonAuditState, version: Int, missSlot: Int, width: Dp, height: Dp, onTap: (Int, Int) -> Unit) {
    val audit = state.audit
    val rows = audit.rows
    val cols = audit.cols
    val res = LocalContext.current.resources
    val currentTap by rememberUpdatedState(onTap)
    val colors = rememberShelfColors()
    val texts = rememberShelfTexts()
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer, colors.packFamily) { BlockLabeler(textMeasurer, colors.packFamily) }
    val typeNames = DeviationKind.entries.map { stringResource(deviationName(it)) }
    val desc = appString(R.string.reyon_audit_board_desc_fmt, rows, cols, state.foundCount, audit.deviations.size)
    Canvas(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc }
            .pointerInput(rows, cols) {
                detectTapGestures { pos ->
                    val g = ShelfGeom(size.width.toFloat(), size.height.toFloat(), rows, cols, density)
                    currentTap(g.rowAt(pos.y), g.colAt(pos.x))
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = version
        val g = ShelfGeom(size.width, size.height, rows, cols, density)
        drawGondola(g, colors)
        drawEmptySlots(g, itemsMask(audit.board, audit.items), colors)
        for (it in audit.items) {
            drawPack(g, labeler, colors, ReyonText.kind(res, it.product.kind), texts.mark(it.product), it.product, it.facings, it.row, it.col)
            drawShelfLabel(g, labeler, colors, it.product, it.facings, it.row, it.col, texts.code(it.product), texts.badges(it.product))
        }
        for ((i, d) in audit.deviations.withIndex()) {
            if (!state.isFound(i)) continue
            drawMaskRing(g, d.slotMask, colors.tokens.bad)
            drawDeviationFlag(g, labeler, colors, d.slotMask, typeNames[d.kind.ordinal])
        }
        if (missSlot >= 0) drawMaskRing(g, 1 shl missSlot, colors.tokens.warn)
    }
}

internal fun itemsMask(board: Board, items: List<ShelfItem>): Int {
    var mask = 0
    for (it in items) mask = mask or board.mask(board.idx(it.row, it.col), it.facings)
    return mask
}

/** Sapma türünün kısa adı: raftaki bayrakta ve tür listesinde. */
@StringRes
internal fun deviationName(kind: DeviationKind): Int = when (kind) {
    DeviationKind.SWAP -> R.string.dev_type_swap
    DeviationKind.GAP -> R.string.dev_type_gap
    DeviationKind.FOREIGN -> R.string.dev_type_foreign
    DeviationKind.BRAND -> R.string.dev_type_brand
    DeviationKind.SIZE -> R.string.dev_type_size
    DeviationKind.SPILL -> R.string.dev_type_spill
}

/**
 * Sapma türleri ve bulunanlar: üstte bu denetimde aranan türler (bulunan tür
 * kırmızı zeminli), altında bulunan her sapmanın açıklaması, en son bulunan
 * en üstte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundList(state: ReyonAuditState, version: Int, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    val tokens = Reyon.tokens
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val audit = state.audit
    val kinds = audit.deviations.map { it.kind }.distinct().sortedBy { it.ordinal }
    val foundKinds = state.foundOrder.map { audit.deviations[it].kind }.toSet()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (k in kinds) {
                val found = k in foundKinds
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (found) tokens.badWeak else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (found) tokens.badWeak else tokens.line),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (found) Icon(ReyonIcons.Flag, contentDescription = null, tint = tokens.onBadWeak, modifier = Modifier.size(13.dp))
                        Text(
                            text = stringResource(deviationName(k)),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = if (found) tokens.onBadWeak else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // En son bulunan en üstte: liste tek satıra sığsa bile son bulgu görünür.
        for (i in state.foundOrder.asReversed()) {
            val d = audit.deviations[i]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(ReyonIcons.Check, contentDescription = null, tint = tokens.ok, modifier = Modifier.size(16.dp))
                Text(
                    text = ReyonText.deviation(res, audit, d),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AuditDoneCard(
    state: ReyonAuditState,
    result: AuditResult,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val res = LocalContext.current.resources
    val time = formatTime(result.time)
    val details = appString(R.string.reyon_audit_result_fmt, ReyonText.level(res, state.audit.level), result.mistakes, result.hints)
    OverlayCard {
        Text(
            text = stringResource(R.string.reyon_audit_done_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(text = time, style = monoStyle(36f, 44f), color = MaterialTheme.colorScheme.primary)
        if (result.record) PersonalBestPill()
        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShareButton(
            ShareContent(
                headline = stringResource(R.string.reyon_audit_done_title),
                details = listOf(details, appString(R.string.time_fmt, time), modeShareLabel(result.daily, result.day)),
                board = auditPainter(state, res),
            ),
        )
        ReportButtons(daily = result.daily, newLabel = R.string.report_new_case, onBack = onBack, onRetry = onRetry)
    }
}
