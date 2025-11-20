package com.example.bluegate

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class DeviceListAdapter(
    private val onCheckClicked: (ScanResult, View) -> Unit,
    private val onOpenClicked: (ScanResult, View) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    private val devices = mutableListOf<ScanResult>()
    private val lastUpdated = mutableMapOf<String, Long>()
    private val throttlePeriod = TimeUnit.SECONDS.toMillis(1)

    fun addDevice(device: ScanResult) {
        val deviceAddress = device.device.address
        val currentTime = System.currentTimeMillis()

        val existingDeviceIndex = devices.indexOfFirst { it.device.address == deviceAddress }

        if (existingDeviceIndex != -1) {
            // It's an existing device, check if we should update.
            val lastUpdateTime = lastUpdated[deviceAddress]
            if (lastUpdateTime == null || (currentTime - lastUpdateTime) > throttlePeriod) {
                devices[existingDeviceIndex] = device
                lastUpdated[deviceAddress] = currentTime
                notifyItemChanged(existingDeviceIndex)
            }
        } else {
            // It's a new device, add it.
            devices.add(device)
            lastUpdated[deviceAddress] = currentTime
            notifyItemInserted(devices.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.device.name ?: "Unknown Device"
        holder.deviceRssi.text = "${device.rssi} dBm"

        val serviceUuids = device.scanRecord?.serviceUuids?.joinToString("\n") { it.uuid.toString() }
        holder.serviceUuids.text = serviceUuids ?: "No services advertised"

        holder.checkButton.setOnClickListener { onCheckClicked(device, holder.deviceContainer) }
        holder.openButton.setOnClickListener { onOpenClicked(device, holder.deviceContainer) }
    }

    override fun getItemCount() = devices.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceContainer: FrameLayout = itemView.findViewById(R.id.device_container)
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val deviceRssi: TextView = itemView.findViewById(R.id.device_rssi)
        val serviceUuids: TextView = itemView.findViewById(R.id.service_uuids)
        val checkButton: Button = itemView.findViewById(R.id.check_button)
        val openButton: Button = itemView.findViewById(R.id.open_button)
    }
}
