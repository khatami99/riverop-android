package com.pupr.kalsel.riverop.data.repository

import androidx.room.withTransaction
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity
import com.pupr.kalsel.riverop.models.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

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

    suspend fun insertLaporanOnly(laporan: LaporanOpEntity) {
        laporanOpDao.insertLaporan(laporan)
    }

    suspend fun insertItemWithTemuans(
        item: ItemInspeksiEntity,
        temuans: List<TemuanKondisiEntity>
    ) {
        database.withTransaction {
            itemInspeksiDao.insertItem(item)
            temuanKondisiDao.insertTemuans(temuans)
        }
    }

    suspend fun updateItemWithTemuans(
        item: ItemInspeksiEntity,
        temuans: List<TemuanKondisiEntity>
    ) {
        database.withTransaction {
            itemInspeksiDao.updateItem(item)
            temuanKondisiDao.deleteTemuansByItemUuid(item.uuid)
            temuanKondisiDao.insertTemuans(temuans)
        }
    }

    suspend fun deleteItemsByLaporanUuid(laporanUuid: String) {
        itemInspeksiDao.deleteItemsByLaporanUuid(laporanUuid)
    }

    /**
     * Daftar laporan di layar juru.
     *
     * Laporan yang belum terkirim selalu ditampilkan seluruhnya — jumlahnya
     * sedikit dan justru itu yang perlu ditindaklanjuti. Riwayat yang sudah
     * terkirim dimuat bertahap mengikuti gulir.
     *
     * Sebelumnya riwayat dipatok 50 dan tidak pernah bertambah, sehingga
     * laporan lama diam-diam hilang dari HP begitu juru melewati angka itu —
     * padahal riwayat sengaja disimpan karena juru sering membukanya kembali
     * di lapangan.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllLaporan(batasRiwayat: Flow<Int>): Flow<List<LaporanOpEntity>> {
        return batasRiwayat.flatMapLatest { batas ->
            combine(
                laporanOpDao.getAllNonSyncedFlow(),
                laporanOpDao.getRecentSyncedFlow(batas)
            ) { belumTerkirim, riwayat ->
                (belumTerkirim + riwayat).sortedByDescending { it.dibuat_di_hp }
            }
        }
    }

    /** Dipakai layar daftar untuk tahu masih ada riwayat yang belum dimuat. */
    suspend fun hitungRiwayatTerkirim(): Int = laporanOpDao.hitungBerstatus(SyncStatus.SYNCED.name)

    suspend fun getUnsyncedLaporan(): List<LaporanOpEntity> {
        return laporanOpDao.getUnsyncedLaporan()
    }

    suspend fun getLaporanByUuid(uuid: String): LaporanOpEntity? {
        return laporanOpDao.getLaporanByUuid(uuid)
    }

    suspend fun updateLaporan(laporan: LaporanOpEntity) {
        laporanOpDao.updateLaporan(laporan)
    }

    suspend fun updateItemInspeksi(item: ItemInspeksiEntity) {
        itemInspeksiDao.updateItem(item)
    }

    suspend fun getItemByUuid(uuid: String): ItemInspeksiEntity? {
        return itemInspeksiDao.getItemByUuid(uuid)
    }

    suspend fun updateSyncStatus(uuid: String, status: SyncStatus) {
        laporanOpDao.updateSyncStatus(uuid, status.name)
    }

    suspend fun hitungBerstatus(status: SyncStatus): Int {
        return laporanOpDao.hitungBerstatus(status.name)
    }

    suspend fun deleteLaporan(uuid: String) {
        database.withTransaction {
            val laporan = laporanOpDao.getLaporanByUuid(uuid)
            if (laporan != null) {
                laporanOpDao.deleteLaporan(laporan)
            }
        }
    }

    suspend fun getItemInspeksiByLaporan(laporanUuid: String): List<ItemInspeksiEntity> {
        return itemInspeksiDao.getItemsByLaporanUuid(laporanUuid)
    }

    suspend fun getTemuanByItemUuid(itemUuid: String): List<TemuanKondisiEntity> {
        return temuanKondisiDao.getTemuansByItemUuid(itemUuid)
    }

    suspend fun updateTemuan(temuan: TemuanKondisiEntity) {
        temuanKondisiDao.updateTemuan(temuan)
    }

    suspend fun getTemuanByLaporan(laporanUuid: String): List<TemuanKondisiEntity> {
        return temuanKondisiDao.getTemuansByLaporanUuid(laporanUuid)
    }
}