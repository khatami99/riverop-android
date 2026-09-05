package com.pupr.kalsel.riverop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity
import com.pupr.kalsel.riverop.data.local.dao.LaporanOpDao
import com.pupr.kalsel.riverop.data.local.dao.ItemInspeksiDao
import com.pupr.kalsel.riverop.data.local.dao.TemuanKondisiDao

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE temuan_kondisi ADD COLUMN foto_2 TEXT")
        db.execSQL("ALTER TABLE temuan_kondisi ADD COLUMN foto_3 TEXT")
    }
}

/**
 * Mengganti kolom wilayah_sungai_id menjadi das_id, mengikuti perubahan di
 * server: pembagian tugas juru dilakukan per DAS, bukan per wilayah sungai.
 *
 * Tabel dibuat ulang alih-alih memakai ALTER TABLE RENAME COLUMN, karena
 * perintah itu baru tersedia sejak SQLite 3.25 sedangkan minSdk aplikasi ini
 * adalah 26 yang masih membawa SQLite 3.19.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE laporan_op_baru (
                uuid TEXT NOT NULL PRIMARY KEY,
                juru_sungai_id INTEGER NOT NULL,
                das_id INTEGER NOT NULL,
                tanggal_inspeksi TEXT NOT NULL,
                bulan INTEGER NOT NULL,
                tahun INTEGER NOT NULL,
                cuaca TEXT NOT NULL,
                debit_air TEXT NOT NULL,
                catatan_umum TEXT,
                kendala TEXT,
                foto_lokasi_1 TEXT,
                foto_lokasi_2 TEXT,
                dibuat_di_hp INTEGER NOT NULL,
                sync_status TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO laporan_op_baru (
                uuid, juru_sungai_id, das_id, tanggal_inspeksi, bulan, tahun,
                cuaca, debit_air, catatan_umum, kendala, foto_lokasi_1,
                foto_lokasi_2, dibuat_di_hp, sync_status
            )
            SELECT
                uuid, juru_sungai_id, wilayah_sungai_id, tanggal_inspeksi, bulan, tahun,
                cuaca, debit_air, catatan_umum, kendala, foto_lokasi_1,
                foto_lokasi_2, dibuat_di_hp, sync_status
            FROM laporan_op
            """.trimIndent()
        )
        db.execSQL("DROP TABLE laporan_op")
        db.execSQL("ALTER TABLE laporan_op_baru RENAME TO laporan_op")
    }
}

@Database(
    entities = [
        LaporanOpEntity::class,
        ItemInspeksiEntity::class,
        TemuanKondisiEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RiverOpDatabase : RoomDatabase() {

    abstract fun laporanOpDao(): LaporanOpDao
    abstract fun itemInspeksiDao(): ItemInspeksiDao
    abstract fun temuanKondisiDao(): TemuanKondisiDao

    companion object {
        @Volatile
        private var INSTANCE: RiverOpDatabase? = null

        fun getDatabase(context: Context): RiverOpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RiverOpDatabase::class.java,
                    "river_op_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}