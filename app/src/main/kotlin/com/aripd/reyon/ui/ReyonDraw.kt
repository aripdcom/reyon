package com.aripd.reyon.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aripd.reyon.engine.Product
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.ui.common.CanvasIcons
import com.aripd.reyon.ui.common.drawIcon
import com.aripd.reyon.ui.theme.Reyon
import com.aripd.reyon.ui.theme.ReyonFonts
import com.aripd.reyon.ui.theme.ReyonTokens
import kotlin.math.abs

/**
 * Dört modun ortak raf çizimi. Görsel dil (1.1.0): raf gerçek raf gibi —
 *
 * - Ambalaj rengi markayı, ambalaj boyu ürünün boyunu taşır; ürün adı ambalajın
 *   üstünde, markanın baş harfi sol üst köşede.
 * - Her rafın altında etiket rayı: ürünün raf etiketinde kategori karesi ve kodu,
 *   talep çubukları, göz hizası ve ağır rozetleri; Satış'ta katkı, Sipariş'te
 *   stok değeri.
 * - Sabit (planogramda yeri verili) ürün raptiyeyle işaretlenir.
 *
 * Tuval CompositionLocal okuyamadığı için renkler ve yazı aileleri temadan bir kez
 * alınır ([rememberShelfColors]) ve çizim işlevlerine geçer.
 */
@Immutable
internal class ShelfColors(
    val tokens: ReyonTokens,
    /** Brif satırına dokununca kuralın ürünlerini saran renk. */
    val highlight: Color,
    /** Ambalajdaki ürün adının yazısı. */
    val packFamily: FontFamily,
    /** Etiketteki rozet yazısının ailesi (arayüzünki). */
    val uiFamily: FontFamily?,
) {
    val packText: Color get() = Color.White
    val packShade: Color = Color.Black.copy(alpha = if (tokens.isDark) 0.22f else 0.18f)
}

@Composable
internal fun rememberShelfColors(): ShelfColors {
    val tokens = Reyon.tokens
    val highlight = MaterialTheme.colorScheme.primary
    val uiFamily = MaterialTheme.typography.labelSmall.fontFamily
    // Arapça ürün adı Arapça Plex'le; öbür diller dar Plex'le ambalaja sığar.
    val arabic = appLocale().language == "ar"
    return remember(tokens, highlight, uiFamily, arabic) {
        ShelfColors(tokens, highlight, if (arabic) ReyonFonts.SansArabic else ReyonFonts.Condensed, uiFamily)
    }
}

/**
 * Raf geometrisi (piksel). Her raf bandı yukarıdan aşağı: hava, ambalaj bölgesi,
 * çıta, etiket rayı. Ambalaj bölgesi en büyük boyun yüksekliği; küçük boylar
 * [PACK_HEIGHTS] oranında kısa ve çıtaya oturur. Gözler raf ünitesinin yan
 * payından ([side]) sonra başlar.
 *
 * Dokunma eşlemesi ([colAt], [rowAt]) de buradan: çizim ile dokunuş aynı ölçüyü
 * kullanır, tuval basıklaşsa da eşleme bozulmaz.
 */
internal class ShelfGeom(
    val width: Float,
    val height: Float,
    val rows: Int,
    val cols: Int,
    dp: Float,
    rail: Boolean = true,
) {
    val side = 6f * dp
    val cw = (width - 2f * side) / cols
    val sh = height / rows
    val railH = if (rail) (sh * 0.22f).coerceIn(13f * dp, 22f * dp).coerceAtMost(sh * 0.34f) else 0f
    val plank = (sh * 0.06f).coerceIn(2.5f * dp, 6f * dp)
    val air = (sh * 0.07f).coerceIn(2f * dp, 8f * dp)
    val gap = (cw * 0.05f).coerceIn(1.5f * dp, 4f * dp)
    val zone = sh - railH - plank - air
    val corner = (cw * 0.08f).coerceIn(2f * dp, 5f * dp)

    /** Ambalajların oturduğu çizgi: çıtanın üstü. */
    fun base(row: Int) = row * sh + air + zone
    fun x(col: Int) = side + col * cw + gap / 2f
    fun w(facings: Int) = facings * cw - gap
    fun packH(size: Int) = zone * PACK_HEIGHTS[(size - 1).coerceIn(0, 2)]
    fun top(row: Int, size: Int) = base(row) - packH(size)
    fun railTop(row: Int) = base(row) + plank
    fun colAt(px: Float) = ((px - side) / cw).toInt().coerceIn(0, cols - 1)
    fun rowAt(py: Float) = (py / sh).toInt().coerceIn(0, rows - 1)

    companion object {
        /** Boy 1, 2, 3'ün ambalaj yüksekliği, en büyüğün oranı olarak. */
        val PACK_HEIGHTS = floatArrayOf(0.70f, 0.85f, 1f)
    }
}

