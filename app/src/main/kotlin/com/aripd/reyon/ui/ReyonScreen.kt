package com.aripd.reyon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.annotation.StringRes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aripd.reyon.R
import com.aripd.reyon.engine.Board
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.Category
import com.aripd.reyon.engine.ClueStatus
import com.aripd.reyon.engine.Product
import com.aripd.reyon.engine.ReyonHint
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonState
import com.aripd.reyon.engine.Rules
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.platform.Sfx
import com.aripd.reyon.platform.ShareContent
import com.aripd.reyon.platform.appString
import com.aripd.reyon.ui.common.ActionBar
import com.aripd.reyon.ui.common.BannerTone
import com.aripd.reyon.ui.common.ButtonKind
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.GuideBanner
import com.aripd.reyon.ui.common.KpiRow
import com.aripd.reyon.ui.common.KpiTile
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ReyonButton
import com.aripd.reyon.ui.common.ReyonCard
import com.aripd.reyon.ui.common.ReyonIcons
import com.aripd.reyon.ui.common.SectionHeader
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.formatTime
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.ui.common.monoStyle
import com.aripd.reyon.ui.theme.Reyon
import java.time.LocalDate

/**
 * Reyon: Görevler ve dört mod. Diziliş (planogram bulmacası), Denetim (uyum
 * sapmalarını bulma), Satış (raf verimi) ve Sipariş (stok haftası).
 *
 * Açılışta Görevler; bir vakaya dokununca o modun ekranı açılır, geri oku
 * Görevler'e döner (yarım vaka saklanır, aynı vakaya dokununca sürer). Rekor =
 * tamamlanan vaka sayısı.
 */
@Composable
fun ReyonScreen(
    highScore: Long,
    onScore: (Long) -> Unit,
    onExit: () -> Unit,
    viewModel: ReyonViewModel = viewModel(),
) {
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val open by viewModel.screenOpen.collectAsStateWithLifecycle()
    val practiceLevel by viewModel.practiceLevel.collectAsStateWithLifecycle()
    val auditVm: ReyonAuditViewModel = viewModel()
    val salesVm: ReyonSalesViewModel = viewModel()
    val orderVm: ReyonOrderViewModel = viewModel()
    val baseline = remember { highScore }
    var solvedSession by remember { mutableIntStateOf(0) }
    val latestOnScore by rememberUpdatedState(onScore)
    val onCompleted = {
        solvedSession++
        latestOnScore(baseline + solvedSession)
    }
    // Görevler'e her dönüşte satırlar kayıtlardan yeniden okunur.
    var refresh by remember { mutableIntStateOf(0) }
    val onBack: () -> Unit = {
        when (kind) {
            ReyonKind.PUZZLE -> viewModel.toMenu()
            ReyonKind.AUDIT -> auditVm.toMenu()
            ReyonKind.SALES -> salesVm.toMenu()
            ReyonKind.ORDER -> orderVm.toMenu()
        }
        viewModel.closeScreen()
        refresh++
    }
    val start: (ReyonKind, ReyonMode, ReyonLevel) -> Unit = { k, mode, level ->
        when (k) {
            ReyonKind.PUZZLE -> viewModel.open(mode, level)
            ReyonKind.AUDIT -> auditVm.open(mode, level)
            ReyonKind.SALES -> salesVm.open(mode, level)
            ReyonKind.ORDER -> orderVm.open(mode, level)
        }
        viewModel.openScreen(k)
    }

    if (!open) {
        ReyonHome(
            practiceLevel = practiceLevel,
            onPracticeLevel = viewModel::setPracticeLevel,
            onDaily = { k -> start(k, ReyonMode.DAILY, dailyLevel(LocalDate.now().toEpochDay())) },
            onPractice = { k -> start(k, ReyonMode.FREE, practiceLevel) },
            onExit = onExit,
            refresh = refresh,
        )
    } else {
        when (kind) {
            ReyonKind.PUZZLE -> ReyonPuzzleContent(viewModel = viewModel, onCompleted = onCompleted, onBack = onBack)
            ReyonKind.AUDIT -> ReyonAuditContent(onCompleted = onCompleted, onBack = onBack, viewModel = auditVm)
            ReyonKind.SALES -> ReyonSalesContent(onCompleted = onCompleted, onBack = onBack, viewModel = salesVm)
            ReyonKind.ORDER -> ReyonOrderContent(onCompleted = onCompleted, onBack = onBack, viewModel = orderVm)
        }
    }
}

