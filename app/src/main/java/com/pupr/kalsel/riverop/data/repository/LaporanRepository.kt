package com.pupr.kalsel.riverop.data.repository

import androidx.room.withTransaction
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity
import com.pupr.kalsel.riverop.models.SyncStatus
import kotlinx.coroutines.flow.Flow

class LaporanRepository(private val database: RiverOpDatabase) {

    private val laporanOpDao = database.laporanOpDao()
    private val itemInspeksiDao = database.itemInspeksiDao()
    private val temuanKondisiDao = database.temuanKondisiDao()

    suspend fun insertLaporanWithDetails(
        laporan: LaporanOpEntity,
        items: List<ItemInspeksiEntity>,
        temuans: List<TemuanKondisiEntity>
    ) {
        database.withTransaction {
            laporanOpDao.insertLaporan(laporan)
            itemInspeksiDao.insertItems(items)
            temuanKondisiDao.insertTemuans(temuans)
        }
    }

    fun getAllLaporan(): Flow<List<LaporanOpEntity>> {
        return laporanOpDao.getAllLaporanFlow()
    }

    suspend fun getUnsyncedLaporan(): List<LaporanOpEntity> {
        return laporanOpDao.getUnsyncedLaporan()
    }

    suspend fun updateSyncStatus(uuid: String, status: SyncStatus) {
        laporanOpDao.updateSyncStatus(uuid, status)
    }

    suspend fun deleteLaporan(uuid: String) {
        database.withTransaction {
            val laporan = laporanOpDao.getLaporanByUuid(uuid)
            if (laporan != null) {
                laporanOpDao.deleteLaporan(laporan)
            }
        }
    }
}