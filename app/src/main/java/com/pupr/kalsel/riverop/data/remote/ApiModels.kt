package com.pupr.kalsel.riverop.data.remote

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val message: String,
    val token: String,
    val user: JuruSungaiModel
)

data class JuruSungaiModel(
    val id: Int,
    val nama: String,
    val username: String,
    val das_id: Int,
    val nama_das: String?
)

data class LogoutResponse(
    val message: String
)

/**
 * Balasan /api/master/profil.
 *
 * Bentuknya dulu datar — id, nama, username langsung di akar — padahal server
 * membungkusnya dalam amplop {success, message, data} seperti seluruh titik
 * akhir lain. Kekeliruan itu tidak pernah ketahuan karena getProfil() memang
 * tidak pernah dipanggil siapa pun; Gson hanya akan mengisi seluruh kolomnya
 * dengan nilai kosong tanpa mengeluh sedikit pun.
 */
data class ProfilResponse(
    val success: Boolean,
    val message: String,
    val data: ProfilJuru?
)

data class ProfilJuru(
    val id: Int,
    val nama: String?,
    val username: String?,
    // Juru sungai bukan PNS sehingga tidak punya NIP; server kini mengirim
    // nomor telepon, yang dipakai dinas untuk menghubungi mereka.
    val no_telepon: String?,
    val das_id: Int?,
    val das: DasProfil?
)

data class DasProfil(
    val id: Int,
    val nama_das: String?
)

data class MasterSyncResponse(
    val success: Boolean,
    val message: String,
    val data: MasterData
)

data class MasterData(
    val kabupaten: List<KabupatenModel>,
    val wilayah_sungai: List<WilayahSungaiModel>,
    val das: List<DasModel>,
    val jenis_bangunan: List<JenisBangunanModel>,
    val bagian_bangunan: List<BagianBangunanModel>,
    val pilihan_kondisi: List<PilihanKondisiModel>
)

data class KabupatenModel(
    val id: Int,
    val nama: String,
    val kode: String,
    val aktif: Boolean
)

data class WilayahSungaiModel(
    val id: Int,
    val nama_sungai: String,
    val kode_sungai: String,
    val aktif: Boolean
)

/**
 * Daerah Aliran Sungai — satuan wilayah kerja juru sungai.
 * Wewenang provinsi hanya mencakup satu wilayah sungai, sehingga pembagian
 * tugas juru dilakukan per DAS di bawahnya.
 */
data class DasModel(
    val id: Int,
    val wilayah_sungai_id: Int,
    val nama_das: String,
    val kode_das: String,
    val aktif: Boolean
)

data class JenisBangunanModel(
    val id: Int,
    val kode: String,
    val nama: String,
    val tipe_konstruksi: String?,
    val aktif: Boolean,
    val urutan: Int
)

data class BagianBangunanModel(
    val id: Int,
    val jenis_bangunan_id: Int,
    val nama: String,
    val urutan: Int,
    val aktif: Boolean
)

data class PilihanKondisiModel(
    val id: Int,
    val bagian_bangunan_id: Int,
    val kondisi: String,
    val urutan: Int,
    val aktif: Boolean
)

data class LaporanSyncRequest(
    val laporan: List<LaporanUploadModel>
)

data class LaporanUploadModel(
    val uuid: String,
    val juru_sungai_id: Int,
    val das_id: Int,
    val tanggal_inspeksi: String,
    val bulan: Int,
    val tahun: Int,
    val cuaca: String?,
    val debit_air: String?,
    val catatan_umum: String?,
    val kendala: String?,
    val foto_lokasi_1: String?,
    val foto_lokasi_2: String?,
    val dibuat_di_hp: String,
    val item_inspeksi: List<ItemInspeksiUploadModel>
)

data class ItemInspeksiUploadModel(
    val uuid: String,
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
    val foto_3: String?,
    val temuan_kondisi: List<TemuanKondisiUploadModel>
)

data class TemuanKondisiUploadModel(
    val bagian_bangunan_id: Int,
    val kondisi_ditemukan: List<String>,
    val tindakan_diperlukan: String?,
    val koordinat_lat: Double?,
    val koordinat_lng: Double?,
    val keterangan: String?,
    val foto: String?,
    val foto_2: String?,
    val foto_3: String?
)

data class LaporanSyncResponse(
    val success: Boolean,
    val synced_uuids: List<String>
)

/**
 * Kabar keadaan sinkronisasi dari HP juru ke server.
 *
 * Diperlukan karena laporan yang GAGAL terkirim, menurut definisinya, tidak
 * pernah sampai ke server — status TERTUNDA dan GAGAL hanya hidup di HP,
 * sehingga dinas tidak akan pernah tahu tanpa dikabari.
 */
data class StatusSinkronisasiRequest(
    val jumlah_tertunda: Int,
    val jumlah_gagal: Int,
    val jumlah_terkirim: Int,
    val versi_aplikasi: String?,
    val pesan_terakhir: String?
)

data class StatusSinkronisasiResponse(
    val success: Boolean,
    val message: String
)
