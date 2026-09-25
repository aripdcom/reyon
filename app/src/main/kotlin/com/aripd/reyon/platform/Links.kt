package com.aripd.reyon.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.aripd.reyon.R

/** Dışa açılan bağlantılar. Uygulama ağa çıkmaz; bağlantıyı cihazdaki tarayıcı açar. */
object Links {
    const val CONTACT = "reyon@aripd.com"

    // Site kendi alan adında (GitHub Pages, Settings → Pages'te tanımlı özel alan
    // adı). tools/check_site.py sitedeki kendi bağlantıların bu adla uyuştuğunu
    // denetliyor; alan adı değişirse ikisi birlikte değişir.
    const val SITE = "https://reyon.aripd.com"

    // Gizlilik politikasının İngilizce sayfası; Play Console'daki adres de bu.
    // Her dilin kendi sayfası var ([privacyFor]). 1.1.0'a kadar adres
    // gizlilik.html'di; o sayfa sitede durur ve okurun diline yönlendirir.
    const val PRIVACY = "https://reyon.aripd.com/privacy.html"
    const val SOURCE = "https://github.com/aripdcom/reyon"
    const val REPORT = "https://github.com/aripdcom/reyon/issues/new"
    const val LICENSE = "https://github.com/aripdcom/reyon/blob/main/LICENSE"
    const val ANDROIDX = "https://developer.android.com/jetpack"
    const val KOTLIN = "https://kotlinlang.org"
    const val PLEX = "https://github.com/IBM/plex"

    /**
     * Sitenin uygulama dilindeki ana sayfası: İngilizce kökte, diğer diller
     * kendi klasöründe (`reyon.aripd.com/tr/`). Bilinmeyen dil İngilizceye düşer.
     */
    fun siteFor(tag: String?): String = if (tag.isEnglishOrNull()) "$SITE/" else "$SITE/$tag/"

    /** Gizlilik politikasının uygulama dilindeki sayfası (`reyon.aripd.com/tr/privacy.html`). */
    fun privacyFor(tag: String?): String = if (tag.isEnglishOrNull()) PRIVACY else "$SITE/$tag/privacy.html"

    private fun String?.isEnglishOrNull(): Boolean = this == null || this == "en" || this !in AppLocale.TAGS

    /**
     * E-posta uygulamasını adresi doldurulmuş olarak açar.
     *
     * ACTION_SENDTO + mailto: yalnızca e-posta uygulamalarını hedefler;
     * ACTION_VIEW tarayıcıyı da aday gösterirdi. Uygulama yine ağa çıkmaz,
     * mektubu kullanıcının kendi e-posta uygulaması gönderir.
     */
    fun email(context: Context, address: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Bağlantıyı tarayıcıda açar; tarayıcı yoksa kısa bir uyarı gösterir. */
    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
