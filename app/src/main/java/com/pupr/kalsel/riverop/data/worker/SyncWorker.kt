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
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.utils.PrefsHelper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

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
            val pending = repo.getUnsyncedLaporan()
            if (pending.isEmpty()) return Result.success()

            var hasFailure = false

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
                        response.code() == 401 -> return Result.failure()
                        response.isSuccessful -> {
                            repo.updateSyncStatus(laporan.uuid, SyncStatus.SYNCED)
                            deleteLocalPhotos(laporan, itemInspeksi, temuanLocalPaths)
                        }
                        else -> {
                            repo.updateSyncStatus(laporan.uuid, SyncStatus.FAILED)
                            hasFailure = true
                        }
                    }

                } catch (e: Exception) {
                    android.util.Log.e("SyncWorker", "Error sync laporan ${laporan.uuid}: ${e.message}", e)
                    repo.updateSyncStatus(laporan.uuid, SyncStatus.FAILED)
                    hasFailure = true
                }
            }

            if (hasFailure) Result.retry() else Result.success()

        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "doWork fatal error: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun uploadFotoIfLocal(path: String?, token: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return null

        val file = File(path)
        if (!file.exists()) return null

        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val response = RetrofitClient.getApiService().uploadFoto(token, body)
        return if (response.isSuccessful) {
            response.body()?.url
        } else {
            throw IOException("Gagal upload foto (${response.code()})")
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
