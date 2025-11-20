package com.example.bluegate

import android.animation.ObjectAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Color
import android.os.ParcelUuid
import android.view.View
import java.util.UUID

class BleManager(private val context: Context, private val bluetoothAdapter: BluetoothAdapter) {

    private val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

    companion object {
        // Updated to the correct Service UUID for your device.
        val SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb") // Battery Service
        val NONCE_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0100-fcc5a35e13f1")
        val AUTHENTICATE_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0101-fcc5a35e13f1")
        val CLIENT_KEY_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0102-fcc5a35e13f1")
        val CLIENT_NONCE_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0103-fcc5a35e13f1")
        val CLIENT_KEY_ACK_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0104-fcc5a35e13f1")
        val AUTHENTICATE_ACK_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0105-fcc5a35e13f1")
    }

    fun startScanning(scanCallback: ScanCallback) {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Re-enabled the filter to find only your specific devices.
        scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    fun stopScanning(scanCallback: ScanCallback) {
        scanner?.stopScan(scanCallback)
    }

    fun connect(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt? {
        return device.connectGatt(context, false, callback)
    }

    fun writeCharacteristic(gatt: BluetoothGatt, uuid: UUID, value: ByteArray) {
        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(uuid)
        characteristic?.let {
            it.value = value
            gatt.writeCharacteristic(it)
        }
    }

    fun readCharacteristic(gatt: BluetoothGatt, uuid: UUID) {
        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(uuid)
        characteristic?.let { gatt.readCharacteristic(it) }
    }

    fun createFadeAnimator(view: View): ObjectAnimator {
        return ObjectAnimator.ofArgb(view, "backgroundColor", Color.WHITE, Color.TRANSPARENT).apply {
            duration = 2000
        }
    }
}
