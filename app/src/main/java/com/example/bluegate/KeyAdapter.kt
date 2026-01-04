package com.example.bluegate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class KeyAdapter(
    private val onItemClick: (KeyInfo) -> Unit
) : ListAdapter<KeyInfo, KeyAdapter.ViewHolder>(KeyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.key_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val keyValue: TextView = itemView.findViewById(R.id.key_value)
        private val keyAdmin: TextView = itemView.findViewById(R.id.key_admin)

        fun bind(item: KeyInfo, onItemClick: (KeyInfo) -> Unit) {
            keyValue.text = item.keyHex
            keyAdmin.text = "Admin: ${item.admin}"
            keyValue.setOnClickListener { onItemClick(item) }
        }
    }
}

class KeyDiffCallback : DiffUtil.ItemCallback<KeyInfo>() {
    override fun areItemsTheSame(oldItem: KeyInfo, newItem: KeyInfo): Boolean {
        return oldItem.keyHex == newItem.keyHex
    }

    override fun areContentsTheSame(oldItem: KeyInfo, newItem: KeyInfo): Boolean {
        return oldItem == newItem
    }
}
