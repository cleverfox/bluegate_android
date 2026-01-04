package com.example.bluegate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature

class Authenticator(
    private val context: Context,
    private val bleManager: BleManager?,
    private val keyPair: KeyPair,
    private val rawPublicKey: ByteArray,
    private val keyManager: KeyManager,
    private val handler: Handler
) {

    companion object {
        private const val TAG = "Authenticator"
    }

    @SuppressLint("MissingPermission")
    fun authenticate(
        device: BluetoothDevice,
        view: View?,
        action: Int,
        onAdminAuthorized: ((BluetoothDevice) -> Unit)? = null,
        onAuthResult: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "Authenticating for device ${device.address}, action: $action")
        bleManager?.connect(device, object : BluetoothGattCallback() {
            var nonce: ByteArray? = null
            var clientNonce: ByteArray? = null

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "Auth Connection state change: status=$status, newState=$newState")
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    clientNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    Log.d(TAG, "Auth Connected, requesting MTU")
                    gatt.requestMtu(85) // common "max" on Android
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    view?.let { handler.post { it.setBackgroundColor(android.graphics.Color.WHITE) } }
                    bleManager?.disconnect(gatt)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "Auth MTU changed: mtu=$mtu, status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (action == 0x01) {
                    bleManager?.readCharacteristic(gatt, BleManager.NONCE_UUID)
                } else {
                    bleManager?.writeCharacteristic(gatt, BleManager.ACTION_UUID, byteArrayOf(action.toByte()))
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                when (characteristic.uuid) {
                    BleManager.NONCE_UUID -> {
                        nonce = value
                        bleManager?.writeCharacteristic(gatt, BleManager.CLIENT_KEY_UUID, rawPublicKey)
                    }
                    BleManager.AUTHENTICATE_ACK_UUID -> {
                        val success = value.firstOrNull() == 1.toByte()
                        Log.d(TAG, "Auth successful: $success")
                        onAuthResult?.invoke(success)
                        view?.let {
                            handler.post { it.setBackgroundColor(if (success) android.graphics.Color.GREEN else android.graphics.Color.RED) }
                            handler.postDelayed({ bleManager?.createFadeAnimator(it)?.start() }, 2000)
                        }
                        if (success && (action and 0x80 == 0x80)) {
                            bleManager?.readCharacteristic(gatt, BleManager.PERMISSIONS_UUID)
                        } else {
                            bleManager?.disconnect(gatt)
                        }
                    }
                    BleManager.PERMISSIONS_UUID -> {
                        val permissions = value.firstOrNull()?.toInt() ?: 0
                        if ((permissions and 0x80) == 0x80) { // is admin
                            onAdminAuthorized?.invoke(gatt.device)
                        } else {
                            handler.post {
                                Toast.makeText(context, "Not an admin.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        bleManager?.disconnect(gatt)
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                when (characteristic.uuid) {
                    BleManager.ACTION_UUID -> bleManager?.readCharacteristic(gatt, BleManager.NONCE_UUID)
                    BleManager.CLIENT_KEY_UUID -> {
                        clientNonce?.let { bleManager?.writeCharacteristic(gatt, BleManager.CLIENT_NONCE_UUID, it) }
                    }
                    BleManager.CLIENT_NONCE_UUID -> {
                        val dataToSign = (nonce ?: ByteArray(0)) + (clientNonce ?: ByteArray(0))
                        var signature = Signature.getInstance("SHA256withECDSA").apply {
                            initSign(keyPair.private)
                            update(dataToSign)
                        }.sign()
                        signature = keyManager.toRawSignature(signature)
                        bleManager?.writeCharacteristic(gatt, BleManager.AUTHENTICATE_UUID, signature)
                    }
                    BleManager.AUTHENTICATE_UUID -> bleManager?.readCharacteristic(gatt, BleManager.AUTHENTICATE_ACK_UUID)
                }
            }
        })
    }
}
