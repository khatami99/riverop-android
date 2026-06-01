package com.pupr.kalsel.riverop.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.data.worker.SyncWorker
import com.pupr.kalsel.riverop.databinding.ActivityDashboardBinding
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.ui.laporan.BuatLaporanActivity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var adapter: LaporanAdapter
    private lateinit var repository: LaporanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPrefs = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)
        binding.tvJuruNama.text = sharedPrefs.getString("juru_sungai_nama", "-")
        binding.tvWilayahSungai.text = "Wilayah Sungai: ${sharedPrefs.getString("nama_wilayah_sungai", "-")}"

        binding.btnBuatLaporan.setOnClickListener {
            startActivity(Intent(this, BuatLaporanActivity::class.java))
        }

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)

        setupRecyclerView()
        observeLaporan()
        scheduleSyncWorker()
        setupSyncManual()
        observeManualSyncStatus()
    }

    private fun setupRecyclerView() {
        adapter = LaporanAdapter()
        binding.rvLaporan.layoutManager = LinearLayoutManager(this)
        binding.rvLaporan.adapter = adapter
    }

    private fun observeLaporan() {
        lifecycleScope.launch {
            repository.getAllLaporan().collect { list ->
                adapter.setLaporan(list)
                if (list.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvLaporan.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvLaporan.visibility = View.VISIBLE
                }

                val unsyncedCount = list.count { it.sync_status == SyncStatus.LOCAL || it.sync_status == SyncStatus.FAILED }
                if (unsyncedCount > 0) {
                    binding.cardSync.visibility = View.VISIBLE
                    binding.tvSyncStatus.text = "Terdapat $unsyncedCount laporan belum disinkronisasi"
                } else {
                    binding.cardSync.visibility = View.GONE
                }
            }
        }
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "river_op_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun setupSyncManual() {
        binding.btnSync.setOnClickListener {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniqueWork(
                "river_op_manual_sync",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }

    private fun observeManualSyncStatus() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData("river_op_manual_sync")
            .observe(this) { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observe
                val workInfo = workInfos[0]
                if (workInfo.state == WorkInfo.State.RUNNING) {
                    binding.pbSync.visibility = View.VISIBLE
                    binding.btnSync.isEnabled = false
                    binding.btnSync.text = "Menyinkronkan..."
                } else {
                    binding.pbSync.visibility = View.GONE
                    binding.btnSync.isEnabled = true
                    binding.btnSync.text = "Sync Sekarang"
                }
            }
    }

    override fun onResume() {
        super.onResume()
        val sharedPrefs = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)
        binding.tvWilayahSungai.text = "Wilayah Sungai: ${sharedPrefs.getString("nama_wilayah_sungai", "-")}"
    }
}