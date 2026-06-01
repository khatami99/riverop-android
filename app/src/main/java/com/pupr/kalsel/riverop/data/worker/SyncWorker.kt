package com.pupr.kalsel.riverop.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.toUploadModel
import com.pupr.kalsel.riverop.data.remote.LaporanSyncRequest
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.models.SyncStatus

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)
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
                    val itemInspeksi = repo.getItemInspeksiByLaporan(laporan.uuid)
                    val temuan = repo.getTemuanByLaporan(laporan.uuid)

                    val payload = LaporanSyncRequest(
                        laporan = listOf(laporan.toUploadModel(itemInspeksi, temuan))
                    )

                    val response = RetrofitClient.getApiService().syncLaporan("Bearer $token", payload)

                    if (response.isSuccessful) {
                        repo.updateSyncStatus(laporan.uuid, SyncStatus.SYNCED)
                    } else {
                        repo.updateSyncStatus(laporan.uuid, SyncStatus.FAILED)
                        hasFailure = true
                    }
                } catch (e: Exception) {
                    repo.updateSyncStatus(laporan.uuid, SyncStatus.FAILED)
                    hasFailure = true
                }
            }

            if (hasFailure) Result.retry() else Result.success()

        } catch (e: Exception) {
            Result.failure()
        }
    }
}