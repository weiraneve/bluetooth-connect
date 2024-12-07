package com.weiran.bluetooth_connect.viewmodel

import Constants
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
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
            Log.i("BluetoothConnect", "开始扫描设备")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_connectionState.value == ConnectionState.Connecting) {
            bluetoothLeScanner?.let { scanner ->
                scanner.stopScan(scanCallback)
                _connectionState.value = ConnectionState.Disconnected
                Log.i("BluetoothConnect", "停止扫描设备")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanWhenConnected() {
        if (_connectionState.value == ConnectionState.Connecting) {
            bluetoothLeScanner?.let { scanner ->
                scanner.stopScan(scanCallback)
                Log.i("BluetoothConnect", "连接成功，停止扫描设备")
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
                    Log.i("BluetoothConnect", "连接成功")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    writeCharacteristic = null
                    Log.i("BluetoothConnect", "连接断开")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(UUID.fromString(Constants.SERVICE_UUID))
                if (service != null) {
                    writeCharacteristic =
                        service.getCharacteristic(UUID.fromString(Constants.CHARACTERISTIC_WRITE_UUID))
                    if (writeCharacteristic != null) {
                        _connectionState.value = ConnectionState.Connected
                        Log.i("BluetoothConnect", "找到写特征，连接完成")
                    } else {
                        Log.e("BluetoothConnect", "未找到写特征")
                        _connectionState.value = ConnectionState.Disconnected
                    }
                } else {
                    Log.e("BluetoothConnect", "未找到服务")
                    _connectionState.value = ConnectionState.Disconnected
                }
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
                        Log.i("BluetoothConnect", "正在连接设备: $deviceName ($deviceAddress)")
                        context?.let { ctx ->
                            device.connectGatt(ctx, false, gattCallback)
                        }
                    } catch (e: Exception) {
                        Log.e("BluetoothConnect", "连接失败: ${e.message}")
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
                    Log.e("BluetoothConnect", "特征不支持写入操作")
                    false
                }
            } == true
        } catch (e: Exception) {
            Log.e("BluetoothConnect", "发送指令失败: ${e.message}")
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