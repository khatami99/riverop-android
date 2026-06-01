package com.pupr.kalsel.riverop.ui.laporan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.databinding.ActivityBuatLaporanBinding
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.UUID

class BuatLaporanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuatLaporanBinding
    private lateinit var repository: LaporanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuatLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)

        val listCuaca = arrayOf("Cerah", "Berawan", "Hujan")
        val adapterCuaca = ArrayAdapter(this, android.R.layout.simple_spinner_item, listCuaca)
        adapterCuaca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spCuaca.adapter = adapterCuaca

        val listDebit = arrayOf("Rendah", "Normal", "Tinggi", "Banjir")
        val adapterDebit = ArrayAdapter(this, android.R.layout.simple_spinner_item, listDebit)
        adapterDebit.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spDebit.adapter = adapterDebit

        binding.btnLanjut.setOnClickListener {
            val day = binding.dpTanggal.dayOfMonth
            val month = binding.dpTanggal.month + 1
            val year = binding.dpTanggal.year

            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, day)
            val tanggalInspeksi = calendar.time

            val cuaca = binding.spCuaca.selectedItem?.toString() ?: ""
            val debit = binding.spDebit.selectedItem?.toString() ?: ""
            val catatan = binding.etCatatan.text.toString().trim()
            val kendala = binding.etKendala.text.toString().trim()

            val sharedPrefs = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)

            val juruSungaiId = try {
                sharedPrefs.getInt("juru_sungai_id", 0)
            } catch (e: ClassCastException) {
                sharedPrefs.getString("juru_sungai_id", "0")?.toIntOrNull() ?: 0
            }

            val wilayahSungaiId = try {
                sharedPrefs.getInt("wilayah_sungai_id", 0)
            } catch (e: ClassCastException) {
                sharedPrefs.getString("wilayah_sungai_id", "0")?.toIntOrNull() ?: 0
            }

            val uuid = UUID.randomUUID().toString()

            val laporan = LaporanOpEntity(
                uuid = uuid,
                juru_sungai_id = juruSungaiId,
                wilayah_sungai_id = wilayahSungaiId,
                tanggal_inspeksi = "$year-${month.toString().padStart(2,'0')}-${day.toString().padStart(2,'0')}",
                bulan = month,
                tahun = year,
                cuaca = cuaca.lowercase(),
                debit_air = debit.lowercase(),
                catatan_umum = catatan.ifEmpty { null },
                kendala = kendala.ifEmpty { null },
                foto_lokasi_1 = null,
                foto_lokasi_2 = null,
                dibuat_di_hp = System.currentTimeMillis(),
                sync_status = SyncStatus.LOCAL
            )

            lifecycleScope.launch {
                try {
                    repository.insertLaporanWithDetails(laporan, emptyList(), emptyList())
                    Toast.makeText(this@BuatLaporanActivity, "Laporan berhasil disimpan secara lokal", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@BuatLaporanActivity, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@BuatLaporanActivity, "Gagal menyimpan laporan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}