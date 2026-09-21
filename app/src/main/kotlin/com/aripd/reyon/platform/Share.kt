package com.aripd.reyon.platform

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
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

    private val BG = 0xFF0B0F1A.toInt()
    private val INK = 0xFFE4EAF5.toInt()
    private val MUTED = 0xFFAAB4C8.toInt()
    private val SECONDARY = 0xFFA78BFA.toInt()
    /** Kart vurgusu: uygulamanın kendi rengi. */
    private val ACCENT = 0xFF60A5FA.toInt()

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

        // Uygulamanın adı kartın başlığında zaten yazıyor; içerik bloğunda
        // ikinci kez tekrarlamak yer yiyor.
        val blocks = ArrayList<Block>()
        blocks += textBlock(content.headline, size = 96f, color = INK, weight = 900, maxWidth = maxW, fit = true)
        if (content.details.isNotEmpty()) {
            blocks += gap(18f)
            blocks += textBlock(content.details.joinToString(" · "), size = 40f, color = MUTED, weight = 500, maxWidth = maxW)
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
            blocks += textBlock(extra, size = 60f, color = INK, weight = 500, maxWidth = maxW, lineSpacing = 1.12f)
        }

        val middleH = blocks.sumOf { it.height.toDouble() }.toFloat()
        val needed = PAD + HEADER_H + SECTION_GAP + middleH + SECTION_GAP + FOOTER_H + PAD
        val height = max(MIN_HEIGHT.toFloat(), needed).roundToInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, height, accent)
        drawHeader(context, canvas, accent)
        // Kısa kartlarda orta blok başlıkla altbilgi arasında ortalanır.
        var y = PAD + HEADER_H + SECTION_GAP + (height - needed) / 2f
        for (block in blocks) {
            block.draw(canvas, y)
            y += block.height
        }
        drawFooter(context, canvas, height, accent)
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

    private fun drawBackground(canvas: Canvas, height: Int, accent: Int) {
        canvas.drawColor(BG)
        val h = height.toFloat()
        val w = WIDTH.toFloat()
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.92f, -80f, 860f,
                intArrayOf(withAlpha(accent, 0x70), withAlpha(accent, 0x00)), null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, glow)
        val glow2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                60f, h + 60f, 760f,
                intArrayOf(withAlpha(SECONDARY, 0x40), withAlpha(SECONDARY, 0x00)), null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, glow2)
    }

    private fun drawHeader(context: Context, canvas: Canvas, accent: Int) {
        val badge = RectF(PAD, PAD, PAD + HEADER_H, PAD + HEADER_H)
        canvas.drawRoundRect(badge, 28f, 28f, fill(accent))
        drawMark(canvas, badge, fill(BG))

        val x = badge.right + 36f
        val title = textPaint(44f, INK, 700)
        val tagline = textPaint(32f, MUTED, 400)
        val titleH = title.lineHeight()
        val tagH = tagline.lineHeight()
        val blockTop = badge.centerY() - (titleH + 8f + tagH) / 2f
        canvas.drawText(context.getString(R.string.app_name), x, blockTop - title.fontMetrics.ascent, title)
        canvas.drawText(
            context.getString(R.string.app_tagline), x, blockTop + titleH + 8f - tagline.fontMetrics.ascent, tagline,
        )
    }

    /**
     * Raf izleği: üç tahta ve üstlerindeki ürün blokları. Uygulama simgesiyle
     * (res/drawable/ic_launcher_foreground.xml) aynı desen — aynı boyda kareler,
     * 5 sütun × 6 satırlık ızgara.
     */
    private fun drawMark(canvas: Canvas, box: RectF, paint: Paint) {
        val plank = (0..4).map { it to 1 } + (0..4).map { it to 3 } + (0..4).map { it to 5 }
        val goods = listOf(0 to 0, 1 to 0, 2 to 2, 3 to 2, 4 to 2, 0 to 4, 2 to 4)
        val unit = box.width() * 0.62f / 39.2f
        val step = unit * 8f
        val side = unit * 7.2f
        val left = box.centerX() - (step * 4f + side) / 2f
        val top = box.centerY() - (step * 5f + side) / 2f
        for ((cx, cy) in plank + goods) {
            val x = left + cx * step
            val y = top + cy * step
            canvas.drawRect(x, y, x + side, y + side, paint)
        }
    }

    private fun drawFooter(context: Context, canvas: Canvas, height: Int, accent: Int) {
        val baseline = height - PAD
        val chips = listOf(R.string.chip_no_ads, R.string.chip_no_trackers, R.string.chip_no_permissions)
            .joinToString(" · ") { context.getString(it) }
        canvas.drawText(chips, PAD, baseline, textPaint(30f, MUTED, 500))
        val link = textPaint(30f, withAlpha(accent, 0xCC), 500).apply { textAlign = Paint.Align.RIGHT }
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
        weight: Int,
        maxWidth: Float,
        spacing: Float = 0f,
        fit: Boolean = false,
        lineSpacing: Float = 1f,
    ): Block {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = typefaceOf(weight)
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

    private fun textPaint(size: Float, color: Int, weight: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = typefaceOf(weight)
    }

    private fun typefaceOf(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else if (weight >= 600) {
            Typeface.DEFAULT_BOLD
        } else {
            Typeface.DEFAULT
        }

    private fun Paint.lineHeight(): Float = fontMetrics.descent - fontMetrics.ascent

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
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
