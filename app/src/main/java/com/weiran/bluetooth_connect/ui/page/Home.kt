package com.weiran.bluetooth_connect.ui.page

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weiran.bluetooth_connect.R
import com.weiran.bluetooth_connect.common.obj.ConnectionState
import com.weiran.bluetooth_connect.viewmodel.BluetoothViewModel

@SuppressLint("MissingPermission")
@Composable
fun Home(viewModel: BluetoothViewModel = viewModel()) {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager.adapter }
    val bluetoothLeScanner = remember { bluetoothAdapter.bluetoothLeScanner }

    val connectionState = viewModel.connectionState.collectAsStateWithLifecycle().value
    val batteryLevel = viewModel.batteryLevel.collectAsStateWithLifecycle().value

    LaunchedEffect(Unit) {
        viewModel.initialize(context, bluetoothLeScanner)
    }

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            viewModel.startScan(bluetoothLeScanner)
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
            viewModel.startScan(bluetoothLeScanner)
        } else {
            permissionLauncher.launch(missingPermissions)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    Button(
                        onClick = {
                            try {
                                if (!bluetoothAdapter.isEnabled) {
                                    Log.i("BluetoothConnect", "蓝牙未启用")
                                    return@Button
                                }
                                checkAndRequestPermissions()
                            } catch (e: Exception) {
                                Log.e("BluetoothConnect", "蓝牙操作错误: ${e.message}")
                            }
                        }
                    ) {
                        Text(
                            text = when (connectionState) {
                                ConnectionState.Connected -> "已连接"
                                ConnectionState.Connecting -> "正在连接..."
                                ConnectionState.Disconnected -> "连接蓝牙"
                            }
                        )
                    }

                    if (connectionState == ConnectionState.Connecting) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.stopScan() }
                        ) {
                            Text(text = "停止扫描")
                        }
                    }
                }

                if (connectionState == ConnectionState.Connected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = { viewModel.sendCommand(1) }
                        ) {
                            Text(text = "指令1")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.sendCommand(255) }
                        ) {
                            Text(text = "指令2")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.sendCommand(5) }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.auto_mode1),
                                contentDescription = "指令3",
                                modifier = Modifier.size(48.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "电量: $batteryLevel%",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
