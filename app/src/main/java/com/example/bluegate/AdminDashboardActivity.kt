package com.example.bluegate

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.bluegate.databinding.ActivityAdminDashboardBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private var bleManager: BleManager? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    // Queue for sequential BLE operations
    private val operationQueue = ArrayDeque<Runnable>()
    private var isOperationInProgress = false

    companion object {
        private const val TAG = "AdminDashboardActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceAddress = intent.getStringExtra("device_address")
        if (deviceAddress == null) {
            Toast.makeText(this, "Device address not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleManager = BleManager(this, bluetoothManager.adapter)

        binding.addKeyButton.setOnClickListener { addKey() }
        binding.removeKeyButton.setOnClickListener { removeKey() }
        binding.setConfigButton.setOnClickListener { setConfig() }
        binding.getConfigButton.setOnClickListener { getConfig() }
        binding.setNameButton.setOnClickListener { setName() }
    }

    override fun onResume() {
        super.onResume()
        connectToDevice()
    }

    override fun onPause() {
        super.onPause()
        disconnectFromDevice()
    }

    private fun connectToDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val device: BluetoothDevice? = bleManager?.bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        bluetoothGatt = bleManager?.connect(device, gattCallback)
    }

    private fun disconnectFromDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bleManager?.disconnect(bluetoothGatt)
        operationQueue.clear()
        isOperationInProgress = false
    }

    private fun queueOperation(runnable: Runnable) {
        operationQueue.add(runnable)
        processOperationQueue()
    }

    private fun processOperationQueue() {
        if (isOperationInProgress || operationQueue.isEmpty()) {
            return
        }
        isOperationInProgress = true
        operationQueue.poll()?.run()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.")
                if (ActivityCompat.checkSelfPermission(this@AdminDashboardActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Services discovered, ready for operations
                runOnUiThread {
                    Toast.makeText(this@AdminDashboardActivity, "Device connected.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite: uuid=${characteristic.uuid}, status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Write failed for ${characteristic.uuid}")
                operationQueue.clear()
            }
            isOperationInProgress = false
            processOperationQueue()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (characteristic.uuid == BleManager.MANAGEMENT_RESULT_UUID) {
                val result = value.firstOrNull()?.toInt() ?: -1
                runOnUiThread {
                    Toast.makeText(this@AdminDashboardActivity, "Management operation result: $result", Toast.LENGTH_SHORT).show()
                }
            } else if (characteristic.uuid == BleManager.MANAGEMENT_PARAM_VAL_UUID) {
                val configValue = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).int
                runOnUiThread {
                    binding.configValueEditText.setText(configValue.toString())
                }
            }
            isOperationInProgress = false
            processOperationQueue()
        }
    }

    private fun addKey() {
        val keyHex = binding.keyToAddEditText.text.toString()
        if (keyHex.length != 64) {
            Toast.makeText(this, "Invalid key length", Toast.LENGTH_SHORT).show()
            return
        }
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val managementKey = byteArrayOf(0x01) + keyBytes // Assuming Ed25519 regular key

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        bluetoothGatt?.let { gatt ->
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_KEY_UUID, managementKey) }
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x01)) }
            queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    private fun removeKey() {
        val keyHex = binding.keyToAddEditText.text.toString()
        if (keyHex.length != 64) {
            Toast.makeText(this, "Invalid key length", Toast.LENGTH_SHORT).show()
            return
        }
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val managementKey = byteArrayOf(0x01) + keyBytes // Assuming Ed25519 regular key

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        bluetoothGatt?.let { gatt ->
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_KEY_UUID, managementKey) }
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x02)) }
            queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    private fun setConfig() {
        val paramId = binding.configIdEditText.text.toString().toIntOrNull()
        val paramValue = binding.configValueEditText.text.toString().toLongOrNull()

        if (paramId == null || paramValue == null) {
            Toast.makeText(this, "Invalid config ID or value", Toast.LENGTH_SHORT).show()
            return
        }

        val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(paramValue.toInt()).array()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        bluetoothGatt?.let { gatt ->
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_ID_UUID, byteArrayOf(paramId.toByte())) }
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_VAL_UUID, valueBytes) }
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x10)) }
            queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    private fun getConfig() {
        val paramId = binding.configIdEditText.text.toString().toIntOrNull()
        if (paramId == null) {
            Toast.makeText(this, "Invalid config ID", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        bluetoothGatt?.let { gatt ->
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_ID_UUID, byteArrayOf(paramId.toByte())) }
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x11)) }
            queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_VAL_UUID) }
        }
    }

    private fun setName() {
        val name = binding.deviceNameEditText.text.toString()
        if (name.isEmpty() || name.length > 63) {
            Toast.makeText(this, "Invalid name length", Toast.LENGTH_SHORT).show()
            return
        }

        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(64)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        bluetoothGatt?.let { gatt ->
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_NAME_UUID, nameBytes) }
            queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x20)) }
            queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }
}
