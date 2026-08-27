package com.pupr.kalsel.riverop.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pupr.kalsel.riverop.R
import com.pupr.kalsel.riverop.data.local.entity.LaporanOpEntity
import com.pupr.kalsel.riverop.databinding.ItemLaporanBinding
import com.pupr.kalsel.riverop.models.SyncStatus
import java.util.Locale

class LaporanAdapter(
    private val onItemClick: (LaporanOpEntity) -> Unit
) : RecyclerView.Adapter<LaporanAdapter.LaporanViewHolder>() {

    private var listLaporan = emptyList<LaporanOpEntity>()

    fun setLaporan(newList: List<LaporanOpEntity>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = listLaporan.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                listLaporan[oldPos].uuid == newList[newPos].uuid
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                listLaporan[oldPos] == newList[newPos]
        })
        listLaporan = newList
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LaporanViewHolder {
        val binding = ItemLaporanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LaporanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LaporanViewHolder, position: Int) {
        holder.bind(listLaporan[position], onItemClick)
    }

    override fun getItemCount(): Int = listLaporan.size

    class LaporanViewHolder(private val binding: ItemLaporanBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(laporan: LaporanOpEntity, onItemClick: (LaporanOpEntity) -> Unit) {
            val parts = laporan.tanggal_inspeksi.split("-")
            val day = parts[2]
            val monthYear = "${getNamaBulan(parts[1].toInt())} ${parts[0]}"

            binding.tvTanggal.text = "Inspeksi Tanggal $day"
            binding.tvBulanTahun.text = monthYear
            binding.tvCuaca.text = "Cuaca: ${laporan.cuaca.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"

            val colorRes = when (laporan.sync_status) {
                SyncStatus.DRAFT -> R.color.status_draft
                SyncStatus.LOCAL -> R.color.status_local
                SyncStatus.SYNCING -> R.color.status_local
                SyncStatus.SYNCED -> R.color.status_synced
                SyncStatus.FAILED -> R.color.status_failed
            }

            binding.tvStatusBadge.text = laporan.sync_status.label
            binding.cvStatus.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, colorRes))

            binding.root.setOnClickListener {
                onItemClick(laporan)
            }
        }

        private fun getNamaBulan(bulan: Int): String {
            val bulanList = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember")
            return bulanList.getOrElse(bulan) { "-" }
        }
    }
}
