package com.pupr.kalsel.riverop.ui.laporan

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.pupr.kalsel.riverop.R
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.ItemInspeksiEntity
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity
import com.pupr.kalsel.riverop.data.remote.BagianBangunanModel
import com.pupr.kalsel.riverop.data.remote.MasterSyncResponse
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.databinding.ActivityFormBagian3Binding
import com.pupr.kalsel.riverop.databinding.ItemInspeksiBagian3Binding
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity
import com.pupr.kalsel.riverop.utils.LokasiHelper
import com.pupr.kalsel.riverop.utils.PhotoPickerHelper
import com.pupr.kalsel.riverop.utils.PrefsHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class FormBagian3Activity : AppCompatActivity() {

    private lateinit var binding: ActivityFormBagian3Binding
    private lateinit var repository: LaporanRepository
    private lateinit var adapter: InspectionAdapter
    private lateinit var photoPickerHelper: PhotoPickerHelper

    private var jenisBangunanId: Int = 0
    private var kodeOp: String? = null
    private var activePosition: Int = -1
    private var activePhotoIndex: Int = -1
    private var tempCameraFile: File? = null

    private var existingLaporanUuid: String? = null
    private var existingItemUuid: String? = null
    private var isEditItemMode = false

    private val itemList = mutableListOf<InspectionItemData>()

    private lateinit var lokasiHelper: LokasiHelper
    private var lokasiLat: Double? = null
    private var lokasiLng: Double? = null

    private val mintaIzinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        if (hasil.values.any { it }) {
            ambilLokasi()
        } else {
            tampilkanStatusLokasi("Izin lokasi ditolak", abu = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFormBagian3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)
        photoPickerHelper = PhotoPickerHelper(this)

        jenisBangunanId = intent.getIntExtra("EXTRA_JENIS_BANGUNAN_ID", 0)
        kodeOp = intent.getStringExtra("EXTRA_KODE_OP")
        existingLaporanUuid = intent.getStringExtra("EXTRA_EXISTING_UUID")
        existingItemUuid = intent.getStringExtra("EXTRA_EXISTING_ITEM_UUID")
        isEditItemMode = !existingItemUuid.isNullOrBlank()

        setNamaBangunanHint()

        lokasiHelper = LokasiHelper(this)
        binding.btnAmbilLokasi.setOnClickListener { mulaiAmbilLokasi() }
        // Dicoba otomatis saat form dibuka; juru tetap bisa mengulang manual.
        mulaiAmbilLokasi()

        binding.etNamaBangunan.setOnEditorActionListener { _, _, _ ->
            tutupKeyboard()
            true
        }

        if (isEditItemMode) {
            prefillFromExistingItem()
        } else {
            setupRecyclerView(emptyMap())
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        binding.btnSimpanDraft3.setOnClickListener {
            if (binding.etNamaBangunan.text.toString().isBlank()) {
                Toast.makeText(this, "Nama bangunan wajib diisi", Toast.LENGTH_SHORT).show()
                binding.etNamaBangunan.requestFocus()
                return@setOnClickListener
            }
            saveBangunanAndAction(targetStatus = SyncStatus.DRAFT, addMore = false)
        }

        binding.btnTambahBangunan.setOnClickListener {
            if (validateInputs()) {
                saveBangunanAndAction(targetStatus = SyncStatus.DRAFT, addMore = true)
            }
        }

        binding.btnSimpanLaporan.setOnClickListener {
            if (validateInputs()) {
                saveBangunanAndAction(targetStatus = SyncStatus.LOCAL, addMore = false)
            }
        }
    }

    private fun setNamaBangunanHint() {
        val contoh = when (kodeOp) {
            "OP-01.a" -> "Palung STA 12+500"
            "OP-01.b" -> "Tanggul KM 12"
            "OP-01.c" -> "Tanggul KM 5"
            "OP-01.d" -> "Turap STA 3+200"
            "OP-01.e" -> "Krib KM 9"
            "OP-01.f" -> "Pelimpah STA 7+500"
            "OP-01.g" -> "Bangunan Pembagi STA 4+000"
            "OP-01.h" -> "Bendung Karet STA 10+000"
            else -> "Bangunan KM 5"
        }
        binding.etNamaBangunan.hint = "Contoh: $contoh"
    }

    private fun prefillFromExistingItem() {
        val itemUuid = existingItemUuid ?: return
        lifecycleScope.launch {
            val existingItem = repository.getItemByUuid(itemUuid)
            existingItem?.nama_bangunan?.let { binding.etNamaBangunan.setText(it) }
            existingItem?.nama_sungai?.let { binding.etNamaSungai.setText(it) }
            existingItem?.lokasi_km_dari?.let { binding.etKmDari.setText(tulisKm(it)) }
            existingItem?.lokasi_km_sampai?.let { binding.etKmSampai.setText(tulisKm(it)) }

            // Tampilkan titik yang sudah tersimpan agar juru tahu bangunan ini
            // sudah berlokasi, tanpa harus mengambil ulang.
            val lat = existingItem?.koordinat_lat
            val lng = existingItem?.koordinat_lng
            if (lat != null && lng != null && lokasiLat == null) {
                lokasiLat = lat
                lokasiLng = lng
                binding.btnAmbilLokasi.text = "Ulangi"
                tampilkanStatusLokasi(
                    String.format(Locale.US, "%.5f, %.5f  (tersimpan)", lat, lng),
                    abu = false
                )
            }

            val temuans = repository.getTemuanByItemUuid(itemUuid)
            val temuanMap = temuans.associate { it.bagian_bangunan_id to it }
            setupRecyclerView(temuanMap)
        }
    }

    private fun setupRecyclerView(temuanMap: Map<Int, TemuanKondisiEntity>) {
        val bagianList = loadBagianBangunanForJenis(jenisBangunanId)

        if (bagianList.isEmpty()) {
            Toast.makeText(this, "Data master bagian bangunan tidak ditemukan. Silakan sync data master dulu.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bagianList.forEach { bagian ->
            val existing = temuanMap[bagian.id]
            itemList.add(
                InspectionItemData(
                    bagianBangunanId = bagian.id,
                    namaBagian = bagian.nama,
                    kondisi = existing?.kondisi_ditemukan ?: "Normal",
                    tindakan = existing?.tindakan_diperlukan ?: "",
                    foto1 = existing?.foto,
                    foto2 = existing?.foto_2,
                    foto3 = existing?.foto_3
                )
            )
        }

        adapter = InspectionAdapter(itemList) { position, photoIndex ->
            activePosition = position
            activePhotoIndex = photoIndex
            showPhotoPickerDialog()
        }

        binding.rvItemInspeksi.layoutManager = LinearLayoutManager(this)
        binding.rvItemInspeksi.adapter = adapter
    }

    private fun loadBagianBangunanForJenis(jenisBangunanId: Int): List<BagianBangunanModel> {
        val sharedPrefs = PrefsHelper.get(this)
        val json = sharedPrefs.getString("master_data", null)
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val gson = Gson()
            val wrapper = gson.fromJson(json, MasterSyncResponse::class.java)
            wrapper?.data?.bagian_bangunan
                ?.filter { it.jenis_bangunan_id == jenisBangunanId && it.aktif }
                ?.sortedBy { it.urutan }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

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

    private fun showPhotoPickerDialog() {
        // Tanpa ini, fokus tetap tertinggal di kolom nama bangunan sehingga
        // keyboard muncul lagi begitu kembali dari kamera atau galeri, dan
        // memakan ruang layar saat juru menggulir daftar bagian bangunan.
        tutupKeyboard()

        AlertDialog.Builder(this)
            .setTitle("Pilih Sumber Foto")
            .setItems(arrayOf("Kamera", "Galeri")) { _, which ->
                if (which == 0) {
                    val file = photoPickerHelper.createImageFile()
                    tempCameraFile = file
                    takePicture.launch(photoPickerHelper.getContentUri(file))
                } else {
                    selectImageFromGallery.launch("image/*")
                }
            }
            .show()
    }

    private fun processSelectedImage(uri: Uri) {
        if (activePosition == -1 || activePhotoIndex == -1) return
        val targetFile = photoPickerHelper.createImageFile()
        val savedPath = photoPickerHelper.compressAndSaveImage(uri, targetFile)
        if (savedPath != null) {
            val item = itemList[activePosition]
            when (activePhotoIndex) {
                1 -> item.foto1 = savedPath
                2 -> item.foto2 = savedPath
                3 -> item.foto3 = savedPath
            }
            adapter.notifyItemChanged(activePosition)
        } else {
            Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        if (binding.etNamaBangunan.text.toString().isBlank()) {
            Toast.makeText(this, "Nama bangunan wajib diisi", Toast.LENGTH_SHORT).show()
            binding.etNamaBangunan.requestFocus()
            return false
        }
        for (item in itemList) {
            if (item.foto1 == null) {
                Toast.makeText(this, "Bagian \"${item.namaBagian}\" wajib punya minimal 1 foto.", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    /* ---------- Keyboard ---------- */

    private fun tutupKeyboard() {
        currentFocus?.let { fokus ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(fokus.windowToken, 0)
            fokus.clearFocus()
        }
    }

    /**
     * Menyentuh area mana pun di luar kolom teks akan menutup keyboard.
     * Juru mengisi form ini sambil berdiri di lapangan, jadi keyboard yang
     * bertahan terus memakan ruang gulir yang dibutuhkan.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val fokus = currentFocus
            if (fokus is EditText) {
                val batas = Rect()
                fokus.getGlobalVisibleRect(batas)
                if (!batas.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    tutupKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /* ---------- Titik lokasi bangunan ---------- */

    private fun mulaiAmbilLokasi() {
        if (!lokasiHelper.adaIzin()) {
            mintaIzinLokasi.launch(LokasiHelper.IZIN)
            return
        }
        ambilLokasi()
    }

    private fun ambilLokasi() {
        binding.pbLokasi.visibility = View.VISIBLE
        binding.tvIkonLokasi.visibility = View.GONE
        binding.btnAmbilLokasi.isEnabled = false
        binding.tvStatusLokasi.text = "Mencari lokasi..."
        binding.tvStatusLokasi.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        lifecycleScope.launch {
            val hasil = lokasiHelper.ambil()

            binding.pbLokasi.visibility = View.GONE
            binding.tvIkonLokasi.visibility = View.VISIBLE
            binding.btnAmbilLokasi.isEnabled = true

            when (hasil) {
                is LokasiHelper.Hasil.Berhasil -> {
                    lokasiLat = hasil.lat
                    lokasiLng = hasil.lng
                    binding.btnAmbilLokasi.text = "Ulangi"
                    tampilkanStatusLokasi(
                        String.format(
                            Locale.US,
                            "%.5f, %.5f  ±%.0f m",
                            hasil.lat, hasil.lng, hasil.akurasiMeter
                        ),
                        abu = false
                    )
                }
                LokasiHelper.Hasil.IzinDitolak -> tampilkanStatusLokasi("Izin lokasi ditolak", abu = true)
                LokasiHelper.Hasil.GpsMati -> tampilkanStatusLokasi("GPS mati, aktifkan dulu", abu = true)
                LokasiHelper.Hasil.TidakDitemukan -> tampilkanStatusLokasi("Lokasi tidak ditemukan, coba lagi", abu = true)
            }
        }
    }

    private fun tampilkanStatusLokasi(pesan: String, abu: Boolean) {
        binding.tvStatusLokasi.text = pesan
        binding.tvStatusLokasi.setTextColor(
            ContextCompat.getColor(this, if (abu) R.color.text_secondary else R.color.kondisi_normal)
        )
    }

    private fun saveBangunanAndAction(targetStatus: SyncStatus, addMore: Boolean) {
        val juruSungaiId = intent.getIntExtra("EXTRA_JURU_SUNGAI_ID", 0)
        val dasId = intent.getIntExtra("EXTRA_DAS_ID", 0)
        val tanggal = intent.getStringExtra("EXTRA_TANGGAL") ?: ""
        val bulan = intent.getIntExtra("EXTRA_BULAN", 0)
        val tahun = intent.getIntExtra("EXTRA_TAHUN", 0)
        val cuaca = intent.getStringExtra("EXTRA_CUACA") ?: ""
        val debit = intent.getStringExtra("EXTRA_DEBIT") ?: ""
        val catatan = intent.getStringExtra("EXTRA_CATATAN") ?: ""
        val kendala = intent.getStringExtra("EXTRA_KENDALA") ?: ""
        val foto1 = intent.getStringExtra("EXTRA_FOTO_1")
        val foto2 = intent.getStringExtra("EXTRA_FOTO_2")

        val namaBangunan = binding.etNamaBangunan.text.toString().trim().ifEmpty { null }
        val namaSungai = binding.etNamaSungai.text.toString().trim().ifEmpty { null }
        val kmDari = bacaKm(binding.etKmDari.text.toString())
        val kmSampai = bacaKm(binding.etKmSampai.text.toString())

        lifecycleScope.launch {
            try {
                val laporanUuid = if (existingLaporanUuid.isNullOrEmpty()) {
                    val newUuid = UUID.randomUUID().toString()
                    repository.insertLaporanOnly(
                        LaporanOpEntity(
                            uuid = newUuid,
                            juru_sungai_id = juruSungaiId,
                            das_id = dasId,
                            tanggal_inspeksi = tanggal,
                            bulan = bulan,
                            tahun = tahun,
                            cuaca = cuaca,
                            debit_air = debit,
                            catatan_umum = catatan.ifEmpty { null },
                            kendala = kendala.ifEmpty { null },
                            foto_lokasi_1 = foto1,
                            foto_lokasi_2 = foto2,
                            sync_status = targetStatus,
                            dibuat_di_hp = System.currentTimeMillis()
                        )
                    )
                    existingLaporanUuid = newUuid
                    newUuid
                } else {
                    val existing = repository.getLaporanByUuid(existingLaporanUuid!!)
                    if (existing != null) {
                        repository.updateLaporan(existing.copy(sync_status = targetStatus))
                    }
                    existingLaporanUuid!!
                }

                if (isEditItemMode) {
                    val itemUuid = existingItemUuid!!
                    val existingItem = repository.getItemByUuid(itemUuid)
                    val updatedItem = (existingItem ?: ItemInspeksiEntity(
                        uuid = itemUuid,
                        laporan_op_uuid = laporanUuid,
                        jenis_bangunan_id = jenisBangunanId,
                        nama_bangunan = null,
                        nama_sungai = null,
                        lokasi_km_dari = null,
                        lokasi_km_sampai = null,
                        koordinat_lat = null,
                        koordinat_lng = null,
                        tipe_konstruksi = null,
                        catatan = null,
                        foto_1 = null,
                        foto_2 = null,
                        foto_3 = null
                    )).let { dasar ->
                        dasar.copy(
                            nama_bangunan = namaBangunan,
                            nama_sungai = namaSungai ?: dasar.nama_sungai,
                            lokasi_km_dari = kmDari ?: dasar.lokasi_km_dari,
                            lokasi_km_sampai = kmSampai ?: dasar.lokasi_km_sampai,
                            // Titik lama dipertahankan bila kali ini gagal diambil,
                            // supaya mengubah data tidak menghapus lokasi yang sudah ada.
                            koordinat_lat = lokasiLat ?: dasar.koordinat_lat,
                            koordinat_lng = lokasiLng ?: dasar.koordinat_lng,
                            foto_1 = itemList.firstOrNull()?.foto1,
                            foto_2 = itemList.firstOrNull()?.foto2,
                            foto_3 = itemList.firstOrNull()?.foto3
                        )
                    }
                    val temuans = itemList.map { item ->
                        TemuanKondisiEntity(
                            item_inspeksi_uuid = itemUuid,
                            bagian_bangunan_id = item.bagianBangunanId,
                            kondisi_ditemukan = item.kondisi,
                            tindakan_diperlukan = item.tindakan.ifBlank { null },
                            koordinat_lat = null,
                            koordinat_lng = null,
                            keterangan = null,
                            foto = item.foto1,
                            foto_2 = item.foto2,
                            foto_3 = item.foto3
                        )
                    }
                    repository.updateItemWithTemuans(updatedItem, temuans)
                } else {
                    val itemUuid = UUID.randomUUID().toString()
                    val itemInspeksi = ItemInspeksiEntity(
                        uuid = itemUuid,
                        laporan_op_uuid = laporanUuid,
                        jenis_bangunan_id = jenisBangunanId,
                        nama_bangunan = namaBangunan,
                        nama_sungai = namaSungai,
                        lokasi_km_dari = kmDari,
                        lokasi_km_sampai = kmSampai,
                        koordinat_lat = lokasiLat,
                        koordinat_lng = lokasiLng,
                        tipe_konstruksi = null,
                        catatan = null,
                        foto_1 = itemList.firstOrNull()?.foto1,
                        foto_2 = itemList.firstOrNull()?.foto2,
                        foto_3 = itemList.firstOrNull()?.foto3
                    )
                    val temuans = itemList.map { item ->
                        TemuanKondisiEntity(
                            item_inspeksi_uuid = itemUuid,
                            bagian_bangunan_id = item.bagianBangunanId,
                            kondisi_ditemukan = item.kondisi,
                            tindakan_diperlukan = item.tindakan.ifBlank { null },
                            koordinat_lat = null,
                            koordinat_lng = null,
                            keterangan = null,
                            foto = item.foto1,
                            foto_2 = item.foto2,
                            foto_3 = item.foto3
                        )
                    }
                    repository.insertItemWithTemuans(itemInspeksi, temuans)
                }

                if (addMore) {
                    startActivity(Intent(this@FormBagian3Activity, PilihJenisBangunanActivity::class.java).apply {
                        putExtra("EXTRA_JURU_SUNGAI_ID", juruSungaiId)
                        putExtra("EXTRA_DAS_ID", dasId)
                        putExtra("EXTRA_TANGGAL", tanggal)
                        putExtra("EXTRA_BULAN", bulan)
                        putExtra("EXTRA_TAHUN", tahun)
                        putExtra("EXTRA_CUACA", cuaca)
                        putExtra("EXTRA_DEBIT", debit)
                        putExtra("EXTRA_CATATAN", catatan)
                        putExtra("EXTRA_KENDALA", kendala)
                        putExtra("EXTRA_FOTO_1", foto1)
                        putExtra("EXTRA_FOTO_2", foto2)
                        putExtra("EXTRA_EXISTING_UUID", laporanUuid)
                    })
                    finish()
                } else {
                    val message = if (targetStatus == SyncStatus.DRAFT) "Draft berhasil disimpan" else "Laporan berhasil disimpan"
                    Toast.makeText(this@FormBagian3Activity, message, Toast.LENGTH_SHORT).show()
                    if (isEditItemMode) {
                        finish()
                    } else {
                        startActivity(Intent(this@FormBagian3Activity, DashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                        finish()
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(this@FormBagian3Activity, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class InspectionAdapter(
    private val list: List<InspectionItemData>,
    private val onPhotoRequested: (position: Int, photoIndex: Int) -> Unit
) : RecyclerView.Adapter<InspectionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInspeksiBagian3Binding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position], position)
    }

    override fun getItemCount(): Int = list.size

    inner class ViewHolder(private val binding: ItemInspeksiBagian3Binding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InspectionItemData, position: Int) {
            binding.tvNamaBagian.text = item.namaBagian

            if (item.kondisi == "Normal") binding.rbNormal.isChecked = true
            else binding.rbBermasalah.isChecked = true

            updateKondisiStyle(item.kondisi == "Normal")

            binding.etTindakan.setText(item.tindakan)

            binding.rgKondisi.setOnCheckedChangeListener { _, checkedId ->
                val isNormal = checkedId == R.id.rbNormal
                item.kondisi = if (isNormal) "Normal" else "Rusak / Bermasalah"
                updateKondisiStyle(isNormal)
            }

            binding.layoutFotoSeksi.visibility = android.view.View.VISIBLE

            fun loadFoto(path: String?, imageView: android.widget.ImageView, layoutPreview: android.view.View) {
                if (path != null) {
                    layoutPreview.visibility = android.view.View.VISIBLE
                    Glide.with(binding.root.context).load(File(path)).into(imageView)
                } else {
                    layoutPreview.visibility = android.view.View.GONE
                }
            }

            loadFoto(item.foto1, binding.imgItemFoto1, binding.layoutPreviewFoto1)
            loadFoto(item.foto2, binding.imgItemFoto2, binding.layoutPreviewFoto2)
            loadFoto(item.foto3, binding.imgItemFoto3, binding.layoutPreviewFoto3)

            val fotoCount = listOfNotNull(item.foto1, item.foto2, item.foto3).size
            binding.btnTambahFoto.visibility = if (fotoCount >= 3) android.view.View.GONE else android.view.View.VISIBLE

            binding.btnTambahFoto.setOnClickListener {
                val nextIndex = when {
                    item.foto1 == null -> 1
                    item.foto2 == null -> 2
                    else -> 3
                }
                onPhotoRequested(position, nextIndex)
            }

            binding.btnHapusItemFoto1.setOnClickListener { item.foto1 = null; notifyItemChanged(position) }
            binding.btnHapusItemFoto2.setOnClickListener { item.foto2 = null; notifyItemChanged(position) }
            binding.btnHapusItemFoto3.setOnClickListener { item.foto3 = null; notifyItemChanged(position) }
        }

        private fun updateKondisiStyle(isNormal: Boolean) {
            val ctx = binding.root.context
            val bgColor = ContextCompat.getColor(ctx, if (isNormal) R.color.kondisi_normal_bg else R.color.kondisi_rusak_bg)
            val normalTextColor = ContextCompat.getColor(ctx, if (isNormal) R.color.kondisi_normal else R.color.text_secondary)
            val rusakTextColor = ContextCompat.getColor(ctx, if (!isNormal) R.color.kondisi_rusak else R.color.text_secondary)
            (binding.root as CardView).setCardBackgroundColor(bgColor)
            binding.rbNormal.setTextColor(normalTextColor)
            binding.rbBermasalah.setTextColor(rusakTextColor)
        }
    }

}

data class InspectionItemData(
    val bagianBangunanId: Int,
    val namaBagian: String,
    var kondisi: String = "Normal",
    var tindakan: String = "",
    var foto1: String? = null,
    var foto2: String? = null,
    var foto3: String? = null
)

/**
 * Juru bisa mengetik koma maupun titik sebagai pemisah desimal, tergantung
 * papan ketik yang dipakai, jadi keduanya diterima. Kosong berarti belum diisi,
 * bukan nol.
 */
private fun bacaKm(teks: String): Double? =
    teks.trim().replace(',', '.').toDoubleOrNull()

private fun tulisKm(nilai: Double): String =
    if (nilai % 1.0 == 0.0) nilai.toInt().toString() else nilai.toString()
