package com.aripd.reyon.platform

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.aripd.reyon.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Paylaşılacak sonuç. Oyun ekranı bitiş kartında üretir; [ShareCard] bunu bir
 * görsel karta ve düz metne çevirip sistemin paylaşım sayfasına verir.
 *
 * [board] verilirse kartın ortasına kare bir alan ayrılır ve oyun tahtası oraya
 * çizilir. Ressam arka plan iş parçacığında çağrılır: yalnızca değişmez durum
 * okumalı ve alanın tamamını (arka plan dahil) kendisi boyamalıdır.
 * [extraText] düz metne eklenir (örn. emoji ızgarası); tahta yoksa görsele de
 * yazılır.
 */
class ShareContent(
    val headline: String,
    val details: List<String> = emptyList(),
    val board: ((Canvas, RectF) -> Unit)? = null,
    val extraText: String? = null,
)

/**
 * Sonuç kartı: 1080 px genişliğinde PNG üretir, önbelleğe yazar ve sistemin
 * paylaşım sayfasını açar. İzin gerektirmez: dosya yalnızca seçilen uygulamaya,
 * yalnızca okuma için açılır (bkz. AndroidManifest FileProvider).
 */
object ShareCard {

    private const val WIDTH = 1080
    private const val MIN_HEIGHT = 1080
    private const val PAD = 84f
    private const val HEADER_H = 120f
    private const val FOOTER_H = 36f
    private const val SECTION_GAP = 64f
    private const val BOARD_CORNER = 28f
    private const val AUTHORITY_SUFFIX = ".share"
    private const val DIR = "shares"
    private const val KEEP_MS = 60L * 60L * 1000L

    /** İşaretin dikdörtgenleri: sol, üst, genişlik, yükseklik (simgeden). */
    private data class Shape(val x: Float, val y: Float, val w: Float, val h: Float)

    private val MARK = listOf(
        Shape(2f, 0f, 10f, 10f),
        Shape(14f, 0f, 10f, 10f),
        Shape(26f, 0f, 10f, 10f),
        Shape(0f, 10f, 48f, 4f),
        Shape(2f, 18f, 10f, 10f),
        Shape(26f, 18f, 10f, 10f),
        Shape(38f, 18f, 10f, 10f),
        Shape(0f, 28f, 48f, 4f),
        Shape(2f, 36f, 10f, 10f),
        Shape(14f, 36f, 10f, 10f),
        Shape(26f, 36f, 10f, 10f),
        Shape(38f, 36f, 10f, 10f),
        Shape(0f, 46f, 48f, 4f),
    )

    // Kart her zaman açık temada: paylaşılan görsel, alıcının temasından bağımsız okunur.
    private val BG = 0xFFF3F2EE.toInt()
    private val INK = 0xFF17201D.toInt()
    private val MUTED = 0xFF56615C.toInt()
    /** Dikkat sarısı: rozetin yanında ince çizgi, tek vurgu. */
    private val ATTENTION = 0xFFFFD23F.toInt()
    /** Kart vurgusu: uygulamanın marka rengi. */
    private val ACCENT = 0xFF0F4C5C.toInt()

    /** Kartın yazıları: IBM Plex Sans; yüklenemezse sistem yazısı. */
    private class Fonts(val regular: Typeface, val semibold: Typeface) {
        fun of(weight: Int): Typeface = if (weight >= 600) semibold else regular
    }

    private fun fonts(context: Context): Fonts {
        fun load(id: Int, fallback: Typeface): Typeface =
            try {
                ResourcesCompat.getFont(context, id) ?: fallback
            } catch (e: Resources.NotFoundException) {
                fallback
            }
        return Fonts(load(R.font.ibm_plex_sans_regular, Typeface.DEFAULT), load(R.font.ibm_plex_sans_semibold, Typeface.DEFAULT_BOLD))
    }

