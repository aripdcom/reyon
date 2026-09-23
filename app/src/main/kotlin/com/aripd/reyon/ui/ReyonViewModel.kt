package com.aripd.reyon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aripd.reyon.engine.ReyonGenerator
import com.aripd.reyon.engine.ReyonHint
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonPuzzle
import com.aripd.reyon.engine.ReyonState
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

/** Çözüm sonucu: süre, ipucu, rekor bilgisi. */
data class ReyonResult(val time: Int, val hints: Int, val record: Boolean, val daily: Boolean, val day: Long)

/**
 * Reyon sıra tabanlıdır; VM üretimi (arka planda), seçim mantığını, süreyi,
 * kalıcılığı ve günlük kayıtları yönetir. [ReyonState] değişken bir nesnedir;
 * her değişimde [version] artar ve ekran durumu doğrudan okur.
 */
class ReyonViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ReyonStore(application)

    /** null = bulmaca yok; menü gösterilir. */
    private val _state = MutableStateFlow<ReyonState?>(null)
    val state: StateFlow<ReyonState?> = _state.asStateFlow()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _elapsed = MutableStateFlow(0)
    val elapsed: StateFlow<Int> = _elapsed.asStateFlow()

    private val _selected = MutableStateFlow(-1)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    /** Vurgulanan brif satırı (dizin); −1 yok. */
    private val _highlightClue = MutableStateFlow(-1)
    val highlightClue: StateFlow<Int> = _highlightClue.asStateFlow()

    private val _lastHint = MutableStateFlow<ReyonHint?>(null)
    val lastHint: StateFlow<ReyonHint?> = _lastHint.asStateFlow()

    private val _result = MutableStateFlow<ReyonResult?>(null)
    val result: StateFlow<ReyonResult?> = _result.asStateFlow()

    private val _kind = MutableStateFlow(store.lastKind())
    val kind: StateFlow<ReyonKind> = _kind.asStateFlow()

    /**
     * Bir modun ekranı açık mı; değilse Görevler gösterilir. Saklanmaz: uygulama
     * her açılışta Görevler'le başlar, yarım vaka o vakaya dokununca sürer.
     */
    private val _screenOpen = MutableStateFlow(false)
    val screenOpen: StateFlow<Boolean> = _screenOpen.asStateFlow()

    private val _level = MutableStateFlow(store.lastLevel())
    val level: StateFlow<ReyonLevel> = _level.asStateFlow()

    /** Görevler'de seçili alıştırma formatı; günün vakası kendi formatında açılır, bunu değiştirmez. */
    private val _practiceLevel = MutableStateFlow(store.practiceLevel())
    val practiceLevel: StateFlow<ReyonLevel> = _practiceLevel.asStateFlow()

    fun setPracticeLevel(level: ReyonLevel) {
        _practiceLevel.value = level
        store.savePracticeLevel(level)
    }

    private val _mode = MutableStateFlow(store.lastMode())
    val mode: StateFlow<ReyonMode> = _mode.asStateFlow()

    private val _records = MutableStateFlow(loadRecords())
    val records: StateFlow<Map<ReyonLevel, ReyonStore.Record>> = _records.asStateFlow()

    /** Süre yalnız ekran önündeyken işler; ekran açılınca LifecycleResumeEffect çözer. */
    private val _timerPaused = MutableStateFlow(true)
    private var generation = 0
    private var runMode = ReyonMode.FREE
    private var runDay = 0L
    private var runSeed = 0L

    val puzzle: ReyonPuzzle? get() = _state.value?.puzzle

    private fun todayEpoch(): Long = LocalDate.now().toEpochDay()

    private fun loadRecords(): Map<ReyonLevel, ReyonStore.Record> {
        val today = todayEpoch()
        return ReyonLevel.entries.mapNotNull { l -> store.dailyRecord(today, l)?.let { l to it } }.toMap()
    }

    init {
        viewModelScope.launch {
            while (true) {
                combine(_state, _timerPaused, _result) { st, paused, result ->
                    !paused && st != null && result == null && !st.isSolved
                }.first { it }
                delay(1000L)
                val st = _state.value
                if (!_timerPaused.value && st != null && _result.value == null && !st.isSolved) {
                    _elapsed.update { it + 1 }
                }
            }
        }
    }

    fun setKind(kind: ReyonKind) {
        _kind.value = kind
        store.saveKind(kind)
    }

    /** Görevler'den bir modun ekranına geçiş; vakayı o modun görünüm modeli açar. */
    fun openScreen(kind: ReyonKind) {
        setKind(kind)
        _screenOpen.value = true
    }

    /** Görevler'e dönüş. */
    fun closeScreen() {
        _screenOpen.value = false
    }

    /**
     * Görevler'den açılış. Aynı vaka (mod, format ve günün vakasında gün) bellekte
     * ya da kayıtta yarım duruyorsa oradan sürer; yoksa yenisi başlar. Günün vakası
     * tohumdan üretildiği için yeniden açmak aynı rafı verir.
     */
    fun open(mode: ReyonMode, level: ReyonLevel) {
        _mode.value = mode
        _level.value = level
        store.saveLast(level, mode)
        val day = todayEpoch()
        val st = _state.value
        if (st != null && sameCase(runMode, runDay, st.puzzle.level, mode, level, day)) return
        if (_generating.value && pending?.let { sameCase(it.mode, it.day, it.level, mode, level, day) } == true) return
        val saved = store.restore()
        if (saved != null && !saved.snapshot.isEmpty() && sameCase(saved.mode, saved.day, saved.level, mode, level, day)) {
            restore(saved)
        } else {
            newGame()
        }
    }

    /** Üretimi süren vakanın kimliği; aynı vakaya ikinci dokunuş üretimi yeniden başlatmasın. */
    private var pending: ReyonStore.Saved? = null

    private fun restore(saved: ReyonStore.Saved) {
        val token = ++generation
        pending = saved
        _generating.value = true
        _result.value = null
        _selected.value = -1
        _highlightClue.value = -1
        _lastHint.value = null
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { ReyonGenerator.generate(saved.seed, saved.level) }
            if (token != generation) return@launch
            val st = ReyonState(puzzle)
            st.restore(saved.snapshot, saved.hints)
            runMode = saved.mode
            runDay = saved.day
            runSeed = saved.seed
            _elapsed.value = saved.elapsed
            _state.value = st
            _generating.value = false
            pending = null
            if (st.isSolved) store.clear()
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

    fun bestTime(level: ReyonLevel): Int = store.best(level)

    /** Seçili zorluk ve modda yeni bulmaca; üretim arka planda. */
    fun newGame() {
        val level = _level.value
        val mode = _mode.value
        val day = todayEpoch()
        val seed = if (mode == ReyonMode.DAILY) ReyonGenerator.dailySeed(day) else Random.nextLong()
        val token = ++generation
        pending = ReyonStore.Saved(seed, level, mode, day, IntArray(0), 0, 0)
        _generating.value = true
        _result.value = null
        _selected.value = -1
        _highlightClue.value = -1
        _lastHint.value = null
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { ReyonGenerator.generate(seed, level) }
            if (token != generation) return@launch
            runMode = mode
            runDay = day
            runSeed = seed
            _elapsed.value = 0
            _state.value = ReyonState(puzzle)
            _generating.value = false
            pending = null
            persist()
            bump()
        }
    }

    /** Aynı zorlukta yeni bulmaca (çözüm kartından). */
    fun retry() = newGame()

    /** Menüye dön; yarım bulmaca korunur ve yeniden açılışta sürer. */
    fun toMenu() {
        persist()
        generation++
        pending = null
        _generating.value = false
        _state.value = null
        _selected.value = -1
        _highlightClue.value = -1
        _lastHint.value = null
        _result.value = null
        _records.value = loadRecords()
        bump()
    }

    fun setPaused(paused: Boolean) {
        _timerPaused.value = paused
        if (paused) persist()
    }

    fun select(product: Int) {
        val st = _state.value ?: return
        if (product < 0 || st.isLocked(product)) {
            _selected.value = -1
            return
        }
        _selected.value = if (_selected.value == product) -1 else product
        _highlightClue.value = -1
    }

    fun toggleHighlight(clueIndex: Int) {
        _highlightClue.value = if (_highlightClue.value == clueIndex) -1 else clueIndex
    }

    /**
     * Rafa dokunma: seçili ürün varsa dokunulan gözü kapsayacak şekilde
     * yerleştirir (sola hizalı tercih); seçim yoksa gözdeki ürünü seçer.
     * Dönüş: bir şey değişti mi.
     */
    fun tapSlot(row: Int, col: Int): Boolean {
        val st = _state.value ?: return false
        if (st.isSolved) return false
        val sel = _selected.value
        if (sel < 0) {
            val occupant = st.occupant(row, col)
            if (occupant >= 0 && !st.isLocked(occupant)) {
                _selected.value = occupant
                return true
            }
            return false
        }
        val f = st.puzzle.products[sel].facings
        for (start in col downTo col - f + 1) {
            if (start < 0 || start + f > st.puzzle.cols) continue
            if (st.place(sel, row, start)) {
                _selected.value = -1
                afterChange()
                return true
            }
        }
        return false
    }

    /** Seçili ürünü tepsiye geri alır. */
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
        if (st.isSolved) return
        if (st.undo()) {
            _selected.value = -1
            afterChange()
        }
    }

    fun hint(): ReyonHint? {
        val st = _state.value ?: return null
        if (st.isSolved) return null
        val h = st.hint() ?: return null
        if (!st.applyHint(h)) return null
        _selected.value = -1
        _lastHint.value = h
        afterChange(keepHint = true)
        return h
    }

    private fun afterChange(keepHint: Boolean = false) {
        val st = _state.value ?: return
        if (!keepHint) _lastHint.value = null
        _highlightClue.value = -1
        if (st.isSolved && _result.value == null) {
            val time = _elapsed.value
            val hints = st.hintsUsed
            val daily = runMode == ReyonMode.DAILY
            var record = false
            if (daily) {
                val first = store.dailyRecord(runDay, st.puzzle.level) == null
                store.saveDaily(runDay, st.puzzle.level, time, hints)
                record = first && hints == 0
                _records.value = loadRecords()
            }
            if (hints == 0 && store.saveBest(st.puzzle.level, time)) record = true
            if (!daily) store.saveLastPractice(ReyonKind.PUZZLE, st.puzzle.level, time, hints)
            _result.value = ReyonResult(time, hints, record, daily, runDay)
            store.clear()
        } else {
            persist()
        }
        bump()
    }

    private fun persist() {
        val st = _state.value ?: return
        if (st.isSolved || _result.value != null) return
        store.save(runSeed, st.puzzle.level, runMode, runDay, st.snapshot(), st.hintsUsed, _elapsed.value)
    }

    private fun bump() {
        _version.update { it + 1 }
    }
}

/** İki vaka aynı mı: mod ve format aynı, günün vakasıysa gün de aynı. */
internal fun sameCase(runMode: ReyonMode, runDay: Long, runLevel: ReyonLevel, mode: ReyonMode, level: ReyonLevel, day: Long): Boolean =
    runMode == mode && runLevel == level && (mode == ReyonMode.FREE || runDay == day)
