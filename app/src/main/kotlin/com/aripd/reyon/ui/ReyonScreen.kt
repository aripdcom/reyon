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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.aripd.reyon.engine.ClueStatus
import com.aripd.reyon.engine.ReyonHint
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonState
import com.aripd.reyon.engine.Rules
import com.aripd.reyon.ui.common.ActionLabel
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ScoreCard
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.formatTime
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.platform.appString

/**
 * Reyon: dört tür, tek ekran. Diziliş (planogram bulmacası), Denetim (uyum
 * sapmalarını bulma), Satış (serbest diziliş) ve Sipariş (stok haftası).
 * Rekor = tamamlanan tur sayısı.
 */
@Composable
fun ReyonScreen(
    highScore: Long,
    onScore: (Long) -> Unit,
    onExit: () -> Unit,
    viewModel: ReyonViewModel = viewModel(),
) {
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val baseline = remember { highScore }
    var solvedSession by remember { mutableIntStateOf(0) }
    val latestOnScore by rememberUpdatedState(onScore)
    val onCompleted = {
        solvedSession++
        latestOnScore(baseline + solvedSession)
    }
    if (kind == ReyonKind.ORDER) {
        ReyonOrderContent(
            solved = baseline + solvedSession,
            onCompleted = onCompleted,
            onKind = viewModel::setKind,
            onExit = onExit,
        )
    } else if (kind == ReyonKind.SALES) {
        ReyonSalesContent(
            solved = baseline + solvedSession,
            onCompleted = onCompleted,
            onKind = viewModel::setKind,
            onExit = onExit,
        )
    } else if (kind == ReyonKind.AUDIT) {
        ReyonAuditContent(
            solved = baseline + solvedSession,
            onCompleted = onCompleted,
            onKind = viewModel::setKind,
            onExit = onExit,
        )
    } else {
        ReyonPuzzleContent(
            viewModel = viewModel,
            solved = baseline + solvedSession,
            onCompleted = onCompleted,
            onKind = viewModel::setKind,
            onExit = onExit,
        )
    }
}

