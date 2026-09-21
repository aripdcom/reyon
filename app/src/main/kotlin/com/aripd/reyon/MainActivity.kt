package com.aripd.reyon

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.ui.theme.ReyonTheme

class MainActivity : ComponentActivity() {

    /** Dokunarak keşif (TalkBack) açık mı; çubuk kararı ve alt pay buna bakar. */
    private val touchExploring = mutableStateOf(false)

    /** Dokunarak keşif açılıp kapanınca çubuk kararı ve alt pay yenilenir. */
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
        touchExploring.value = enabled
        hideSystemBars()
    }

    /**
     * Seçili dili uygular. Android 13+ bunu sistem düzeyinde yaptığı için
     * [AppLocale.wrap] orada bağlamı olduğu gibi döndürür; 8-12'de kaydedilen
     * dile sarar.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val accessibility = getSystemService(AccessibilityManager::class.java)
        touchExploring.value = accessibility?.isTouchExplorationEnabled == true
        accessibility?.addTouchExplorationStateChangeListener(touchExplorationListener)
        hideSystemBars()
        setContent {
            ReyonTheme {
                ExplorationInset(exploring = touchExploring.value) {
                    ReyonApp(onQuit = { finish() })
                }
            }
        }
    }

    override fun onDestroy() {
        getSystemService(AccessibilityManager::class.java)?.removeTouchExplorationStateChangeListener(touchExplorationListener)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Tam ekran: gezinme ve durum çubukları gizlenir; kenardan kaydırınca
     * geçici olarak görünüp kendiliğinden kaybolurlar. Ekranlar
     * safeDrawingPadding kullandığından çentik payı korunur.
     *
     * Dokunarak keşif (TalkBack) açıkken çubuklar gizlenmez: gizli çubuğun
     * bölgesine çizilen düğmelerin erişilebilirlik sınırı bazı cihazlarda
     * sıfırlanıyor ve ekran okuyucu oraya inemiyor (docs/oyun-testi.md,
     * Reyon Sipariş bulgu 3). Çubuklar görünürken içerik onların üstünde kalır.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val exploring = getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        if (exploring) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Dokunarak keşif açıkken alta durum çubuğu yüksekliği kadar ek pay. SM-A515F'te
 * erişilebilirlik penceresi uygulama alanının boyunda ama y = 0'a çakılı
 * geliyor; alanın son 33 dp'si (durum çubuğu kadar) ağaçtan düşüyor ve oradaki
 * düğmelere ekran okuyucu inemiyor (docs/oyun-testi.md, Reyon Sipariş doğrulama
 * turu). Pay, en alttaki eylem satırını o bandın üstüne çeker; keşif kapalıyken
 * sıfırdır.
 */
@Composable
private fun ExplorationInset(exploring: Boolean, content: @Composable () -> Unit) {
    val bottom = if (exploring) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    Box(modifier = Modifier.fillMaxSize().padding(bottom = bottom)) {
        content()
    }
}
