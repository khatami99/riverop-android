package com.pupr.kalsel.riverop.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.toUploadModel
import com.pupr.kalsel.riverop.data.remote.LaporanSyncRequest
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import com.pupr.kalsel.riverop.data.remote.StatusSinkronisasiRequest
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.utils.KabarSinkronisasi
import com.pupr.kalsel.riverop.utils.PrefsHelper
import com.pupr.kalsel.riverop.utils.SesiJuru
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Server menolak sesi HP ini.
 *
 * Dibuat sebagai jenis tersendiri, bukan ditangani di tempat, karena
 * penolakan bisa datang dari dua tempat yang berjauhan — saat mengunggah foto
 * dan saat mengirim laporan — sedangkan penanganannya harus satu dan sama:
 * hentikan seluruh pekerjaan, buang sesinya, lalu kabari juru. Bila ditangani
 * di tempat, jalur unggah foto akan tertelan penangkap Exception di bawahnya
 * dan berubah menjadi sekadar "laporan gagal terkirim" — pesan yang menyuruh
 * juru menunggu jaringan, padahal menunggu tidak akan menolongnya sama sekali.
 */
private class SesiDitolak : Exception("Sesi ditolak server")

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PrefsHelper.get(applicationContext)
        val token = prefs.getString("auth_token", null)

        if (token.isNullOrEmpty()) return Result.failure()

        val db = RiverOpDatabase.getDatabase(applicationContext)
        val repo = LaporanRepository(db)

        return try {
            // Dibereskan lebih dulu: laporan yang membeku berstatus SYNCING
            // karena percobaan sebelumnya mati di tengah jalan tidak akan
            // pernah dijemput getUnsyncedLaporan().
            repo.pulihkanYangTergantung()

            val pending = repo.getUnsyncedLaporan()
            if (pending.isEmpty()) {
                laporStatus(repo, token, null)
                return Result.success()
            }

            var hasFailure = false

            // Dihitung, bukan disimpulkan dari hasil akhir: sebagian laporan
            // bisa terkirim sementara sebagian lain gagal, dan juru berhak
            // tahu persis berapa yang mana.
            var jumlahTerkirim = 0
            var jumlahGagal = 0

            for (laporan in pending) {
                try {
                    repo.updateSyncStatus(laporan.uuid, SyncStatus.SYNCING)

                    val foto1Url = uploadFotoIfLocal(laporan.foto_lokasi_1, token)
                    val foto2Url = uploadFotoIfLocal(laporan.foto_lokasi_2, token)

                    val laporanToSync = if (foto1Url != null || foto2Url != null) {
                        val updated = laporan.copy(
                            foto_lokasi_1 = foto1Url ?: laporan.foto_lokasi_1,
                            foto_lokasi_2 = foto2Url ?: laporan.foto_lokasi_2
                        )
                        repo.updateLaporan(updated)
                        updated
                    } else {
                        laporan
                    }

                    val itemInspeksi = repo.getItemInspeksiByLaporan(laporan.uuid)
                    val temuanLocalPaths = mutableListOf<String>()
                    val updatedItems = itemInspeksi.map { item ->
                        val f1 = uploadFotoIfLocal(item.foto_1, token)
                        val f2 = uploadFotoIfLocal(item.foto_2, token)
                        val f3 = uploadFotoIfLocal(item.foto_3, token)
                        val resultItem = if (f1 != null || f2 != null || f3 != null) {
                            val updated = item.copy(
                                foto_1 = f1 ?: item.foto_1,
                                foto_2 = f2 ?: item.foto_2,
                                foto_3 = f3 ?: item.foto_3
                            )
                            repo.updateItemInspeksi(updated)
                            updated
                        } else {
                            item
                        }

                        val temuanItem = repo.getTemuanByItemUuid(item.uuid)
                        for (t in temuanItem) {
                            listOfNotNull(t.foto, t.foto_2, t.foto_3)
                                .filter { !it.startsWith("http") }
                                .forEach { temuanLocalPaths.add(it) }
                            val tf1 = uploadFotoIfLocal(t.foto, token)
                            val tf2 = uploadFotoIfLocal(t.foto_2, token)
                            val tf3 = uploadFotoIfLocal(t.foto_3, token)
                            if (tf1 != null || tf2 != null || tf3 != null) {
                                repo.updateTemuan(
                                    t.copy(
                                        foto = tf1 ?: t.foto,
                                        foto_2 = tf2 ?: t.foto_2,
                                        foto_3 = tf3 ?: t.foto_3
                                    )
                                )
                            }
                        }

                        resultItem
                    }

                    val temuan = repo.getTemuanByLaporan(laporan.uuid)
                    val payload = LaporanSyncRequest(
                        laporan = listOf(laporanToSync.toUploadModel(updatedItems, temuan))
                    )

                    val response = RetrofitClient.getApiService().syncLaporan(token, payload)

                    when {
                        response.code() == 401 -> throw SesiDitolak()
                        response.isSuccessful -> {
                            repo.updateSyncStatus(laporan.uuid, SyncStatus.SYNCED)
                            deleteLocalPhotos(laporan, itemInspeksi, temuanLocalPaths)
                            jumlahTerkirim++
                        }
                        else -> {
                            repo.updateSyncStatus(laporan.uuid, SyncStatus.FAILED)
                            hasFailure = true
                            jumlahGagal++
                        }
                    }

                } catch (e: SesiDitolak) {
                    // Bukan kegagalan laporan ini, melainkan kegagalan seluruh
                    // HP. Dikembalikan ke LOCAL — bukan FAILED — supaya sesudah
                    // juru masuk lagi ia terkirim seperti laporan baru, dan
                    // tidak terlanjur terhitung sebagai gagal di halaman
                    // Sinkronisasi dinas.
                    repo.updateSyncStatus(laporan.uuid, SyncStatus.LOCAL)
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("SyncWorker", "Error sync laporan ${laporan.uuid}: ${e.message}", e)
                    repo.updateSyncStatus(laporan.uuid, SyncStatus.FAILED)
                    hasFailure = true
                    jumlahGagal++
                }
            }

            laporStatus(repo, token, if (hasFailure) "Sebagian laporan gagal terkirim" else null)

            // Berkabar hanya bila memang ada yang dikerjakan. Pekerja ini
            // berjalan tiap lima belas menit, dan kabar "tidak ada apa-apa"
            // sembilan puluh enam kali sehari akan membuat juru mematikan
            // seluruh notifikasi aplikasi ini.
            KabarSinkronisasi.berhasil(applicationContext, jumlahTerkirim)

            if (jumlahGagal > 0) {
                KabarSinkronisasi.gagal(applicationContext, jumlahGagal)
            } else {
                // Antrean sudah bersih, jadi kabar gagal dari percobaan
                // sebelumnya harus ikut ditarik — bukan dibiarkan menggantung
                // menyatakan sesuatu yang tidak lagi benar.
                KabarSinkronisasi.bersihkanGagal(applicationContext)
            }

            if (hasFailure) Result.retry() else Result.success()

        } catch (e: SesiDitolak) {
            tanganiSesiDitolak()
            Result.failure()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "doWork fatal error: ${e.message}", e)
            laporStatus(repo, token, e.message?.take(255))
            Result.failure()
        }
    }

    /**
     * Mengabarkan keadaan antrean di HP ini ke server.
     *
     * Server tidak bisa mengetahuinya sendiri: laporan yang gagal terkirim
     * memang tidak pernah sampai ke sana. Kegagalan mengabarkan pun sengaja
     * ditelan — kalau jaringan sedang mati, tidak ada gunanya menggagalkan
     * seluruh pekerjaan sinkronisasi hanya karena kabarnya tidak terkirim.
     */
    private suspend fun laporStatus(repo: LaporanRepository, token: String, pesan: String?) {
        try {
            val versi = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0).versionName

            // Tanpa "Bearer " di depan: nilai yang tersimpan di preferensi
            // SUDAH memuatnya (lihat LoginViewModel). Sebelumnya di sini
            // ditambahkan sekali lagi sehingga terkirim "Bearer Bearer xxx",
            // dan itu lolos hanya karena Laravel kebetulan memungut potongan
            // "Bearer " yang paling belakang. Seluruh pemanggilan lain di
            // aplikasi ini memakai token apa adanya.
            RetrofitClient.getApiService().laporStatusSinkronisasi(
                token,
                StatusSinkronisasiRequest(
                    jumlah_tertunda = repo.hitungBerstatus(SyncStatus.LOCAL),
                    jumlah_gagal = repo.hitungBerstatus(SyncStatus.FAILED),
                    jumlah_terkirim = repo.hitungBerstatus(SyncStatus.SYNCED),
                    versi_aplikasi = versi,
                    pesan_terakhir = pesan
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncWorker", "Gagal mengabarkan status: ${e.message}")
        }
    }

    /**
     * Menutup sesi yang sudah ditolak server, lalu memberi tahu juru.
     *
     * Sebelum ini penolakan 401 hanya menghasilkan Result.failure() tanpa
     * suara: tokennya tetap tersimpan, pekerjaan berkala tetap terjadwal, dan
     * HP itu mengetuk server dengan token mati tiap lima belas menit tanpa
     * batas. Juru sendiri tidak pernah diberi tahu — ia terus mencatat, dan
     * yang lebih dulu mengetahui keanehannya justru dinas.
     *
     * Sengaja TIDAK mencoba mengabarkan status ke server: permintaan itu
     * memakai token yang sama dan pasti ikut ditolak.
     */
    private fun tanganiSesiDitolak() {
        android.util.Log.w("SyncWorker", "Sesi ditolak server, sesi dibuang.")
        SesiJuru.bersihkan(applicationContext)
        KabarSinkronisasi.sesiBerakhir(applicationContext)
    }

    private suspend fun uploadFotoIfLocal(path: String?, token: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return null

        val file = File(path)
        if (!file.exists()) return null

        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val response = RetrofitClient.getApiService().uploadFoto(token, body)
        return when {
            response.isSuccessful -> response.body()?.url
            response.code() == 401 -> throw SesiDitolak()
            else -> throw IOException("Gagal upload foto (${response.code()})")
        }
    }

    private fun deleteLocalPhotos(
        laporan: LaporanOpEntity,
        items: List<ItemInspeksiEntity>,
        temuanPaths: List<String>
    ) {
        listOfNotNull(laporan.foto_lokasi_1, laporan.foto_lokasi_2).forEach {
            if (!it.startsWith("http")) File(it).delete()
        }
        items.forEach { item ->
            listOfNotNull(item.foto_1, item.foto_2, item.foto_3).forEach {
                if (!it.startsWith("http")) File(it).delete()
            }
        }
        temuanPaths.forEach { File(it).delete() }
    }
}