@Composable
private fun ReyonPuzzleContent(
    viewModel: ReyonViewModel,
    solved: Long,
    onCompleted: () -> Unit,
    onKind: (ReyonKind) -> Unit,
    onExit: () -> Unit,
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
    BackHandler { onExit() }

    val st = state
    @Suppress("UNUSED_VARIABLE")
    val tick = version

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
                label = stringResource(R.string.time_label),
                value = formatTime(elapsed),
                modifier = Modifier.weight(1f),
                highlight = true,
            )
            ScoreCard(
                label = stringResource(R.string.difficulty_label),
                value = st?.let { ReyonText.level(res, it.puzzle.level) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            ScoreCard(
                label = stringResource(R.string.solved_label),
                value = solved.toString(),
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
                    // Brif ile tepsi kalan yüksekliği paylaşıyor; "kalan" burada
                    // ölçülüyor, böylece ipucu satırı da hesaba giriyor.
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
                st == null && generating -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_generating))
                }
                st == null -> MenuCard(
                    level = level,
                    mode = mode,
                    records = records,
                    bestTime = viewModel.bestTime(level),
                    onKind = onKind,
                    onLevel = viewModel::setLevel,
                    onMode = viewModel::setMode,
                    onStart = viewModel::newGame,
                    onExit = onExit,
                )
                result != null -> SolvedCard(
                    state = st,
                    result = result!!,
                    onRetry = viewModel::retry,
                    onMenu = viewModel::toMenu,
                    onExit = onExit,
                )
            }
        }

        if (st != null && result == null) {
            Controls(
                canUndo = st.canUndo,
                canRemove = selected >= 0 && st.isPlaced(selected),
                onUndo = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.undo()
                },
                onRemove = {
                    if (viewModel.removeSelected()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onHint = {
                    val h = viewModel.hint()
                    if (h != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        sound?.play(Sfx.CLEAR, volume = 0.6f)
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Raf
// ---------------------------------------------------------------------------

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
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer) { BlockLabeler(textMeasurer) }
    val path = remember { Path() }
    val unplaced = puzzle.products.size - state.placedCount
    val desc = appString(R.string.reyon_board_desc_fmt, rows, cols, unplaced)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(ReyonPalette.BoardBg)
            .semantics { contentDescription = desc }
            .pointerInput(rows, cols) {
                detectTapGestures(
                    onTap = { pos ->
                        val col = (pos.x / (size.width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
                        val row = (pos.y / (size.height / rows.toFloat())).toInt().coerceIn(0, rows - 1)
                        currentTap(row, col)
                    },
                    onLongPress = { pos ->
                        val col = (pos.x / (size.width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
                        val row = (pos.y / (size.height / rows.toFloat())).toInt().coerceIn(0, rows - 1)
                        currentLong(row, col)
                    },
                )
            },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = version
        val g = ShelfGeom(size.width, size.height, rows, cols)
        drawShelfFrame(g)
        for (p in puzzle.products) {
            val pl = state.placement(p.id) ?: continue
            val ring = when {
                p.id == selected -> ReyonPalette.SelectedRing
                p.id == hinted -> ReyonPalette.HintRing
                p.id in violated -> ReyonPalette.ViolatedRing
                p.id in highlighted -> ReyonPalette.HighlightRing
                else -> null
            }
            drawBlock(g, path, labeler, ReyonText.kind(res, p.kind), p, p.facings, pl.row, pl.col, ring = ring, locked = state.isLocked(p.id))
        }
    }
}

// ---------------------------------------------------------------------------
// Brif, tepsi, ipucu satırı, kontroller
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
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val briefLabel = stringResource(R.string.reyon_brief_label)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = briefLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        state.puzzle.brief.forEachIndexed { index, clue ->
            val status = state.status(clue)
            val (glyph, color) = when (status) {
                ClueStatus.PENDING -> "○" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                ClueStatus.SATISFIED -> "✓" to ReyonPalette.Satisfied
                ClueStatus.VIOLATED -> "✗" to ReyonPalette.Violated
            }
            val text = ReyonText.clue(res, state.puzzle, clue)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (index == highlightClue) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onToggle(index) }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .semantics { contentDescription = "$briefLabel: $glyph $text" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = glyph, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == ClueStatus.VIOLATED) ReyonPalette.Violated else MaterialTheme.colorScheme.onSurface.copy(alpha = if (status == ClueStatus.SATISFIED) 0.6f else 0.9f),
                )
            }
        }
    }
}

@Composable
private fun HintLine(state: ReyonState, hint: ReyonHint?) {
    if (hint == null) return
    val res = LocalContext.current.resources
    val text = when (hint) {
        is ReyonHint.Wrong -> appString(R.string.reyon_hint_wrong_fmt, ReyonText.kind(res, state.puzzle.products[hint.product].kind))
        is ReyonHint.Place -> appString(
            R.string.reyon_hint_place_fmt,
            ReyonText.kind(res, state.puzzle.products[hint.product].kind),
            ReyonText.shelfAt(res, hint.row, state.puzzle.rows),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ReyonPalette.HintRing,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
    val res = LocalContext.current.resources
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val trayLabel = stringResource(R.string.reyon_tray_label)
    val pending = state.puzzle.products.filter { !state.isPlaced(it.id) }
    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = if (pending.isEmpty()) stringResource(R.string.reyon_tray_empty) else trayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (p in pending) {
                val name = ReyonText.kind(res, p.kind)
                val desc = "$trayLabel: " + appString(R.string.reyon_tray_item_fmt, name, p.facings)
                val isSelected = p.id == selected
                val ring = when {
                    isSelected -> ReyonPalette.SelectedRing
                    p.id == wrongHint -> ReyonPalette.HintRing
                    p.id in highlighted -> ReyonPalette.HighlightRing
                    else -> Color.Transparent
                }
                Surface(
                    onClick = { onSelect(p.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, ring),
                    modifier = Modifier.semantics { contentDescription = desc },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(brandColor(p.brand)))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = buildString {
                                append("×${p.facings} ")
                                repeat(p.size) { append('•') }
                                if (p.premium) append(" ★")
                                if (p.heavy) append(" ▼")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(canUndo: Boolean, canRemove: Boolean, onUndo: () -> Unit, onRemove: () -> Unit, onHint: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.weight(1f)) {
            ActionLabel(stringResource(R.string.undo))
        }
        OutlinedButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.weight(1f)) {
            ActionLabel(stringResource(R.string.reyon_remove))
        }
        Button(onClick = onHint, modifier = Modifier.weight(1f)) {
            ActionLabel(stringResource(R.string.reyon_hint))
        }
    }
}

// ---------------------------------------------------------------------------
// Kartlar (ortak parçalar Denetim'de de kullanılır)
// ---------------------------------------------------------------------------

@Composable
internal fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 8.dp),
        )
    }
}

