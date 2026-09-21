package com.aripd.reyon.engine

/** Ürün kategorileri; reyon içindeki blok mantığının temeli. */
enum class Category { ICECEK, ATISTIRMALIK, KAHVALTILIK, TEMIZLIK, BAKIM }

/**
 * Ürün türleri. Ad ve simge uygulama katmanında; motor yalnızca kategori ve
 * "büyük boyda ağır sayılabilir" bilgisini taşır.
 */
enum class Kind(val category: Category, val heavyProne: Boolean = false) {
    KOLA(Category.ICECEK, true),
    SU(Category.ICECEK, true),
    GAZOZ(Category.ICECEK),
    MEYVE_SUYU(Category.ICECEK, true),
    AYRAN(Category.ICECEK),
    SODA(Category.ICECEK),
    LIMONATA(Category.ICECEK),
    CIPS(Category.ATISTIRMALIK),
    KRAKER(Category.ATISTIRMALIK),
    KURUYEMIS(Category.ATISTIRMALIK),
    CIKOLATA(Category.ATISTIRMALIK),
    GOFRET(Category.ATISTIRMALIK),
    BISKUVI(Category.ATISTIRMALIK),
    SOS(Category.ATISTIRMALIK),
    BAL(Category.KAHVALTILIK),
    RECEL(Category.KAHVALTILIK),
    PEYNIR(Category.KAHVALTILIK),
    ZEYTIN(Category.KAHVALTILIK, true),
    TEREYAGI(Category.KAHVALTILIK),
    YUMURTA(Category.KAHVALTILIK),
    SUT(Category.KAHVALTILIK, true),
    DETERJAN(Category.TEMIZLIK, true),
    CAMASIR_SUYU(Category.TEMIZLIK, true),
    BULASIK_DETERJANI(Category.TEMIZLIK),
    YUMUSATICI(Category.TEMIZLIK, true),
    SUNGER(Category.TEMIZLIK),
    KAGIT_HAVLU(Category.TEMIZLIK),
    COP_POSETI(Category.TEMIZLIK),
    SAMPUAN(Category.BAKIM),
    SABUN(Category.BAKIM),
    DIS_MACUNU(Category.BAKIM),
    DEODORANT(Category.BAKIM),
    TIRAS_KOPUGU(Category.BAKIM),
    ISLAK_MENDIL(Category.BAKIM),
    PAMUK(Category.BAKIM),
}

/** Soyut markalar; ad ve renk uygulama katmanında. */
enum class Brand { A, B, C, D }

/**
 * Zorluk: raf boyutu, ürün sayısı aralığı, kategori sayısı, verili ürün
 * sayısı, ipucu üst sınırı ve çıkarım için izin verilen teknikler.
 */
enum class ReyonLevel(
    val rows: Int,
    val cols: Int,
    val products: IntRange,
    val categories: IntRange,
    val givens: IntRange,
    val maxClues: Int,
    val techniques: Int,
) {
    KOLAY(3, 4, 5..7, 2..2, 1..2, 6, Tech.ALL and Tech.NARY.inv()),
    ORTA(4, 5, 8..10, 2..3, 0..1, 9, Tech.ALL),
    ZOR(4, 6, 10..12, 3..4, 0..0, 12, Tech.ALL),
}

/** Raf ünitesi geometrisi. Göz indeksi: satır × sütun + sütun; ürün bloğu sola hizalı başlar. */
class Board(val rows: Int, val cols: Int) {
    val slots: Int get() = rows * cols

    /** Göz hizası rafı (üstten ikinci). */
    val eyeRow: Int get() = 1
    val bottomRow: Int get() = rows - 1

    fun idx(row: Int, col: Int): Int = row * cols + col
    fun rowOf(idx: Int): Int = idx / cols
    fun colOf(idx: Int): Int = idx % cols

    /** [idx] gözünden başlayan [facings] genişliğindeki bloğun kapladığı gözler (bit maskesi). */
    fun mask(idx: Int, facings: Int): Int = ((1 shl facings) - 1) shl idx

    /** [facings] genişliğindeki bir ürünün olası tüm başlangıç gözleri. */
    fun placements(facings: Int): Int {
        var m = 0
        for (r in 0 until rows) for (c in 0..cols - facings) m = m or (1 shl idx(r, c))
        return m
    }

    /** [row] rafındaki başlangıç gözleri. */
    fun placementsInRow(row: Int, facings: Int): Int {
        var m = 0
        for (c in 0..cols - facings) m = m or (1 shl idx(row, c))
        return m
    }
}

