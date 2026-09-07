package com.pupr.kalsel.riverop.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.models.SyncStatus

@Dao
interface LaporanOpDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaporan(laporan: LaporanOpEntity)

    @Update
    suspend fun updateLaporan(laporan: LaporanOpEntity)

    @Delete
    suspend fun deleteLaporan(laporan: LaporanOpEntity)

    @Query("SELECT * FROM laporan_op WHERE uuid = :uuid")
    suspend fun getLaporanByUuid(uuid: String): LaporanOpEntity?

    @Query("SELECT * FROM laporan_op WHERE sync_status != 'SYNCED' ORDER BY dibuat_di_hp DESC")
    fun getAllNonSyncedFlow(): Flow<List<LaporanOpEntity>>

    @Query("SELECT * FROM laporan_op WHERE sync_status = 'SYNCED' ORDER BY dibuat_di_hp DESC LIMIT :limit")
    fun getRecentSyncedFlow(limit: Int): Flow<List<LaporanOpEntity>>

    @Query("SELECT * FROM laporan_op WHERE sync_status IN ('LOCAL', 'FAILED')")
    suspend fun getUnsyncedLaporan(): List<LaporanOpEntity>

    @Query("UPDATE laporan_op SET sync_status = :status WHERE uuid = :uuid")
    suspend fun updateSyncStatus(uuid: String, status: String)

    @Query("SELECT COUNT(*) FROM laporan_op WHERE sync_status = :status")
    suspend fun hitungBerstatus(status: String): Int
}