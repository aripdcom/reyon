package com.aripd.reyon.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.aripd.reyon.R

/** Uygulamanın ses efektleri; hepsi prosedürel üretilmiş küçük WAV'lar. */
enum class Sfx(@RawRes val res: Int) {
    /** Küçük olumlu an: ürün seçme, doğru yerleştirme. */
    POP(R.raw.sfx_pop),

    /** Tamamlanma: bulmaca çözüldü, denetim bitti. */
    CLEAR(R.raw.sfx_clear),

    /** Büyük an: rekor, üç yıldız. */
    BIG(R.raw.sfx_big),

    /** Rafa bırakma / geri alma. */
    DROP(R.raw.sfx_drop),
}

/**
 * SoundPool tabanlı hafif ses çalar. Efektler prosedürel üretilmiş küçük
 * WAV'lardır. [enabled] her çalmada okunur; ayarlardaki ses düğmesi böylece
 * anında etki eder.
 */
class SoundPlayer(context: Context, private val enabled: () -> Boolean) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Sfx, Int> =
        Sfx.entries.associateWith { pool.load(context, it.res, 1) }

    val isEnabled: Boolean get() = enabled()

    fun play(sfx: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled()) return
        val id = ids[sfx] ?: return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}

/** Ekranların ses çalara eriştiği yer; sağlanmadıysa sessizlik. */
val LocalSound = staticCompositionLocalOf<SoundPlayer?> { null }
