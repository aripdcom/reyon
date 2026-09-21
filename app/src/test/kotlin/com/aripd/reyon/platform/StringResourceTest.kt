package com.aripd.reyon.platform

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Derlenmiş metinlerin değişmezleri.
 *
 * aapt2 iki şeyi sessizce yapar ve ikisi de derlemeyi kırmaz, yalnızca
 * kullanıcı görür: kaçışsız çift tırnağı tırnak aç/kapa sayıp atar, %% ikilisini
 * olduğu gibi bırakır (yüzdeyi tekleştiren şey String.format'tır, argümansız
 * getString onu çalıştırmaz). Kaynak tarafını tools/check_strings.py denetler;
 * burada okunan, aapt2'nin gerçekten ne ürettiğidir.
 */
@RunWith(AndroidJUnit4::class)
class StringResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theLicenseNoteKeepsItsQuotes() {
        assertTrue(
            "marka adı tırnak içinde kalmalı",
            context.getString(R.string.about_app_license).contains("\"Reyon\""),
        )
    }

    /**
     * Arapça'da sayı taşıyan metinler Latin rakamla mı yazılıyor?
     *
     * `getString(id, sayı)` cihazın yerel ayarıyla biçimler ve Arapça'da
     * `%d`'yi Hint-Arap rakamına çevirir; stok sayaçları cihazda `٩/١٢`
     * görünüyordu. [appText] Locale.ROOT ile biçimlediği için rakamlar Latin
     * kalır. Test ham okumanın hâlâ Hint-Arap bastığını da doğrular: kural
     * gerçekten gerekli.
     */
    @Test
    fun numbersStayLatinInArabic() {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val arabic = context.createConfigurationContext(config).resources
        val id = R.string.reyon_order_item_desc_fmt

        val safe = appText(arabic, id, "Kola", 9, 12, 14)
        assertTrue("metin Arapça kaynaktan gelmeli", safe.contains("المخزون"))
        assertTrue("rakamlar Latin olmalı: $safe", safe.contains("9") && safe.contains("12"))
        assertFalse("Hint-Arap rakamı kalmamalı: $safe", safe.any { it in '٠'..'٩' })

        val raw = arabic.getString(id, "Kola", 9, 12, 14)
        assertTrue(
            "ham okuma Hint-Arap basıyor, kural bu yüzden var: $raw",
            raw.any { it in '٠'..'٩' },
        )
    }

    /**
     * Yüzde taşıyan metinler biçimlenince tek yüzde işareti bırakır.
     *
     * Kaynakta `%%` yazmak zorunlu (aapt2 tek `%`'yi biçim belirteci sanır);
     * ekrana `%` olarak inmesini String.format sağlıyor. Metin argümansız
     * okunursa kullanıcı `%%` görür — testin koruduğu şey bu.
     */
    @Test
    fun percentSignsCollapseWhenFormatted() {
        val formatted = appText(context, R.string.reyon_sales_best_fmt, 80)
        assertTrue("yüzde ekrana inmeli: $formatted", formatted.contains("80%"))
        assertFalse("çift yüzde kalmamalı: $formatted", formatted.contains("%%"))
    }
}
