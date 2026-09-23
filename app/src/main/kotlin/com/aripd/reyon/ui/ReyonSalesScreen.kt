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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonSales
import com.aripd.reyon.engine.ReyonSalesState
import com.aripd.reyon.engine.SalesRule
import com.aripd.reyon.engine.SalesRules
import com.aripd.reyon.engine.SalesScore
import com.aripd.reyon.engine.SalesScorer
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.platform.Sfx
import com.aripd.reyon.platform.ShareContent
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.platform.appString
import com.aripd.reyon.ui.common.ActionBar
import com.aripd.reyon.ui.common.ButtonKind
import com.aripd.reyon.ui.common.FitText
import com.aripd.reyon.ui.common.GameTopBar
import com.aripd.reyon.ui.common.OverlayCard
import com.aripd.reyon.ui.common.ReyonButton
import com.aripd.reyon.ui.common.ReyonCard
import com.aripd.reyon.ui.common.SectionHeader
import com.aripd.reyon.ui.common.Segmented
import com.aripd.reyon.ui.common.ShareButton
import com.aripd.reyon.ui.common.kpiLabelStyle
import com.aripd.reyon.ui.common.modeShareLabel
import com.aripd.reyon.ui.common.monoStyle
import com.aripd.reyon.ui.theme.Reyon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import java.util.Locale

/** Katkı: işaretli, Latin rakamla ("+7", "−8" değil "-8"). */
internal fun signed(value: Int): String = String.format(Locale.ROOT, "%+d", value)

