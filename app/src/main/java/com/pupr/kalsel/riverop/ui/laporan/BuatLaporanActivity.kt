package com.pupr.kalsel.riverop.ui.laporan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.databinding.ActivityBuatLaporanBinding
import com.pupr.kalsel.riverop.utils.PhotoPickerHelper
import com.pupr.kalsel.riverop.utils.PrefsHelper
import kotlinx.coroutines.launch
import java.io.File

class BuatLaporanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuatLaporanBinding
    private lateinit var repository: LaporanRepository
    private lateinit var photoPickerHelper: PhotoPickerHelper

    private var isEditMode = false
    private var existingLaporanUuid: String? = null
    private var pathFoto1: String? = null
    private var pathFoto2: String? = null
    private var currentPhotoIndex = 1
    private var tempCameraFile: File? = null

    private val selectImageFromGallery = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processSelectedImage(it) }
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraFile?.let { file ->
                val uri = photoPickerHelper.getContentUri(file)
                processSelectedImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuatLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)
        photoPickerHelper = PhotoPickerHelper(this)

        isEditMode = intent.getBooleanExtra("EXTRA_EDIT_MODE", false)
        existingLaporanUuid = intent.getStringExtra("EXTRA_LAPORAN_UUID")

        setupDropdowns()
        setupPhotoButtons()

        if (isEditMode && !existingLaporanUuid.isNullOrBlank()) {
            prefillFromExisting()
            binding.btnLanjut.text = "Simpan Perubahan"
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        binding.btnLanjut.setOnClickListener {
            if (isEditMode) saveAndGoBack() else saveHeaderAndProceed()
        }
    }

    private fun prefillFromExisting() {
        val uuid = existingLaporanUuid ?: return
        lifecycleScope.launch {
            val laporan = repository.getLaporanByUuid(uuid) ?: return@launch

            val parts = laporan.tanggal_inspeksi.split("-")
            if (parts.size == 3) {
                val year = parts[0].toIntOrNull() ?: return@launch
                val month = parts[1].toIntOrNull()?.minus(1) ?: return@launch
                val day = parts[2].toIntOrNull() ?: return@launch
                binding.dpTanggal.updateDate(year, month, day)
            }

            val listCuaca = arrayOf("cerah", "berawan", "hujan")
            val cuacaIndex = listCuaca.indexOfFirst { it.equals(laporan.cuaca, ignoreCase = true) }
            if (cuacaIndex >= 0) binding.spCuaca.setSelection(cuacaIndex)

            val listDebit = arrayOf("rendah", "normal", "tinggi", "banjir")
            val debitIndex = listDebit.indexOfFirst { it.equals(laporan.debit_air, ignoreCase = true) }
            if (debitIndex >= 0) binding.spDebit.setSelection(debitIndex)

            binding.etCatatan.setText(laporan.catatan_umum ?: "")
            binding.etKendala.setText(laporan.kendala ?: "")

            laporan.foto_lokasi_1?.let { path ->
                pathFoto1 = path
                Glide.with(this@BuatLaporanActivity).load(File(path)).into(binding.imgFoto1)
                binding.btnHapusFoto1.visibility = android.view.View.VISIBLE
            }
            laporan.foto_lokasi_2?.let { path ->
                pathFoto2 = path
                Glide.with(this@BuatLaporanActivity).load(File(path)).into(binding.imgFoto2)
                binding.btnHapusFoto2.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun saveAndGoBack() {
        val uuid = existingLaporanUuid ?: return

        if (pathFoto1 == null) {
            Toast.makeText(this, "Foto lokasi 1 wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val day = binding.dpTanggal.dayOfMonth
        val month = binding.dpTanggal.month + 1
        val year = binding.dpTanggal.year
        val tanggal = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        val cuaca = binding.spCuaca.selectedItem?.toString()?.lowercase() ?: ""
        val debit = binding.spDebit.selectedItem?.toString()?.lowercase() ?: ""
        val catatan = binding.etCatatan.text.toString().trim()
        val kendala = binding.etKendala.text.toString().trim()

        lifecycleScope.launch {
            val existing = repository.getLaporanByUuid(uuid) ?: return@launch
            repository.updateLaporan(
                existing.copy(
                    tanggal_inspeksi = tanggal,
                    bulan = month,
                    tahun = year,
                    cuaca = cuaca,
                    debit_air = debit,
                    catatan_umum = catatan.ifEmpty { null },
                    kendala = kendala.ifEmpty { null },
                    foto_lokasi_1 = pathFoto1,
                    foto_lokasi_2 = pathFoto2
                )
            )
            Toast.makeText(this@BuatLaporanActivity, "Info laporan diperbarui", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupDropdowns() {
        val listCuaca = arrayOf("Cerah", "Berawan", "Hujan")
        val adapterCuaca = ArrayAdapter(this, android.R.layout.simple_spinner_item, listCuaca)
        adapterCuaca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spCuaca.adapter = adapterCuaca

        val listDebit = arrayOf("Rendah", "Normal", "Tinggi", "Banjir")
        val adapterDebit = ArrayAdapter(this, android.R.layout.simple_spinner_item, listDebit)
        adapterDebit.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spDebit.adapter = adapterDebit
    }

    private fun setupPhotoButtons() {
        binding.btnKameraFoto1.setOnClickListener { currentPhotoIndex = 1; launchCamera() }
        binding.btnGaleriFoto1.setOnClickListener { currentPhotoIndex = 1; selectImageFromGallery.launch("image/*") }
        binding.btnHapusFoto1.setOnClickListener {
            pathFoto1 = null
            binding.imgFoto1.setImageResource(android.R.drawable.ic_menu_camera)
            binding.btnHapusFoto1.visibility = android.view.View.GONE
        }
        binding.btnKameraFoto2.setOnClickListener { currentPhotoIndex = 2; launchCamera() }
        binding.btnGaleriFoto2.setOnClickListener { currentPhotoIndex = 2; selectImageFromGallery.launch("image/*") }
        binding.btnHapusFoto2.setOnClickListener {
            pathFoto2 = null
            binding.imgFoto2.setImageResource(android.R.drawable.ic_menu_camera)
            binding.btnHapusFoto2.visibility = android.view.View.GONE
        }
    }

    private fun launchCamera() {
        val file = photoPickerHelper.createImageFile()
        tempCameraFile = file
        takePicture.launch(photoPickerHelper.getContentUri(file))
    }

    private fun processSelectedImage(uri: Uri) {
        val targetFile = photoPickerHelper.createImageFile()
        val savedPath = photoPickerHelper.compressAndSaveImage(uri, targetFile)
        if (savedPath != null) {
            if (currentPhotoIndex == 1) {
                pathFoto1 = savedPath
                Glide.with(this).load(savedPath).into(binding.imgFoto1)
                binding.btnHapusFoto1.visibility = android.view.View.VISIBLE
            } else {
                pathFoto2 = savedPath
                Glide.with(this).load(savedPath).into(binding.imgFoto2)
                binding.btnHapusFoto2.visibility = android.view.View.VISIBLE
            }
        } else {
            Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveHeaderAndProceed() {
        val day = binding.dpTanggal.dayOfMonth
        val month = binding.dpTanggal.month + 1
        val year = binding.dpTanggal.year
        val cuaca = binding.spCuaca.selectedItem?.toString() ?: ""
        val debit = binding.spDebit.selectedItem?.toString() ?: ""
        val catatan = binding.etCatatan.text.toString().trim()
        val kendala = binding.etKendala.text.toString().trim()

        if (pathFoto1 == null) {
            Toast.makeText(this, "Foto lokasi 1 wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPrefs = PrefsHelper.get(this)
        val juruSungaiId = try {
            sharedPrefs.getInt("juru_sungai_id", 0)
        } catch (e: ClassCastException) {
            sharedPrefs.getString("juru_sungai_id", "0")?.toIntOrNull() ?: 0
        }
        val dasId = try {
            sharedPrefs.getInt("das_id", 0)
        } catch (e: ClassCastException) {
            sharedPrefs.getString("das_id", "0")?.toIntOrNull() ?: 0
        }

        if (juruSungaiId == 0 || dasId == 0) {
            Toast.makeText(this, "Sesi login tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        val tanggal = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

        startActivity(Intent(this, PilihJenisBangunanActivity::class.java).apply {
            putExtra("EXTRA_JURU_SUNGAI_ID", juruSungaiId)
            putExtra("EXTRA_DAS_ID", dasId)
            putExtra("EXTRA_TANGGAL", tanggal)
            putExtra("EXTRA_BULAN", month)
            putExtra("EXTRA_TAHUN", year)
            putExtra("EXTRA_CUACA", cuaca.lowercase())
            putExtra("EXTRA_DEBIT", debit.lowercase())
            putExtra("EXTRA_CATATAN", catatan)
            putExtra("EXTRA_KENDALA", kendala)
            putExtra("EXTRA_FOTO_1", pathFoto1)
            putExtra("EXTRA_FOTO_2", pathFoto2)
        })
    }
}