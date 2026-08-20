package com.pupr.kalsel.riverop.data.local.dao

import androidx.room.*
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity

@Dao
interface TemuanKondisiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemuan(temuan: TemuanKondisiEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemuans(temuans: List<TemuanKondisiEntity>)

    @Query("SELECT * FROM temuan_kondisi WHERE item_inspeksi_uuid = :itemUuid")
    suspend fun getTemuansByItemUuid(itemUuid: String): List<TemuanKondisiEntity>

    @Update
    suspend fun updateTemuan(temuan: TemuanKondisiEntity)

    @Query("DELETE FROM temuan_kondisi WHERE item_inspeksi_uuid = :itemUuid")
    suspend fun deleteTemuansByItemUuid(itemUuid: String)
}