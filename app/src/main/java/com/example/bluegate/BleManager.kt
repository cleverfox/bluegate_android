package com.example.bluegate

import android.Manifest
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
import android.util.Log
import android.view.View
import androidx.annotation.RequiresPermission
import java.util.UUID

class BleManager(private val context: Context, private val bluetoothAdapter: BluetoothAdapter) {

    private val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

    companion object {
        private const val TAG = "BleManager"
        val DEVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val SERVICE_UUID: UUID = UUID.fromString("6a7e6a7e-4929-42d0-0000-fcc5a35e13f1")
        val NONCE_UUID: UUID = UUID.fromString("00000100-0000-1000-8000-00805F9B34FB")
        val AUTHENTICATE_UUID: UUID = UUID.fromString("00000101-0000-1000-8000-00805F9B34FB")
        val CLIENT_KEY_UUID: UUID = UUID.fromString("00000102-0000-1000-8000-00805F9B34FB")
        val CLIENT_NONCE_UUID: UUID = UUID.fromString("00000103-0000-1000-8000-00805F9B34FB")
        val CLIENT_KEY_ACK_UUID: UUID = UUID.fromString("00000104-0000-1000-8000-00805F9B34FB")
        val AUTHENTICATE_ACK_UUID: UUID = UUID.fromString("00000105-0000-1000-8000-00805F9B34FB")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning(scanCallback: ScanCallback) {
        Log.d(TAG, "startScanning")
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(DEVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning(scanCallback: ScanCallback) {
        Log.d(TAG, "stopScanning")
        scanner?.stopScan(scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt? {
        Log.d(TAG, "Connecting to device ${device.address}")
        return device.connectGatt(context, false, callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(gatt: BluetoothGatt?) {
        Log.d(TAG, "Disconnecting from device")
        gatt?.disconnect()
        gatt?.close()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCharacteristic(gatt: BluetoothGatt, uuid: UUID, value: ByteArray) {
        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(uuid)
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: $uuid")
            service?.let {
                Log.d(TAG, "Available characteristics in service ${it.uuid}:")
                it.characteristics.forEach { char ->
                    Log.d(TAG, "  - ${char.uuid}")
                }
            } ?: run {
                Log.e(TAG, "Service not found: $SERVICE_UUID")
                gatt.services?.forEach { s ->
                    Log.d(TAG, "Available service: ${s.uuid}")
                }
            }
            disconnect(gatt)
            return
        }

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;

        characteristic.value = value
        Log.d(TAG, "Writing to characteristic: $uuid, value: ${value.joinToString { "%02x".format(it) }}")
        val success = gatt.writeCharacteristic(characteristic)
        if (success) {
            Log.d(TAG, "writeCharacteristic initiated successfully.")
        } else {
            Log.e(TAG, "writeCharacteristic initiation failed.")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(gatt: BluetoothGatt, uuid: UUID) {
        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(uuid)
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: $uuid")
            service?.let {
                Log.d(TAG, "Available characteristics in service ${it.uuid}:")
                it.characteristics.forEach { char ->
                    Log.d(TAG, "  - ${char.uuid}")
                }
            } ?: run {
                Log.e(TAG, "Service not found: $SERVICE_UUID")
                gatt.services?.forEach { s ->
                    Log.d(TAG, "Available service: ${s.uuid}")
                }
            }
            disconnect(gatt)
            return
        }
        Log.d(TAG, "Reading from characteristic: $uuid")
        gatt.readCharacteristic(characteristic)
    }

    fun createFadeAnimator(view: View): ObjectAnimator {
        return ObjectAnimator.ofArgb(view, "backgroundColor", Color.WHITE, Color.TRANSPARENT).apply {
            duration = 2000
        }
    }
}
