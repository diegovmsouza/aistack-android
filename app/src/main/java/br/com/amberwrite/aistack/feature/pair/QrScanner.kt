package br.com.amberwrite.aistack.feature.pair

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.amberwrite.aistack.core.relay.PairLink
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.icons.Lucide
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Leitor de QR do pareamento (CameraX + ML Kit). Chama [onPairFound] uma única vez,
 * com o primeiro código que for um link de pareamento válido.
 */
@Composable
fun QrScanner(
    onPairFound: (PairLink) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreviewWithScanner(onPairFound)
            ScannerReticle()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AiTheme.colors.bg)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Acesso à câmera necessário", style = AiTheme.typography.heading, color = AiTheme.colors.fg)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Para ler o QR Code de pareamento mostrado no AiStack do desktop, autorize a câmera.",
                    style = AiTheme.typography.bodySmall,
                    color = AiTheme.colors.fg2,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                AiButton(
                    text = "Autorizar câmera",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    leadingIcon = Lucide.Camera
                )
            }
        }

        AiIconButton(
            icon = Lucide.ChevronLeft,
            contentDescription = "Voltar",
            onClick = onBack,
            variant = ButtonVariant.Secondary,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun ScannerReticle() {
    val accent = AiTheme.colors.accent
    val transition = rememberInfiniteTransition(label = "scanLine")
    val lineOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "linePos"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(260.dp)
                .border(2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * lineOffset
                drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
            }
        }
        Text(
            text = "Aponte para o QR Code no AiStack do desktop",
            color = Color.White,
            style = AiTheme.typography.label,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CameraPreviewWithScanner(onPairFound: (PairLink) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFound by rememberUpdatedState(onPairFound)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    // O analisador roda na thread do executor: a trava precisa ser atômica.
    val found = remember { AtomicBoolean(false) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            barcodeScanner.close()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    if (found.get()) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    processImageProxy(barcodeScanner, proxy) { raw ->
                        val link = PairLink.parse(raw) ?: return@processImageProxy
                        if (found.compareAndSet(false, true)) {
                            mainExecutor.execute { currentOnFound(link) }
                        }
                    }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    Log.e("QrScanner", "Falha ao vincular a câmera: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(scanner: BarcodeScanner, proxy: ImageProxy, onValue: (String) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { codes ->
            codes.firstNotNullOfOrNull { c -> c.rawValue?.takeIf { it.contains("aistack://pair") || it.contains("relay=") } }
                ?.let(onValue)
        }
        .addOnCompleteListener { proxy.close() }
}
