package com.example.bluegate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ParameterAdapter(
    private val onItemClick: (Pair<Int, Int>) -> Unit
) : RecyclerView.Adapter<ParameterAdapter.ViewHolder>() {

    private var parameters: List<Pair<Int, Int>> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun updateParameters(newParameters: List<Pair<Int, Int>>) {
        parameters = newParameters
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.parameter_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (id, value) = parameters[position]
        holder.parameterId.text = id.toString()
        holder.parameterValue.text = value.toString()
        holder.itemView.setOnClickListener { onItemClick(parameters[position]) }
    }

    override fun getItemCount() = parameters.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val parameterId: TextView = itemView.findViewById(R.id.parameter_id)
        val parameterValue: TextView = itemView.findViewById(R.id.parameter_value)
    }
}