/** Bloğa sığdırılmış ad: ölçülmüş yerleşim ve gerekirse yatay sıkıştırma oranı (1 = yok). */
internal class BlockLabel(val layout: TextLayoutResult, val scaleX: Float) {
    val width: Float get() = layout.size.width * scaleX
    val height: Float get() = layout.size.height.toFloat()
    val sizeSp: Float get() = layout.layoutInput.style.fontSize.value
    val lines: Int get() = layout.lineCount
    val ellipsized: Boolean get() = layout.isLineEllipsized(layout.lineCount - 1)
}

/**
 * Ölçülmüş yazılar; önbellekli.
 *
 * [fit] ürün adını ambalaja sığdırır: en büyük puntodan başlayarak önce tek satır,
 * sığmazsa ortaya en yakın boşluktan iki satır denenir; punto [minSp]'ye kadar
 * iner. Hiçbiri sığmazsa en küçük puntoda tek satır yatay olarak en çok
 * [MIN_CONDENSE] oranına kadar sıkıştırılır; o da yetmezse üç nokta.
 * Böylece "Bulaşık deterjanı" gibi uzun adlar tek yüzlü gözde iki satıra
 * iner, "Yumuşatıcı" gibi tek kelimeler hafifçe daralır, kırpma en son çaredir.
 *
 * Ad yazısı ambalajınki gibi dar ([family], varsayılan IBM Plex Sans Condensed);
 * raf etiketindeki kod ve değerler eş genişlikli ([mono]).
 */