class Product(
    val id: Int,
    val kind: Kind,
    val brand: Brand,
    /** Boy: 1 küçük, 2 orta, 3 büyük. */
    val size: Int,
    /** Yüz sayısı: rafta kapladığı bitişik göz sayısı. */
    val facings: Int,
    /** Yüksek marjlı: göz hizası kuralına tabi. */
    val premium: Boolean,
    /** Ağır: alt raf kuralına tabi. */
    val heavy: Boolean,
    /** Talep (1–3): satış modunda konum puanının çarpanı. */
    val demand: Int = 2,
) {
    val category: Category get() = kind.category
    override fun toString(): String = "${kind.name}#$id(${brand.name},b$size,f$facings${if (premium) ",★" else ""}${if (heavy) ",ağır" else ""})"
}

/** Bir ürünün rafı ve başlangıç sütunu. */
data class Placement(val row: Int, val col: Int)

/**
 * Planogram brifi: her ipucu bir yerleşim ilkesidir. Metinler uygulama
 * katmanında; motor yapısal bilgiyi taşır. Ürünler kimliğiyle anılır.
 */
sealed class Clue {
    /** Verili: ürün baştan rafta (kilitli). */
    data class Placed(val product: Int, val row: Int, val col: Int) : Clue()

    /** Ürün belirli rafta. */
    data class OnShelf(val product: Int, val row: Int) : Clue()

    /** Ürünün bloğu belirli gözü (sütunu) kapsıyor. */
    data class InSlot(val product: Int, val col: Int) : Clue()

    /** Ürün rafın en solunda ya da en sağında. */
    data class AtEdge(val product: Int, val left: Boolean) : Clue()

    /** İki ürün aynı rafta yan yana (blokları birbirine değer). */
    data class Adjacent(val a: Int, val b: Int) : Clue()

    /** [a] aynı rafta [b]'nin solunda (bitişik olmak zorunda değil). */
    data class LeftOf(val a: Int, val b: Int) : Clue()

    data class SameShelf(val a: Int, val b: Int) : Clue()

    data class DifferentShelf(val a: Int, val b: Int) : Clue()

    /** [a], [b]'nin hemen üstündeki rafta ve sütunları kesişiyor. */
    data class Above(val a: Int, val b: Int) : Clue()

    /** Yüksek marjlı (★) ürünlerin tümü göz hizası rafında. */
    data object EyeLevel : Clue()

    /** Ağır ürünlerin tümü en alt rafta. */
    data object HeavyBottom : Clue()

    /** Kategorinin ürünleri tek rafta, bitişik bir blok. */
    data class CategoryBlock(val category: Category) : Clue()

    /** İki kategori hiçbir rafı paylaşmaz. */
    data class CategoriesApart(val a: Category, val b: Category) : Clue()

    /** Marka dikey blok: markanın rafları ardışık, komşu raflardaki blokların sütunları kesişir. */
    data class BrandVertical(val brand: Brand) : Clue()

    /** Aynı raftaki marka ürünleri soldan sağa büyüyen boyda. */
    data class SizeFlow(val brand: Brand) : Clue()
}

enum class ClueStatus { PENDING, SATISFIED, VIOLATED }

/** Üretim iş sayaçları; duvar saatine değil işe bakılır (bkz. docs/oyun-testi.md). */
data class GenStats(
    /** Düzen örnekleme denemesi. */
    val attempts: Int,
    /** Çözücünün gezdiği toplam düğüm. */
    val solverNodes: Long,
    /** Aday ipucu sayısı (son denemede). */
    val candidates: Int,
    /** Çıkarımda kullanılan teknikler (bit maskesi). */
    val techniques: Int,
)

class ReyonPuzzle internal constructor(
    val seed: Long,
    val level: ReyonLevel,
    val products: List<Product>,
    val clues: List<Clue>,
    internal val solutionIdx: IntArray,
    val stats: GenStats,
) {
    val rows: Int get() = level.rows
    val cols: Int get() = level.cols
    val board = Board(rows, cols)

    /** Verili (kilitli) ürünler. */
    val givens: List<Clue.Placed> = clues.filterIsInstance<Clue.Placed>()

    /** Brif satırları: veriliyle değil kuralla anlatılan ipuçları. */
    val brief: List<Clue> = clues.filter { it !is Clue.Placed }

    init {
        require(products.sumOf { it.facings } == board.slots) { "ürünler rafı tam doldurmalı" }
        require(products.withIndex().all { (i, p) -> p.id == i }) { "ürün kimlikleri sıralı olmalı" }
    }

    fun solution(product: Int): Placement = Placement(board.rowOf(solutionIdx[product]), board.colOf(solutionIdx[product]))

    fun product(id: Int): Product = products[id]
}
