package com.pupr.kalsel.riverop.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.work.*
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.data.worker.SyncWorker
import com.pupr.kalsel.riverop.databinding.ActivityDashboardBinding
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.ui.laporan.BuatLaporanActivity
import com.pupr.kalsel.riverop.ui.login.LoginActivity
import com.pupr.kalsel.riverop.ui.laporan.DetailLaporanActivity
import androidx.core.content.ContextCompat
import com.pupr.kalsel.riverop.utils.KabarSinkronisasi
import com.pupr.kalsel.riverop.utils.PrefsHelper
import com.pupr.kalsel.riverop.utils.SesiJuru
import com.pupr.kalsel.riverop.utils.SessionManager
import com.google.gson.Gson
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Jumlah riwayat terkirim yang dimuat pertama kali, lalu tiap kali menggulir. */
private const val BATAS_RIWAYAT_AWAL = 50
private const val BATAS_RIWAYAT_TAMBAHAN = 50

private const val SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var adapter: LaporanAdapter
    private lateinit var repository: LaporanRepository

    private val mintaIzinNotifikasi =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPrefs = PrefsHelper.get(this)
        binding.tvJuruNama.text = sharedPrefs.getString("juru_sungai_nama", "-")
        binding.tvWilayahSungai.text = "DAS: ${sharedPrefs.getString("nama_das", "-")}"

        binding.btnBuatLaporan.setOnClickListener {
            startActivity(Intent(this, BuatLaporanActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)

        KabarSinkronisasi.siapkanSaluran(this)
        tanyakanIzinNotifikasi()

        setupRecyclerView()
        observeLaporan()
        scheduleSyncWorker()
        setupSyncManual()
        observeManualSyncStatus()
        refreshMasterData()
    }

    override fun onResume() {
        super.onResume()
        if (isSessionExpired()) {
            performLogout()
            return
        }
        val sharedPrefs = PrefsHelper.get(this)
        binding.tvWilayahSungai.text = "DAS: ${sharedPrefs.getString("nama_das", "-")}"
        // Bila server menolak token selagi layar ini terbuka, juru dikeluarkan
        // beserta alasannya. Tanpa alasan, ia cuma melihat dirinya terlempar ke
        // layar masuk tanpa sebab dan akan mengira aplikasinya rusak.
        SessionManager.onUnauthorized = {
            performLogout("Sesi di HP ini sudah tidak berlaku. Masuk lagi ya — kalau tetap ditolak, hubungi petugas dinas.")
        }
    }

    private fun isSessionExpired(): Boolean {
        val loginTimestamp = PrefsHelper.get(this).getLong("login_timestamp", 0L)
        if (loginTimestamp == 0L) return false
        return System.currentTimeMillis() - loginTimestamp > SESSION_DURATION_MS
    }

    override fun onPause() {
        super.onPause()
        SessionManager.onUnauthorized = null
    }

    private fun setupRecyclerView() {
        adapter = LaporanAdapter { laporan ->
            val intent = Intent(this, DetailLaporanActivity::class.java)
            intent.putExtra("EXTRA_LAPORAN_UUID", laporan.uuid)
            startActivity(intent)
        }

        binding.rvLaporan.layoutManager = LinearLayoutManager(this)
        binding.rvLaporan.adapter = adapter
        pasangGulirTakBerujung()
    }

    /**
     * Riwayat terkirim dimuat bertahap. Nilainya naik saat gulir mendekati
     * dasar daftar, sehingga juru dengan ratusan laporan tetap bisa membuka
     * laporan lamanya tanpa memaksa HP memuat semuanya sekaligus.
     */
    private val batasRiwayat = MutableStateFlow(BATAS_RIWAYAT_AWAL)
    private var sedangMemuatRiwayat = false

    private fun pasangGulirTakBerujung() {
        binding.rvLaporan.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return

                val tataLetak = rv.layoutManager as? LinearLayoutManager ?: return
                val terakhirTerlihat = tataLetak.findLastVisibleItemPosition()

                if (terakhirTerlihat >= tataLetak.itemCount - 5) {
                    muatRiwayatBerikutnya()
                }
            }
        })
    }

    private fun muatRiwayatBerikutnya() {
        if (sedangMemuatRiwayat) return
        sedangMemuatRiwayat = true

        lifecycleScope.launch {
            val tersedia = repository.hitungRiwayatTerkirim()

            // Berhenti menambah bila seluruh riwayat sudah termuat, supaya
            // gulir sampai dasar tidak memicu permintaan tanpa akhir.
            if (batasRiwayat.value < tersedia) {
                batasRiwayat.value += BATAS_RIWAYAT_TAMBAHAN
            }

            sedangMemuatRiwayat = false
        }
    }

    private fun observeLaporan() {
        lifecycleScope.launch {
            repository.getAllLaporan(batasRiwayat).collect { list ->
                adapter.setLaporan(list)
                if (list.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvLaporan.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvLaporan.visibility = View.VISIBLE
                }

                val unsyncedCount = list.count {
                    it.sync_status == SyncStatus.LOCAL || it.sync_status == SyncStatus.FAILED
                }
                if (unsyncedCount > 0) {
                    binding.cardSync.visibility = View.VISIBLE
                    binding.tvSyncStatus.text = "Terdapat $unsyncedCount laporan belum disinkronisasi"
                } else {
                    binding.cardSync.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Meminta izin notifikasi sekali saja, tidak berulang tiap membuka layar.
     *
     * Menanyakannya lagi dan lagi tidak menambah peluang diizinkan — sesudah
     * dua kali ditolak sistem sendiri berhenti menampilkan permintaannya —
     * tetapi berhasil membuat aplikasi terasa memaksa. Yang menolak tetap
     * bisa menyalakannya belakangan dari setelan Android.
     *
     * Ditanyakan di sini, bukan saat memasang, sebab pada layar inilah juru
     * pertama kali melihat antrean laporannya, sehingga permintaannya punya
     * kaitan yang jelas dengan apa yang sedang dilihatnya.
     */
    private fun tanyakanIzinNotifikasi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val sudah = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (sudah) return

        val prefs = PrefsHelper.get(this)
        if (prefs.getBoolean("izin_notifikasi_pernah_diminta", false)) return

        prefs.edit().putBoolean("izin_notifikasi_pernah_diminta", true).apply()
        mintaIzinNotifikasi.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keluar")
            .setMessage("Yakin ingin keluar?")
            .setPositiveButton("Ya") { dialog, _ ->
                dialog.dismiss()
                performLogout()
            }
            .setNegativeButton("Tidak") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun refreshMasterData() {
        lifecycleScope.launch {
            try {
                val prefs = PrefsHelper.get(this@DashboardActivity)
                val token = prefs.getString("auth_token", null) ?: return@launch
                val dasId = prefs.getInt("das_id", 0)

                val response = RetrofitClient.getApiService().syncMaster(token)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@launch
                    val jsonString = Gson().toJson(body)
                    val namaDas = body.data.das.find { it.id == dasId }?.nama_das ?: "-"
                    prefs.edit().apply {
                        putString("master_data", jsonString)
                        putString("nama_das", namaDas)
                        apply()
                    }
                    binding.tvWilayahSungai.text = "DAS: $namaDas"
                }
            } catch (e: Exception) {
                // Gagal refresh master data tidak mengganggu penggunaan app
            }
        }
    }

    private fun performLogout(alasan: String? = null) {
        SesiJuru.bersihkan(this)

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        alasan?.let { intent.putExtra(LoginActivity.EXTRA_ALASAN, it) }
        startActivity(intent)
        finish()
    }
}
