package org.cleverfox.bluegate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.cleverfox.bluegate.AdminDashboardActivity.BleOperationContext
import org.cleverfox.bluegate.databinding.FragmentManagementBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ManagementFragment : Fragment() {

    private var _binding: FragmentManagementBinding? = null
    val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var adminActivity: AdminDashboardActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagementBinding.inflate(inflater, container, false)
        adminActivity = activity as AdminDashboardActivity
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.selectedParameter.observe(viewLifecycleOwner) { (id, value) ->
            binding.configIdEditText.setText(id.toString())
            binding.configValueEditText.setText(value.toString())
        }

        sharedViewModel.selectedKeyHex.observe(viewLifecycleOwner) { keyHex ->
            binding.keyToAddEditText.setText(keyHex)
            if (keyHex.length >= 2) {
                val firstByte = keyHex.take(2).toInt(16)
                binding.adminCheckbox.isChecked = (firstByte and 0x80) != 0
                binding.admAdminCheckbox.isChecked = (firstByte and 0x40) != 0
                binding.setAdminCheckbox.isChecked = (firstByte and 0x20) != 0
                binding.manualDoorsCheckbox.isChecked = (firstByte and 0x04) != 0
            }
        }

        binding.addKeyButton.setOnClickListener { addKey() }
        binding.removeKeyButton.setOnClickListener { removeKey() }
        binding.setConfigButton.setOnClickListener { setConfig() }
        binding.getConfigButton.setOnClickListener { getConfig() }
        binding.setNameButton.setOnClickListener { setName() }
    }

    private fun addKey() {
        val keyHex = binding.keyToAddEditText.text.toString()
        if (keyHex.length != 66) {
            Toast.makeText(context, "Invalid key length", Toast.LENGTH_SHORT).show()
            return
        }

        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val isAdmin = binding.adminCheckbox.isChecked
        val isAdmAdmin = binding.admAdminCheckbox.isChecked
        val isSetAdmin = binding.setAdminCheckbox.isChecked
        val isManualDoors = binding.manualDoorsCheckbox.isChecked

        // The first byte is the key type (0x02 or 0x03) and the rest is the 32-byte X coordinate
        val keyType = (keyBytes[0].toInt() and 0x03).toByte()
        val xCoord = keyBytes.sliceArray(1..32)

        var flags = keyType.toInt()
        if (isAdmin) flags = flags or 0x80
        if (isAdmAdmin) flags = flags or 0x40
        if (isSetAdmin) flags = flags or 0x20
        if (isManualDoors) flags = flags or 0x04
        val managementKey = byteArrayOf(flags.toByte()) + xCoord

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        adminActivity.currentOperationContext = BleOperationContext.ADD_KEY
        adminActivity.bluetoothGatt?.let { gatt ->
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_KEY_UUID, managementKey) }
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x01)) }
            adminActivity.queueOperation { adminActivity.bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    private fun removeKey() {
        val keyHex = binding.keyToAddEditText.text.toString()
        if (keyHex.length != 66) {
            Toast.makeText(context, "Invalid key length", Toast.LENGTH_SHORT).show()
            return
        }

        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val isAdmin = binding.adminCheckbox.isChecked
        val isAdmAdmin = binding.admAdminCheckbox.isChecked
        val isSetAdmin = binding.setAdminCheckbox.isChecked
        val isManualDoors = binding.manualDoorsCheckbox.isChecked
        val keyType = (keyBytes[0].toInt() and 0x03).toByte()
        val xCoord = keyBytes.sliceArray(1..32)
        var flags = keyType.toInt()
        if (isAdmin) flags = flags or 0x80
        if (isAdmAdmin) flags = flags or 0x40
        if (isSetAdmin) flags = flags or 0x20
        if (isManualDoors) flags = flags or 0x04
        val managementKey = byteArrayOf(flags.toByte()) + xCoord

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        adminActivity.currentOperationContext = BleOperationContext.REMOVE_KEY
        adminActivity.bluetoothGatt?.let { gatt ->
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_KEY_UUID, managementKey) }
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x02)) }
            adminActivity.queueOperation { adminActivity.bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    private fun setConfig() {
        val paramId = binding.configIdEditText.text.toString().toIntOrNull()
        val paramValue = binding.configValueEditText.text.toString().toLongOrNull()

        if (paramId == null || paramValue == null) {
            Toast.makeText(context, "Invalid config ID or value", Toast.LENGTH_SHORT).show()
            return
        }

        val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(paramValue.toInt()).array()

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        adminActivity.currentOperationContext = BleOperationContext.SET_CONFIG
        adminActivity.bluetoothGatt?.let { gatt ->
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_ID_UUID, byteArrayOf(paramId.toByte())) }
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_VAL_UUID, valueBytes) }
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x10)) }
            adminActivity.queueOperation { adminActivity.bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    private fun getConfig() {
        val paramId = binding.configIdEditText.text.toString().toIntOrNull()
        if (paramId == null) {
            Toast.makeText(context, "Invalid config ID", Toast.LENGTH_SHORT).show()
            return
        }

        adminActivity.currentOperationContext = BleOperationContext.GET_SINGLE_CONFIG
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        adminActivity.bluetoothGatt?.let { gatt ->
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_ID_UUID, byteArrayOf(paramId.toByte())) }
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x11)) }
            adminActivity.queueOperation { adminActivity.bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_PARAM_VAL_UUID) }
        }
    }

    private fun setName() {
        val name = binding.deviceNameEditText.text.toString()
        if (name.isEmpty() || name.length > 63) {
            Toast.makeText(context, "Invalid name length", Toast.LENGTH_SHORT).show()
            return
        }

        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(64)

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        adminActivity.currentOperationContext = BleOperationContext.SET_NAME
        adminActivity.bluetoothGatt?.let { gatt ->
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_NAME_UUID, nameBytes) }
            adminActivity.queueOperation { adminActivity.bleManager?.writeCharacteristic(gatt, BleManager.MANAGEMENT_ACTION_UUID, byteArrayOf(0x20)) }
            adminActivity.queueOperation { adminActivity.bleManager?.readCharacteristic(gatt, BleManager.MANAGEMENT_RESULT_UUID) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
