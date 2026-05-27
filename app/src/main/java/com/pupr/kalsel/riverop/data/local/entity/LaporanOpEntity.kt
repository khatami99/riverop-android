package com.pupr.kalsel.riverop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pupr.kalsel.riverop.models.SyncStatus

@Entity(tableName = "laporan_op")
data class LaporanOpEntity(
    @PrimaryKey val uuid: String,
    val juru_sungai_id: Int,
    val wilayah_sungai_id: Int,
    val tanggal_inspeksi: String,
    val bulan: Int,
    val tahun: Int,
    val cuaca: String,
    val debit_air: String,
    val catatan_umum: String?,
    val kendala: String?,
    val foto_lokasi_1: String?,
    val foto_lokasi_2: String?,
    val dibuat_di_hp: Long,
    val sync_status: SyncStatus = SyncStatus.DRAFT
)