internal class BlockLabeler(
    private val measurer: TextMeasurer,
    private val family: FontFamily = ReyonFonts.Condensed,
) {
    private val labels = HashMap<String, TextLayoutResult>()
    private val fits = HashMap<String, BlockLabel>()

    /** Tek satırlık, sabit puntolu eş genişlikli yazı (değerler için); sığmazsa üç nokta. */
    fun label(text: String, maxWidth: Float, sizeSp: Float, color: Color): TextLayoutResult =
        labels.getOrPut("L|$text|${maxWidth.toInt()}|$sizeSp|${color.value}") {
            measurer.measure(
                AnnotatedString(text),
                style = TextStyle(fontSize = sizeSp.sp, fontFamily = ReyonFonts.Mono, fontWeight = FontWeight.SemiBold, color = color),
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
            )
        }

    /** Tek satır, sınırsız genişlikte eş genişlikli yazı; ölçüsüne bakıp yerleştirmek için. */
    fun mono(text: String, sizeSp: Float, color: Color): TextLayoutResult =
        labels.getOrPut("M|$text|$sizeSp|${color.value}") {
            measurer.measure(
                AnnotatedString(text),
                style = TextStyle(fontSize = sizeSp.sp, fontFamily = ReyonFonts.Mono, fontWeight = FontWeight.SemiBold, color = color),
                softWrap = false,
                maxLines = 1,
            )
        }

    /** Tek satır, sınırsız genişlikte arayüz yazısı (rozet adı, sapma türü). */
    fun ui(text: String, sizeSp: Float, color: Color, uiFamily: FontFamily?): TextLayoutResult =
        labels.getOrPut("U|$text|$sizeSp|${color.value}|${uiFamily.hashCode()}") {
            measurer.measure(
                AnnotatedString(text),
                style = TextStyle(fontSize = sizeSp.sp, fontFamily = uiFamily, fontWeight = FontWeight.SemiBold, color = color),
                softWrap = false,
                maxLines = 1,
            )
        }

    fun fit(text: String, maxWidth: Float, maxHeight: Float, maxSp: Float, minSp: Float, color: Color): BlockLabel =
        fits.getOrPut("$text|${maxWidth.toInt()}|${maxHeight.toInt()}|$maxSp|$minSp|${color.value}") {
            compute(text, maxWidth, maxHeight, maxSp, minSp, color)
        }

    private fun compute(text: String, maxWidth: Float, maxHeight: Float, maxSp: Float, minSp: Float, color: Color): BlockLabel {
        val w = maxWidth.toInt().coerceAtLeast(1)
        val twoLines = splitForTwoLines(text)
        var sp = maxSp
        while (sp >= minSp - 0.01f) {
            fitting(text, sp, w, maxHeight, 1, color)?.let { return BlockLabel(it, 1f) }
            if (twoLines != null) fitting(twoLines, sp, w, maxHeight, 2, color)?.let { return BlockLabel(it, 1f) }
            sp -= 1f
        }
        val natural = measurer.measure(
            AnnotatedString(text),
            style = style(minSp, color),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
        )
        val scale = maxWidth / natural.size.width.coerceAtLeast(1)
        if (natural.size.height <= maxHeight && scale >= MIN_CONDENSE) return BlockLabel(natural, scale)
        val cut = measurer.measure(
            AnnotatedString(text),
            style = style(minSp, color),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = w),
        )
        return BlockLabel(cut, 1f)
    }

    private fun fitting(text: String, sp: Float, w: Int, maxHeight: Float, lines: Int, color: Color): TextLayoutResult? {
        val l = measurer.measure(
            AnnotatedString(text),
            style = style(sp, color),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = lines,
            constraints = Constraints(maxWidth = w),
        )
        return l.takeIf { !it.didOverflowWidth && it.lineCount == lines && it.size.height <= maxHeight }
    }

    private fun style(sp: Float, color: Color) = TextStyle(
        fontSize = sp.sp,
        lineHeight = (sp * LINE_HEIGHT).sp,
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
    )

    companion object {
        const val MIN_CONDENSE = 0.72f
        const val LINE_HEIGHT = 1.08f

        /** Ortaya en yakın boşluğu satır sonu yapar; boşluk yoksa null. */
        fun splitForTwoLines(text: String): String? {
            val mid = text.length / 2
            var best = -1
            for (i in text.indices) {
                if (text[i] == ' ' && (best < 0 || abs(i - mid) < abs(best - mid))) best = i
            }
            return if (best <= 0 || best >= text.length - 1) null else text.substring(0, best) + "\n" + text.substring(best + 1)
        }
    }
}

/** Ürün adı puntosu (sp): bölge yüksekliğinden türetilir, bu aralıkta tutulur. */
internal const val NAME_MIN_SP = 8f
internal const val NAME_MAX_SP = 13f

/** Raf ünitesi: zemin, her rafta çıta ve etiket rayı. */
internal fun DrawScope.drawGondola(g: ShelfGeom, c: ShelfColors) {
    drawRect(c.tokens.gondola)
    for (r in 0 until g.rows) {
        drawRect(c.tokens.plank, topLeft = Offset(0f, g.base(r)), size = Size(g.width, g.plank))
        if (g.railH > 0f) drawRect(c.tokens.rail, topLeft = Offset(0f, g.railTop(r)), size = Size(g.width, g.railH))
    }
}

/** Boş gözler: kesikli çerçeve. [occupied] dolu gözlerin maskesi (satır × sütun + sütun). */
internal fun DrawScope.drawEmptySlots(g: ShelfGeom, occupied: Int, c: ShelfColors) {
    if (g.zone <= 0f) return
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
    for (r in 0 until g.rows) for (col in 0 until g.cols) {
        if (occupied and (1 shl (r * g.cols + col)) != 0) continue
        drawRoundRect(
            c.tokens.emptySlot,
            topLeft = Offset(g.x(col), g.top(r, 3)),
            size = Size(g.w(1), g.zone),
            cornerRadius = CornerRadius(g.corner, g.corner),
            style = stroke,
        )
    }
}

