package com.example.bluegate

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.bluegate.databinding.ActivityMainBinding
import java.security.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bleManager: BleManager? = null
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var keyPair: KeyPair
    private lateinit var rawPublicKey: ByteArray
    private lateinit var clientNonce: ByteArray

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            enableBluetooth()
        } else {
            Toast.makeText(this, "Permissions required for BLE scanning", Toast.LENGTH_SHORT).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            initializeBleManager()
            startScanning()
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

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val keyManager = KeyManager(keyStore)
        keyPair = keyManager.getOrCreateKeyPair()

        val raw_key = keyManager.extractUncompressedECPoint(keyPair.public.encoded)
        rawPublicKey = keyManager.compressPublicKeyPoint(raw_key!!)
        Log.i(TAG, "My public key ${rawPublicKey?.joinToString("") { "%02x".format(it) }}")

        val publicKeyHex = rawPublicKey.joinToString("") { "%02x".format(it) }

        val truncatedKey = "${publicKeyHex.take(4)}...${publicKeyHex.takeLast(4)}"

        binding.contentMain.publicKeyText.text = "Your key: $truncatedKey"

        binding.contentMain.copyButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Public Key", publicKeyHex)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        deviceListAdapter = DeviceListAdapter(
            onCheckClicked = { result, view -> handleCheck(result, view) },
            onOpenClicked = { result, view -> handleOpen(result, view) }
        )
        binding.contentMain.devicesRecyclerView.adapter = deviceListAdapter
        binding.contentMain.devicesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        } else {
            enableBluetooth()
        }
    }

    override fun onPause() {
        super.onPause()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleManager?.stopScanning(scanCallback)
    }

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
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            initializeBleManager()
            startScanning()
        }
    }

    private fun initializeBleManager() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            bleManager = BleManager(this, bluetoothAdapter)
        }
    }

    private fun startScanning() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleManager?.startScanning(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread { deviceListAdapter.addDevice(result) }
        }
    }

    private fun handleCheck(result: ScanResult, view: View) {
        Log.d(TAG, "handleCheck: starting for device ${result.device.address}")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleManager?.connect(result.device, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected, requesting MTU")
                    // you can discover services first or after MTU; both patterns are used
//                    gatt.discoverServices()
                    gatt.requestMtu(85) // common “max” on Android
                } else {
                    Log.e(TAG, "Connection state change: status=$status, newState=$newState")
                }

                /*
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    gatt.discoverServices()
                } else*/ if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.post { view.setBackgroundColor(Color.WHITE) }
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    bleManager?.disconnect(gatt)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed: mtu=$mtu, status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Now you can safely send up to (mtu - 3) bytes in a single write
                    // e.g. start your writes here or set a flag that MTU is ready
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.d(TAG, "onServicesDiscovered: status $status")
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                bleManager?.writeCharacteristic(gatt, BleManager.CLIENT_KEY_UUID, rawPublicKey)
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d(TAG, "onCharacteristicWrite: uuid ${characteristic.uuid}, status $status")
                if (characteristic.uuid == BleManager.CLIENT_KEY_UUID) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    bleManager?.readCharacteristic(gatt, BleManager.CLIENT_KEY_ACK_UUID)
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                Log.d(TAG, "onCharacteristicRead: uuid ${characteristic.uuid}, status $status, value ${value.joinToString()}")
                if (characteristic.uuid == BleManager.CLIENT_KEY_ACK_UUID) {
                    val success = value.firstOrNull() == 1.toByte()
                    Log.d(TAG, "onCharacteristicRead: CLIENT_KEY_ACK_UUID success: $success")
                    handler.post { view.setBackgroundColor(if (success) Color.GREEN else Color.RED) }
                    handler.postDelayed({ bleManager?.createFadeAnimator(view)?.start() }, 2000)
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    bleManager?.disconnect(gatt)
                }
            }
        })
    }

    private fun handleOpen(result: ScanResult, view: View) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleManager?.connect(result.device, object : BluetoothGattCallback() {
            var nonce: ByteArray? = null

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected, requesting MTU")
                    // you can discover services first or after MTU; both patterns are used
//                    gatt.discoverServices()
                    gatt.requestMtu(85) // common “max” on Android
                    clientNonce = (System.currentTimeMillis() / 1000).toString(16).toByteArray()
                } else {
                    Log.e(TAG, "Connection state change: status=$status, newState=$newState")
                }

                /*
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    gatt.discoverServices()
                } else*/ if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.post { view.setBackgroundColor(Color.WHITE) }
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    bleManager?.disconnect(gatt)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed: mtu=$mtu, status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Now you can safely send up to (mtu - 3) bytes in a single write
                    // e.g. start your writes here or set a flag that MTU is ready
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                bleManager?.readCharacteristic(gatt, BleManager.NONCE_UUID)
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (characteristic.uuid == BleManager.NONCE_UUID) {
                    nonce = value
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    bleManager?.writeCharacteristic(gatt, BleManager.CLIENT_NONCE_UUID, clientNonce)
                } else if (characteristic.uuid == BleManager.AUTHENTICATE_ACK_UUID) {
                    val success = value.firstOrNull() == 1.toByte()
                    handler.post { view.setBackgroundColor(if (success) Color.GREEN else Color.RED) }
                    handler.postDelayed({ bleManager?.createFadeAnimator(view)?.start() }, 2000)
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    bleManager?.disconnect(gatt)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                when (characteristic.uuid) {
                    BleManager.CLIENT_NONCE_UUID -> bleManager?.writeCharacteristic(gatt, BleManager.CLIENT_KEY_UUID, rawPublicKey)
                    BleManager.CLIENT_KEY_UUID -> {
                        val dataToSign = nonce!! + clientNonce
                        val sha256 = MessageDigest.getInstance("SHA-256").digest(dataToSign)
                        Log.i(TAG,"ToSign ${dataToSign.joinToString("") { "%02x".format(it) }}")
                        Log.i(TAG, "Hash ${sha256.joinToString("") { "%02x".format(it) }}")


                        val signature = Signature.getInstance(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Ed25519" else "SHA256withECDSA").apply {
                            initSign(keyPair.private)
                            update(sha256)
                        }.sign()

                        bleManager?.writeCharacteristic(gatt, BleManager.AUTHENTICATE_UUID, signature)
                    }
                    BleManager.AUTHENTICATE_UUID -> bleManager?.readCharacteristic(gatt, BleManager.AUTHENTICATE_ACK_UUID)
                }
            }
        })
    }
}
