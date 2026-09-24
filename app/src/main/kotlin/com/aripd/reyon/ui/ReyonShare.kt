package com.aripd.reyon.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.aripd.reyon.platform.ShareDraw
import com.aripd.reyon.platform.drawCentered
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.Category
import com.aripd.reyon.engine.ReyonAuditState
import com.aripd.reyon.engine.ReyonOrderState
import com.aripd.reyon.engine.ReyonSalesState
import com.aripd.reyon.engine.ReyonState
import com.aripd.reyon.engine.ShelfItem

/**
 * Paylaşım kartının raf renkleri (ARGB): uygulamanın açık temasıyla aynı. Kart
 * her zaman açık zeminde çizilir; paylaşılan görsel temadan bağımsız okunur.
 */
internal object ShareShelfColors {
    val GONDOLA = 0xFFE6E3DB.toInt()
    val PLANK = 0xFFA9A59A.toInt()
    val RAIL = 0xFF2E3432.toInt()
    val LABEL = 0xFFFFFFFF.toInt()
    val FOUND = 0xFFB42318.toInt()
}

/** Ambalaj renkleri (ARGB), marka sırasıyla; ekrandaki açık temanın aynısı. */
internal fun brandArgb(brand: Brand): Int = when (brand) {
    Brand.A -> 0xFF2C4A7A.toInt()
    Brand.B -> 0xFF2F6B55.toInt()
    Brand.C -> 0xFF9C3552.toInt()
    Brand.D -> 0xFF86591A.toInt()
}

/** Kategori renkleri (ARGB), etiket karesi için; [Category] sırasıyla. */
private fun categoryArgb(category: Category): Int = when (category) {
    Category.ICECEK -> 0xFF2F6FBF.toInt()
    Category.ATISTIRMALIK -> 0xFFD9542B.toInt()
    Category.KAHVALTILIK -> 0xFF6F7F22.toInt()
    Category.TEMIZLIK -> 0xFF1A8FA0.toInt()
    Category.BAKIM -> 0xFF9A4FB0.toInt()
}

/** Diziliş paylaşımı: dizilmiş raf. */
internal fun reyonPainter(state: ReyonState, res: Resources): (Canvas, RectF) -> Unit {
    val puzzle = state.puzzle
    val items = puzzle.products.mapNotNull { p -> state.placement(p.id)?.let { ShelfItem(p, it.row, it.col, p.facings) } }
    return shelfPainter(items, puzzle.rows, puzzle.cols, res, emptyList())
}

/** Denetim paylaşımı: gerçek raf, bulunan sapmalar yeşil çerçeveli. */
internal fun auditPainter(state: ReyonAuditState, res: Resources): (Canvas, RectF) -> Unit {
    val audit = state.audit
    val rings = audit.deviations.indices.filter { state.isFound(it) }.map { audit.deviations[it].slotMask }
    return shelfPainter(audit.items, audit.rows, audit.cols, res, rings)
}

/** Satış paylaşımı: oyuncunun dizilişi. */
internal fun salesPainter(state: ReyonSalesState, res: Resources): (Canvas, RectF) -> Unit {
    val sales = state.sales
    val items = sales.products.mapNotNull { p -> state.placement(p.id)?.let { ShelfItem(p, it.row, it.col, p.facings) } }
    return shelfPainter(items, sales.rows, sales.cols, res, emptyList())
}

/** Sipariş paylaşımı: plan rafı (stok düzeyleri kartta yer almaz). */
internal fun orderPainter(state: ReyonOrderState, res: Resources): (Canvas, RectF) -> Unit {
    val order = state.order
    val items = order.items.map { ShelfItem(it.product, it.row, it.col, it.product.facings) }
    return shelfPainter(items, order.rows, order.cols, res, emptyList())
}

/**
 * Kartın rafı: ekrandaki gondolun sade hâli. Ambalaj rengi marka, boyu ürün boyu,
 * adı üstünde; her rafın altında etiket rayı ve ürünün kategori karesi. Bulunan
 * sapmalar (Denetim) kırmızı çerçeveyle.
 */
private fun shelfPainter(items: List<ShelfItem>, rows: Int, cols: Int, res: Resources, rings: List<Int>): (Canvas, RectF) -> Unit = { canvas, rect ->
    val side = rect.width() * 0.02f
    val cw = (rect.width() - 2 * side) / cols
    val sh = rect.height() / rows
    val rail = sh * 0.16f
    val plank = sh * 0.05f
    val air = sh * 0.06f
    val zone = sh - rail - plank - air
    val gap = cw * 0.05f
    canvas.drawRect(rect, ShareDraw.fill(ShareShelfColors.GONDOLA))
    val plankPaint = ShareDraw.fill(ShareShelfColors.PLANK)
    val railPaint = ShareDraw.fill(ShareShelfColors.RAIL)
    for (r in 0 until rows) {
        val base = rect.top + r * sh + air + zone
        canvas.drawRect(RectF(rect.left, base, rect.right, base + plank), plankPaint)
        canvas.drawRect(RectF(rect.left, base + plank, rect.right, base + plank + rail), railPaint)
    }
    val labelPaint = ShareDraw.fill(ShareShelfColors.LABEL)
    for (it in items) {
        val p = it.product
        val x = rect.left + side + it.col * cw + gap / 2f
        val w = it.facings * cw - gap
        val h = zone * floatArrayOf(0.70f, 0.85f, 1f)[(p.size - 1).coerceIn(0, 2)]
        val base = rect.top + it.row * sh + air + zone
        val corner = cw * 0.06f
        canvas.drawRoundRect(RectF(x, base - h, x + w, base), corner, corner, ShareDraw.fill(brandArgb(p.brand)))
        val name = ReyonText.kind(res, p.kind)
        val paint = Paint(ShareDraw.text(minOf(sh * 0.17f, cw * 0.24f), 0xFFFFFFFF.toInt()))
        val maxWidth = w - cw * 0.14f
        val measured = paint.measureText(name)
        if (measured > maxWidth) paint.textSize = paint.textSize * maxWidth / measured
        canvas.drawCentered(name, x + w / 2f, base - h / 2f, paint)
        val lh = rail * 0.7f
        val ly = base + plank + (rail - lh) / 2f
        canvas.drawRoundRect(RectF(x, ly, x + w, ly + lh), lh * 0.12f, lh * 0.12f, labelPaint)
        val sq = lh * 0.5f
        canvas.drawRect(RectF(x + lh * 0.3f, ly + (lh - sq) / 2f, x + lh * 0.3f + sq, ly + (lh + sq) / 2f), ShareDraw.fill(categoryArgb(p.category)))
    }
    val ring = ShareDraw.stroke(ShareShelfColors.FOUND, cw * 0.045f)
    for (mask in rings) {
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                if (mask and (1 shl (r * cols + c)) == 0) {
                    c++
                    continue
                }
                var end = c
                while (end + 1 < cols && mask and (1 shl (r * cols + end + 1)) != 0) end++
                val x = rect.left + side + c * cw + gap / 4f
                val top = rect.top + r * sh + air * 0.5f
                canvas.drawRoundRect(RectF(x, top, x + (end - c + 1) * cw - gap / 2f, rect.top + r * sh + air + zone), cw * 0.06f, cw * 0.06f, ring)
                c = end + 1
            }
        }
    }
}
