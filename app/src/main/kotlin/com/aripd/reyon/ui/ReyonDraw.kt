package com.aripd.reyon.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.Product
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Diziliş, Denetim ve Satış ekranlarının ortak raf çizimi. */
internal object ReyonPalette {
    val BoardBg = Color(0xFF0F1628)
    val Plank = Color(0xFF475569)
    val PlankEdge = Color(0xFF64748B)
    val SlotLine = Color(0x22FFFFFF)
    val BlockText = Color(0xFF0F172A)
    val Badge = Color(0x66FFFFFF)
    val Lock = Color(0x99000000)
    val SelectedRing = Color(0xFFF8FAFC)
    val HighlightRing = Color(0xFF4DE1FF)
    val ViolatedRing = Color(0xFFF87171)
    val HintRing = Color(0xFFFDE68A)
    val FoundRing = Color(0xFF4ADE80)
    val MissRing = Color(0xFFF87171)
    val EmptySlot = Color(0x14FFFFFF)
    val EmptyShade = Color(0x73000000)
    val Satisfied = Color(0xFF4ADE80)
    val Violated = Color(0xFFF87171)
}

internal fun brandColor(brand: Brand): Color = Color(brandArgb(brand))

/** Raf geometrisi: göz genişliği, raf yüksekliği, kalas kalınlığı, iç boşluk. */
internal class ShelfGeom(val width: Float, val height: Float, val rows: Int, val cols: Int) {
    val cw = width / cols
    val sh = height / rows
    val plank = sh * 0.12f
    val pad = cw * 0.05f
    val corner = cw * 0.12f
    fun x(col: Int) = col * cw + pad
    fun y(row: Int) = row * sh + pad
    fun w(facings: Int) = facings * cw - 2 * pad
    val h = sh - plank - 2 * pad
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
 * Ölçülmüş blok etiketleri; önbellekli.
 *
 * [fit] adı verilen alana sığdırır: en büyük puntodan başlayarak önce tek satır,
 * sığmazsa ortaya en yakın boşluktan iki satır denenir; punto [minSp]'ye kadar
 * iner. Hiçbiri sığmazsa en küçük puntoda tek satır yatay olarak en çok
 * [MIN_CONDENSE] oranına kadar sıkıştırılır; o da yetmezse üç nokta.
 * Böylece "Bulaşık deterjanı" gibi uzun adlar tek yüzlü gözde iki satıra
 * iner, "Yumuşatıcı" gibi tek kelimeler hafifçe daralır, kırpma en son çaredir.
 */
internal class BlockLabeler(private val measurer: TextMeasurer) {
    private val labels = HashMap<String, TextLayoutResult>()
    private val fits = HashMap<String, BlockLabel>()

