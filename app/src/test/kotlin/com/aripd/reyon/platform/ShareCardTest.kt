package com.aripd.reyon.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paylaşım kartı: rozetteki raf izleği ve tek uygulamalık başlık bloğu.
 *
 * Kart tuvale elle çizildiği için derleyici hiçbir şey doğrulamaz; çizim
 * sırasındaki bir hata ancak kullanıcı paylaşmaya çalışınca ortaya çıkar.
 * Test kartı gerçekten üretir (Robolectric NATIVE grafik yığınıyla) ve düz
 * metnin uygulamanın adıyla başlayıp bağlantıyla bittiğini görür.
 */
@RunWith(AndroidJUnit4::class)
class ShareCardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aTextOnlyCardIsASquareAndDrawsWithoutFailing() {
        val bitmap = ShareCard.render(
            context,
            ShareContent(headline = "Çözüldü!", details = listOf("Kolay", "Süre: 1:12")),
        )
        assertEquals("kart 1080 px geniş olmalı", 1080, bitmap.width)
        assertEquals("metin kartı kare olmalı", bitmap.width, bitmap.height)
        // Tuval boyandı mı: köşe pikseli saydam kalmamalı.
        assertTrue("arka plan boyanmalı", bitmap.getPixel(4, 4) != 0)
    }

    @Test
    fun theBoardPainterGetsASquareAreaAndTheCardGrows() {
        var painted = false
        var square = false
        val bitmap = ShareCard.render(
            context,
            ShareContent(
                headline = "Denetim",
                board = { canvas, area ->
                    painted = true
                    square = area.width() == area.height()
                    canvas.drawRect(area, ShareDraw.fill(0xFF102030.toInt()))
                },
            ),
        )
        assertTrue("tahta ressamı çağrılmalı", painted)
        assertTrue("tahtaya kare alan verilmeli", square)
        assertTrue("tahtalı kart kareden uzun olmalı", bitmap.height > bitmap.width)
    }

    @Test
    fun thePlainTextNamesTheAppAndEndsWithTheLink() {
        val text = ShareCard.plainText(context, ShareContent(headline = "Çözüldü!"))
        assertTrue("metin uygulamanın adıyla başlamalı: $text", text.startsWith(context.getString(R.string.app_name)))
        assertTrue("metin bağlantıyla bitmeli: $text", text.trimEnd().endsWith(context.getString(R.string.share_link)))
        assertFalse("biçim belirteci kalmamalı: $text", text.contains("%"))
    }
}
