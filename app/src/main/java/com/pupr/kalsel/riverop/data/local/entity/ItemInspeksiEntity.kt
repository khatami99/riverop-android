package com.pupr.kalsel.riverop.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity

@Entity(
    tableName = "item_inspeksi",
    foreignKeys = [
        ForeignKey(
            entity = LaporanOpEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["laporan_op_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ItemInspeksiEntity(
    @PrimaryKey val uuid: String,
    val laporan_op_uuid: String,
    val jenis_bangunan_id: Int,
    val nama_bangunan: String?,
    val nama_sungai: String?,
    val lokasi_km_dari: Double?,
    val lokasi_km_sampai: Double?,
    val koordinat_lat: Double?,
    val koordinat_lng: Double?,
    val tipe_konstruksi: String?,
    val catatan: String?,
    val foto_1: String?,
    val foto_2: String?,
    val foto_3: String?
)