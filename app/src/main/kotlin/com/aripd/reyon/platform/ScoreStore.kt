package com.aripd.reyon.platform

import android.content.Context

/** Çözülen tur rekorunu cihazda saklar. Veri cihazdan asla çıkmaz. */
class ScoreStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("reyon_scores", Context.MODE_PRIVATE)

    fun best(): Long = prefs.getLong(KEY_BEST, 0L)

    fun submit(score: Long) {
        if (score > best()) {
            prefs.edit().putLong(KEY_BEST, score).apply()
        }
    }

    private companion object {
        const val KEY_BEST = "solved"
    }
}
