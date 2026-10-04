package br.com.amberwrite.aistack.feature.chat.composer

import android.net.Uri
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.Spinner
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CameraPhase { Starting, Ready, Failed }
private enum class FlashMode(val mode: Int) { Off(ImageCapture.FLASH_MODE_OFF), Auto(ImageCapture.FLASH_MODE_AUTO), On(ImageCapture.FLASH_MODE_ON) }

/**
 * Câmera em tela cheia (CameraX): pré-visualização, troca de lente, flash desligado/automático/ligado
 * e revisão da foto antes de anexar. A foto vai para `cacheDir/camera`; o composer a reduz e envia.
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
    haptics: AiHaptics = AiHaptics.None,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var photo by remember { mutableStateOf<File?>(null) }
        val motion = AiTheme.motion
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AnimatedContent(
                targetState = photo,
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
                label = "camera-step",
            ) { file ->
                if (file == null) {
                    CameraLive(onPhoto = { photo = it }, onDismiss = onDismiss, haptics = haptics)
                } else {
                    PhotoReview(
                        file = file,
                        onRetake = {
                            file.delete()
                            photo = null
                        },
                        onUse = { onCaptured(Uri.fromFile(file)) },
                        onDismiss = {
                            file.delete()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraLive(onPhoto: (File) -> Unit, onDismiss: () -> Unit, haptics: AiHaptics) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(CameraPhase.Starting) }
    var hasFront by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(FlashMode.Auto) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val shutter = remember { Animatable(0f) }
    val reduced = AiTheme.reducedMotion
    val captureFailedText = stringResource(R.string.composer_camera_capture_failed)

    val controller = remember(attempt) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureFlashMode = flash.mode
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        phase = CameraPhase.Starting
        runCatching { controller.bindToLifecycle(lifecycleOwner) }.onFailure { phase = CameraPhase.Failed }
        val future = controller.initializationFuture
        future.addListener({
            val ok = runCatching { future.get() }.isSuccess
            if (!ok) {
                phase = CameraPhase.Failed
            } else {
                val back = runCatching { controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
                hasFront = runCatching { controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
                if (!back && hasFront) {
                    front = true
                    controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                }
                phase = if (back || hasFront) CameraPhase.Ready else CameraPhase.Failed
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { controller.unbind() } }
    }

    fun capture() {
        if (capturing || phase != CameraPhase.Ready) return
        capturing = true
        captureError = null
        haptics.perform(HapticKind.Confirm)
        if (!reduced) scope.launch {
            shutter.snapTo(0.85f)
            shutter.animateTo(0f, tween(260))
        }
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "IMG_$stamp.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                capturing = false
                onPhoto(file)
            }

            override fun onError(exception: ImageCaptureException) {
                capturing = false
                file.delete()
                captureError = captureFailedText
                haptics.perform(HapticKind.Reject)
            }
        })
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            update = { it.controller = controller },
            modifier = Modifier.fillMaxSize(),
        )
        // Clarão do obturador.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutter.value }.background(Color.White))

        when (phase) {
            CameraPhase.Starting -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spinner(size = 28.dp, color = Color.White)
                Text(stringResource(R.string.composer_camera_starting), color = Color.White.copy(alpha = 0.8f), style = AiTheme.typography.bodySmall)
            }
            CameraPhase.Failed -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Lucide.Camera, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.7f))
                Text(
                    stringResource(R.string.composer_camera_failed),
                    color = Color.White,
                    style = AiTheme.typography.body,
                    textAlign = TextAlign.Center,
                )
                AiButton(
                    text = stringResource(R.string.composer_retry),
                    onClick = { attempt++ },
                    variant = ButtonVariant.Secondary,
                    leadingIcon = Lucide.RefreshCw,
                )
            }
            CameraPhase.Ready -> Unit
        }

        // Barra superior: fechar e flash.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiIconButton(
                icon = Lucide.X,
                contentDescription = stringResource(R.string.composer_camera_close),
                onClick = onDismiss,
                size = 48.dp,
                tint = Color.White,
            )
            Spacer(Modifier.weight(1f))
            if (phase == CameraPhase.Ready) {
                val flashLabel = when (flash) {
                    FlashMode.Off -> stringResource(R.string.composer_camera_flash_off)
                    FlashMode.Auto -> stringResource(R.string.composer_camera_flash_auto)
                    FlashMode.On -> stringResource(R.string.composer_camera_flash_on)
                }
                val flashDesc = stringResource(R.string.composer_camera_flash_desc, flashLabel)
                Row(
                    Modifier
                        .clip(AiTheme.shapes.pill)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .semantics { contentDescription = flashDesc }
                        .clickable(role = Role.Button) {
                            haptics.perform(HapticKind.Tick)
                            flash = FlashMode.entries[(flash.ordinal + 1) % FlashMode.entries.size]
                            controller.imageCaptureFlashMode = flash.mode
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Lucide.Zap, null, Modifier.size(18.dp),
                        tint = if (flash == FlashMode.Off) Color.White.copy(alpha = 0.5f) else Color(0xFFFFD54F),
                    )
                    Text(flashLabel, color = Color.White, style = AiTheme.typography.label)
                }
            }
        }

        // Barra inferior: erro, obturador e troca de lente.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            captureError?.let {
                Text(
                    it,
                    color = Color.White,
                    style = AiTheme.typography.bodySmall,
                    modifier = Modifier.clip(AiTheme.shapes.sm).background(AiTheme.colors.danger.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(56.dp))
                Spacer(Modifier.weight(1f))
                val shutterDesc = stringResource(R.string.composer_camera_shutter)
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .semantics { contentDescription = shutterDesc }
                        .clickable(enabled = phase == CameraPhase.Ready && !capturing, role = Role.Button) { capture() }
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(if (phase == CameraPhase.Ready) Color.White else Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (capturing) Spinner(size = 24.dp, color = Color.Black)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                    if (phase == CameraPhase.Ready && hasFront) {
                        AiIconButton(
                            icon = Lucide.RefreshCw,
                            contentDescription = stringResource(
                                if (front) R.string.composer_camera_switch_back else R.string.composer_camera_switch_front
                            ),
                            onClick = {
                                haptics.perform(HapticKind.Tick)
                                front = !front
                                controller.cameraSelector =
                                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            },
                            size = 52.dp,
                            iconSize = 22.dp,
                            tint = Color.White,
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhotoReview(file: File, onRetake: () -> Unit, onUse: () -> Unit, onDismiss: () -> Unit) {
    val painter = rememberThumbnail(Uri.fromFile(file).toString(), maxSide = 720.dp)
    Box(Modifier.fillMaxSize()) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = stringResource(R.string.composer_camera_review_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Spinner(Modifier.align(Alignment.Center), size = 28.dp, color = Color.White)
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            AiIconButton(
                icon = Lucide.X,
                contentDescription = stringResource(R.string.composer_camera_close),
                onClick = onDismiss,
                size = 48.dp,
                tint = Color.White,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            AiButton(
                text = stringResource(R.string.composer_camera_retake),
                onClick = onRetake,
                variant = ButtonVariant.Secondary,
                leadingIcon = Lucide.RefreshCw,
                modifier = Modifier.weight(1f),
            )
            AiButton(
                text = stringResource(R.string.composer_camera_use),
                onClick = onUse,
                variant = ButtonVariant.Primary,
                leadingIcon = Lucide.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
