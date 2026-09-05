package com.pupr.kalsel.riverop.data.local.entity

import com.pupr.kalsel.riverop.data.remote.ItemInspeksiUploadModel
import com.pupr.kalsel.riverop.data.remote.LaporanUploadModel
import com.pupr.kalsel.riverop.data.remote.TemuanKondisiUploadModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun LaporanOpEntity.toUploadModel(
    items: List<ItemInspeksiEntity>,
    temuanList: List<TemuanKondisiEntity>
): LaporanUploadModel {
    val temuanByItem = temuanList.groupBy { it.item_inspeksi_uuid }

    return LaporanUploadModel(
        uuid = uuid,
        juru_sungai_id = juru_sungai_id,
        das_id = das_id,
        tanggal_inspeksi = tanggal_inspeksi,
        bulan = bulan,
        tahun = tahun,
        cuaca = cuaca,
        debit_air = debit_air,
        catatan_umum = catatan_umum,
        kendala = kendala,
        foto_lokasi_1 = foto_lokasi_1,
        foto_lokasi_2 = foto_lokasi_2,
        dibuat_di_hp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(dibuat_di_hp)),
        item_inspeksi = items.map { item ->
            item.toUploadModel(temuanByItem[item.uuid] ?: emptyList())
        }
    )
}

fun ItemInspeksiEntity.toUploadModel(
    temuan: List<TemuanKondisiEntity>
): ItemInspeksiUploadModel {
    return ItemInspeksiUploadModel(
        uuid = uuid,
        jenis_bangunan_id = jenis_bangunan_id,
        nama_bangunan = nama_bangunan,
        lokasi_km_dari = lokasi_km_dari,
        lokasi_km_sampai = lokasi_km_sampai,
        koordinat_lat = koordinat_lat,
        koordinat_lng = koordinat_lng,
        tipe_konstruksi = tipe_konstruksi,
        catatan = catatan,
        foto_1 = foto_1,
        foto_2 = foto_2,
        foto_3 = foto_3,
        temuan_kondisi = temuan.map { it.toUploadModel() }
    )
}

fun TemuanKondisiEntity.toUploadModel(): TemuanKondisiUploadModel {
    return TemuanKondisiUploadModel(
        bagian_bangunan_id = bagian_bangunan_id,
        kondisi_ditemukan = parseKondisiStringToList(kondisi_ditemukan),
        tindakan_diperlukan = tindakan_diperlukan,
        koordinat_lat = koordinat_lat,
        koordinat_lng = koordinat_lng,
        keterangan = keterangan,
        foto = foto,
        foto_2 = foto_2,
        foto_3 = foto_3
    )
}

private fun parseKondisiStringToList(kondisiStr: String?): List<String> {
    if (kondisiStr.isNullOrBlank()) return emptyList()
    return kondisiStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
}