/** Satış modu: serbest diziliş, canlı raf verimi, uzmana oranla sonuç. */
@Composable
internal fun ReyonSalesContent(
    onCompleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReyonSalesViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val sound = LocalSound.current

    // Aynı sonuç ikinci kez sayılmasın (bkz. ReyonPuzzleContent).
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
    BackHandler { onBack() }

    val st = state
    var howTo by rememberHowTo(ReyonKind.SALES)
    @Suppress("UNUSED_VARIABLE")
    val tick = version
    val score = remember(st, version) { st?.score() }
    val shownLevel = st?.sales?.level ?: level

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(
            title = stringResource(R.string.reyon_kind_sales),
            subtitle = caseSubtitle(shownLevel, mode == ReyonMode.DAILY),
            onExit = onBack,
        ) {
            HowToButton { howTo = true }
        }
        YieldCard(score = score?.total, expert = st?.sales?.target)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
                        BreakdownLine(state = st, selected = selected)
                    }
                    // Kural paneli ile yerleştirilecek ürünler kalan yüksekliği paylaşıyor;
                    // "kalan" burada ölçülüyor, böylece üstteki döküm satırı da hesaba giriyor.
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Kolu yerleştirilecek ürün kalıp kalmadığı seçiyor.
                        //
                        // Ürün varken panel ağırlıksız, yani önce ölçülüyor: içeriği kadar
                        // yer alıyor, en çok panelCap kadar; ürünler kalanı alıp kendi içinde
                        // kayıyor. Ters kol (ürünler önce) 411 dp'de beşinci kuralın adını
                        // kırpıyordu, çünkü ürün listesi tura göre iki ya da üç sıra oluyor
                        // ve panelin payı onunla 240 ↔ 208 dp arasında oynuyordu.
                        //
                        // Ürün kalmayınca liste tek satırlık bir not ("Tüm ürünler rafta")
                        // çiziyor; orada panele tavan koymak altında ~83 dp boşluk bırakıyordu
                        // (cihazda ölçüldü, 480 ve 563 dp). O durumda kol Diziliş'inki gibi:
                        // liste doğal boyunu alıyor, panel kalanı. Tur bitince liste hiç
                        // çizilmiyor, o da bu kola düşüyor.
                        val trayPending = st.sales.products.any { !st.isPlaced(it.id) }
                        // maxHeight'a bağlı olan her şey sütunun dışında hesaplanmalı:
                        // ColumnScope da @LayoutScopeMarker taşıdığı için içeride
                        // BoxWithConstraintsScope örtülüyor. Modifier'ın kendisi ise
                        // tersine, içeride kurulmalı — weight bir ColumnScope uzantısı.
                        val panelMax = panelCap(maxHeight)
                        // Gövdeler yalnız panel tabanına sıkışmışken tek satıra iniyor.
                        // Ürünler bitince panel kalanın hepsini aldığından o sıkışma
                        // kalmıyor, gövdeler iki satıra dönüyor: kuralların okunduğu an
                        // orası.
                        val compactRules = trayPending && rulesAreCompact(maxHeight)
                        Column(modifier = Modifier.fillMaxSize()) {
                            RulesPanel(
                                score = score,
                                compact = compactRules,
                                modifier = (
                                    if (trayPending) Modifier.heightIn(max = panelMax)
                                    else Modifier.weight(1f, fill = false)
                                    ).testTag(REYON_PANEL_TAG),
                            )
                            if (!st.finished) {
                                ProductChips(
                                    products = st.sales.products.filter { !st.isPlaced(it.id) },
                                    selected = selected,
                                    onSelect = { id ->
                                        viewModel.select(id)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    ringOf = { null },
                                    detail = { p -> appString(R.string.sales_chip_fmt, p.facings, p.demand) },
                                    modifier = (if (trayPending) Modifier.weight(1f, fill = false) else Modifier)
                                        .testTag(REYON_TRAY_TAG),
                                )
                            }
                        }
                    }
                }
            }
            when {
                st == null -> OverlayCard {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reyon_sales_generating))
                }
                result != null && review == SalesReview.RESULT -> SalesResultCard(
                    state = st,
                    result = result!!,
                    onReopen = viewModel::reopen,
                    onReview = viewModel::setReview,
                    onRetry = viewModel::retry,
                    onBack = onBack,
                )
                howTo && result == null -> HowToCard(ReyonKind.SALES, shownLevel) { howTo = false }
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
                    text = stringResource(R.string.reyon_sales_finish),
                    onClick = {
                        if (viewModel.finish() != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = st.isComplete,
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Raf verimi kartı: puan, uzmanın puanı ve uzmana oran. Tek satır ve ince çubuk:
 * kısa ekranda kural paneline yer kalsın diye KPI satırından uzun değil.
 */
@Composable
private fun YieldCard(score: Int?, expert: Int?) {
    val tokens = Reyon.tokens
    val percent = if (score != null && expert != null && expert > 0) score * 100 / expert else null
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tokens.line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    FitText(
                        text = stringResource(R.string.reyon_sales_score_label).uppercase(appLocale()),
                        style = kpiLabelStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        Text(text = score?.toString() ?: "—", style = monoStyle(19f, 25f), modifier = Modifier.alignByBaseline())
                        if (expert != null) {
                            Text(
                                text = appString(R.string.report_per_expert_fmt, expert),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .alignByBaseline(),
                            )
                        }
                    }
                }
                if (percent != null) {
                    Text(
                        text = appString(R.string.percent_fmt, percent),
                        style = monoStyle(15f, 20f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ProgressBar(
                fraction = (percent ?: 0) / 100f,
                color = MaterialTheme.colorScheme.primary,
                height = 5.dp,
                modifier = Modifier.padding(top = 4.dp),
            )
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
    val colors = rememberShelfColors()
    val texts = rememberShelfTexts()
    val textMeasurer = rememberTextMeasurer()
    val labeler = remember(textMeasurer, colors.packFamily) { BlockLabeler(textMeasurer, colors.packFamily) }
    val names = remember(sales, res) { sales.products.map { ReyonText.kind(res, it.kind) } }
    val unplaced = sales.products.size - state.placedCount
    val desc = appString(R.string.reyon_sales_board_desc_fmt, rows, cols, unplaced, score.total)
    val target = remember(sales) { targetOf(sales) }
    val targetScore = remember(sales) { SalesScorer.score(sales.board, sales.products, target) }
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
        val contributions = if (showTarget) targetScore.byProduct else score.byProduct
        val placed = sales.products.mapNotNull { p ->
            if (showTarget) {
                Triple(p, sales.board.rowOf(target[p.id]), sales.board.colOf(target[p.id]))
            } else {
                state.placement(p.id)?.let { Triple(p, it.row, it.col) }
            }
        }
        drawEmptySlots(g, occupiedMask(sales.board, placed), colors)
        for ((p, row, col) in placed) {
            val c = contributions[p.id]
            drawPack(g, labeler, colors, names[p.id], texts.mark(p), p, p.facings, row, col)
            drawShelfLabel(
                g, labeler, colors, p, p.facings, row, col, texts.code(p), texts.badges(p),
                value = signed(c),
                valueColor = when {
                    c > 0 -> colors.tokens.labelOk
                    c < 0 -> colors.tokens.labelBad
                    else -> null
                },
                highlighted = !showTarget && p.id == selected,
            )
            if (!showTarget && p.id == selected) drawPackRing(g, p, p.facings, row, col, colors.tokens.attention)
        }
    }
}

/** Uzmanın dizilişinin ürün→göz dizisi (iyileştiricinin bulduğu). */
private fun targetOf(sales: ReyonSales): IntArray {
    val st = ReyonSalesState(sales)
    st.applyTarget()
    return st.snapshot()
}

@Composable
private fun BreakdownLine(state: ReyonSalesState, selected: Int) {
    val res = LocalContext.current.resources
    val text = if (selected >= 0 && state.isPlaced(selected)) {
        val p = state.sales.products[selected]
        val single = SalesScorer.score(state.sales.board, state.sales.products, state.snapshot())
        val position = SalesRules.position(p, state.placement(selected)!!.row, state.sales.rows)
        val parts = ArrayList<String>()
        parts += appString(R.string.reyon_sales_part_fmt, stringResource(R.string.reyon_sales_rule_position), position)
        val total = single.byProduct[selected]
        val rest = total - position
        if (rest != 0) parts += appString(R.string.reyon_sales_part_fmt, stringResource(R.string.reyon_sales_neighbors), rest)
        appString(R.string.reyon_sales_breakdown_fmt, ReyonText.kind(res, p.kind), parts.joinToString(" · "), total)
    } else if (selected >= 0) {
        val p = state.sales.products[selected]
        appString(R.string.reyon_sales_selected_fmt, ReyonText.kind(res, p.kind), p.demand)
    } else {
        stringResource(R.string.reyon_sales_pick_hint)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

/** Sonuçtan sonra: kendi dizilişin, uzmanınki ve rapora dönüş. */
@Composable
private fun ReviewBar(review: SalesReview, target: Int, onReview: (SalesReview) -> Unit) {
    Segmented(
        options = listOf(SalesReview.MINE, SalesReview.TARGET, SalesReview.RESULT),
        selected = review,
        onSelect = onReview,
        minHeight = 40.dp,
        modifier = Modifier.padding(top = 6.dp),
    ) { option, isSelected ->
        Text(
            text = when (option) {
                SalesReview.MINE -> stringResource(R.string.reyon_sales_review_mine)
                SalesReview.TARGET -> appString(R.string.reyon_sales_review_target_fmt, target)
                SalesReview.RESULT -> stringResource(R.string.reyon_sales_back_to_result)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RulesPanel(score: SalesScore, compact: Boolean, modifier: Modifier = Modifier) {
    val tokens = Reyon.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader(
            title = stringResource(R.string.reyon_sales_rules_label),
            trailing = appString(R.string.rules_total_fmt, score.total),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
        ReyonCard {
            // Kısa ekranda iki satır sınırı Konum kuralının son parçasını ("göz hizası
            // etiketli yalnız göz hizasında ×4") üç noktanın arkasına atıyor; cihazda
            // 360 dp'de ölçüldü. O çarpan başka hiçbir yerde yazılı değil. Satıra
            // dokununca gövde tam açılıyor — kapalı görünüm değişmediği için 140 dp'lik
            // tabanda üçüncü kuralın adı yerinde kalıyor, açılan satır panelin kendi
            // kaydırmasına taşıyor. Metni 14 dilde kısaltmaya gerek kalmıyor.
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
                // tahmin edilmiyor, yerleşimden okunuyor.
                var tasan by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (acik) tokens.brandWeak else Color.Transparent)
                        .clickable { acilan = if (acik) null else rule }
                        // Satır arası dar: beş kuralda her dp sayılır ve hiçbir metni
                        // kırpmadan kazanılır; iki satır sınırıyla birlikte üçüncü
                        // kuralın adını tabanın içine sokuyor.
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(name), style = MaterialTheme.typography.labelLarge)
                        // Gövdenin satır sayısı panelin payına bağlı. İki satır, uzun
                        // ekranda beş kuralın hepsini sığdırıyor. Panel tabanına (140 dp)
                        // sıkışmışsa tek satıra iniyor: iki satırla kaç kuralın adının
                        // okunduğu dile bağlı hâle geliyordu (cihazda 360×640 dp'de Türkçe
                        // üç, Almanca ve Fince iki kural), tek satırla üçüncü kuralın adı
                        // her dilde tabanın içinde kalıyor. Dokununca kalkıyor.
                        Text(
                            text = stringResource(desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = signed(value),
                        style = monoStyle(15f, 20f),
                        color = when {
                            value > 0 -> tokens.ok
                            value < 0 -> tokens.bad
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (rule != SalesRule.entries.last()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
                }
            }
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
    onBack: () -> Unit,
) {
    val res = LocalContext.current.resources
    val levelName = ReyonText.level(res, state.sales.level)
    val details = appString(R.string.reyon_sales_result_fmt, result.score, result.target, result.percent)
    val band = ComplianceBand.of(result.percent)
    OverlayCard {
        Text(
            text = stringResource(R.string.reyon_sales_done_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        ExpertMetric(
            label = stringResource(R.string.reyon_sales_score_label),
            value = result.score,
            expert = result.target,
            percent = result.percent,
        )
        if (result.score > result.target) {
            Text(
                text = stringResource(R.string.reyon_sales_beat_target),
                style = MaterialTheme.typography.titleSmall,
                color = Reyon.tokens.ok,
                textAlign = TextAlign.Center,
            )
        } else if (result.record) {
            PersonalBestPill()
        }
        ShareButton(
            ShareContent(
                headline = stringResource(R.string.reyon_sales_done_title),
                details = listOf(levelName, details, stringResource(band.label), modeShareLabel(result.daily, result.day)),
                board = salesPainter(state, res),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReyonButton(text = stringResource(R.string.reyon_sales_reopen), onClick = onReopen, modifier = Modifier.weight(1f))
            ReyonButton(text = stringResource(R.string.reyon_sales_show_target), onClick = { onReview(SalesReview.TARGET) }, modifier = Modifier.weight(1f))
        }
        ReportButtons(daily = result.daily, newLabel = R.string.report_new_case, onBack = onBack, onRetry = onRetry)
    }
}
