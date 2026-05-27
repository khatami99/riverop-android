package com.pupr.kalsel.riverop.data.local.dao

import androidx.room.*
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity

@Dao
interface ItemInspeksiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemInspeksiEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemInspeksiEntity>)

    @Query("SELECT * FROM item_inspeksi WHERE laporan_op_uuid = :laporanUuid")
    suspend fun getItemsByLaporanUuid(laporanUuid: String): List<ItemInspeksiEntity>

    @Query("DELETE FROM item_inspeksi WHERE laporan_op_uuid = :laporanUuid")
    suspend fun deleteItemsByLaporanUuid(laporanUuid: String)
}