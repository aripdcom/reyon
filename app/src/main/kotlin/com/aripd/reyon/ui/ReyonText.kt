package com.aripd.reyon.ui

import android.content.res.Resources
import com.aripd.reyon.R
import com.aripd.reyon.engine.Brand
import com.aripd.reyon.engine.Category
import com.aripd.reyon.engine.Clue
import com.aripd.reyon.engine.Deviation
import com.aripd.reyon.engine.DeviationKind
import com.aripd.reyon.engine.ReyonAudit
import com.aripd.reyon.engine.Kind
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.engine.ReyonPuzzle
import com.aripd.reyon.platform.appText

/** Motor yapılarını yerelleştirilmiş metne çevirir; paylaşım ve testler de kullanır. */
object ReyonText {

    fun kind(res: Resources, kind: Kind): String = res.getString(
        when (kind) {
            Kind.KOLA -> R.string.reyon_kind_kola
            Kind.SU -> R.string.reyon_kind_su
            Kind.GAZOZ -> R.string.reyon_kind_gazoz
            Kind.MEYVE_SUYU -> R.string.reyon_kind_meyve_suyu
            Kind.AYRAN -> R.string.reyon_kind_ayran
            Kind.SODA -> R.string.reyon_kind_soda
            Kind.LIMONATA -> R.string.reyon_kind_limonata
            Kind.CIPS -> R.string.reyon_kind_cips
            Kind.KRAKER -> R.string.reyon_kind_kraker
            Kind.KURUYEMIS -> R.string.reyon_kind_kuruyemis
            Kind.CIKOLATA -> R.string.reyon_kind_cikolata
            Kind.GOFRET -> R.string.reyon_kind_gofret
            Kind.BISKUVI -> R.string.reyon_kind_biskuvi
            Kind.SOS -> R.string.reyon_kind_sos
            Kind.BAL -> R.string.reyon_kind_bal
            Kind.RECEL -> R.string.reyon_kind_recel
            Kind.PEYNIR -> R.string.reyon_kind_peynir
            Kind.ZEYTIN -> R.string.reyon_kind_zeytin
            Kind.TEREYAGI -> R.string.reyon_kind_tereyagi
            Kind.YUMURTA -> R.string.reyon_kind_yumurta
            Kind.SUT -> R.string.reyon_kind_sut
            Kind.DETERJAN -> R.string.reyon_kind_deterjan
            Kind.CAMASIR_SUYU -> R.string.reyon_kind_camasir_suyu
            Kind.BULASIK_DETERJANI -> R.string.reyon_kind_bulasik_deterjani
            Kind.YUMUSATICI -> R.string.reyon_kind_yumusatici
            Kind.SUNGER -> R.string.reyon_kind_sunger
            Kind.KAGIT_HAVLU -> R.string.reyon_kind_kagit_havlu
            Kind.COP_POSETI -> R.string.reyon_kind_cop_poseti
            Kind.SAMPUAN -> R.string.reyon_kind_sampuan
            Kind.SABUN -> R.string.reyon_kind_sabun
            Kind.DIS_MACUNU -> R.string.reyon_kind_dis_macunu
            Kind.DEODORANT -> R.string.reyon_kind_deodorant
            Kind.TIRAS_KOPUGU -> R.string.reyon_kind_tiras_kopugu
            Kind.ISLAK_MENDIL -> R.string.reyon_kind_islak_mendil
            Kind.PAMUK -> R.string.reyon_kind_pamuk
        },
    )

    /** Sipariş haftasının gün adı: 0 pazartesi … 6 pazar. */
    fun dow(res: Resources, day: Int): String = res.getString(
        when (day.coerceIn(0, 6)) {
            0 -> R.string.reyon_order_dow_1
            1 -> R.string.reyon_order_dow_2
            2 -> R.string.reyon_order_dow_3
            3 -> R.string.reyon_order_dow_4
            4 -> R.string.reyon_order_dow_5
            5 -> R.string.reyon_order_dow_6
            else -> R.string.reyon_order_dow_7
        },
    )

    fun category(res: Resources, category: Category): String = res.getString(
        when (category) {
            Category.ICECEK -> R.string.reyon_cat_icecek
            Category.ATISTIRMALIK -> R.string.reyon_cat_atistirmalik
            Category.KAHVALTILIK -> R.string.reyon_cat_kahvaltilik
            Category.TEMIZLIK -> R.string.reyon_cat_temizlik
            Category.BAKIM -> R.string.reyon_cat_bakim
        },
    )

    fun brand(res: Resources, brand: Brand): String = res.getString(
        when (brand) {
            Brand.A -> R.string.reyon_brand_a
            Brand.B -> R.string.reyon_brand_b
            Brand.C -> R.string.reyon_brand_c
            Brand.D -> R.string.reyon_brand_d
        },
    )

