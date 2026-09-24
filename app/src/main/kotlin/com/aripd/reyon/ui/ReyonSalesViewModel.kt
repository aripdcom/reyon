package com.aripd.reyon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonSalesGenerator
import com.aripd.reyon.engine.ReyonSalesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

data class SalesResult(val score: Int, val target: Int, val stars: Int, val percent: Int, val record: Boolean, val daily: Boolean, val day: Long)

/** Sonuç görünümü: sonuç kartı, hedef diziliş ya da oyuncunun dizilişi. */
enum class SalesReview { RESULT, TARGET, MINE }

/**
 * Satış modu: serbest yerleştirme, canlı puan, tamamlama ve inceleme.
 * Süre tutulmaz; ölçüt puandır. [ReyonSalesState] değişken bir nesnedir,
 * her değişimde [version] artar.
 */
class ReyonSalesViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ReyonStore(application)

    private val _state = MutableStateFlow<ReyonSalesState?>(null)
    val state: StateFlow<ReyonSalesState?> = _state.asStateFlow()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _selected = MutableStateFlow(-1)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _result = MutableStateFlow<SalesResult?>(null)
    val result: StateFlow<SalesResult?> = _result.asStateFlow()

    private val _review = MutableStateFlow(SalesReview.RESULT)
    val review: StateFlow<SalesReview> = _review.asStateFlow()

    private val _level = MutableStateFlow(store.lastLevel())
    val level: StateFlow<ReyonLevel> = _level.asStateFlow()

    private val _mode = MutableStateFlow(store.lastMode())
    val mode: StateFlow<ReyonMode> = _mode.asStateFlow()

    private val _records = MutableStateFlow(loadRecords())
    val records: StateFlow<Map<ReyonLevel, ReyonStore.SalesRecord>> = _records.asStateFlow()

    private var generation = 0
    private var runMode = ReyonMode.FREE
    private var runDay = 0L
    private var runSeed = 0L

    private fun todayEpoch(): Long = LocalDate.now().toEpochDay()

    private fun loadRecords(): Map<ReyonLevel, ReyonStore.SalesRecord> {
        val today = todayEpoch()
        return ReyonLevel.entries.mapNotNull { l -> store.salesDailyRecord(today, l)?.let { l to it } }.toMap()
    }

    /**
     * Görevler'den açılış: aynı vaka bellekte ya da kayıtta duruyorsa sürer (bitmişse
     * sonucu yeniden gösterilir), yoksa yenisi başlar ([ReyonViewModel.open] ile aynı kural).
     */
    fun open(mode: ReyonMode, level: ReyonLevel) {
        _mode.value = mode
        _level.value = level
        store.saveLast(level, mode)
        val day = todayEpoch()
        val st = _state.value
        if (st != null && sameCase(runMode, runDay, st.sales.level, mode, level, day)) return
        if (_generating.value && pending?.let { sameCase(it.mode, it.day, it.level, mode, level, day) } == true) return
        val saved = store.restoreSales()
        if (saved != null && sameCase(saved.mode, saved.day, saved.level, mode, level, day)) restore(saved) else newGame()
    }

    private var pending: ReyonStore.SavedSales? = null

    private fun restore(saved: ReyonStore.SavedSales) {
        val token = ++generation
        pending = saved
        _generating.value = true
        _result.value = null
        _review.value = SalesReview.RESULT
        _selected.value = -1
        viewModelScope.launch {
            val sales = withContext(Dispatchers.Default) { ReyonSalesGenerator.generate(saved.seed, saved.level) }
            if (token != generation) return@launch
            val st = ReyonSalesState(sales)
            st.restore(saved.snapshot, saved.finished)
            runMode = saved.mode
            runDay = saved.day
            runSeed = saved.seed
            _state.value = st
            _generating.value = false
            pending = null
            if (st.finished) _result.value = resultOf(st, record = false)
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

    fun bestPercent(level: ReyonLevel): Int = store.salesBest(level)

    fun newGame() {
        val level = _level.value
        val mode = _mode.value
        val day = todayEpoch()
        val seed = if (mode == ReyonMode.DAILY) ReyonSalesGenerator.dailySeed(day) else Random.nextLong()
        val token = ++generation
        pending = ReyonStore.SavedSales(seed, level, mode, day, IntArray(0), false)
        _generating.value = true
        _result.value = null
        _review.value = SalesReview.RESULT
        _selected.value = -1
        viewModelScope.launch {
            val sales = withContext(Dispatchers.Default) { ReyonSalesGenerator.generate(seed, level) }
            if (token != generation) return@launch
            runMode = mode
            runDay = day
            runSeed = seed
            _state.value = ReyonSalesState(sales)
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
        _review.value = SalesReview.RESULT
        _selected.value = -1
        _records.value = loadRecords()
        bump()
    }

    fun persistNow() = persist()

    fun select(product: Int) {
        val st = _state.value ?: return
        if (product < 0 || st.finished) {
            _selected.value = -1
            return
        }
        _selected.value = if (_selected.value == product) -1 else product
    }

    /** Rafa dokunma: seçili ürün varsa yerleştirir, yoksa gözdeki ürünü seçer. */
    fun tapSlot(row: Int, col: Int): Boolean {
        val st = _state.value ?: return false
        if (st.finished) return false
        val sel = _selected.value
        if (sel < 0) {
            val occupant = st.occupant(row, col)
            if (occupant >= 0) {
                _selected.value = occupant
                bump()
                return true
            }
            return false
        }
        val f = st.sales.products[sel].facings
        for (start in col downTo col - f + 1) {
            if (start < 0 || start + f > st.sales.cols) continue
            if (st.place(sel, row, start)) {
                _selected.value = -1
                afterChange()
                return true
            }
        }
        return false
    }

    fun removeSelected(): Boolean {
        val st = _state.value ?: return false
        val sel = _selected.value
        if (sel < 0 || !st.isPlaced(sel)) return false
        val ok = st.remove(sel)
        if (ok) {
            _selected.value = -1
            afterChange()
        }
        return ok
    }

    fun undo() {
        val st = _state.value ?: return
        if (st.undo()) {
            _selected.value = -1
            afterChange()
        }
    }

    /** Tamamla: puanı mühürler, kayıtları günceller. */
    fun finish(): SalesResult? {
        val st = _state.value ?: return null
        if (!st.finish()) return null
        _selected.value = -1
        val score = st.score().total
        val target = st.sales.target
        var record = false
        if (runMode == ReyonMode.DAILY) {
            if (store.saveSalesDaily(runDay, st.sales.level, score, target)) record = true
            _records.value = loadRecords()
        } else {
            val percent = if (target > 0) score * 100 / target else 0
            if (store.saveSalesBest(st.sales.level, percent)) record = true
            store.saveLastPractice(ReyonKind.SALES, st.sales.level, score, target)
        }
        val r = resultOf(st, record)
        _result.value = r
        _review.value = SalesReview.RESULT
        persist()
        bump()
        return r
    }

    private fun resultOf(st: ReyonSalesState, record: Boolean): SalesResult {
        val score = st.score().total
        val target = st.sales.target
        val percent = if (target > 0) score * 100 / target else 0
        return SalesResult(score, target, st.sales.stars(score), percent, record, runMode == ReyonMode.DAILY, runDay)
    }

    /** Düzenlemeye dön: mühür kalkar, sonuç kartı kapanır. */
    fun reopen() {
        val st = _state.value ?: return
        st.reopen()
        _result.value = null
        _review.value = SalesReview.RESULT
        persist()
        bump()
    }

    fun setReview(review: SalesReview) {
        _review.value = review
    }

    private fun afterChange() {
        persist()
        bump()
    }

    private fun persist() {
        val st = _state.value ?: return
        store.saveSales(runSeed, st.sales.level, runMode, runDay, st.snapshot(), st.finished)
    }

    private fun bump() {
        _version.update { it + 1 }
    }
}
