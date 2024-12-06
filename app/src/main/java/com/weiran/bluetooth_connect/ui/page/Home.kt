import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.weiran.bluetooth_connect.ui.theme.BluetoothconnectTheme

@SuppressLint("MissingPermission")
@Composable
fun Home() {
    BluetoothconnectTheme {
        val context = LocalContext.current
        val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
        val bluetoothAdapter = remember { bluetoothManager.adapter }
        val bluetoothLeScanner = remember { bluetoothAdapter.bluetoothLeScanner }
        var isScanning by remember { mutableStateOf(false) }

        // GATT回调
        val gattCallback = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.i("BluetoothConnect", "连接成功")
                            // 这里可以开始发现服务
                            gatt?.discoverServices()
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.i("BluetoothConnect", "连接断开")
                            // 处理断开连接的情况
                        }
                    }
                }
            }
        }

        // 开始扫描的函数
        fun startScan(scanner: BluetoothLeScanner, callback: ScanCallback) {
            if (!isScanning) {
                isScanning = true
                scanner.startScan(callback)
                Log.i("BluetoothConnect", "开始扫描设备")
            }
        }

        // 扫描回调
        val scanCallback = remember {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val deviceName = device.name
                    val deviceAddress = device.address

                    if (deviceName != null &&
                        deviceName.isNotEmpty() &&
                        (deviceName.contains("Ao") || deviceName.contains("ao"))
                    ) {

                        // 停止扫描
                        bluetoothLeScanner.stopScan(this)
                        isScanning = false

                        // 延迟3秒后连接设备
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                Log.i("BluetoothConnect", "正在连接设备: $deviceName ($deviceAddress)")

                                // 检查权限
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    Log.e("BluetoothConnect", "缺少BLUETOOTH_CONNECT权限")
                                    return@postDelayed
                                }

                                // 这里需要替换成您的BLE服务连接逻辑
                                // 如果您使用BluetoothGatt，可以这样连接：
                                device.connectGatt(context, false, gattCallback)

                            } catch (e: Exception) {
                                Log.e("BluetoothConnect", "连接失败: ${e.message}")
                                // 重新开始扫描
                                startScan(bluetoothLeScanner, this)
                            }
                        }, 3000)
                    }
                }
            }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        try {
                            if (!bluetoothAdapter.isEnabled) {
                                Log.i("BluetoothConnect", "蓝牙未启用")
                                return@Button
                            }

                            // 开始扫描
                            startScan(bluetoothLeScanner, scanCallback)

                        } catch (e: Exception) {
                            Log.e("BluetoothConnect", "蓝牙操作错误: ${e.message}")
                        }
                    }
                ) {
                    Text(text = if (isScanning) "正在扫描..." else "连接蓝牙")
                }
            }
        }
    }
}
