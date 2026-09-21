package com.aripd.reyon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aripd.reyon.engine.DaySummary
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonOrderGenerator
import com.aripd.reyon.engine.ReyonOrderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

data class OrderResult(
    val score: Int,
    val target: Int,
    val stars: Int,
    val percent: Int,
    val turnover: Float,
    val targetTurnover: Float,
    val service: Int,
    val targetService: Int,
    val record: Boolean,
    val daily: Boolean,
    val day: Long,
)

/**
 * Sipariş modu: gün gün sipariş, gün kapanışı ve hafta sonucu. Süre tutulmaz;
 * ölçüt kârdır. [ReyonOrderState] değişken bir nesnedir, her değişimde
 * [version] artar. Kapanan günün dökümü [summary] ile kart olarak gösterilir.
 */
class ReyonOrderViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ReyonStore(application)

    private val _state = MutableStateFlow<ReyonOrderState?>(null)
    val state: StateFlow<ReyonOrderState?> = _state.asStateFlow()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _summary = MutableStateFlow<DaySummary?>(null)
    val summary: StateFlow<DaySummary?> = _summary.asStateFlow()

    private val _result = MutableStateFlow<OrderResult?>(null)
    val result: StateFlow<OrderResult?> = _result.asStateFlow()

    /** Son ipucunun dokunduğu ürün; −1 yok. Ekran satırı vurgular. */
    private val _hinted = MutableStateFlow(-1)
    val hinted: StateFlow<Int> = _hinted.asStateFlow()

    private val _level = MutableStateFlow(store.lastLevel())
    val level: StateFlow<ReyonLevel> = _level.asStateFlow()

    private val _mode = MutableStateFlow(store.lastMode())
    val mode: StateFlow<ReyonMode> = _mode.asStateFlow()

    private val _records = MutableStateFlow(loadRecords())
    val records: StateFlow<Map<ReyonLevel, ReyonStore.OrderRecord>> = _records.asStateFlow()

    private var generation = 0
    private var runMode = ReyonMode.FREE
    private var runDay = 0L
    private var runSeed = 0L

    private fun todayEpoch(): Long = LocalDate.now().toEpochDay()

    private fun loadRecords(): Map<ReyonLevel, ReyonStore.OrderRecord> {
        val today = todayEpoch()
        return ReyonLevel.entries.mapNotNull { l -> store.orderDailyRecord(today, l)?.let { l to it } }.toMap()
    }

    init {
        val saved = store.restoreOrder()
        if (saved != null) {
            val token = ++generation
            _generating.value = true
            viewModelScope.launch {
                val week = withContext(Dispatchers.Default) { ReyonOrderGenerator.generate(saved.seed, saved.level) }
                if (token != generation) return@launch
                val st = ReyonOrderState(week)
                st.restore(saved.snapshot)
                runMode = saved.mode
                runDay = saved.day
                runSeed = saved.seed
                _state.value = st
                _generating.value = false
                if (st.isComplete) _result.value = resultOf(st, record = false)
                bump()
            }
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

    fun bestPercent(level: ReyonLevel): Int = store.orderBest(level)

    fun newGame() {
        val level = _level.value
        val mode = _mode.value
        val day = todayEpoch()
        val seed = if (mode == ReyonMode.DAILY) ReyonOrderGenerator.dailySeed(day) else Random.nextLong()
        val token = ++generation
        _generating.value = true
        _result.value = null
        _summary.value = null
        _hinted.value = -1
        viewModelScope.launch {
            val week = withContext(Dispatchers.Default) { ReyonOrderGenerator.generate(seed, level) }
            if (token != generation) return@launch
            runMode = mode
            runDay = day
            runSeed = seed
            _state.value = ReyonOrderState(week)
            _generating.value = false
            persist()
            bump()
        }
    }

    fun retry() = newGame()

    fun toMenu() {
        persist()
        generation++
        _generating.value = false
        _state.value = null
        _result.value = null
        _summary.value = null
        _hinted.value = -1
        _records.value = loadRecords()
        bump()
    }

    fun persistNow() = persist()

    fun setOrder(item: Int, cases: Int): Boolean {
        val st = _state.value ?: return false
        if (!st.setOrder(item, cases)) return false
        _hinted.value = -1
        afterChange()
        return true
    }

    fun adjust(item: Int, delta: Int): Boolean {
        val st = _state.value ?: return false
        return setOrder(item, st.orderOf(item) + delta)
    }

    /** Uzman önerisi: ilk ayrılan sipariş düzeltilir; ürün dizini döner. */
    fun hint(): Int? {
        val st = _state.value ?: return null
        val i = st.hint() ?: return null
        _hinted.value = i
        afterChange()
        return i
    }

    /** Günü kapatır; döküm kartı açılır, hafta bittiyse sonuç hesaplanır. */
    fun closeDay(): DaySummary? {
        val st = _state.value ?: return null
        val s = st.closeDay() ?: return null
        _hinted.value = -1
        _summary.value = s
        if (st.isComplete) finish(st)
        afterChange()
        return s
    }

    /** Döküm kartını kapatır. */
    fun dismissSummary() {
        _summary.value = null
        bump()
    }

    private fun finish(st: ReyonOrderState) {
        val score = st.profit
        val target = st.order.target
        var record = false
        if (runMode == ReyonMode.DAILY) {
            if (store.saveOrderDaily(runDay, st.order.level, score, target)) record = true
            _records.value = loadRecords()
        } else {
            val percent = if (target > 0) score * 100 / target else 0
            if (store.saveOrderBest(st.order.level, percent)) record = true
        }
        _result.value = resultOf(st, record)
    }

    private fun resultOf(st: ReyonOrderState, record: Boolean): OrderResult {
        val score = st.profit
        val target = st.order.target
        val percent = if (target > 0) score * 100 / target else 0
        return OrderResult(
            score, target, st.order.stars(score), percent,
            st.turnover(), st.order.targetTurnover, st.serviceLevel(), st.order.targetService,
            record, runMode == ReyonMode.DAILY, runDay,
        )
    }

    private fun afterChange() {
        persist()
        bump()
    }

    private fun persist() {
        val st = _state.value ?: return
        store.saveOrder(runSeed, st.order.level, runMode, runDay, st.snapshot())
    }

    private fun bump() {
        _version.update { it + 1 }
    }
}