    /** Tek satırlık, sabit puntolu etiket (rozetler için); sığmazsa üç nokta. */
    fun label(text: String, maxWidth: Float, sizeSp: Float, color: Color): TextLayoutResult =
        labels.getOrPut("$text|${maxWidth.toInt()}|$sizeSp|${color.value}") {
            measurer.measure(
                AnnotatedString(text),
                style = style(sizeSp, color),
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
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
        fontWeight = FontWeight.Bold,
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

/** Blok adı puntosu (sp): bölge yüksekliğinden türetilir, bu aralıkta tutulur. */
internal const val NAME_MIN_SP = 8f
internal const val NAME_MAX_SP = 14f

internal fun DrawScope.drawShelfFrame(g: ShelfGeom) {
    for (r in 0 until g.rows) {
        val y = (r + 1) * g.sh - g.plank
        drawRoundRect(ReyonPalette.Plank, topLeft = Offset(0f, y), size = Size(g.width, g.plank), cornerRadius = CornerRadius(g.plank * 0.3f, g.plank * 0.3f))
        drawRect(ReyonPalette.PlankEdge, topLeft = Offset(0f, y), size = Size(g.width, g.plank * 0.25f))
        for (c in 1 until g.cols) {
            drawLine(ReyonPalette.SlotLine, Offset(c * g.cw, r * g.sh + g.pad), Offset(c * g.cw, y - g.pad), strokeWidth = 1.dp.toPx())
        }
    }
}

/**
 * Bir ürün bloğu: marka rengi, sığdırılmış ad, alt şeritte boy noktaları, ★ ve
 * ağırlık işareti, isteğe bağlı rozet (sağ altta), kilit ve çerçeve.
 *
 * Blok içi düzen yükseklikten türetilir: alt şerit işaretleri (ve varsa rozeti)
 * alır, kalan bölge ada kalır; ad bölgeye [BlockLabeler.fit] ile sığdırılır.
 */
internal fun DrawScope.drawBlock(
    g: ShelfGeom,
    path: Path,
    labeler: BlockLabeler,
    name: String,
    product: Product,
    facings: Int,
    row: Int,
    col: Int,
    ring: Color? = null,
    locked: Boolean = false,
    dim: Boolean = false,
    badge: TextLayoutResult? = null,
) {
    val x = g.x(col)
    val y = g.y(row)
    val w = g.w(facings)
    val h = g.h
    val fill = brandColor(product.brand).let { if (locked || dim) it.copy(alpha = 0.8f) else it }
    drawRoundRect(fill, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(g.corner, g.corner))

    val r = (h * 0.055f).coerceIn(1.6.dp.toPx(), 4.dp.toPx())
    val badgeW = badge?.let { it.size.width + 4.dp.toPx() } ?: 0f
    val badgeH = badge?.size?.height?.toFloat() ?: 0f
    val strip = maxOf(r * 3.4f, if (badge != null) badgeH + 2.dp.toPx() else 0f)
    val marksRight = if (badge != null) x + w - g.pad - badgeW - 2.dp.toPx() else x + w
    drawMarks(path, product, x, marksRight, y + h - strip / 2f, r)
    if (badge != null) {
        val bx = x + w - g.pad - badgeW
        val by = y + h - g.pad * 0.5f - badgeH
        drawRoundRect(ReyonPalette.Badge, topLeft = Offset(bx, by), size = Size(badgeW, badgeH), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()))
        drawText(badge, topLeft = Offset(bx + 2.dp.toPx(), by))
    }

    val top = y + maxOf(1.5.dp.toPx(), h * 0.06f)
    val zone = (y + h - strip) - top
    val margin = maxOf(2.5.dp.toPx(), g.cw * 0.04f)
    val maxSp = (zone * 0.42f / density).coerceIn(NAME_MIN_SP, NAME_MAX_SP)
    val label = labeler.fit(name, w - 2 * margin, zone, maxSp, NAME_MIN_SP, ReyonPalette.BlockText)
    drawLabel(label, x + w / 2f, top + zone / 2f)

    if (locked) drawLock(x + w - h * 0.22f, y + h * 0.16f, h * 0.12f)
    if (ring != null) {
        drawRoundRect(ring, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(g.corner, g.corner), style = Stroke(width = 3.dp.toPx()))
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

/** Göz maskesindeki her satır koşusunun etrafına çerçeve. */
internal fun DrawScope.drawMaskRing(g: ShelfGeom, mask: Int, color: Color, width: Float = 3.dp.toPx()) {
    for (r in 0 until g.rows) {
        var c = 0
        while (c < g.cols) {
            if (mask and (1 shl (r * g.cols + c)) == 0) {
                c++
                continue
            }
            var end = c
            while (end + 1 < g.cols && mask and (1 shl (r * g.cols + end + 1)) != 0) end++
            val x = c * g.cw + g.pad * 0.5f
            val y = r * g.sh + g.pad * 0.5f
            drawRoundRect(color, topLeft = Offset(x, y), size = Size((end - c + 1) * g.cw - g.pad, g.sh - g.plank - g.pad), cornerRadius = CornerRadius(g.corner, g.corner), style = Stroke(width = width))
            c = end + 1
        }
    }
}

/** Boy noktaları, ★ (yüksek marj) ve ağırlık işareti; [left, right] aralığında ortalanır. */
internal fun DrawScope.drawMarks(path: Path, p: Product, left: Float, right: Float, cy: Float, r: Float) {
    var cx = (left + right) / 2f - (p.size - 1) * r * 1.6f
    if (p.premium) cx -= r * 2.2f
    if (p.heavy) cx -= r * 2.2f
    repeat(p.size) {
        drawCircle(ReyonPalette.BlockText, radius = r, center = Offset(cx, cy))
        cx += r * 3.2f
    }
    if (p.premium) {
        cx += r * 1.2f
        drawStar(path, cx, cy, r * 2.2f)
        cx += r * 4.4f
    }
    if (p.heavy) {
        cx += r * 0.6f
        drawRoundRect(ReyonPalette.BlockText, topLeft = Offset(cx - r * 1.8f, cy - r * 0.6f), size = Size(r * 3.6f, r * 2.2f), cornerRadius = CornerRadius(r * 0.6f, r * 0.6f))
        drawRect(ReyonPalette.BlockText, topLeft = Offset(cx - r * 0.7f, cy - r * 1.7f), size = Size(r * 1.4f, r * 1.2f))
    }
}

internal fun DrawScope.drawStar(path: Path, cx: Float, cy: Float, r: Float) {
    path.reset()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.45f
        val px = cx + rr * cos(a)
        val py = cy + rr * sin(a)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, ReyonPalette.BlockText)
}

internal fun DrawScope.drawLock(cx: Float, cy: Float, r: Float) {
    drawRoundRect(ReyonPalette.Lock, topLeft = Offset(cx - r, cy), size = Size(2 * r, r * 1.6f), cornerRadius = CornerRadius(r * 0.3f, r * 0.3f))
    drawCircle(ReyonPalette.Lock, radius = r * 0.7f, center = Offset(cx, cy), style = Stroke(width = r * 0.35f))
}
