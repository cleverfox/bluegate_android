package com.example.bluegate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : ListAdapter<LogEntry, LogAdapter.ViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.log_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logTitle: TextView = itemView.findViewById(R.id.log_title)
        private val logDetails: TextView = itemView.findViewById(R.id.log_details)
        private val logMeta: TextView = itemView.findViewById(R.id.log_meta)

        fun bind(item: LogEntry) {
            logTitle.text = "Index ${item.index} - ${if (item.success) "OK" else "FAIL"}"
            logDetails.text = "Key: ${item.pubkey} | Addr: ${item.addr}"
            logMeta.text = "Action: ${item.authAction} | Uptime: ${item.uptimeMs} ms"
        }
    }
}

class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
    override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
        return oldItem.index == newItem.index
    }

    override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
        return oldItem == newItem
    }
}
