package org.cleverfox.bluegate

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(
    private val onInfoClicked: (ScanResult, View) -> Unit,
    private val onOpenClicked: (ScanResult, View) -> Unit,
    private val onManageClicked: (ScanResult) -> Unit
) : ListAdapter<ScanResult, DeviceListAdapter.ViewHolder>(DeviceDiffCallback()) {

    private val permissions = mutableMapOf<String, Int>()

    fun updatePermissions(deviceAddress: String, permissionLevel: Int) {
        permissions[deviceAddress] = permissionLevel
        val index = currentList.indexOfFirst { it.device.address == deviceAddress }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_list_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, permissions[device.device.address] ?: 0, onInfoClicked, onOpenClicked, onManageClicked)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val device = getItem(position)
            for (payload in payloads) {
                if (payload is Bundle) {
                    if (payload.containsKey("rssi")) {
                        holder.deviceRssi.text = "${device.rssi} dBm"
                    }
                }
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceInfoLayout: LinearLayout = itemView.findViewById(R.id.device_info_layout)
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val deviceRssi: TextView = itemView.findViewById(R.id.device_rssi)
        private val openButton: Button = itemView.findViewById(R.id.open_button)
        private val manageButton: Button = itemView.findViewById(R.id.manage_button)

        @SuppressLint("MissingPermission")
        fun bind(
            device: ScanResult,
            permissionLevel: Int,
            onInfoClicked: (ScanResult, View) -> Unit,
            onOpenClicked: (ScanResult, View) -> Unit,
            onManageClicked: (ScanResult) -> Unit
        ) {
            deviceName.text = device.device.name ?: "Unknown Device"
            deviceRssi.text = "${device.rssi} dBm"

            deviceInfoLayout.setOnClickListener { onInfoClicked(device, itemView) }
            openButton.setOnClickListener { onOpenClicked(device, itemView) }
            manageButton.setOnClickListener { onManageClicked(device) }

            if ((permissionLevel and 0x80) == 0x80) { // Admin bit is set
                manageButton.visibility = View.VISIBLE
                openButton.layoutParams = (openButton.layoutParams as LinearLayout.LayoutParams).apply {
                    weight = 0.5f
                }
                manageButton.layoutParams = (manageButton.layoutParams as LinearLayout.LayoutParams).apply {
                    weight = 0.5f
                }
            } else {
                manageButton.visibility = View.GONE
                openButton.layoutParams = (openButton.layoutParams as LinearLayout.LayoutParams).apply {
                    weight = 1f
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
class DeviceDiffCallback : DiffUtil.ItemCallback<ScanResult>() {
    override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
        return oldItem.device.address == newItem.device.address
    }

    override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
        return oldItem.rssi == newItem.rssi && oldItem.device.name == newItem.device.name
    }

    override fun getChangePayload(oldItem: ScanResult, newItem: ScanResult): Any? {
        val diff = Bundle()
        if (oldItem.rssi != newItem.rssi) {
            diff.putInt("rssi", newItem.rssi)
        }
        if (oldItem.device.name != newItem.device.name) {
            diff.putString("name", newItem.device.name)
        }
        return if (diff.isEmpty) null else diff
    }
}