/** Ambalajın dış çizgisi: üst köşeler yuvarlak, alt köşeler çıtaya oturur. */
private fun packPath(g: ShelfGeom, left: Float, top: Float, right: Float, bottom: Float): Path {
    val r = CornerRadius(g.corner, g.corner)
    val flat = CornerRadius(g.corner * 0.4f, g.corner * 0.4f)
    return Path().apply { addRoundRect(RoundRect(left, top, right, bottom, r, r, flat, flat)) }
}

/**
 * Bir ürünün ambalajı: marka rengi, boyu ürün boyu, üstünde sığdırılmış ad, sol
 * üstte markanın baş harfi ([mark]), sabitse sağ üstte raptiye. [stock] 1'in
 * altındaysa ambalajın o kadarı dolu çizilir (Sipariş: raftaki stok), kalanı
 * soluk kalır. [dim] ambalajı soldurur (Satış'ta hedef dizilişin gösterimi).
 */
internal fun DrawScope.drawPack(
    g: ShelfGeom,
    labeler: BlockLabeler,
    c: ShelfColors,
    name: String,
    mark: String?,
    product: Product,
    facings: Int,
    row: Int,
    col: Int,
    pinned: Boolean = false,
    dim: Boolean = false,
    stock: Float = 1f,
) {
    if (g.zone <= 0f) return
    val x = g.x(col)
    val w = g.w(facings)
    val h = g.packH(product.size)
    val y = g.base(row) - h
    val shape = packPath(g, x, y, x + w, y + h)
    val color = c.tokens.pack(product.brand).let { if (dim) it.copy(alpha = 0.5f) else it }
    if (stock < 1f) {
        drawPath(shape, color.copy(alpha = color.alpha * 0.28f))
        val level = h * stock.coerceIn(0f, 1f)
        clipRect(left = x, top = y + h - level, right = x + w, bottom = y + h) { drawPath(shape, color) }
    } else {
        drawPath(shape, color)
    }
    val shade = minOf(4.dp.toPx(), h * 0.12f)
    drawRect(c.packShade, topLeft = Offset(x, y + h - shade), size = Size(w, shade))

    // Üst şerit: marka harfi ve raptiye; yer yoksa ikisi de çizilmez, ad öne geçer.
    val strip = 11.dp.toPx()
    val roomForStrip = h >= 30.dp.toPx() && w >= 26.dp.toPx()
    val textColor = c.packText.copy(alpha = if (dim) 0.7f else 1f)
    if (roomForStrip && mark != null) {
        val m = labeler.mono(mark, 8.5f, textColor.copy(alpha = 0.8f))
        drawText(m, topLeft = Offset(x + 4.dp.toPx(), y + 2.dp.toPx()))
    }
    if (pinned) {
        val size = if (roomForStrip) 10.dp.toPx() else minOf(9.dp.toPx(), h * 0.45f)
        drawIcon(CanvasIcons.Pin, x + w - size / 2f - 3.dp.toPx(), y + size / 2f + 2.dp.toPx(), size, textColor, strokeUnits = 2.4f)
    }

    val top = y + if (roomForStrip) strip else maxOf(1.5.dp.toPx(), h * 0.06f)
    val zone = (y + h - shade) - top
    val margin = maxOf(3.dp.toPx(), g.cw * 0.04f)
    if (zone <= 0f) return
    val maxSp = (zone * 0.42f / density).coerceIn(NAME_MIN_SP, NAME_MAX_SP)
    val label = labeler.fit(name, w - 2 * margin, zone, maxSp, NAME_MIN_SP, textColor)
    drawLabel(label, x + w / 2f, top + zone / 2f)
}

