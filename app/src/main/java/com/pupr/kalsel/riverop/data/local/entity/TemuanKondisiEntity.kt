package com.pupr.kalsel.riverop.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity

@Entity(
    tableName = "temuan_kondisi",
    foreignKeys = [
        ForeignKey(
            entity = ItemInspeksiEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["item_inspeksi_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TemuanKondisiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val item_inspeksi_uuid: String,
    val bagian_bangunan_id: Int,
    val kondisi_ditemukan: String,
    val tindakan_diperlukan: String?,
    val koordinat_lat: Double?,
    val koordinat_lng: Double?,
    val keterangan: String?,
    val foto: String?
)