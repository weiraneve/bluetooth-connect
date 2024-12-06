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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import java.util.*

class BluetoothViewModel : ViewModel() {
    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    companion object {
        private const val SERVICE_UUID = "0000FFE5-0000-1000-8000-00805f9b34fb"
        private const val CHARACTERISTIC_WRITE_UUID = "0000FFE9-0000-1000-8000-00805f9b34fb"
    }

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null

    private var bluetoothLeScanner: BluetoothLeScanner? = null

    fun initialize(context: Context, scanner: BluetoothLeScanner) {
        this.context = context
        this.bluetoothLeScanner = scanner
    }

    @SuppressLint("MissingPermission")
    fun startScan(scanner: BluetoothLeScanner) {
        if (!_isScanning.value) {
            _isScanning.value = true
            bluetoothLeScanner = scanner  // 保存scanner引用
            scanner.startScan(scanCallback)
            Log.i("BluetoothConnect", "开始扫描设备")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_isScanning.value) {
            bluetoothLeScanner?.let { scanner ->
                scanner.stopScan(scanCallback)
                _isScanning.value = false
                Log.i("BluetoothConnect", "停止扫描设备")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _isConnected.value = true
                    bluetoothGatt = gatt
                    gatt?.discoverServices()
                    Log.i("BluetoothConnect", "连接成功")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                    writeCharacteristic = null
                    Log.i("BluetoothConnect", "连接断开")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(UUID.fromString(SERVICE_UUID))
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_WRITE_UUID))
                    if (writeCharacteristic != null) {
                        Log.i("BluetoothConnect", "找到写特征")
                    } else {
                        Log.e("BluetoothConnect", "未找到写特征")
                    }
                } else {
                    Log.e("BluetoothConnect", "未找到服务")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(bluetoothLeScanner: BluetoothLeScanner) {
        if (_isScanning.value) {
            bluetoothLeScanner.stopScan(scanCallback)
            _isScanning.value = false
            Log.i("BluetoothConnect", "停止扫描设备")
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

                stopScan()  // 使用修改后的stopScan方法

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
            } ?: false
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