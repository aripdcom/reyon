package com.aripd.reyon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aripd.reyon.engine.AuditTap
import com.aripd.reyon.engine.ReyonAuditGenerator
import com.aripd.reyon.engine.ReyonAuditState
import com.aripd.reyon.engine.ReyonLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

data class AuditResult(val time: Int, val mistakes: Int, val hints: Int, val record: Boolean, val daily: Boolean, val day: Long)

/**
 * Denetim modu: üretim arka planda, dokunma sonuçları, süre, kalıcılık ve
 * günlük kayıt. [ReyonAuditState] değişken bir nesnedir; her değişimde
 * [version] artar.
 */
class ReyonAuditViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ReyonStore(application)

    private val _state = MutableStateFlow<ReyonAuditState?>(null)
    val state: StateFlow<ReyonAuditState?> = _state.asStateFlow()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _elapsed = MutableStateFlow(0)
    val elapsed: StateFlow<Int> = _elapsed.asStateFlow()

    private val _result = MutableStateFlow<AuditResult?>(null)
    val result: StateFlow<AuditResult?> = _result.asStateFlow()

    /** Son yanlış dokunulan göz (indeks); −1 yok. Ekran kısa süre kırmızı gösterir. */
    private val _missSlot = MutableStateFlow(-1)
    val missSlot: StateFlow<Int> = _missSlot.asStateFlow()

    private val _level = MutableStateFlow(store.lastLevel())
    val level: StateFlow<ReyonLevel> = _level.asStateFlow()

    private val _mode = MutableStateFlow(store.lastMode())
    val mode: StateFlow<ReyonMode> = _mode.asStateFlow()

    private val _records = MutableStateFlow(loadRecords())
    val records: StateFlow<Map<ReyonLevel, ReyonStore.AuditRecord>> = _records.asStateFlow()

    /** Süre yalnız ekran önündeyken işler; ekran açılınca LifecycleResumeEffect çözer. */
    private val _timerPaused = MutableStateFlow(true)
    private var generation = 0
    private var runMode = ReyonMode.FREE
    private var runDay = 0L
    private var runSeed = 0L

    private fun todayEpoch(): Long = LocalDate.now().toEpochDay()

    private fun loadRecords(): Map<ReyonLevel, ReyonStore.AuditRecord> {
        val today = todayEpoch()
        return ReyonLevel.entries.mapNotNull { l -> store.auditDailyRecord(today, l)?.let { l to it } }.toMap()
    }

    init {
        viewModelScope.launch {
            while (true) {
                combine(_state, _timerPaused, _result) { st, paused, result ->
                    !paused && st != null && result == null && !st.isComplete
                }.first { it }
                delay(1000L)
                val st = _state.value
                if (!_timerPaused.value && st != null && _result.value == null && !st.isComplete) {
                    _elapsed.update { it + 1 }
                }
            }
        }
    }

    /**
     * Görevler'den açılış: aynı vaka bellekte ya da kayıtta yarım duruyorsa sürer,
     * yoksa yenisi başlar ([ReyonViewModel.open] ile aynı kural).
     */
    fun open(mode: ReyonMode, level: ReyonLevel) {
        _mode.value = mode
        _level.value = level
        store.saveLast(level, mode)
        val day = todayEpoch()
        val st = _state.value
        if (st != null && sameCase(runMode, runDay, st.audit.level, mode, level, day)) return
        if (_generating.value && pending?.let { sameCase(it.mode, it.day, it.level, mode, level, day) } == true) return
        val saved = store.restoreAudit()
        if (saved != null && sameCase(saved.mode, saved.day, saved.level, mode, level, day)) restore(saved) else newGame()
    }

    private var pending: ReyonStore.SavedAudit? = null

    private fun restore(saved: ReyonStore.SavedAudit) {
        val token = ++generation
        pending = saved
        _generating.value = true
        _result.value = null
        _missSlot.value = -1
        viewModelScope.launch {
            val audit = withContext(Dispatchers.Default) { ReyonAuditGenerator.generate(saved.seed, saved.level) }
            if (token != generation) return@launch
            val st = ReyonAuditState(audit)
            st.restore(saved.snapshot)
            runMode = saved.mode
            runDay = saved.day
            runSeed = saved.seed
            _elapsed.value = saved.elapsed
            _state.value = st
            _generating.value = false
            pending = null
            if (st.isComplete) store.clearAudit()
            bump()
        }
    }

    fun setLevel(level: ReyonLevel) {
        _level.value = level
        store.saveLast(level, _mode.value)
    }

    fun setMode(mode: ReyonMode) {
        _mode.value = mode
        store.saveLast(_level.value, mode)
    }

    fun bestTime(level: ReyonLevel): Int = store.auditBest(level)

    fun newGame() {
        val level = _level.value
        val mode = _mode.value
        val day = todayEpoch()
        val seed = if (mode == ReyonMode.DAILY) ReyonAuditGenerator.dailySeed(day) else Random.nextLong()
        val token = ++generation
        pending = ReyonStore.SavedAudit(seed, level, mode, day, IntArray(0), 0)
        _generating.value = true
        _result.value = null
        _missSlot.value = -1
        viewModelScope.launch {
            val audit = withContext(Dispatchers.Default) { ReyonAuditGenerator.generate(seed, level) }
            if (token != generation) return@launch
            runMode = mode
            runDay = day
            runSeed = seed
            _elapsed.value = 0
            _state.value = ReyonAuditState(audit)
            _generating.value = false
            pending = null
            persist()
            bump()
        }
    }

    fun retry() = newGame()

    fun toMenu() {
        persist()
        generation++
        pending = null
        _generating.value = false
        _state.value = null
        _result.value = null
        _missSlot.value = -1
        _records.value = loadRecords()
        bump()
    }

    fun setPaused(paused: Boolean) {
        _timerPaused.value = paused
        if (paused) persist()
    }

    fun clearMiss() {
        _missSlot.value = -1
    }

    fun tap(row: Int, col: Int): AuditTap {
        val st = _state.value ?: return AuditTap.Already
        if (st.isComplete) return AuditTap.Already
        val r = st.tap(row, col)
        when (r) {
            is AuditTap.Found -> {
                _missSlot.value = -1
                afterChange()
            }
            AuditTap.Miss -> {
                _missSlot.value = st.audit.board.idx(row, col)
                afterChange()
            }
            AuditTap.Already -> Unit
        }
        return r
    }

    fun hint(): Int? {
        val st = _state.value ?: return null
        val i = st.hint() ?: return null
        _missSlot.value = -1
        afterChange()
        return i
    }

    private fun afterChange() {
        val st = _state.value ?: return
        if (st.isComplete && _result.value == null) {
            val time = _elapsed.value
            val daily = runMode == ReyonMode.DAILY
            val clean = st.mistakes == 0 && st.hintsUsed == 0
            var record = false
            if (daily) {
                val first = store.auditDailyRecord(runDay, st.audit.level) == null
                store.saveAuditDaily(runDay, st.audit.level, time, st.mistakes, st.hintsUsed)
                record = first && clean
                _records.value = loadRecords()
            }
            if (clean && store.saveAuditBest(st.audit.level, time)) record = true
            if (!daily) store.saveLastPractice(ReyonKind.AUDIT, st.audit.level, time, st.mistakes)
            _result.value = AuditResult(time, st.mistakes, st.hintsUsed, record, daily, runDay)
            store.clearAudit()
        } else {
            persist()
        }
        bump()
    }

    private fun persist() {
        val st = _state.value ?: return
        if (st.isComplete || _result.value != null) return
        store.saveAudit(runSeed, st.audit.level, runMode, runDay, st.snapshot(), _elapsed.value)
    }

    private fun bump() {
        _version.update { it + 1 }
    }
}
