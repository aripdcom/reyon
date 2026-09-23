package com.aripd.reyon.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Tasarım taslağının çizgi simgeleri: 24 birimlik kutuda, 2 birim kalınlığında,
 * yuvarlak uçlu. Yol verisi taslaktaki SVG'lerin aynısı (daireler ve köşesi
 * yuvarlak dikdörtgenler yay komutlarına açıldı). Aynı veri iki yoldan
 * kullanılıyor: Compose `Icon` için [ImageVector], raf tuvali için [Path]
 * ([drawIcon]).
 */
object ReyonIcons {
    internal const val BACK = "M19 12H5M12 19l-7-7 7-7"
    internal const val SLIDERS = "M4 7h9M17 7h3M4 17h3M11 17h9M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"
    internal const val CHECK = "M5 12.5l4.5 4.5L19 7.5"
    internal const val CHEVRON = "M9 6l6 6-6 6"
    internal const val PLANOGRAM = "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2zM3 10h18M3 15h18M10 4v6M14 10v5"
    internal const val AUDIT = "M7 4h10a2 2 0 0 1 2 2v13a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2zM9 3h6v3H9zM9 13l2 2 4-4"
    internal const val SALES = "M3 17l6-6 4 4 8-8M15 7h6v6"
    internal const val ORDER = "M3 7.5l9-4.5 9 4.5v9l-9 4.5-9-4.5zM3 7.5l9 4.5 9-4.5M12 12v9"
    internal const val SHARE = "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v13"
    internal const val PIN = "M12 16v6M8 3h8l-1.5 6 3.5 3.5V16H6v-3.5L9.5 9z"
    internal const val EYE = "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0"
    internal const val HEAVY = "M12 3v12M6 10l6 6 6-6M4 21h16"
    internal const val INFO = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M12 11v5M12 7.5v.5"
    internal const val FLAG = "M5 21V4M5 4h12l-2.5 4.5L17 13H5"

    val Back: ImageVector by lazy { strokeIcon("back", BACK, mirror = true) }
    val Sliders: ImageVector by lazy { strokeIcon("sliders", SLIDERS) }
    val Check: ImageVector by lazy { strokeIcon("check", CHECK) }
    val Chevron: ImageVector by lazy { strokeIcon("chevron", CHEVRON, mirror = true) }
    val Planogram: ImageVector by lazy { strokeIcon("planogram", PLANOGRAM) }
    val Audit: ImageVector by lazy { strokeIcon("audit", AUDIT) }
    val Sales: ImageVector by lazy { strokeIcon("sales", SALES) }
    val Order: ImageVector by lazy { strokeIcon("order", ORDER) }
    val Share: ImageVector by lazy { strokeIcon("share", SHARE) }
    val Pin: ImageVector by lazy { strokeIcon("pin", PIN) }
    val Eye: ImageVector by lazy { strokeIcon("eye", EYE) }
    val Heavy: ImageVector by lazy { strokeIcon("heavy", HEAVY) }
    val Info: ImageVector by lazy { strokeIcon("info", INFO) }
    val Flag: ImageVector by lazy { strokeIcon("flag", FLAG) }

    /** Yön bildiren simgeler ([mirror]) sağdan sola dillerde aynalanır. */
    private fun strokeIcon(name: String, data: String, mirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = mirror,
        )
            .addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}

/** Tuvalde çizilecek simge yolları; bir kez ayrıştırılır. */
internal object CanvasIcons {
    val Pin: Path by lazy { PathParser().parsePathString(ReyonIcons.PIN).toPath() }
    val Eye: Path by lazy { PathParser().parsePathString(ReyonIcons.EYE).toPath() }
    val Heavy: Path by lazy { PathParser().parsePathString(ReyonIcons.HEAVY).toPath() }
    val Flag: Path by lazy { PathParser().parsePathString(ReyonIcons.FLAG).toPath() }
    val Check: Path by lazy { PathParser().parsePathString(ReyonIcons.CHECK).toPath() }
}

/** 24 birimlik simge yolunu (cx, cy) merkezli, [size] piksel kutuya çizer. */
internal fun DrawScope.drawIcon(icon: Path, cx: Float, cy: Float, size: Float, color: Color, strokeUnits: Float = 2f) {
    val s = size / 24f
    withTransform({
        translate(cx - size / 2f, cy - size / 2f)
        scale(s, s, pivot = Offset.Zero)
    }) {
        drawPath(icon, color, style = Stroke(width = strokeUnits, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
