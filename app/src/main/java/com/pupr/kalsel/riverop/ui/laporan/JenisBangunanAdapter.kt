package com.pupr.kalsel.riverop.ui.laporan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pupr.kalsel.riverop.databinding.ItemJenisBangunanBinding

class JenisBangunanAdapter(
    private val list: List<JenisBangunanModel>,
    private val onItemSelected: (JenisBangunanModel) -> Unit
) : RecyclerView.Adapter<JenisBangunanAdapter.ViewHolder>() {

    private var selectedPosition = -1

    inner class ViewHolder(val binding: ItemJenisBangunanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemJenisBangunanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvKodeOp.text = item.kode_op ?: item.kode ?: ""
        holder.binding.tvNamaBangunan.text = item.nama
        holder.binding.rbSelected.isChecked = position == selectedPosition

        holder.binding.root.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)
            onItemSelected(item)
        }
    }

    override fun getItemCount(): Int = list.size

    fun getSelectedItem(): JenisBangunanModel? {
        return if (selectedPosition != -1) list[selectedPosition] else null
    }
}