@Composable
private fun ReyonPuzzleContent(
    viewModel: ReyonViewModel,
    onCompleted: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val highlightClue by viewModel.highlightClue.collectAsStateWithLifecycle()
    val lastHint by viewModel.lastHint.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val sound = LocalSound.current

    // Aynı çözüm ikinci kez sayılmasın: döndürmede de, Görevler'e gidip dönüşte
    // de, mod değişiminde de. Sayaç mevcut durumdan başlar, çünkü ViewModel
    // Activity'ye bağlıdır ve sonucu ekrandan çıkınca da taşır. Kaydedilen
    // durum burada yetmez: kökte SaveableStateHolder yok, Görevler'e dönüşte
    // kayıt silinir.
    var countedSeed by remember {
        mutableLongStateOf(state?.puzzle?.seed?.takeIf { result != null } ?: Long.MIN_VALUE)
    }
    val latestCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(result) {
        val seed = state?.puzzle?.seed ?: return@LaunchedEffect
        if (result != null && seed != countedSeed) {
            countedSeed = seed
            latestCompleted()
            sound?.play(Sfx.BIG)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.setPaused(false)
        onPauseOrDispose { viewModel.setPaused(true) }
    }
    BackHandler { onBack() }

    val st = state
    var howTo by rememberHowTo(ReyonKind.PUZZLE)
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val shownLevel = st?.puzzle?.level ?: level

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(
            title = stringResource(R.string.reyon_kind_puzzle),
            subtitle = caseSubtitle(shownLevel, mode == ReyonMode.DAILY),
            onExit = onBack,
        ) {
            HowToButton { howTo = true }
        }
        val satisfied = st?.let { s -> s.puzzle.brief.count { s.status(it) == ClueStatus.SATISFIED } }
        KpiRow {
            KpiTile(
                label = stringResource(R.string.kpi_brief),
                value = satisfied?.toString() ?: "—",
                unit = st?.let { "/${it.puzzle.brief.size}" },
                valueColor = if (st != null && satisfied == st.puzzle.brief.size) Reyon.tokens.ok else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(R.string.kpi_placed),
                value = st?.placedCount?.toString() ?: "—",
                unit = st?.let { "/${it.puzzle.products.size}" },
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = stringResource(R.string.time_label),
                value = formatTime(elapsed),
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
                val highlighted = remember(st, highlightClue, version) {
                    if (highlightClue in st.puzzle.brief.indices) Rules.involved(st.puzzle.brief[highlightClue], st.puzzle.products).toSet() else emptySet()
                }
                val violated = remember(st, version) {
                    st.puzzle.brief.filter { st.status(it) == ClueStatus.VIOLATED }
                        .flatMap { Rules.involved(it, st.puzzle.products).toList() }.toSet()
                }
                val hinted = (lastHint as? ReyonHint.Place)?.product ?: -1
                val wrongHint = (lastHint as? ReyonHint.Wrong)?.product ?: -1
                val shelfH = shelfHeight(maxWidth, maxHeight, st.puzzle.cols / (st.puzzle.rows * 0.78f))
                Column(modifier = Modifier.fillMaxSize()) {
                    ShelfCanvas(
                        state = st,
                        version = version,
                        height = shelfH,
                        selected = selected,
                        highlighted = highlighted,
                        violated = violated,
                        hinted = hinted,
                        onTap = { row, col ->
                            if (viewModel.tapSlot(row, col)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sound?.play(Sfx.POP, volume = 0.5f)
                            } else if (selected >= 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onLongPress = { row, col ->
                            val occupant = st.occupant(row, col)
                            if (occupant >= 0 && !st.isLocked(occupant)) {
                                viewModel.select(occupant)
                                if (viewModel.removeSelected()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    sound?.play(Sfx.DROP, volume = 0.4f, rate = 1.4f)
                                }
                            }
                        },
                    )
                    HintLine(state = st, hint = lastHint)
                    // Brif ile yerleştirilecek ürünler kalan yüksekliği paylaşıyor; "kalan"
                    // burada ölçülüyor, böylece kılavuz bandı da hesaba giriyor.
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val trayH = trayHeight(maxHeight)
                        Column(modifier = Modifier.fillMaxSize()) {
                            Brief(
                                state = st,
                                version = version,
                                highlightClue = highlightClue,
                                onToggle = viewModel::toggleHighlight,
                                modifier = Modifier.weight(1f, fill = false).testTag(REYON_PANEL_TAG),
                            )
                            Tray(
                                state = st,
                                version = version,
                                selected = selected,
                                highlighted = highlighted,
                                wrongHint = wrongHint,
                                onSelect = { id ->
                                    viewModel.select(id)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.heightIn(max = trayH).testTag(REYON_TRAY_TAG),
                            )
                        }
                    }
                }
            }
            when {
                st == null -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_generating))
                }
                result != null -> SolvedCard(
                    state = st,
                    result = result!!,
                    onRetry = viewModel::retry,
                    onBack = onBack,
                )
                howTo -> HowToCard(ReyonKind.PUZZLE, shownLevel) { howTo = false }
            }
        }

        if (st != null && result == null) {
            ActionBar {
                ReyonButton(
                    text = stringResource(R.string.undo),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.undo()
                    },
                    enabled = st.canUndo,
                    modifier = Modifier.weight(1f),
                )
                ReyonButton(
                    text = stringResource(R.string.reyon_remove),
                    onClick = { if (viewModel.removeSelected()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    enabled = selected >= 0 && st.isPlaced(selected),
                    modifier = Modifier.weight(1f),
                )
                ReyonButton(
                    text = stringResource(R.string.reyon_hint),
                    onClick = {
                        val h = viewModel.hint()
                        if (h != null) {
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

// ---------------------------------------------------------------------------
// Raf
// ---------------------------------------------------------------------------

/** Ürünlerin kapladığı gözlerin maskesi; boş gözler kesikli çizilir. */
internal fun occupiedMask(board: Board, placed: List<Triple<Product, Int, Int>>, facings: (Product) -> Int = { it.facings }): Int {
    var mask = 0
    for ((p, row, col) in placed) mask = mask or board.mask(board.idx(row, col), facings(p))
    return mask
}

/** Rozet metinleri ve kategori kodları; tuval bunları bir kez okur. */
internal class ShelfTexts(val codes: List<String>, val marks: List<String>, val eye: String, val heavy: String) {
    fun badges(p: Product) = LabelBadges(p.premium, p.heavy, if (p.premium) eye else if (p.heavy) heavy else null)
    fun code(p: Product) = codes[p.category.ordinal]
    fun mark(p: Product) = marks[p.brand.ordinal]
}

@Composable
internal fun rememberShelfTexts(): ShelfTexts {
    val res = LocalContext.current.resources
    val eye = stringResource(R.string.reyon_badge_eye)
    val heavy = stringResource(R.string.reyon_badge_heavy)
    return remember(res, eye, heavy) {
        ShelfTexts(
            Category.entries.map { ReyonText.categoryCode(res, it) },
            Brand.entries.map { ReyonText.brandMark(res, it) },
            eye,
            heavy,
        )
    }
}

@Composable
private fun ShelfCanvas(
    state: ReyonState,
    version: Int,
    height: Dp,
    selected: Int,
    highlighted: Set<Int>,
    violated: Set<Int>,
    hinted: Int,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
) {
    val puzzle = state.puzzle
    val rows = puzzle.rows
    val cols = puzzle.cols
    val res = LocalContext.current.resources
    val currentTap by rememberUpdatedState(onTap)
    val currentLong by rememberUpdatedState(onLongPress)
    val colors = rememberShelfColors()
    val texts = rememberShelfTexts()
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer, colors.packFamily) { BlockLabeler(textMeasurer, colors.packFamily) }
    val names = remember(puzzle, res) { puzzle.products.map { ReyonText.kind(res, it.kind) } }
    val unplaced = puzzle.products.size - state.placedCount
    val desc = appString(R.string.reyon_board_desc_fmt, rows, cols, unplaced)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc }
            .pointerInput(rows, cols) {
                detectTapGestures(
                    onTap = { pos ->
                        val g = ShelfGeom(size.width.toFloat(), size.height.toFloat(), rows, cols, density)
                        currentTap(g.rowAt(pos.y), g.colAt(pos.x))
                    },
                    onLongPress = { pos ->
                        val g = ShelfGeom(size.width.toFloat(), size.height.toFloat(), rows, cols, density)
                        currentLong(g.rowAt(pos.y), g.colAt(pos.x))
                    },
                )
            },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = version
        val g = ShelfGeom(size.width, size.height, rows, cols, density)
        drawGondola(g, colors)
        val placed = puzzle.products.mapNotNull { p -> state.placement(p.id)?.let { Triple(p, it.row, it.col) } }
        drawEmptySlots(g, occupiedMask(puzzle.board, placed), colors)
        for ((p, row, col) in placed) {
            drawPack(g, labeler, colors, names[p.id], texts.mark(p), p, p.facings, row, col, pinned = state.isLocked(p.id))
            drawShelfLabel(g, labeler, colors, p, p.facings, row, col, texts.code(p), texts.badges(p), highlighted = p.id == selected)
        }
        for ((p, row, col) in placed) {
            val ring = when {
                p.id == selected -> colors.tokens.attention
                p.id == hinted -> colors.tokens.attention
                p.id in violated -> colors.tokens.bad
                p.id in highlighted -> colors.highlight
                else -> null
            }
            if (ring != null) drawPackRing(g, p, p.facings, row, col, ring, width = if (p.id == selected) 3.dp.toPx() else 2.5.dp.toPx())
        }
    }
}

// ---------------------------------------------------------------------------
// Brif, yerleştirilecek ürünler, kılavuz bandı
// ---------------------------------------------------------------------------

@Composable
private fun Brief(
    state: ReyonState,
    version: Int,
    highlightClue: Int,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val tokens = Reyon.tokens
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val briefLabel = stringResource(R.string.reyon_brief_label)
    val statuses = state.puzzle.brief.map { state.status(it) }
    val satisfied = statuses.count { it == ClueStatus.SATISFIED }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader(
            title = briefLabel,
            trailing = appString(R.string.brief_satisfied_fmt, satisfied, statuses.size),
            trailingColor = if (satisfied == statuses.size) tokens.ok else Color.Unspecified,
            trailingBold = satisfied == statuses.size,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
        ReyonCard {
            state.puzzle.brief.forEachIndexed { index, clue ->
                val status = statuses[index]
                val text = ReyonText.clue(res, state.puzzle, clue)
                val statusWord = stringResource(
                    when (status) {
                        ClueStatus.PENDING -> R.string.brief_status_pending
                        ClueStatus.SATISFIED -> R.string.brief_status_ok
                        ClueStatus.VIOLATED -> R.string.brief_status_broken
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index == highlightClue) tokens.brandWeak else Color.Transparent)
                        .clickable { onToggle(index) }
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics { contentDescription = "$briefLabel: $statusWord · $text" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusDot(status)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                        color = if (status == ClueStatus.VIOLATED) tokens.bad else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (index < state.puzzle.brief.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
                }
            }
        }
    }
}

/** Kuralın durumu: sağlandı (onay), bozuluyor (çarpı), bekliyor (boş halka). */
@Composable
private fun StatusDot(status: ClueStatus) {
    val tokens = Reyon.tokens
    val (bg, fg) = when (status) {
        ClueStatus.SATISFIED -> tokens.okWeak to tokens.ok
        ClueStatus.VIOLATED -> tokens.badWeak to tokens.bad
        ClueStatus.PENDING -> Color.Transparent to tokens.line
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(bg, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            ClueStatus.SATISFIED -> Icon(ReyonIcons.Check, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            ClueStatus.VIOLATED -> Text("✕", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            ClueStatus.PENDING -> Canvas(modifier = Modifier.size(18.dp)) {
                drawCircle(fg, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun HintLine(state: ReyonState, hint: ReyonHint?) {
    if (hint == null) return
    val res = LocalContext.current.resources
    val guide = stringResource(R.string.reyon_hint)
    when (hint) {
        is ReyonHint.Wrong -> GuideBanner(
            title = guide,
            body = appString(R.string.reyon_hint_wrong_fmt, ReyonText.kind(res, state.puzzle.products[hint.product].kind)),
            tone = BannerTone.WARN,
            modifier = Modifier.padding(top = 8.dp),
        )
        is ReyonHint.Place -> GuideBanner(
            title = guide,
            body = appString(
                R.string.reyon_hint_place_fmt,
                ReyonText.kind(res, state.puzzle.products[hint.product].kind),
                ReyonText.shelfAt(res, hint.row, state.puzzle.rows),
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Yerleştirilecek ürünler: ambalaj rengi ve boyunda küçük bir örnek, ad, yüz
 * sayısı; göz hizası ve ağır ürünlerde rozet simgesi. Seçili ürün dikkat
 * sarısıyla çerçevelenir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductChips(
    products: List<Product>,
    selected: Int,
    onSelect: (Int) -> Unit,
    ringOf: (Product) -> Color?,
    detail: @Composable (Product) -> String,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val tokens = Reyon.tokens
    val trayLabel = stringResource(R.string.reyon_tray_label)
    val eye = stringResource(R.string.reyon_badge_eye)
    val heavy = stringResource(R.string.reyon_badge_heavy)
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp).verticalScroll(rememberScrollState())) {
        SectionHeader(
            title = if (products.isEmpty()) stringResource(R.string.reyon_tray_empty) else trayLabel,
            trailing = if (products.isEmpty()) null else appString(R.string.tray_count_fmt, products.size),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (p in products) {
                val name = ReyonText.kind(res, p.kind)
                val extra = listOfNotNull(if (p.premium) eye else null, if (p.heavy) heavy else null).joinToString(", ")
                val desc = "$trayLabel: " + appString(R.string.reyon_tray_item_fmt, name, p.facings) + if (extra.isEmpty()) "" else ", $extra"
                val isSelected = p.id == selected
                val ring = if (isSelected) tokens.attention else ringOf(p)
                Surface(
                    onClick = { onSelect(p.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (ring != null) 2.dp else 1.dp, ring ?: tokens.line),
                    modifier = Modifier.semantics { contentDescription = desc },
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(11.dp)
                                .height(10.dp + 4.dp * p.size)
                                .background(tokens.pack(p.brand), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp)),
                        )
                        Text(text = name, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        Text(text = detail(p), style = monoStyle(12f, 16f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.premium) Icon(ReyonIcons.Eye, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        if (p.heavy) Icon(ReyonIcons.Heavy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Tray(
    state: ReyonState,
    version: Int,
    selected: Int,
    highlighted: Set<Int>,
    wrongHint: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val tokens = Reyon.tokens
    val highlight = MaterialTheme.colorScheme.primary
    ProductChips(
        products = state.puzzle.products.filter { !state.isPlaced(it.id) },
        selected = selected,
        onSelect = onSelect,
        ringOf = { p ->
            when (p.id) {
                wrongHint -> tokens.warn
                in highlighted -> highlight
                else -> null
            }
        },
        detail = { p -> appString(R.string.facings_fmt, p.facings) },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Rapor
// ---------------------------------------------------------------------------

@Composable
private fun SolvedCard(
    state: ReyonState,
    result: ReyonResult,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val res = LocalContext.current.resources
    val time = formatTime(result.time)
    val levelName = ReyonText.level(res, state.puzzle.level)
    val details = appString(R.string.reyon_result_fmt, levelName, result.hints)
    OverlayCard {
        Text(
            text = stringResource(R.string.reyon_done_title),
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
                headline = stringResource(R.string.reyon_done_title),
                details = listOf(details, appString(R.string.time_fmt, time), modeShareLabel(result.daily, result.day)),
                board = reyonPainter(state, res),
            ),
        )
        ReportButtons(daily = result.daily, newLabel = R.string.report_new_case, onBack = onBack, onRetry = onRetry)
    }
}

/**
 * Raporun altındaki iki düğme: Görevler'e dön ve yeni vaka. Günün vakasında yeni
 * vaka aynı rafı yeniden açar (tohum gün); o yüzden "Yeniden dene" yazar.
 */
@Composable
internal fun ReportButtons(daily: Boolean, @StringRes newLabel: Int, onBack: () -> Unit, onRetry: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReyonButton(
            text = stringResource(R.string.nav_tasks),
            onClick = onBack,
            modifier = Modifier.weight(1f),
        )
        ReyonButton(
            text = stringResource(if (daily) R.string.report_try_again else newLabel),
            onClick = onRetry,
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.weight(1f),
        )
    }
}
