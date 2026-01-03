package com.example.bluegate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ParameterAdapter(
    private val onItemClick: (Pair<Int, Int>) -> Unit
) : ListAdapter<Pair<Int, Int>, ParameterAdapter.ViewHolder>(ParameterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.parameter_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val parameterId: TextView = itemView.findViewById(R.id.parameter_id)
        private val parameterValue: TextView = itemView.findViewById(R.id.parameter_value)

        fun bind(item: Pair<Int, Int>, onItemClick: (Pair<Int, Int>) -> Unit) {
            parameterId.text = "ID: ${item.first}"
            parameterValue.text = "Value: ${item.second}"
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}

class ParameterDiffCallback : DiffUtil.ItemCallback<Pair<Int, Int>>() {
    override fun areItemsTheSame(oldItem: Pair<Int, Int>, newItem: Pair<Int, Int>): Boolean {
        return oldItem.first == newItem.first
    }

    override fun areContentsTheSame(oldItem: Pair<Int, Int>, newItem: Pair<Int, Int>): Boolean {
        return oldItem == newItem
    }
}
