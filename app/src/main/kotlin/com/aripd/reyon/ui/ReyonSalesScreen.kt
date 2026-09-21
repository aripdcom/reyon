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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
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
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonSalesState
import com.aripd.reyon.engine.SalesRule
import com.aripd.reyon.engine.SalesScore
import com.aripd.reyon.ui.common.ActionLabel
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ScoreCard
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.formatScore
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.platform.appString

internal fun starsText(stars: Int): String = "★".repeat(stars) + "☆".repeat(3 - stars)

/** Satış modu: serbest diziliş, canlı puan, hedefe göre yıldız. */
@Composable
internal fun ReyonSalesContent(
    solved: Long,
    onCompleted: () -> Unit,
    onKind: (ReyonKind) -> Unit,
    onExit: () -> Unit,
    viewModel: ReyonSalesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
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
        mutableLongStateOf(state?.sales?.seed?.takeIf { result != null } ?: Long.MIN_VALUE)
    }
    val latestCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(result) {
        val seed = state?.sales?.seed ?: return@LaunchedEffect
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
    val score = remember(st, version) { st?.score() }

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
                label = stringResource(R.string.reyon_sales_score_label),
                value = score?.total?.let { formatScore(it.toLong()) } ?: "—",
                modifier = Modifier.weight(1f),
                highlight = true,
            )
            ScoreCard(
                label = stringResource(R.string.reyon_sales_target_label),
                value = st?.sales?.target?.let { formatScore(it.toLong()) } ?: "—",
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
            if (st != null && score != null) {
                val showTarget = result != null && review == SalesReview.TARGET
                val shelfH = shelfHeight(maxWidth, maxHeight, st.sales.cols / (st.sales.rows * 0.78f))
                Column(modifier = Modifier.fillMaxSize()) {
                    SalesCanvas(
                        state = st,
                        score = score,
                        version = version,
                        height = shelfH,
                        selected = selected,
                        showTarget = showTarget,
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
                            if (occupant >= 0 && !st.finished) {
                                viewModel.select(occupant)
                                if (viewModel.removeSelected()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    sound?.play(Sfx.DROP, volume = 0.4f, rate = 1.4f)
                                }
                            }
                        },
                    )
                    if (result != null && review != SalesReview.RESULT) {
                        ReviewBar(review = review, target = st.sales.target, onReview = viewModel::setReview)
                    } else {
                        BreakdownLine(state = st, score = score, selected = selected)
                    }
                    // Kural paneli ile tepsi kalan yüksekliği paylaşıyor; "kalan"
                    // burada ölçülüyor, böylece üstteki döküm satırı da hesaba giriyor.
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Kolu tepside yerleştirilecek ürün kalıp kalmadığı seçiyor.
                        //
                        // Ürün varken panel ağırlıksız, yani önce ölçülüyor: içeriği kadar
                        // yer alıyor, en çok panelCap kadar; tepsi kalanı alıp kendi içinde
                        // kayıyor. Ters kol (tepsi önce) 411 dp'de beşinci kuralın adını
                        // kırpıyordu, çünkü tepsi tura göre iki ya da üç sıra oluyor ve
                        // panelin payı onunla 240 ↔ 208 dp arasında oynuyordu.
                        //
                        // Ürün kalmayınca tepsi tek satırlık bir not ("Tüm ürünler rafta")
                        // çiziyor; orada panele tavan koymak altında ~83 dp boşluk bırakıyordu
                        // (cihazda ölçüldü, 480 ve 563 dp). O durumda kol Diziliş'inki gibi:
                        // tepsi doğal boyunu alıyor, panel kalanı. Tur bitince tepsi hiç
                        // çizilmiyor, o da bu kola düşüyor.
                        val trayPending = st.sales.products.any { !st.isPlaced(it.id) }
                        // maxHeight'a bağlı olan her şey sütunun dışında hesaplanmalı:
                        // ColumnScope da @LayoutScopeMarker taşıdığı için içeride
                        // BoxWithConstraintsScope örtülüyor. Modifier'ın kendisi ise
                        // tersine, içeride kurulmalı — weight bir ColumnScope uzantısı.
                        val panelMax = panelCap(maxHeight)
                        // Gövdeler yalnız panel tabanına sıkışmışken tek satıra iniyor.
                        // Tepsi boşalınca panel kalanın hepsini aldığından o sıkışma
                        // kalmıyor, gövdeler iki satıra dönüyor: kuralların okunduğu an
                        // orası.
                        val compactRules = trayPending && rulesAreCompact(maxHeight)
                        Column(modifier = Modifier.fillMaxSize()) {
                            RulesPanel(
                                score = score,
                                target = st.sales.target,
                                compact = compactRules,
                                modifier = (
                                    if (trayPending) Modifier.heightIn(max = panelMax)
                                    else Modifier.weight(1f, fill = false)
                                    ).testTag(REYON_PANEL_TAG),
                            )
                            if (!st.finished) {
                                SalesTray(
                                    state = st,
                                    version = version,
                                    selected = selected,
                                    modifier = (if (trayPending) Modifier.weight(1f, fill = false) else Modifier)
                                        .testTag(REYON_TRAY_TAG),
                                ) { id ->
                                    viewModel.select(id)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                }
            }
            when {
                st == null && generating -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_sales_generating))
                }
                st == null -> SalesMenuCard(
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
                result != null && review == SalesReview.RESULT -> SalesResultCard(
                    state = st,
                    result = result!!,
                    onReopen = viewModel::reopen,
                    onReview = viewModel::setReview,
                    onRetry = viewModel::retry,
                    onMenu = viewModel::toMenu,
                    onExit = onExit,
                )
            }
        }

        if (st != null && result == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.undo()
                    },
                    enabled = st.canUndo,
                    modifier = Modifier.weight(1f),
                ) {
                    ActionLabel(stringResource(R.string.undo))
                }
                OutlinedButton(
                    onClick = { if (viewModel.removeSelected()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    enabled = selected >= 0 && st.isPlaced(selected),
                    modifier = Modifier.weight(1f),
                ) {
                    ActionLabel(stringResource(R.string.reyon_remove))
                }
                Button(
                    onClick = {
                        if (viewModel.finish() != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = st.isComplete,
                    modifier = Modifier.weight(1f),
                ) {
                    ActionLabel(stringResource(R.string.reyon_sales_finish))
                }
            }
        }
    }
}

