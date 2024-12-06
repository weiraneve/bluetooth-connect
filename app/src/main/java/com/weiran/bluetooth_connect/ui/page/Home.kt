import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.weiran.bluetooth_connect.ui.theme.BluetoothconnectTheme

@Composable
fun Home() {
    BluetoothconnectTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val tag = "MainActivity"

                Button(
                    onClick = {
                        // todo
                        Log.i(tag, "Hello World")
                    }
                ) {
                    Text(text = "按钮")
                }
            }
        }
    }
}
