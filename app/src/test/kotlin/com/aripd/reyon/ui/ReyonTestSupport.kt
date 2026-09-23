package com.aripd.reyon.ui

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.str

/**
 * Reyon ekran testlerinin ortak hijyeni. Robolectric aynı JVM'deki testler
 * arasında SharedPreferences'ı paylaşabiliyor ve görünüm modeli etkinlik
 * yok edilirken (test bittikten, @After çalıştıktan sonra) açık turu yeniden
 * kaydediyor. O yüzden her test önce turu bırakır (geri oku Görevler'e döner,
 * tur saklanır ama ekran boşalır), sonra tercihleri temizler.
 */
object ReyonTestSupport {
    /**
     * Tercihleri temizler. "Nasıl oynanır" kartı ilk girişte kendiliğinden açılıp
     * oyun alanının ortasını kapatıyor; oyun akışını sınayan testler için dört
     * modun kartı görülmüş sayılır. Kartın kendisini [ReyonHomeTest] sınıyor.
     */
    fun clearPrefs(introsSeen: Boolean = true) {
        val editor = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("reyon_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
        if (introsSeen) {
            for (kind in ReyonKind.entries) editor.putBoolean("intro_seen_" + kind.name, true)
        }
        editor.commit()
    }
}

private fun ComposeContentTestRule.homeShown(): Boolean =
    onAllNodesWithTag(HOME_TAG).fetchSemanticsNodes().isNotEmpty()

private fun ComposeContentTestRule.backShown(): Boolean =
    onAllNodesWithContentDescription(str(R.string.back)).fetchSemanticsNodes().isNotEmpty()

/** Ekran hangi durumda açılırsa açılsın Görevler'e getirir. */
fun ComposeContentTestRule.reyonOpenHome() {
    waitUntil(timeoutMillis = 30_000) { homeShown() || backShown() }
    if (!homeShown()) {
        onAllNodesWithContentDescription(str(R.string.back)).onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) { homeShown() }
    }
}

/** Görevler'den alıştırma: formatı seçer, modun satırına dokunur. */
fun ComposeContentTestRule.reyonStartPractice(kind: ReyonKind, level: ReyonLevel = ReyonLevel.KOLAY) {
    reyonOpenHome()
    // Kısa ekranda Görevler kayar: satırlar görünür alana getirilerek basılır.
    onNodeWithText(str(formatName(level))).performScrollTo().performClick()
    onNodeWithTag(homePracticeTag(kind)).performScrollTo().performClick()
}

/** Test sonunda açık turu bırakır; içerik hiç kurulmadıysa sessizce geçer. */
fun ComposeContentTestRule.reyonLeaveRound() {
    try {
        if (!homeShown() && backShown()) {
            onAllNodesWithContentDescription(str(R.string.back)).onFirst().performClick()
            waitForIdle()
        }
    } catch (e: IllegalStateException) {
        // Compose hiyerarşisi yok: test içerik kurmadan bitti.
    }
}