@Composable
private fun SalesCanvas(
    state: ReyonSalesState,
    score: SalesScore,
    version: Int,
    height: Dp,
    selected: Int,
    showTarget: Boolean,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
) {
    val sales = state.sales
    val rows = sales.rows
    val cols = sales.cols
    val res = LocalContext.current.resources
    val currentTap by rememberUpdatedState(onTap)
    val currentLong by rememberUpdatedState(onLongPress)
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer) { BlockLabeler(textMeasurer) }
    val path = remember { Path() }
    val unplaced = sales.products.size - state.placedCount
    val desc = appString(R.string.reyon_sales_board_desc_fmt, rows, cols, unplaced, score.total)
    val targetScore = remember(sales) { com.aripd.reyon.engine.SalesScorer.score(sales.board, sales.products, targetOf(sales)) }
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
        val at = if (showTarget) targetOf(sales) else null
        val contributions = if (showTarget) targetScore.byProduct else score.byProduct
        for (p in sales.products) {
            val row: Int
            val col: Int
            if (at != null) {
                row = sales.board.rowOf(at[p.id])
                col = sales.board.colOf(at[p.id])
            } else {
                val pl = state.placement(p.id) ?: continue
                row = pl.row
                col = pl.col
            }
            val ring = if (!showTarget && p.id == selected) ReyonPalette.SelectedRing else null
            // Katkı rozeti bloğun sağ altına, işaret şeridine çizilir; punto blok boyuyla ölçeklenir.
            val badge = labeler.label("%+d".format(contributions[p.id]), g.cw, (g.h * 0.2f / density).coerceIn(8f, 10f), ReyonPalette.BlockText)
            drawBlock(g, path, labeler, ReyonText.kind(res, p.kind), p, p.facings, row, col, ring = ring, dim = showTarget, badge = badge)
        }
    }
}

/** Hedef dizilişin ürün→göz dizisi (iyileştiricinin bulduğu). */
private fun targetOf(sales: com.aripd.reyon.engine.ReyonSales): IntArray {
    val st = ReyonSalesState(sales)
    st.applyTarget()
    return st.snapshot()
}