    /** Kartı üretir (IO'da), önbelleğe yazar ve paylaşım sayfasını açar. */
    suspend fun share(context: Context, content: ShareContent) {
        val app = context.applicationContext
        val file = withContext(Dispatchers.IO) {
            val bitmap = render(app, content)
            try {
                writePng(app, bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        val uri = FileProvider.getUriForFile(app, app.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, plainText(app, content))
            clipData = ClipData.newRawUri(app.getString(R.string.app_name), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, app.getString(R.string.share_chooser))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Görselin yanında giden düz metin (görsel kabul etmeyen hedefler yalnız bunu alır). */
    fun plainText(context: Context, content: ShareContent): String {
        val name = context.getString(R.string.app_name)
        return buildString {
            append(name).append(" · ").append(content.headline)
            if (content.details.isNotEmpty()) {
                append('\n').append(content.details.joinToString(" · "))
            }
            content.extraText?.let { append("\n\n").append(it) }
            append("\n\n").append(context.getString(R.string.share_tagline))
            append('\n').append(context.getString(R.string.share_link))
        }
    }

    /** Kartı çizer. Boy içeriğe göre uzar; metin kartları 1080×1080 karedir. */
    fun render(context: Context, content: ShareContent): Bitmap {
        val accent = ACCENT
        val maxW = WIDTH - 2 * PAD
        val fonts = fonts(context)

        // Uygulamanın adı kartın başlığında zaten yazıyor; içerik bloğunda
        // ikinci kez tekrarlamak yer yiyor.
        val blocks = ArrayList<Block>()
        blocks += textBlock(content.headline, size = 88f, color = INK, typeface = fonts.semibold, maxWidth = maxW, fit = true)
        if (content.details.isNotEmpty()) {
            blocks += gap(18f)
            blocks += textBlock(content.details.joinToString(" · "), size = 40f, color = MUTED, typeface = fonts.regular, maxWidth = maxW)
        }
        val board = content.board
        val extra = content.extraText
        if (board != null) {
            blocks += gap(SECTION_GAP)
            blocks += Block(maxW) { canvas, top ->
                drawBoard(canvas, RectF(PAD, top, PAD + maxW, top + maxW), board)
            }
        } else if (!extra.isNullOrBlank()) {
            blocks += gap(48f)
            blocks += textBlock(extra, size = 60f, color = INK, typeface = fonts.regular, maxWidth = maxW, lineSpacing = 1.12f)
        }

        val middleH = blocks.sumOf { it.height.toDouble() }.toFloat()
        val needed = PAD + HEADER_H + SECTION_GAP + middleH + SECTION_GAP + FOOTER_H + PAD
        val height = max(MIN_HEIGHT.toFloat(), needed).roundToInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas)
        drawHeader(context, canvas, accent, fonts)
        // Kısa kartlarda orta blok başlıkla altbilgi arasında ortalanır.
        var y = PAD + HEADER_H + SECTION_GAP + (height - needed) / 2f
        for (block in blocks) {
            block.draw(canvas, y)
            y += block.height
        }
        drawFooter(context, canvas, height, accent, fonts)
        return bitmap
    }

    private fun writePng(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        // Eski kartlar silinir; az önce paylaşılan bir dosya alıcı tarafından
        // hâlâ okunuyor olabilir, o yüzden bir saat beklenir.
        dir.listFiles()?.forEach { if (now - it.lastModified() > KEEP_MS) it.delete() }
        val file = File(dir, "reyon-$now.png")
        file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    // -----------------------------------------------------------------------
    // Çizim
    // -----------------------------------------------------------------------

    private class Block(val height: Float, val draw: (Canvas, Float) -> Unit)

    private fun gap(height: Float) = Block(height) { _, _ -> }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(BG)
    }

    private fun drawHeader(context: Context, canvas: Canvas, accent: Int, fonts: Fonts) {
        val badge = RectF(PAD, PAD, PAD + HEADER_H, PAD + HEADER_H)
        canvas.drawRoundRect(badge, 28f, 28f, fill(accent))
        drawMark(canvas, badge, fill(0xFFFFFFFF.toInt()))
        canvas.drawRect(RectF(badge.right + 36f, badge.bottom - 6f, badge.right + 36f + 96f, badge.bottom), fill(ATTENTION))

        val x = badge.right + 36f
        val title = textPaint(44f, INK, fonts.semibold)
        val tagline = textPaint(32f, MUTED, fonts.regular)
        val titleH = title.lineHeight()
        val tagH = tagline.lineHeight()
        val blockTop = badge.centerY() - (titleH + 8f + tagH) / 2f
        canvas.drawText(context.getString(R.string.app_name), x, blockTop - title.fontMetrics.ascent, title)
        canvas.drawText(
            context.getString(R.string.app_tagline), x, blockTop + titleH + 8f - tagline.fontMetrics.ascent, tagline,
        )
    }

    /**
     * Raf izleği: üç çıta ve üstlerindeki ürünler, ortadaki rafta bir göz boş.
     * Uygulama simgesiyle (res/drawable/ic_launcher_foreground.xml) birebir
     * aynı desen; oranlar oradaki 48×50 birimlik kutudan alındı.
     */
    private fun drawMark(canvas: Canvas, box: RectF, paint: Paint) {
        val scale = box.width() * 0.60f / 48f
        val left = box.centerX() - 48f * scale / 2f
        val top = box.centerY() - 50f * scale / 2f
        for ((x, y, w, h) in MARK) {
            canvas.drawRect(
                left + x * scale, top + y * scale,
                left + (x + w) * scale, top + (y + h) * scale, paint,
            )
        }
    }

    private fun drawFooter(context: Context, canvas: Canvas, height: Int, accent: Int, fonts: Fonts) {
        val baseline = height - PAD
        val chips = listOf(R.string.chip_no_ads, R.string.chip_no_trackers, R.string.chip_no_permissions)
            .joinToString(" · ") { context.getString(it) }
        canvas.drawText(chips, PAD, baseline, textPaint(30f, MUTED, fonts.regular))
        val link = textPaint(30f, accent, fonts.semibold).apply { textAlign = Paint.Align.RIGHT }
        val shortLink = context.getString(R.string.share_link).removePrefix("https://").removePrefix("http://")
        canvas.drawText(shortLink, WIDTH - PAD, baseline, link)
    }

    private fun drawBoard(canvas: Canvas, rect: RectF, painter: (Canvas, RectF) -> Unit) {
        val clip = Path().apply { addRoundRect(rect, BOARD_CORNER, BOARD_CORNER, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        painter(canvas, rect)
        canvas.restore()
    }

    /**
     * Ortalanmış, gerekirse satır kıran metin bloğu. [fit] verilirse tek satıra
     * sığana dek küçültülür (başlıklar için).
     */
    private fun textBlock(
        text: String,
        size: Float,
        color: Int,
        typeface: Typeface,
        maxWidth: Float,
        spacing: Float = 0f,
        fit: Boolean = false,
        lineSpacing: Float = 1f,
    ): Block {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
            letterSpacing = spacing
        }
        if (fit) {
            while (paint.textSize > 44f && paint.measureText(text) > maxWidth) paint.textSize -= 4f
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .build()
        return Block(layout.height.toFloat()) { canvas, top ->
            canvas.save()
            canvas.translate(PAD, top)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun textPaint(size: Float, color: Int, typeface: Typeface): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
    }

    private fun Paint.lineHeight(): Float = fontMetrics.descent - fontMetrics.ascent

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

}

/** Tahta ressamları için ortak küçük yardımcılar (android.graphics). */
object ShareDraw {

    fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    /** Ortadan hizalı metin boyası. */
    fun text(size: Float, color: Int, bold: Boolean = true): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}

/** Metni (cx, cy) noktasına dikeyde ve yatayda ortalayarak çizer (ressamlar için). */
fun Canvas.drawCentered(text: String, cx: Float, cy: Float, paint: Paint) {
    val fm = paint.fontMetrics
    drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
}
