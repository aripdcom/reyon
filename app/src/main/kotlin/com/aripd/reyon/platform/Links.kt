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

    // Site şimdilik proje sayfasında; kendi alan adı bağlanınca burası ve
    // tools/check_site.py'deki karşılığı birlikte değişir.
    const val SITE = "https://aripdcom.github.io/reyon"
    const val PRIVACY = "https://aripdcom.github.io/reyon/gizlilik.html"
    const val SOURCE = "https://github.com/aripdcom/reyon"
    const val REPORT = "https://github.com/aripdcom/reyon/issues/new"
    const val LICENSE = "https://github.com/aripdcom/reyon/blob/main/LICENSE"
    const val ANDROIDX = "https://developer.android.com/jetpack"
    const val KOTLIN = "https://kotlinlang.org"

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
