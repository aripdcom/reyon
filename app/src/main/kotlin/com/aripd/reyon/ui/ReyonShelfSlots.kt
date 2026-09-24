package com.aripd.reyon.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import com.aripd.reyon.R
import com.aripd.reyon.engine.Product
import com.aripd.reyon.platform.appText

/**
 * Raf gözlerini ekran okuyucuya tek tek açar. Tuval tek bir düğümdü; gözlerin
 * konumu okunmuyor, bir göze ürün koymanın erişilebilir yolu yoktu (docs/cihaz-testi.md,
 * v1.0.1 E ve 1.1.0 E). Her göz için görünmez bir düğüm, tuvalin çizdiği geometriyle
 * ([ShelfGeom]) aynı yerde durur: "göz hizası (2. raf), soldan 3. göz: Peynir".
 * Çift dokunuş parmak dokunuşuyla aynı işi yapar ([onActivate]); uzun basışın işi
 * (raftan kaldırma) özel eylem olarak sunulur ([removeLabel], [onRemove]).
 *
 * [contents] ve [removable] satır sırasıyla göz başına bir öğe; çağıran, oyunun
 * durumunu okuduğu kapsamda hazır liste olarak verir. İşlev olarak verilince
 * düğümler durum değişince yeniden oluşmuyor, bayat içerik okunuyordu.
 *
 * Düğümlerin dokunma işleyicisi yok: parmakla dokunuş aşağıdaki tuvale geçer,
 * görenler için hiçbir şey değişmez. Tuvalin üstüne `matchParentSize()` ile konur.
 */
@Composable
internal fun ShelfSlots(
    rows: Int,
    cols: Int,
    contents: List<String>,
    onActivate: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
    rail: Boolean = true,
    removeLabel: String? = null,
    removable: List<Boolean> = emptyList(),
    onRemove: (row: Int, col: Int) -> Unit = { _, _ -> },
) {
    val res = LocalContext.current.resources
    Layout(
        modifier = modifier,
        content = {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val label = appText(
                        res,
                        R.string.reyon_slot_fmt,
                        ReyonText.shelf(res, row, rows),
                        col + 1,
                        contents[row * cols + col],
                    )
                    val canRemove = removeLabel != null && removable.getOrElse(row * cols + col) { false }
                    Box(
                        modifier = Modifier.semantics {
                            contentDescription = label
                            role = Role.Button
                            onClick { onActivate(row, col); true }
                            if (canRemove) {
                                customActions = listOf(CustomAccessibilityAction(removeLabel!!) { onRemove(row, col); true })
                            }
                        },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val g = ShelfGeom(width.toFloat(), height.toFloat(), rows, cols, density, rail = rail)
        val placeables = measurables.map { it.measure(Constraints.fixed(g.cw.toInt().coerceAtLeast(1), g.sh.toInt().coerceAtLeast(1))) }
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val row = i / cols
                val col = i % cols
                p.place((g.side + col * g.cw).toInt(), (row * g.sh).toInt())
            }
        }
    }
}

/**
 * Ürünün ekran okuyucuda okunan adı: tür, marka, boy ("Kuruyemiş, Meltem, orta boy").
 * Marka ve boy rafta yalnız ambalajın harfi ve noktalarıyla görünüyor; Denetim'in marka
 * ve boy sapmaları, Diziliş'in boy akışı kuralı bunlarsız okunamaz.
 */
internal fun spokenProduct(res: Resources, p: Product): String {
    val size = when (p.size) {
        1 -> R.string.reyon_size_small
        2 -> R.string.reyon_size_medium
        else -> R.string.reyon_size_large
    }
    return listOf(ReyonText.kind(res, p.kind), ReyonText.brand(res, p.brand), res.getString(size)).joinToString(", ")
}

/** Gözün içeriği: ürün adı ya da "boş", ardından durumlar ("seçili"). */
internal fun slotContent(res: Resources, name: String?, vararg states: String?): String =
    (listOf(name ?: res.getString(R.string.reyon_slot_empty)) + states.filterNotNull()).joinToString(", ")
