package com.weiran.bluetooth_connect.viewmodel

import com.weiran.bluetooth_connect.common.Constants
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import com.weiran.bluetooth_connect.common.obj.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BluetoothViewModel : ViewModel() {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private var batteryCharacteristic: BluetoothGattCharacteristic? = null
    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    companion object {
        private const val TAG = "BluetoothViewModel"
    }

    fun initialize(context: Context, scanner: BluetoothLeScanner) {
        this.context = context
        this.bluetoothLeScanner = scanner
    }

    @SuppressLint("MissingPermission")
    fun startScan(scanner: BluetoothLeScanner) {
        if (_connectionState.value == ConnectionState.Disconnected) {
            _connectionState.value = ConnectionState.Connecting
            bluetoothLeScanner = scanner
            scanner.startScan(scanCallback)
            Log.i(TAG, "开始扫描设备")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_connectionState.value == ConnectionState.Connecting) {
            bluetoothLeScanner?.let { scanner ->
                scanner.stopScan(scanCallback)
                _connectionState.value = ConnectionState.Disconnected
                Log.i(TAG, "停止扫描设备")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanWhenConnected() {
        if (_connectionState.value == ConnectionState.Connecting) {
            bluetoothLeScanner?.let { scanner ->
                scanner.stopScan(scanCallback)
                Log.i(TAG, "连接成功，停止扫描设备")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connecting
                    bluetoothGatt = gatt
                    gatt?.discoverServices()
                    Log.i(TAG, "连接成功")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    writeCharacteristic = null
                    Log.i(TAG, "连接断开")
                }
            }
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(UUID.fromString(Constants.SERVICE_UUID))
                if (service != null) {
                    writeCharacteristic =
                        service.getCharacteristic(UUID.fromString(Constants.CHARACTERISTIC_WRITE_UUID))
                    batteryCharacteristic =
                        service.getCharacteristic(UUID.fromString(Constants.CHARACTERISTIC_READ_NOTIFY))

                    if (writeCharacteristic != null && batteryCharacteristic != null) {
                        val success = gatt.setCharacteristicNotification(batteryCharacteristic, true)
                        Log.d(TAG, "设置电量通知: $success")

                        batteryCharacteristic?.let { characteristic ->
                            val descriptor = characteristic.getDescriptor(
                                UUID.fromString(Constants.CHARACTERISTIC_READ_NOTIFY)
                            )
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                                Log.d(TAG, "写入通知描述符")
                            }

                            gatt.readCharacteristic(characteristic)
                        }

                        _connectionState.value = ConnectionState.Connected
                        Log.i(TAG, "找到所需特征，连接完成")
                    } else {
                        Log.e(TAG, "未找到必要的特征")
                        _connectionState.value = ConnectionState.Disconnected
                    }
                } else {
                    Log.e(TAG, "未找到服务")
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == UUID.fromString(Constants.CHARACTERISTIC_READ_NOTIFY)) {
                    val batteryValue = value[0].toInt() and 0xFF
                    _batteryLevel.value = batteryValue
                    Log.i(TAG, "读取到电量: $batteryValue%")
                }
            } else {
                Log.e(TAG, "读取特征值失败: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "收到特征变化通知: ${characteristic.uuid}")
            Log.d(TAG, "数据内容: ${value.contentToString()}")

            if (characteristic.uuid == UUID.fromString(Constants.CHARACTERISTIC_READ_NOTIFY)) {
                val batteryValue = value[0].toInt() and 0xFF
                _batteryLevel.value = batteryValue
                Log.i(TAG, "电量: $batteryValue%")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "通知描述符写入成功")
            } else {
                Log.e(TAG, "通知描述符写入失败: $status")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name
            val deviceAddress = device.address

            if (deviceName != null &&
                deviceName.isNotEmpty() &&
                (deviceName.contains("Ao") || deviceName.contains("ao"))
            ) {

                stopScanWhenConnected()

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Log.i(TAG, "正在连接设备: $deviceName ($deviceAddress)")
                        context?.let { ctx ->
                            device.connectGatt(ctx, false, gattCallback)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "连接失败: ${e.message}")
                        bluetoothLeScanner?.let { scanner ->
                            startScan(scanner)
                        }
                    }
                }, 3000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun sendCommand(key: Int): Boolean {
        val data = byteArrayOf((key shr 8).toByte(), key.toByte())
        return try {
            writeCharacteristic?.let { characteristic ->
                val properties = characteristic.properties
                if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                    characteristic.value = data
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    bluetoothGatt?.writeCharacteristic(characteristic)
                    true
                } else {
                    Log.e(TAG, "特征不支持写入操作")
                    false
                }
            } == true
        } catch (e: Exception) {
            Log.e(TAG, "发送指令失败: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        context = null
        bluetoothLeScanner = null
    }
} 