package com.aripd.reyon.ui

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.aripd.reyon.R
import com.aripd.reyon.str

/**
 * Reyon ekran testlerinin ortak hijyeni. Robolectric aynı JVM'deki testler
 * arasında SharedPreferences'ı paylaşabiliyor ve görünüm modeli etkinlik
 * yok edilirken (test bittikten, @After çalıştıktan sonra) açık turu yeniden
 * kaydediyor. O yüzden her test önce turu bırakır ("Başa dön" durumu
 * boşaltır, sonraki kayıt boş kalır), sonra tercihleri temizler.
 */
object ReyonTestSupport {
    fun clearPrefs() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("reyon_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

/** Ekran hangi durumda açılırsa açılsın menüye getirir ve tür çiplerini bekler. */
fun ComposeContentTestRule.reyonOpenMenu() {
    val back = str(R.string.reyon_to_menu)
    val chip = str(R.string.reyon_kind_sales)
    waitUntil(timeoutMillis = 30_000) {
        onAllNodesWithText(back).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText(chip).fetchSemanticsNodes().isNotEmpty()
    }
    // Bitmiş bir tur geri yüklendiyse hem üst çubukta hem sonuç kartında "Başa dön" olur; ilki yeter.
    if (onAllNodesWithText(back).fetchSemanticsNodes().isNotEmpty()) {
        onAllNodesWithText(back).onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(chip).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Tür çipine basar; yeni türün görünüm modeli sızmış bir turu geri yüklediyse menüye döner. */
fun ComposeContentTestRule.reyonPickKind(kindLabel: String) {
    onNodeWithText(kindLabel).performClick()
    reyonOpenMenu()
}

/** Test sonunda açık turu bırakır; içerik hiç kurulmadıysa sessizce geçer. */
fun ComposeContentTestRule.reyonLeaveRound() {
    val back = str(R.string.reyon_to_menu)
    try {
        if (onAllNodesWithText(back).fetchSemanticsNodes().isNotEmpty()) {
            onAllNodesWithText(back).onFirst().performClick()
            waitForIdle()
        }
    } catch (e: IllegalStateException) {
        // Compose hiyerarşisi yok: test içerik kurmadan bitti.
    }
}