/** Ambalajın çevresine halka: seçim (sarı), kuralın ürünleri, ihlal. */
internal fun DrawScope.drawPackRing(g: ShelfGeom, product: Product, facings: Int, row: Int, col: Int, color: Color, width: Float = 3.dp.toPx()) {
    if (g.zone <= 0f) return
    val out = width / 2f + 1.dp.toPx()
    val x = g.x(col) - out
    val y = g.top(row, product.size) - out
    drawRoundRect(
        color,
        topLeft = Offset(x, y),
        size = Size(g.w(facings) + 2 * out, g.packH(product.size) + out + width / 2f),
        cornerRadius = CornerRadius(g.corner + out, g.corner + out),
        style = Stroke(width = width),
    )
}

/** Etiketin yanında gösterilecek rozet: göz hizası (yüksek marjlı) ve ağır ürün. */
internal class LabelBadges(val eye: Boolean, val heavy: Boolean, val text: String?)

/**
 * Ürünün raf etiketi, rafın altındaki rayda: kategori karesi ve kodu, sonra
 * sığdıkça talep çubukları, rozet simgesi ve rozetin adı. [value] verilirse
 * (Satış'ta katkı, Sipariş'te stok) sağa yaslanır ve talep çubuklarının önüne
 * geçer. [highlighted] etiketi dikkat sarısına boyar (seçili ürün).
 */
internal fun DrawScope.drawShelfLabel(
    g: ShelfGeom,
    labeler: BlockLabeler,
    c: ShelfColors,
    product: Product,
    facings: Int,
    row: Int,
    col: Int,
    code: String,
    badges: LabelBadges,
    value: String? = null,
    valueColor: Color? = null,
    showDemand: Boolean = true,
    highlighted: Boolean = false,
) {
    if (g.railH <= 0f) return
    val lh = g.railH * 0.72f
    val lx = g.x(col)
    val lw = g.w(facings)
    val ly = g.railTop(row) + (g.railH - lh) / 2f
    val paper = if (highlighted) c.tokens.attention else c.tokens.label
    val ink = if (highlighted) c.tokens.onAttention else c.tokens.onLabel
    drawRoundRect(paper, topLeft = Offset(lx, ly), size = Size(lw, lh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))

    val pad = minOf(4.dp.toPx(), lw * 0.06f)
    val gap = 3.dp.toPx()
    val sp = (lh * 0.62f / density).coerceIn(6.5f, 10f)
    val cy = ly + lh / 2f
    var left = lx + pad
    var right = lx + lw - pad

    val swatch = minOf(lh * 0.45f, 7.dp.toPx())
    if (right - left < swatch) return
    drawRoundRect(
        c.tokens.category(product.category),
        topLeft = Offset(left, cy - swatch / 2f),
        size = Size(swatch, swatch),
        cornerRadius = CornerRadius(swatch * 0.25f, swatch * 0.25f),
    )
    left += swatch + gap * 0.8f

    val codeText = labeler.mono(code, sp, ink)
    if (left + codeText.size.width > right) return
    drawText(codeText, topLeft = Offset(left, cy - codeText.size.height / 2f))
    left += codeText.size.width + gap

    if (value != null) {
        val v = labeler.mono(value, sp, valueColor ?: ink)
        if (right - v.size.width >= left) {
            drawText(v, topLeft = Offset(right - v.size.width, cy - v.size.height / 2f))
            right -= v.size.width + gap
        }
    }

    if (showDemand) {
        val barW = maxOf(1.5.dp.toPx(), lh * 0.18f)
        val barGap = maxOf(1.dp.toPx(), barW * 0.5f)
        val barH = lh * 0.5f
        val need = 3 * barW + 2 * barGap
        if (left + need <= right) {
            for (i in 0 until 3) {
                drawRoundRect(
                    if (i < product.demand) ink else c.tokens.demandOff,
                    topLeft = Offset(left + i * (barW + barGap), cy - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW * 0.3f, barW * 0.3f),
                )
            }
            left += need + gap
        }
    }

    val icon = lh * 0.7f
    var drewIcon = false
    for ((show, path) in listOf(badges.eye to CanvasIcons.Eye, badges.heavy to CanvasIcons.Heavy)) {
        if (!show || left + icon > right) continue
        drawIcon(path, left + icon / 2f, cy, icon, ink, strokeUnits = 2.4f)
        left += icon + gap * 0.6f
        drewIcon = true
    }
    val text = badges.text
    if (drewIcon && text != null) {
        val t = labeler.ui(text, (sp - 0.5f).coerceAtLeast(6.5f), ink.copy(alpha = 0.85f), c.uiFamily)
        if (left + t.size.width <= right) drawText(t, topLeft = Offset(left, cy - t.size.height / 2f))
    }
}

