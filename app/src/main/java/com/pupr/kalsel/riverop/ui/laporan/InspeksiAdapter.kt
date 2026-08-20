package com.pupr.kalsel.riverop.ui.laporan

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.pupr.kalsel.riverop.databinding.ItemInspeksiBinding

data class BagianBangunanHardcoded(
    val nama: String,
    val pilihanKondisi: List<String>
)

data class TemuanKondisiInputState(
    val bagianNama: String,
    val selectedConditions: MutableList<String> = mutableListOf(),
    var keterangan: String = ""
)

class InspeksiAdapter(
    private val list: List<BagianBangunanHardcoded>,
    private val states: List<TemuanKondisiInputState>
) : RecyclerView.Adapter<InspeksiAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemInspeksiBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInspeksiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val state = states[position]

        holder.binding.tvBagianNama.text = "${position + 1}. ${item.nama}"

        holder.binding.llCheckboxContainer.removeAllViews()
        for (kondisi in item.pilihanKondisi) {
            val checkBox = CheckBox(holder.binding.root.context).apply {
                text = kondisi
                isChecked = state.selectedConditions.contains(kondisi)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        state.selectedConditions.add(kondisi)
                    } else {
                        state.selectedConditions.remove(kondisi)
                    }
                }
            }
            holder.binding.llCheckboxContainer.addView(checkBox)
        }

        holder.binding.etKeterangan.text = null
        holder.binding.etKeterangan.setText(state.keterangan)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                state.keterangan = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.binding.etKeterangan.addTextChangedListener(textWatcher)
        holder.binding.etKeterangan.tag = textWatcher
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        val tag = holder.binding.etKeterangan.tag
        if (tag is TextWatcher) {
            holder.binding.etKeterangan.removeTextChangedListener(tag)
        }
    }

    override fun getItemCount(): Int = list.size
}