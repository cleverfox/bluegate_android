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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.bluegate.databinding.ActivityAdminDashboardBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    var bleManager: BleManager? = null
    var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    // Queue for sequential BLE operations
    private val operationQueue = ArrayDeque<Runnable>()
    private var isOperationInProgress = false

    private val parameters = mutableListOf<Pair<Int, Int>>()
    private val sharedViewModel: SharedViewModel by viewModels()

    enum class BleOperationContext { NONE, GET_SINGLE_CONFIG, GET_ALL_CONFIGS, SET_CONFIG, GET_NAME, SET_NAME, ADD_KEY, REMOVE_KEY }
    var currentOperationContext = BleOperationContext.NONE

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

        val viewPager: ViewPager2 = binding.viewPager
        val tabLayout: TabLayout = binding.tabLayout

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Manage"
                1 -> "Parameters"
                else -> null
            }
        }.attach()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    readAllParameters()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        sharedViewModel.selectedParameter.observe(this) {
            viewPager.currentItem = 0
        }
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

    fun queueOperation(runnable: Runnable) {
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

    private fun readAllParameters() {
        parameters.clear()
        currentOperationContext = BleOperationContext.GET_ALL_CONFIGS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothGatt?.let { gatt ->
            for (i in 0..31) {
                queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_ID_UUID, byteArrayOf(i.toByte())) }
                queueOperation { bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x11)) }
                queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_VAL_UUID) }
            }
        }
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
                runOnUiThread {
                    Toast.makeText(this@AdminDashboardActivity, "Device connected.", Toast.LENGTH_SHORT).show()
                }
                processOperationQueue()
                currentOperationContext = BleOperationContext.GET_NAME
                queueOperation { bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_NAME_UUID) }
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
            Log.d(TAG, "onCharacteristicRead: uuid=${characteristic.uuid}, value=${value.joinToString { "%02x".format(it) }}, status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    BleManager.MANAGEMENT_RESULT_UUID -> {
                        val result = value.firstOrNull()?.toInt() ?: -1
                        runOnUiThread {
                            Toast.makeText(this@AdminDashboardActivity, "Management operation result: $result", Toast.LENGTH_SHORT).show()
                        }
                    }
                    BleManager.MANAGEMENT_PARAM_VAL_UUID -> {
                        val configValue = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).int
                        Log.d(TAG, "Config value: $configValue op $currentOperationContext")
                        when (currentOperationContext) {
                            BleOperationContext.GET_SINGLE_CONFIG -> {
                                val mgmtFragment = supportFragmentManager.findFragmentByTag("f0") as? ManagementFragment
                                mgmtFragment?.let {
                                    runOnUiThread {
                                        it.binding.configValueEditText.setText(configValue.toString())
                                    }
                                }
                                currentOperationContext = BleOperationContext.NONE
                            }
                            BleOperationContext.GET_ALL_CONFIGS -> {
                                val paramId = parameters.size
                                parameters.add(Pair(paramId, configValue))
                                val paramsFragment = supportFragmentManager.findFragmentByTag("f1") as? ParametersFragment
                                paramsFragment?.let {
                                    runOnUiThread {
                                        it.updateParameters(ArrayList(parameters))
                                    }
                                }
                                if (parameters.size >= 32) {
                                    currentOperationContext = BleOperationContext.NONE
                                }
                            }
                            else -> {}
                        }
                    }
                    BleManager.MANAGEMENT_NAME_UUID -> {
                        val nullIndex = value.indexOf(0.toByte())
                        val deviceName = if (nullIndex != -1) String(value, 0, nullIndex, Charsets.UTF_8) else String(value, Charsets.UTF_8)
                        val mgmtFragment = supportFragmentManager.findFragmentByTag("f0") as? ManagementFragment
                        mgmtFragment?.let {
                            runOnUiThread {
                                it.binding.deviceNameEditText.setText(deviceName)
                            }
                        }
                    }
                }
            }
            isOperationInProgress = false
            processOperationQueue()
        }
    }
}