/**
 * Dört tür çipi tek satıra bu genişliğin altında sığmıyor ("Denetim" 360 dp'de
 * kırpılıyordu). Kart içi genişlik 411 dp'de 299 dp, 360 dp'de 248 dp.
 */
internal val KIND_CHIPS_ONE_ROW_MIN = 280.dp

private fun kindLabel(kind: ReyonKind): Int = when (kind) {
    ReyonKind.PUZZLE -> R.string.reyon_kind_puzzle
    ReyonKind.AUDIT -> R.string.reyon_kind_audit
    ReyonKind.SALES -> R.string.reyon_kind_sales
    ReyonKind.ORDER -> R.string.reyon_kind_order
}

/** Tür seçimi: Diziliş / Denetim / Satış / Sipariş. Geniş kartta tek sıkı satır, dar kartta iki satır. */
@Composable
internal fun KindChips(kind: ReyonKind, onKind: (ReyonKind) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth < KIND_CHIPS_ONE_ROW_MIN) 2 else 4
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (rowKinds in ReyonKind.entries.chunked(perRow)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in rowKinds) {
                        Chip(stringResource(kindLabel(k)), kind == k, Modifier.weight(1f), compact = perRow == 4) { onKind(k) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModeAndLevelChips(mode: ReyonMode, level: ReyonLevel, onMode: (ReyonMode) -> Unit, onLevel: (ReyonLevel) -> Unit) {
    val res = LocalContext.current.resources
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.mode_daily), mode == ReyonMode.DAILY, Modifier.weight(1f)) { onMode(ReyonMode.DAILY) }
        Chip(stringResource(R.string.mode_free), mode == ReyonMode.FREE, Modifier.weight(1f)) { onMode(ReyonMode.FREE) }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in ReyonLevel.entries) {
            Chip(ReyonText.level(res, l), level == l, Modifier.weight(1f)) { onLevel(l) }
        }
    }
}

@Composable
private fun MenuCard(
    level: ReyonLevel,
    mode: ReyonMode,
    records: Map<ReyonLevel, ReyonStore.Record>,
    bestTime: Int,
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
        KindChips(kind = ReyonKind.PUZZLE, onKind = onKind)
        Text(
            text = stringResource(R.string.reyon_intro),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        ModeAndLevelChips(mode = mode, level = level, onMode = onMode, onLevel = onLevel)
        Text(
            text = appString(R.string.reyon_level_desc_fmt, level.rows, level.cols, level.products.first, level.products.last),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        val record = records[level]
        val info = when {
            mode == ReyonMode.DAILY && record != null ->
                appString(R.string.reyon_daily_done_fmt, formatTime(record.time), record.hints)
            mode == ReyonMode.DAILY -> stringResource(R.string.reyon_daily_desc)
            bestTime > 0 -> appString(R.string.reyon_best_fmt, formatTime(bestTime))
            else -> stringResource(R.string.reyon_free_desc)
        }
        Text(
            text = info,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (mode == ReyonMode.DAILY && record != null) R.string.reyon_play_again else R.string.reyon_start))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exit_app))
        }
    }
}

@Composable
private fun SolvedCard(
    state: ReyonState,
    result: ReyonResult,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    onExit: () -> Unit,
) {
    val res = LocalContext.current.resources
    val time = formatTime(result.time)
    val details = appString(R.string.reyon_result_fmt, ReyonText.level(res, state.puzzle.level), result.hints)
    OverlayCard {
        Text(
            text = stringResource(R.string.congrats),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        if (result.record) {
            Text(
                text = stringResource(R.string.new_record),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        ShareButton(
            ShareContent(
                headline = stringResource(R.string.share_solved),
                details = listOf(details, appString(R.string.time_fmt, time), modeShareLabel(result.daily, result.day)),
                board = reyonPainter(state, res),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.same_difficulty))
        }
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reyon_to_menu))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exit_app))
        }
    }
}
