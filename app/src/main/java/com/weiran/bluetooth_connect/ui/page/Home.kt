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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

        // 开始扫描的函数
        fun startScan(scanner: BluetoothLeScanner, callback: ScanCallback) {
            if (!isScanning) {
                isScanning = true
                scanner.startScan(callback)
                Log.i("BluetoothConnect", "开始扫描设备")
            }
        }

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

        // 权限请求启动器
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            if (allPermissionsGranted) {
                // 权限获取成功，开始扫描
                startScan(bluetoothLeScanner, scanCallback)
            } else {
                Log.e("BluetoothConnect", "未获得必要权限")
            }
        }

        // 检查权限函数
        fun checkAndRequestPermissions() {
            val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }

            val missingPermissions = requiredPermissions.filter {
                ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (missingPermissions.isEmpty()) {
                // 已经有所有权限，直接开始扫描
                startScan(bluetoothLeScanner, scanCallback)
            } else {
                // 请求缺少的权限
                permissionLauncher.launch(missingPermissions)
            }
        }

        // 停止扫描的函数
        fun stopScan() {
            if (isScanning) {
                bluetoothLeScanner.stopScan(scanCallback)
                isScanning = false
                Log.i("BluetoothConnect", "停止扫描设备")
            }
        }

        // 扫描回调
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row {
                    Button(
                        onClick = {
                            try {
                                if (!bluetoothAdapter.isEnabled) {
                                    Log.i("BluetoothConnect", "蓝牙未启用")
                                    return@Button
                                }

                                // 检查并请求权限
                                checkAndRequestPermissions()

                            } catch (e: Exception) {
                                Log.e("BluetoothConnect", "蓝牙操作错误: ${e.message}")
                            }
                        }
                    ) {
                        Text(text = if (isScanning) "正在扫描..." else "连接蓝牙")
                    }

                    // 当正在扫描时显示停止按钮
                    if (isScanning) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { stopScan() }
                        ) {
                            Text(text = "停止扫描")
                        }
                    }
                }
            }
        }
    }
}