@Composable
private fun BreakdownLine(state: ReyonSalesState, score: SalesScore, selected: Int) {
    val res = LocalContext.current.resources
    val text = if (selected >= 0 && state.isPlaced(selected)) {
        val p = state.sales.products[selected]
        val single = com.aripd.reyon.engine.SalesScorer.score(state.sales.board, state.sales.products, state.snapshot())
        val parts = ArrayList<String>()
        parts += appString(R.string.reyon_sales_part_fmt, stringResource(R.string.reyon_sales_rule_position), com.aripd.reyon.engine.SalesRules.position(p, state.placement(selected)!!.row, state.sales.rows))
        val total = single.byProduct[selected]
        val rest = total - com.aripd.reyon.engine.SalesRules.position(p, state.placement(selected)!!.row, state.sales.rows)
        if (rest != 0) parts += appString(R.string.reyon_sales_part_fmt, stringResource(R.string.reyon_sales_neighbors), rest)
        appString(R.string.reyon_sales_breakdown_fmt, ReyonText.kind(res, p.kind), parts.joinToString(" · "), total)
    } else if (selected >= 0) {
        val p = state.sales.products[selected]
        appString(R.string.reyon_sales_selected_fmt, ReyonText.kind(res, p.kind), p.demand)
    } else {
        stringResource(R.string.reyon_sales_pick_hint)
    }
    @Suppress("UNUSED_VARIABLE")
    val unused = score.total
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Composable
private fun ReviewBar(review: SalesReview, target: Int, onReview: (SalesReview) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(stringResource(R.string.reyon_sales_review_mine), review == SalesReview.MINE, Modifier.weight(1f)) { onReview(SalesReview.MINE) }
        Chip(appString(R.string.reyon_sales_review_target_fmt, target), review == SalesReview.TARGET, Modifier.weight(1.2f)) { onReview(SalesReview.TARGET) }
        Chip(stringResource(R.string.reyon_sales_back_to_result), false, Modifier.weight(1f)) { onReview(SalesReview.RESULT) }
    }
}

