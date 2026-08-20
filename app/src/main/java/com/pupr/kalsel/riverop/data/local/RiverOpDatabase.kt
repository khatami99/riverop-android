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

@Database(
    entities = [
        LaporanOpEntity::class,
        ItemInspeksiEntity::class,
        TemuanKondisiEntity::class
    ],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}