/** Etiketi (cx, cy) merkezli çizer; sıkıştırılmışsa yatayda ölçekler. */
internal fun DrawScope.drawLabel(label: BlockLabel, cx: Float, cy: Float) {
    val topLeft = Offset(cx - label.layout.size.width / 2f, cy - label.layout.size.height / 2f)
    if (label.scaleX < 1f) {
        scale(scaleX = label.scaleX, scaleY = 1f, pivot = Offset(cx, cy)) { drawText(label.layout, topLeft = topLeft) }
    } else {
        drawText(label.layout, topLeft = topLeft)
    }
}

/** Göz maskesindeki her satır koşusunun kutusu (raf bandının ambalaj bölgesi). */
private inline fun ShelfGeom.forEachRun(mask: Int, block: (row: Int, from: Int, to: Int) -> Unit) {
    for (r in 0 until rows) {
        var c = 0
        while (c < cols) {
            if (mask and (1 shl (r * cols + c)) == 0) {
                c++
                continue
            }
            var end = c
            while (end + 1 < cols && mask and (1 shl (r * cols + end + 1)) != 0) end++
            block(r, c, end)
            c = end + 1
        }
    }
}

/** Göz maskesindeki her satır koşusunun etrafına çerçeve (Denetim: bulunan ya da yanlış işaret). */
internal fun DrawScope.drawMaskRing(g: ShelfGeom, mask: Int, color: Color, width: Float = 3.dp.toPx()) {
    if (g.zone <= 0f) return
    g.forEachRun(mask) { r, from, to ->
        val x = g.x(from) - width / 2f
        val y = g.top(r, 3) - width / 2f
        drawRoundRect(
            color,
            topLeft = Offset(x, y),
            size = Size(g.x(to) + g.w(1) - g.x(from) + width, g.zone + width),
            cornerRadius = CornerRadius(g.corner + width / 2f, g.corner + width / 2f),
            style = Stroke(width = width),
        )
    }
}

/**
 * Bulunan sapmanın türü: maskenin ilk koşusunun ortasında beyaz hap, içinde bayrak
 * ve türün adı. Ad sığmazsa yalnız bayrak kalır.
 */
internal fun DrawScope.drawDeviationFlag(g: ShelfGeom, labeler: BlockLabeler, c: ShelfColors, mask: Int, text: String) {
    if (g.zone <= 0f) return
    var done = false
    g.forEachRun(mask) { r, from, to ->
        if (done) return@forEachRun
        done = true
        val left = g.x(from)
        val width = g.x(to) + g.w(1) - left
        val h = minOf(16.dp.toPx(), g.zone * 0.42f)
        val sp = (h * 0.62f / density).coerceIn(7f, 10.5f)
        val icon = h * 0.7f
        val padX = 5.dp.toPx()
        val t = labeler.ui(text, sp, c.tokens.labelBad, c.uiFamily)
        val full = padX + icon + 3.dp.toPx() + t.size.width + padX
        val pillW = if (full <= width - 4.dp.toPx()) full else minOf(icon + 2 * padX, width)
        val cx = left + width / 2f
        val cy = g.base(r) - g.zone * 0.5f
        drawRoundRect(
            c.tokens.label,
            topLeft = Offset(cx - pillW / 2f, cy - h / 2f),
            size = Size(pillW, h),
            cornerRadius = CornerRadius(h / 2f, h / 2f),
        )
        drawIcon(CanvasIcons.Flag, cx - pillW / 2f + padX + icon / 2f, cy, icon, c.tokens.labelBad, strokeUnits = 2.4f)
        if (pillW == full) drawText(t, topLeft = Offset(cx - pillW / 2f + padX + icon + 3.dp.toPx(), cy - t.size.height / 2f))
    }
}