@Composable
private fun RulesPanel(score: SalesScore, target: Int, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.reyon_sales_rules_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            val fill = MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.surfaceVariant
            val fraction = if (target > 0) (score.total.toFloat() / target).coerceIn(0f, 1f) else 0f
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape),
            ) {
                drawRect(track)
                drawRect(fill, size = Size(size.width * fraction, size.height))
            }
        }
        // Kısa ekranda iki satır sınırı Konum kuralının son parçasını ("★ yalnız göz
        // hizasında ×4") üç noktanın arkasına atıyor; cihazda 360 dp'de ölçüldü.
        // O çarpan başka hiçbir yerde yazılı değil: kurulum kartının özeti kuralları
        // sayıyor ama çarpan vermiyor ve ★ kuralını hiç anmıyor. Satıra dokununca
        // gövde tam açılıyor — kapalı görünüm değişmediği için 140 dp'lik tabanda
        // üçüncü kuralın adı yerinde kalıyor, açılan satır panelin kendi kaydırmasına
        // taşıyor. Metni 14 dilde kısaltmaya gerek kalmıyor, çarpanlar da duruyor.
        var acilan by remember { mutableStateOf<SalesRule?>(null) }
        for (rule in SalesRule.entries) key(rule) {
            val (name, desc) = when (rule) {
                SalesRule.POSITION -> R.string.reyon_sales_rule_position to R.string.reyon_sales_rule_position_desc
                SalesRule.COMPLEMENT -> R.string.reyon_sales_rule_complement to R.string.reyon_sales_rule_complement_desc
                SalesRule.CONFLICT -> R.string.reyon_sales_rule_conflict to R.string.reyon_sales_rule_conflict_desc
                SalesRule.CATEGORY -> R.string.reyon_sales_rule_category to R.string.reyon_sales_rule_category_desc
                SalesRule.BRAND -> R.string.reyon_sales_rule_brand to R.string.reyon_sales_rule_brand_desc
            }
            val value = score.of(rule)
            val acik = acilan == rule
            // Ok yalnız gövdesi gerçekten kırpılan satırda çıkıyor: hangi kuralın
            // kaç satır tuttuğu dile ve ekran genişliğine göre değişiyor, o yüzden
            // tahmin edilmiyor, yerleşimden okunuyor. 411 dp'de beş gövde de iki
            // satıra sığdığı için hiç ok görünmüyor.
            var tasan by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (acik) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else Color.Transparent,
                    )
                    .clickable { acilan = if (acik) null else rule }
                    // Satır arası 2 → 1 dp: beş kuralda 10 dp eder ve hiçbir metni
                    // kırpmadan kazanılır. Tek başına yetmez, iki satır sınırıyla
                    // birlikte üçüncü kuralın adını tabanın içine sokuyor.
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    // Gövdenin satır sayısı panelin payına bağlı. İki satır, uzun
                    // ekranda beş kuralın hepsini sığdırıyor. Panel tabanına (140 dp)
                    // sıkışmışsa tek satıra iniyor: iki satırla kaç kuralın adının
                    // okunduğu dile bağlı hâle geliyordu (cihazda 360×640 dp'de Türkçe
                    // üç, Almanca ve Fince iki kural), tek satırla üçüncü kuralın adı
                    // her dilde tabanın içinde kalıyor. Sınır beş kuralın hepsine
                    // konuyor ki panel dile göre oynamasın. Dokununca kalkıyor.
                    Text(
                        text = stringResource(desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = if (acik) Int.MAX_VALUE else if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!acik) tasan = it.hasVisualOverflow },
                    )
                }
                if (tasan || acik) {
                    Icon(
                        imageVector = if (acik) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        // Süsleme: ekran okuyucu gövdenin tamamını kırpılmışken de
                        // okuyor, satırın dokunma eylemini de kendisi duyuruyor.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = "%+d".format(value),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        value > 0 -> ReyonPalette.Satisfied
                        value < 0 -> ReyonPalette.Violated
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalesTray(
    state: ReyonSalesState,
    version: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val res = LocalContext.current.resources
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val trayLabel = stringResource(R.string.reyon_tray_label)
    val pending = state.sales.products.filter { !state.isPlaced(it.id) }
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
                Surface(
                    onClick = { onSelect(p.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, if (isSelected) ReyonPalette.SelectedRing else Color.Transparent),
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
                                append(appString(R.string.reyon_sales_demand_fmt, p.demand))
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
private fun SalesMenuCard(
    level: ReyonLevel,
    mode: ReyonMode,
    records: Map<ReyonLevel, ReyonStore.SalesRecord>,
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
        KindChips(kind = ReyonKind.SALES, onKind = onKind)
        Text(
            text = stringResource(R.string.reyon_sales_intro),
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
            mode == ReyonMode.DAILY && record != null -> {
                val stars = if (record.target > 0) when {
                    record.score >= record.target -> 3
                    record.score >= record.target * 0.9 -> 2
                    record.score >= record.target * 0.75 -> 1
                    else -> 0
                } else 0
                appString(R.string.reyon_sales_daily_done_fmt, record.score, record.target, starsText(stars))
            }
            mode == ReyonMode.DAILY -> stringResource(R.string.reyon_sales_daily_desc)
            bestPercent > 0 -> appString(R.string.reyon_sales_best_fmt, bestPercent)
            else -> stringResource(R.string.reyon_sales_free_desc)
        }
        Text(
            text = info,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (mode == ReyonMode.DAILY && record != null) R.string.reyon_play_again else R.string.reyon_sales_start))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exit_app))
        }
    }
}

@Composable
private fun SalesResultCard(
    state: ReyonSalesState,
    result: SalesResult,
    onReopen: () -> Unit,
    onReview: (SalesReview) -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    onExit: () -> Unit,
) {
    val res = LocalContext.current.resources
    val details = appString(R.string.reyon_sales_result_fmt, result.score, result.target, result.percent)
    val levelName = ReyonText.level(res, state.sales.level)
    OverlayCard {
        Text(
            text = stringResource(R.string.reyon_sales_done_title),
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
                text = stringResource(R.string.reyon_sales_beat_target),
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
        ShareButton(
            ShareContent(
                headline = appString(R.string.share_score_fmt, formatScore(result.score.toLong())),
                details = listOf(levelName, details, starsText(result.stars), modeShareLabel(result.daily, result.day)),
                board = salesPainter(state, res),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReopen, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reyon_sales_reopen))
            }
            OutlinedButton(onClick = { onReview(SalesReview.TARGET) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reyon_sales_show_target))
            }
        }
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
