package com.aripd.reyon.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.aripd.reyon.platform.ShareDraw
import com.aripd.reyon.platform.drawCentered
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.ReyonAuditState
import com.aripd.reyon.engine.ReyonOrderState
import com.aripd.reyon.engine.ReyonSalesState
import com.aripd.reyon.engine.ReyonState
import com.aripd.reyon.engine.ShelfItem

/** Marka renkleri (ARGB); ekran ve paylaşım kartı aynı paleti kullanır. */
internal fun brandArgb(brand: Brand): Int = when (brand) {
    Brand.A -> 0xFF60A5FA.toInt()
    Brand.B -> 0xFFFBBF24.toInt()
    Brand.C -> 0xFF4ADE80.toInt()
    Brand.D -> 0xFFF472B6.toInt()
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

private fun shelfPainter(items: List<ShelfItem>, rows: Int, cols: Int, res: Resources, rings: List<Int>): (Canvas, RectF) -> Unit = { canvas, rect ->
    val cw = rect.width() / cols
    val sh = rect.height() / rows
    val plank = sh * 0.12f
    val pad = cw * 0.05f
    canvas.drawRect(rect, ShareDraw.fill(0xFF0F1628.toInt()))
    val plankPaint = ShareDraw.fill(0xFF475569.toInt())
    for (r in 0 until rows) {
        val y = rect.top + (r + 1) * sh - plank
        canvas.drawRoundRect(RectF(rect.left, y, rect.right, y + plank), plank * 0.3f, plank * 0.3f, plankPaint)
    }
    val nameText = ShareDraw.text(sh * 0.2f, 0xFF0F172A.toInt())
    val markText = ShareDraw.text(sh * 0.2f, 0xFF0F172A.toInt(), bold = false)
    for (it in items) {
        val p = it.product
        val x = rect.left + it.col * cw + pad
        val y = rect.top + it.row * sh + pad
        val w = it.facings * cw - 2 * pad
        val h = sh - plank - 2 * pad
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), cw * 0.12f, cw * 0.12f, ShareDraw.fill(brandArgb(p.brand)))
        val name = ReyonText.kind(res, p.kind)
        val paint = Paint(nameText)
        val maxWidth = w - cw * 0.16f
        val measured = paint.measureText(name)
        if (measured > maxWidth) paint.textSize = paint.textSize * maxWidth / measured
        canvas.drawCentered(name, x + w / 2f, y + h * 0.42f, paint)
        val marks = buildString {
            repeat(p.size) { append('•') }
            if (p.premium) append(" ★")
            if (p.heavy) append(" ▼")
        }
        canvas.drawCentered(marks, x + w / 2f, y + h * 0.76f, markText)
    }
    val ring = ShareDraw.stroke(0xFF4ADE80.toInt(), cw * 0.05f)
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
                val x = rect.left + c * cw + pad * 0.5f
                val y = rect.top + r * sh + pad * 0.5f
                canvas.drawRoundRect(RectF(x, y, x + (end - c + 1) * cw - pad, y + sh - plank - pad), cw * 0.12f, cw * 0.12f, ring)
                c = end + 1
            }
        }
    }
}
