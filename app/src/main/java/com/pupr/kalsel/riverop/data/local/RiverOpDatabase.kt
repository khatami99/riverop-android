package com.pupr.kalsel.riverop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity
import com.pupr.kalsel.riverop.data.local.dao.LaporanOpDao
import com.pupr.kalsel.riverop.data.local.dao.ItemInspeksiDao
import com.pupr.kalsel.riverop.data.local.dao.TemuanKondisiDao
import androidx.room.TypeConverters

@Database(
    entities = [
        LaporanOpEntity::class,
        ItemInspeksiEntity::class,
        TemuanKondisiEntity::class
    ],
    version = 1,
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}