    fun level(res: Resources, level: ReyonLevel): String = res.getString(
        when (level) {
            ReyonLevel.KOLAY -> R.string.difficulty_easy
            ReyonLevel.ORTA -> R.string.difficulty_medium
            ReyonLevel.ZOR -> R.string.difficulty_hard
        },
    )

    /** Raf adı, bulunma hâlinde: "en üst rafta", "göz hizasında (2. raf)", "3. rafta", "en alt rafta". */
    fun shelfAt(res: Resources, row: Int, rows: Int): String = when {
        row == 0 -> res.getString(R.string.reyon_shelf_top_at)
        row == 1 -> res.getString(R.string.reyon_shelf_eye_at)
        row == rows - 1 -> res.getString(R.string.reyon_shelf_bottom_at)
        else -> appText(res, R.string.reyon_shelf_nth_at, row + 1)
    }

    /** Raf adı, yalın: "en üst raf", "göz hizası (2. raf)", ... */
    fun shelf(res: Resources, row: Int, rows: Int): String = when {
        row == 0 -> res.getString(R.string.reyon_shelf_top)
        row == 1 -> res.getString(R.string.reyon_shelf_eye)
        row == rows - 1 -> res.getString(R.string.reyon_shelf_bottom)
        else -> appText(res, R.string.reyon_shelf_nth, row + 1)
    }

    /** Bulunan sapmanın açıklaması. */
    fun deviation(res: Resources, audit: ReyonAudit, d: Deviation): String {
        fun name(id: Int) = kind(res, audit.plan[id].kind)
        val a = d.products[0]
        return when (d.kind) {
            DeviationKind.SWAP -> appText(res, R.string.reyon_dev_swap_fmt, name(a), name(d.partner))
            DeviationKind.GAP -> {
                val missing = audit.items.none { it.product.id == a }
                res.getString(if (missing) R.string.reyon_dev_missing_fmt else R.string.reyon_dev_gap_fmt, name(a))
            }
            DeviationKind.FOREIGN -> appText(res, R.string.reyon_dev_foreign_fmt, name(a), d.foreign?.let { kind(res, it) } ?: "?")
            DeviationKind.BRAND -> {
                val actual = audit.items.firstOrNull { it.product.id == a }?.product?.brand ?: audit.plan[a].brand
                appText(res, R.string.reyon_dev_brand_fmt, name(a), brand(res, audit.plan[a].brand), brand(res, actual))
            }
            DeviationKind.SIZE -> appText(res, R.string.reyon_dev_size_fmt, name(a))
            DeviationKind.SPILL -> {
                val b = d.partner
                val grownIsA = (audit.items.firstOrNull { it.product.id == a }?.facings ?: 0) > audit.plan[a].facings
                val grown = if (grownIsA) a else b
                val shrunk = if (grownIsA) b else a
                appText(res, R.string.reyon_dev_spill_fmt, name(grown), name(shrunk))
            }
        }
    }

    fun clue(res: Resources, puzzle: ReyonPuzzle, clue: Clue): String {
        fun name(id: Int) = kind(res, puzzle.products[id].kind)
        return when (clue) {
            is Clue.Placed -> appText(res, R.string.reyon_clue_placed, name(clue.product), shelfAt(res, clue.row, puzzle.rows))
            is Clue.OnShelf -> appText(res, R.string.reyon_clue_on_shelf, name(clue.product), shelfAt(res, clue.row, puzzle.rows))
            is Clue.InSlot -> appText(res, R.string.reyon_clue_in_slot, name(clue.product), clue.col + 1)
            is Clue.AtEdge -> res.getString(if (clue.left) R.string.reyon_clue_left_edge else R.string.reyon_clue_right_edge, name(clue.product))
            is Clue.Adjacent -> appText(res, R.string.reyon_clue_adjacent, name(clue.a), name(clue.b))
            is Clue.LeftOf -> appText(res, R.string.reyon_clue_left_of, name(clue.a), name(clue.b))
            is Clue.SameShelf -> appText(res, R.string.reyon_clue_same_shelf, name(clue.a), name(clue.b))
            is Clue.DifferentShelf -> appText(res, R.string.reyon_clue_different_shelf, name(clue.a), name(clue.b))
            is Clue.Above -> appText(res, R.string.reyon_clue_above, name(clue.a), name(clue.b))
            Clue.EyeLevel -> res.getString(R.string.reyon_clue_eye_level)
            Clue.HeavyBottom -> res.getString(R.string.reyon_clue_heavy_bottom)
            is Clue.CategoryBlock -> appText(res, R.string.reyon_clue_category_block, category(res, clue.category))
            is Clue.CategoriesApart -> appText(res, R.string.reyon_clue_categories_apart, category(res, clue.a), category(res, clue.b))
            is Clue.BrandVertical -> appText(res, R.string.reyon_clue_brand_vertical, brand(res, clue.brand))
            is Clue.SizeFlow -> appText(res, R.string.reyon_clue_size_flow, brand(res, clue.brand))
        }
    }
}
