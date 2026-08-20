package com.pupr.kalsel.riverop.ui.laporan

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.pupr.kalsel.riverop.data.local.RiverOpDatabase
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.data.local.entity.TemuanKondisiEntity
import com.pupr.kalsel.riverop.data.remote.MasterSyncResponse
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import com.pupr.kalsel.riverop.data.repository.LaporanRepository
import com.pupr.kalsel.riverop.databinding.ActivityDetailLaporanBinding
import com.pupr.kalsel.riverop.databinding.DialogPhotoPreviewBinding
import com.pupr.kalsel.riverop.databinding.ItemBangunanHeaderBinding
import com.pupr.kalsel.riverop.databinding.ItemTemuanKondisiBinding
import com.pupr.kalsel.riverop.models.SyncStatus
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity
import kotlinx.coroutines.launch
import java.io.File

private fun loadPhotoInto(imageView: android.widget.ImageView, path: String, centerCrop: Boolean = false) {
    var request = if (path.startsWith("http://") || path.startsWith("https://")) {
        Glide.with(imageView.context).load(path)
    } else if (path.startsWith("/") && !File(path).exists()) {
        Glide.with(imageView.context).load(RetrofitClient.resolvePhotoUrl(path))
    } else {
        Glide.with(imageView.context).load(File(path))
    }
    if (centerCrop) request = request.centerCrop()
    request.into(imageView)
}

class DetailLaporanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailLaporanBinding
    private lateinit var repository: LaporanRepository
    private var laporanUuid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val database = RiverOpDatabase.getDatabase(this)
        repository = LaporanRepository(database)

        laporanUuid = intent.getStringExtra("EXTRA_LAPORAN_UUID")
        if (laporanUuid.isNullOrBlank()) {
            Toast.makeText(this, "Laporan tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadData()
    }

    private fun loadData() {
        val uuid = laporanUuid ?: return
        lifecycleScope.launch {
            val laporan = repository.getLaporanByUuid(uuid)
            if (laporan == null) {
                Toast.makeText(this@DetailLaporanActivity, "Gagal memuat detail laporan", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            displayHeader(laporan)

            val items = repository.getItemInspeksiByLaporan(uuid)
            val listItems = mutableListOf<DetailListItem>()
            items.forEachIndexed { index, item ->
                val (nama, kode) = getJenisBangunanNameAndKode(item.jenis_bangunan_id)
                listItems.add(DetailListItem.BangunanHeader("Bangunan ${index + 1} — $nama", kode))
                val temuans = repository.getTemuanByItemUuid(item.uuid)
                temuans.forEach { listItems.add(DetailListItem.Temuan(it)) }
            }
            setupRecyclerView(listItems)

            setupActionButtons(laporan)
        }
    }

    private fun openPhotoPreview(path: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val previewBinding = DialogPhotoPreviewBinding.inflate(layoutInflater)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(previewBinding.root)

        loadPhotoInto(previewBinding.imgPreview, path)
        previewBinding.imgPreview.setOnClickListener { dialog.dismiss() }
        previewBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun displayHeader(laporan: LaporanOpEntity) {
        binding.tvTanggal.text = laporan.tanggal_inspeksi
        binding.tvCuaca.text = laporan.cuaca ?: "-"
        binding.tvDebit.text = laporan.debit_air ?: "-"
        binding.tvCatatan.text = laporan.catatan_umum ?: "Tidak ada catatan"
        binding.tvKendala.text = laporan.kendala ?: "Tidak ada kendala"

        val status = laporan.sync_status
        binding.tvStatusBadge.text = status.name

        val colorHex = when (status) {
            SyncStatus.DRAFT -> "#9E9E9E"
            SyncStatus.LOCAL -> "#FF9800"
            SyncStatus.SYNCING -> "#FF9800"
            SyncStatus.SYNCED -> "#4CAF50"
            SyncStatus.FAILED -> "#F44336"
        }
        binding.cardStatusBadge.setCardBackgroundColor(Color.parseColor(colorHex))

        val foto1 = laporan.foto_lokasi_1
        val foto2 = laporan.foto_lokasi_2
        val adaFoto = !foto1.isNullOrBlank() || !foto2.isNullOrBlank()

        if (adaFoto) {
            binding.layoutFotoLokasi.visibility = View.VISIBLE
            binding.tvTidakAdaFoto.visibility = View.GONE

            if (!foto1.isNullOrBlank()) {
                binding.imgFotoLokasi1.visibility = View.VISIBLE
                loadPhotoInto(binding.imgFotoLokasi1, foto1, centerCrop = true)
                binding.imgFotoLokasi1.setOnClickListener { openPhotoPreview(foto1) }
            } else {
                binding.imgFotoLokasi1.visibility = View.GONE
            }

            if (!foto2.isNullOrBlank()) {
                binding.imgFotoLokasi2.visibility = View.VISIBLE
                loadPhotoInto(binding.imgFotoLokasi2, foto2, centerCrop = true)
                binding.imgFotoLokasi2.setOnClickListener { openPhotoPreview(foto2) }
            } else {
                binding.imgFotoLokasi2.visibility = View.GONE
            }
        } else {
            binding.layoutFotoLokasi.visibility = View.GONE
            binding.tvTidakAdaFoto.visibility = View.VISIBLE
        }
    }

    private fun getJenisBangunanNameAndKode(id: Int): Pair<String, String?> {
        val sharedPrefs = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)
        val json = sharedPrefs.getString("master_data", null)
        if (!json.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val wrapper = gson.fromJson(json, MasterSyncResponse::class.java)
                val match = wrapper?.data?.jenis_bangunan?.find { it.id == id }
                if (match != null) return match.nama to match.kode
            } catch (e: Exception) { }
        }
        return "Jenis Bangunan ID: $id" to null
    }

    private fun setupRecyclerView(listItems: List<DetailListItem>) {
        val sharedPrefs = getSharedPreferences("river_op_prefs", Context.MODE_PRIVATE)
        val masterDataJson = sharedPrefs.getString("master_data", null)

        binding.rvTemuan.layoutManager = LinearLayoutManager(this)
        binding.rvTemuan.adapter = DetailListAdapter(listItems, masterDataJson) { path ->
            openPhotoPreview(path)
        }
    }

    private fun setupActionButtons(laporan: LaporanOpEntity) {
        val isDraft = laporan.sync_status == SyncStatus.DRAFT
        val isDeletable = laporan.sync_status == SyncStatus.LOCAL || isDraft

        if (isDraft) {
            binding.tvDetailDeskripsi.visibility = View.VISIBLE
            binding.btnLengkapiLaporan.visibility = View.VISIBLE
            binding.btnLengkapiLaporan.setOnClickListener {
                val intent = Intent(this, RingkasanBangunanActivity::class.java).apply {
                    putExtra("EXTRA_LAPORAN_UUID", laporanUuid)
                }
                startActivity(intent)
            }
        } else {
            binding.tvDetailDeskripsi.visibility = View.GONE
            binding.btnLengkapiLaporan.visibility = View.GONE
        }

        if (isDeletable) {
            binding.btnHapus.visibility = View.VISIBLE
            binding.btnHapus.setOnClickListener {
                showDeleteConfirmation()
            }
        } else {
            binding.btnHapus.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Laporan")
            .setMessage("Apakah Anda yakin ingin menghapus laporan ini secara permanen dari perangkat?")
            .setPositiveButton("Hapus") { dialog, _ ->
                dialog.dismiss()
                deleteLaporan()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun deleteLaporan() {
        val uuid = laporanUuid ?: return
        lifecycleScope.launch {
            try {
                repository.deleteLaporan(uuid)
                Toast.makeText(this@DetailLaporanActivity, "Laporan berhasil dihapus", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@DetailLaporanActivity, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@DetailLaporanActivity, "Gagal menghapus laporan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private sealed class DetailListItem {
        data class BangunanHeader(val judul: String, val kode: String?) : DetailListItem()
        data class Temuan(val data: TemuanKondisiEntity) : DetailListItem()
    }

    private class DetailListAdapter(
        private val items: List<DetailListItem>,
        private val masterDataJson: String?,
        private val onPhotoClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_TEMUAN = 1
        }

        class HeaderViewHolder(val binding: ItemBangunanHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        class TemuanViewHolder(val binding: ItemTemuanKondisiBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is DetailListItem.BangunanHeader -> TYPE_HEADER
                is DetailListItem.Temuan -> TYPE_TEMUAN
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderViewHolder(ItemBangunanHeaderBinding.inflate(inflater, parent, false))
            } else {
                TemuanViewHolder(ItemTemuanKondisiBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is DetailListItem.BangunanHeader -> {
                    holder as HeaderViewHolder
                    holder.binding.tvHeaderNamaBangunan.text = item.judul
                    if (!item.kode.isNullOrBlank()) {
                        holder.binding.tvHeaderKodeOp.visibility = View.VISIBLE
                        holder.binding.tvHeaderKodeOp.text = item.kode
                    } else {
                        holder.binding.tvHeaderKodeOp.visibility = View.GONE
                    }
                }
                is DetailListItem.Temuan -> {
                    holder as TemuanViewHolder
                    val temuan = item.data
                    holder.binding.tvNamaBagian.text = getBagianBangunanName(temuan.bagian_bangunan_id, masterDataJson)

                    holder.binding.cgKondisi.removeAllViews()
                    val conditions = temuan.kondisi_ditemukan.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    for (cond in conditions) {
                        val chip = Chip(holder.binding.root.context).apply {
                            text = cond
                            isClickable = false
                            isCheckable = false
                        }
                        holder.binding.cgKondisi.addView(chip)
                    }

                    if (!temuan.keterangan.isNullOrBlank()) {
                        holder.binding.layoutKeterangan.visibility = View.VISIBLE
                        holder.binding.tvKeterangan.text = temuan.keterangan
                    } else {
                        holder.binding.layoutKeterangan.visibility = View.GONE
                    }

                    if (!temuan.foto.isNullOrBlank()) {
                        holder.binding.imgFotoTemuan.visibility = View.VISIBLE
                        loadPhotoInto(holder.binding.imgFotoTemuan, temuan.foto, centerCrop = true)
                        holder.binding.imgFotoTemuan.setOnClickListener { onPhotoClick(temuan.foto) }
                    } else {
                        holder.binding.imgFotoTemuan.visibility = View.GONE
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        private fun getBagianBangunanName(id: Int, json: String?): String {
            if (!json.isNullOrEmpty()) {
                try {
                    val gson = Gson()
                    val wrapper = gson.fromJson(json, MasterSyncResponse::class.java)
                    val match = wrapper?.data?.bagian_bangunan?.find { it.id == id }
                    if (match != null) return match.nama
                } catch (e: Exception) { }
            }
            return "Bagian $id"
        }
    }
}