package com.pupr.kalsel.riverop.ui.laporan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.remote.MasterSyncResponse
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.databinding.ActivityRingkasanBangunanBinding
import com.pupr.kalsel.riverop.databinding.ItemRingkasanBangunanBinding
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity
import kotlinx.coroutines.launch

class RingkasanBangunanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRingkasanBangunanBinding
    private lateinit var repository: LaporanRepository
    private var laporanUuid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRingkasanBangunanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)

        laporanUuid = intent.getStringExtra("EXTRA_LAPORAN_UUID")
        if (laporanUuid.isNullOrBlank()) {
            Toast.makeText(this, "Laporan tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadData()

        binding.btnEditInfoUmum.setOnClickListener {
            startActivity(Intent(this, BuatLaporanActivity::class.java).apply {
                putExtra("EXTRA_LAPORAN_UUID", laporanUuid)
                putExtra("EXTRA_EDIT_MODE", true)
            })
        }

        binding.btnTambahBangunan.setOnClickListener {
            val uuid = laporanUuid ?: return@setOnClickListener
            lifecycleScope.launch {
                val laporan = repository.getLaporanByUuid(uuid) ?: return@launch
                startActivity(Intent(this@RingkasanBangunanActivity, PilihJenisBangunanActivity::class.java).apply {
                    putExtra("EXTRA_JURU_SUNGAI_ID", laporan.juru_sungai_id)
                    putExtra("EXTRA_WILAYAH_SUNGAI_ID", laporan.wilayah_sungai_id)
                    putExtra("EXTRA_TANGGAL", laporan.tanggal_inspeksi)
                    putExtra("EXTRA_BULAN", laporan.bulan)
                    putExtra("EXTRA_TAHUN", laporan.tahun)
                    putExtra("EXTRA_CUACA", laporan.cuaca)
                    putExtra("EXTRA_DEBIT", laporan.debit_air)
                    putExtra("EXTRA_CATATAN", laporan.catatan_umum)
                    putExtra("EXTRA_KENDALA", laporan.kendala)
                    putExtra("EXTRA_FOTO_1", laporan.foto_lokasi_1)
                    putExtra("EXTRA_FOTO_2", laporan.foto_lokasi_2)
                    putExtra("EXTRA_EXISTING_UUID", uuid)
                })
            }
        }

        binding.btnKirimLaporan.setOnClickListener {
            val uuid = laporanUuid ?: return@setOnClickListener
            lifecycleScope.launch {
                val laporan = repository.getLaporanByUuid(uuid)
                if (laporan == null) {
                    Toast.makeText(this@RingkasanBangunanActivity, "Laporan tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val items = repository.getItemInspeksiByLaporan(uuid)
                if (items.isEmpty()) {
                    Toast.makeText(this@RingkasanBangunanActivity, "Minimal harus ada 1 bangunan yang diinspeksi", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                repository.updateLaporan(laporan.copy(sync_status = SyncStatus.LOCAL))
                Toast.makeText(this@RingkasanBangunanActivity, "Laporan siap disinkronisasi", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@RingkasanBangunanActivity, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val uuid = laporanUuid ?: return
        lifecycleScope.launch {
            val laporan = repository.getLaporanByUuid(uuid) ?: return@launch
            binding.tvInfoUmum.text = "${laporan.tanggal_inspeksi} · Cuaca: ${laporan.cuaca?.replaceFirstChar { it.uppercase() } ?: "-"} · Debit: ${laporan.debit_air?.replaceFirstChar { it.uppercase() } ?: "-"}"

            val items = repository.getItemInspeksiByLaporan(uuid)
            val masterJson = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE).getString("master_data", null)

            binding.rvBangunan.layoutManager = LinearLayoutManager(this@RingkasanBangunanActivity)
            binding.rvBangunan.adapter = BangunanAdapter(items, masterJson) { item, index ->
                startActivity(Intent(this@RingkasanBangunanActivity, FormBagian3Activity::class.java).apply {
                    putExtra("EXTRA_EXISTING_UUID", uuid)
                    putExtra("EXTRA_EXISTING_ITEM_UUID", item.uuid)
                    putExtra("EXTRA_JENIS_BANGUNAN_ID", item.jenis_bangunan_id)
                    putExtra("EXTRA_KODE_OP", getKodeOp(item.jenis_bangunan_id, masterJson))
                    putExtra("EXTRA_NOMOR_BANGUNAN", index + 1)
                    val laporan2 = laporan
                    putExtra("EXTRA_JURU_SUNGAI_ID", laporan2.juru_sungai_id)
                    putExtra("EXTRA_WILAYAH_SUNGAI_ID", laporan2.wilayah_sungai_id)
                    putExtra("EXTRA_TANGGAL", laporan2.tanggal_inspeksi)
                    putExtra("EXTRA_BULAN", laporan2.bulan)
                    putExtra("EXTRA_TAHUN", laporan2.tahun)
                    putExtra("EXTRA_CUACA", laporan2.cuaca)
                    putExtra("EXTRA_DEBIT", laporan2.debit_air)
                    putExtra("EXTRA_CATATAN", laporan2.catatan_umum)
                    putExtra("EXTRA_KENDALA", laporan2.kendala)
                    putExtra("EXTRA_FOTO_1", laporan2.foto_lokasi_1)
                    putExtra("EXTRA_FOTO_2", laporan2.foto_lokasi_2)
                })
            }
        }
    }

    private fun getKodeOp(jenisBangunanId: Int, masterJson: String?): String? {
        if (masterJson.isNullOrEmpty()) return null
        return try {
            val wrapper = Gson().fromJson(masterJson, MasterSyncResponse::class.java)
            wrapper?.data?.jenis_bangunan?.find { it.id == jenisBangunanId }?.kode
        } catch (e: Exception) { null }
    }

    private class BangunanAdapter(
        private val items: List<ItemInspeksiEntity>,
        private val masterJson: String?,
        private val onEdit: (ItemInspeksiEntity, Int) -> Unit
    ) : RecyclerView.Adapter<BangunanAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemRingkasanBangunanBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemRingkasanBangunanBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvNomorBangunan.text = "Bangunan ${position + 1}"
            val (nama, kode) = getNamaAndKode(item.jenis_bangunan_id)
            holder.binding.tvNamaBangunan.text = nama
            holder.binding.tvKodeBangunan.text = kode ?: ""
            holder.binding.btnEditBangunan.setOnClickListener { onEdit(item, position) }
        }

        override fun getItemCount() = items.size

        private fun getNamaAndKode(id: Int): Pair<String, String?> {
            if (masterJson.isNullOrEmpty()) return "Jenis Bangunan $id" to null
            return try {
                val wrapper = Gson().fromJson(masterJson, MasterSyncResponse::class.java)
                val match = wrapper?.data?.jenis_bangunan?.find { it.id == id }
                (match?.nama ?: "Jenis Bangunan $id") to match?.kode
            } catch (e: Exception) { "Jenis Bangunan $id" to null }
        }
    }
}