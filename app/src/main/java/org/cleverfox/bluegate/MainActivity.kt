package org.cleverfox.bluegate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.cleverfox.bluegate.databinding.ActivityMainBinding
import java.security.KeyPair
import java.security.KeyStore
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bleManager: BleManager? = null
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var keyPair: KeyPair
    private lateinit var rawPublicKey: ByteArray
    private lateinit var keyManager : KeyManager

    private val handler = Handler(Looper.getMainLooper())
    private val devices = mutableListOf<ScanResult>()
    private val lastUpdated = mutableMapOf<String, Long>()
    private val throttlePeriod = TimeUnit.SECONDS.toMillis(1)

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            enableBluetooth()
        } else {
            Toast.makeText(this, "Permissions required for BLE scanning", Toast.LENGTH_SHORT).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onBluetoothReady()
        } else {
            Toast.makeText(this, "Bluetooth is required for this app to function", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyManager = KeyManager(keyStore)
        keyPair = keyManager.getOrCreateKeyPair()

        val raw_key = keyManager.extractUncompressedECPoint(keyPair.public.encoded)
        rawPublicKey = keyManager.compressPublicKeyPoint(raw_key!!)
        Log.i(TAG, "My public key ${rawPublicKey.joinToString("") { "%02x".format(it) }}")

        updatePublicKeyDisplay()

        binding.contentMain.copyButton.setOnClickListener {
            val fullPublicKeyHex = rawPublicKey.joinToString("") { "%02x".format(it) }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Public Key", fullPublicKeyHex)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        deviceListAdapter = DeviceListAdapter(
            onInfoClicked = { result, view -> handleCheck(result, view) },
            onOpenClicked = { result, view -> handleOpen(result, view) },
            onManageClicked = { result -> handleManage(result) }
        )
        binding.contentMain.devicesRecyclerView.adapter = deviceListAdapter
        binding.contentMain.devicesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun updatePublicKeyDisplay() {
        val fullPublicKeyHex = rawPublicKey.joinToString("") { "%02x".format(it) }

        val truncatedKey = if (fullPublicKeyHex.length > 12) {
            "${fullPublicKeyHex.take(6)}...${fullPublicKeyHex.takeLast(12)}"
        } else {
            fullPublicKeyHex
        }
        binding.contentMain.publicKeyText.text = "Your key: $truncatedKey"
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (requiredPermissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            enableBluetooth()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleManager?.stopScanning(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            onBluetoothReady()
        }
    }

    @SuppressLint("MissingPermission")
    private fun onBluetoothReady() {
//        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        updatePublicKeyDisplay()
        initializeBleManager()
        startScanning()
    }

    private fun initializeBleManager() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleManager = BleManager(this, bluetoothManager.adapter)
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        devices.clear()
        deviceListAdapter.submitList(devices.toList())
        bleManager?.startScanning(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceAddress = result.device.address
            val currentTime = System.currentTimeMillis()

            val existingDeviceIndex = devices.indexOfFirst { it.device.address == deviceAddress }

            if (existingDeviceIndex != -1) {
                val lastUpdateTime = lastUpdated[deviceAddress]
                if (lastUpdateTime == null || (currentTime - lastUpdateTime) > throttlePeriod) {
                    devices[existingDeviceIndex] = result
                    lastUpdated[deviceAddress] = currentTime
                    val payload = Bundle().apply { putInt("rssi", result.rssi) }
                    deviceListAdapter.notifyItemChanged(existingDeviceIndex, payload)
                }
            } else {
                devices.add(result)
                lastUpdated[deviceAddress] = currentTime
                deviceListAdapter.submitList(devices.toList())
            }
        }
    }

    private fun handleManage(result: ScanResult) {
        authenticate(result.device, null, 0x80)
    }

    @SuppressLint("MissingPermission")
    private fun handleCheck(result: ScanResult, view: View) {
        Log.d(TAG, "handleCheck: starting for device ${result.device.address}")
        bleManager?.connect(result.device, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected, requesting MTU")
                    gatt.requestMtu(85) // common “max” on Android
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.post { view.setBackgroundColor(Color.WHITE) }
                    bleManager?.disconnect(gatt)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed: mtu=$mtu, status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.d(TAG, "onServicesDiscovered: status $status")
                bleManager?.writeCharacteristic(gatt, BleManager.CLIENT_KEY_UUID, rawPublicKey)
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d(TAG, "onCharacteristicWrite: uuid ${characteristic.uuid}, status $status")
                if (characteristic.uuid == BleManager.CLIENT_KEY_UUID) {
                    bleManager?.readCharacteristic(gatt, BleManager.CLIENT_KEY_ACK_UUID)
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                Log.d(TAG, "onCharacteristicRead: uuid ${characteristic.uuid}, status $status, value ${value.joinToString()}")
                when (characteristic.uuid) {
                    BleManager.CLIENT_KEY_ACK_UUID -> {
                        val success = value.firstOrNull() == 1.toByte()
                        Log.d(TAG, "onCharacteristicRead: CLIENT_KEY_ACK_UUID success: $success")
                        handler.post { view.setBackgroundColor(if (success) Color.GREEN else Color.RED) }
                        if (success) {
                            bleManager?.readCharacteristic(gatt, BleManager.PERMISSIONS_UUID)
                        } else {
                            handler.postDelayed({ bleManager?.createFadeAnimator(view)?.start() }, 2000)
                            bleManager?.disconnect(gatt)
                        }
                    }
                    BleManager.PERMISSIONS_UUID -> {
                        val permissionLevel = value.firstOrNull()?.toInt() ?: 0
                        runOnUiThread {
                            deviceListAdapter.updatePermissions(gatt.device.address, permissionLevel)
                        }
                        handler.postDelayed({ bleManager?.createFadeAnimator(view)?.start() }, 2000)
                        bleManager?.disconnect(gatt)
                    }
                }
            }
        })
    }

    private fun handleOpen(result: ScanResult, view: View) {
        authenticate(result.device, view, 0x01)
    }

    private fun authenticate(device: BluetoothDevice, view: View?, action: Int) {
        val authenticator = Authenticator(this, bleManager, keyPair, rawPublicKey, keyManager, handler)
        authenticator.authenticate(
            device,
            view,
            action,
            onAdminAuthorized = {
                val intent = Intent(this@MainActivity, AdminDashboardActivity::class.java)
                intent.putExtra("device_address", it.address)
                startActivity(intent)
            }
        )
    }
}
