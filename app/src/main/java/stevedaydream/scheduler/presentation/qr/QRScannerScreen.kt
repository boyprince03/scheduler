// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改开始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView
import stevedaydream.scheduler.util.showToast // 引用 scheduler/util/Extensions.kt 中的函式

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onCodeScanned: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var flashEnabled by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                context.showToast("相機權限已被拒絕")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("掃描 QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { flashEnabled = !flashEnabled }) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = if (flashEnabled) "關閉閃光燈" else "開啟閃光燈"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (hasCameraPermission) {
            // 顯示掃描器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                QRCodeScanner(
                    flashEnabled = flashEnabled,
                    onScanned = { code ->
                        if (!hasScanned) {
                            hasScanned = true
                            onCodeScanned(code)
                        }
                    }
                )

                // 掃描提示
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "將 QR Code 對準框內掃描",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // 權限被拒絕或等待授權
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "需要相機權限才能掃描 QR Code",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("再次請求權限")
                }
            }
        }
    }
}

@Composable
private fun QRCodeScanner(
    flashEnabled: Boolean,
    onScanned: (String) -> Unit
) {
    var barcodeView: CompoundBarcodeView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            barcodeView?.pause()
        }
    }

    AndroidView(
        factory = { context ->
            CompoundBarcodeView(context).apply {
                barcodeView = this

                // 設定掃描回調
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.let { code ->
                            onScanned(code)
                        }
                    }
                })

                // 啟動預覽
                resume()
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            // 更新閃光燈狀態
            if (flashEnabled) {
                view.setTorchOn()
            } else {
                view.setTorchOff()
            }
        }
    )
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