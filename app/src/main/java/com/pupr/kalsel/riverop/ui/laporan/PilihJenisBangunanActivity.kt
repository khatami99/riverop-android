package com.pupr.kalsel.riverop.ui.laporan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.databinding.ActivityPilihJenisBangunanBinding

class PilihJenisBangunanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPilihJenisBangunanBinding
    private lateinit var adapter: JenisBangunanAdapter
    private lateinit var repository: LaporanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPilihJenisBangunanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)

        // Baca semua data dari intent
        val juruSungaiId = intent.getIntExtra("EXTRA_JURU_SUNGAI_ID", 0)
        val wilayahSungaiId = intent.getIntExtra("EXTRA_WILAYAH_SUNGAI_ID", 0)
        val tanggal = intent.getStringExtra("EXTRA_TANGGAL") ?: ""
        val bulan = intent.getIntExtra("EXTRA_BULAN", 0)
        val tahun = intent.getIntExtra("EXTRA_TAHUN", 0)
        val cuaca = intent.getStringExtra("EXTRA_CUACA") ?: ""
        val debit = intent.getStringExtra("EXTRA_DEBIT") ?: ""
        val catatan = intent.getStringExtra("EXTRA_CATATAN") ?: ""
        val kendala = intent.getStringExtra("EXTRA_KENDALA") ?: ""
        val foto1 = intent.getStringExtra("EXTRA_FOTO_1")
        val foto2 = intent.getStringExtra("EXTRA_FOTO_2")
        val existingUuid = intent.getStringExtra("EXTRA_EXISTING_UUID") // null kalau laporan baru

        setupRecyclerView()

        // Back: kalau ada existing UUID berarti kita lagi tambah bangunan ke laporan existing
        // jangan hapus laporannya, cukup finish
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        binding.btnKembali.setOnClickListener {
            finish()
        }

        binding.btnLanjut.setOnClickListener {
            val selected = adapter.getSelectedItem()
            if (selected == null) {
                Toast.makeText(this, "Silakan pilih salah satu jenis bangunan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nextIntent = Intent(this, FormBagian3Activity::class.java).apply {
                putExtra("EXTRA_JURU_SUNGAI_ID", juruSungaiId)
                putExtra("EXTRA_WILAYAH_SUNGAI_ID", wilayahSungaiId)
                putExtra("EXTRA_TANGGAL", tanggal)
                putExtra("EXTRA_BULAN", bulan)
                putExtra("EXTRA_TAHUN", tahun)
                putExtra("EXTRA_CUACA", cuaca)
                putExtra("EXTRA_DEBIT", debit)
                putExtra("EXTRA_CATATAN", catatan)
                putExtra("EXTRA_KENDALA", kendala)
                putExtra("EXTRA_JENIS_BANGUNAN_ID", selected.id)
                putExtra("EXTRA_KODE_OP", selected.kode_op ?: selected.kode)
                putExtra("EXTRA_FOTO_1", foto1)
                putExtra("EXTRA_FOTO_2", foto2)
                putExtra("EXTRA_EXISTING_UUID", existingUuid) // teruskan UUID existing
            }
            startActivity(nextIntent)
        }
    }

    private fun setupRecyclerView() {
        val list = loadJenisBangunan()
        adapter = JenisBangunanAdapter(list) {}
        binding.rvJenisBangunan.layoutManager = LinearLayoutManager(this)
        binding.rvJenisBangunan.adapter = adapter
    }

    private fun loadJenisBangunan(): List<JenisBangunanModel> {
        val sharedPrefs = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)
        val json = sharedPrefs.getString("master_data", null)
        if (!json.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val wrapper = gson.fromJson(json, MasterDataResponse::class.java)
                val list = wrapper?.jenis_bangunan ?: wrapper?.jenisBangunan
                if (!list.isNullOrEmpty()) return list
            } catch (e: Exception) { }
        }
        return getFallbackList()
    }

    private fun getFallbackList(): List<JenisBangunanModel> {
        return listOf(
            JenisBangunanModel(1, "Palung Sungai / Sudetan / Floodway", "OP-01.a"),
            JenisBangunanModel(2, "Tanggul Banjir (Tanah Timbun)", "OP-01.b"),
            JenisBangunanModel(3, "Tanggul Banjir / Revetmen (Pasangan Batu)", "OP-01.c"),
            JenisBangunanModel(4, "Turap / Revetmen (Beton/Baja)", "OP-01.d"),
            JenisBangunanModel(5, "Krib", "OP-01.e"),
            JenisBangunanModel(6, "Pelimpah Banjir", "OP-01.f"),
            JenisBangunanModel(7, "Bangunan Pengendali / Pembagi Aliran", "OP-01.g"),
            JenisBangunanModel(8, "Bendung Karet", "OP-01.h")
        )
    }

    data class MasterDataResponse(
        val jenis_bangunan: List<JenisBangunanModel>? = null,
        val jenisBangunan: List<JenisBangunanModel>? = null
    )
}