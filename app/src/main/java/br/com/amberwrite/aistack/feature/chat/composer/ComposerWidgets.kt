package br.com.amberwrite.aistack.feature.chat.composer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.feature.common.ConfirmDialog
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.tokens.Durations
import br.com.amberwrite.aistack.ui.icons.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* Componentes genéricos que faltavam no design system, mantidos dentro do composer. */

/** Bloco de esqueleto com brilho deslizante (estático no modo de movimento reduzido). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = AiTheme.shapes.sm) {
    val c = AiTheme.colors
    val base = c.surface3
    val shine = c.fg.copy(alpha = if (c.isDark) 0.08f else 0.06f)
    if (AiTheme.reducedMotion) {
        Box(modifier.clip(shape).background(base))
        return
    }
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(Durations.Shimmer, easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton-x",
    )
    Box(
        modifier
            .clip(shape)
            .background(base)
            .background(
                Brush.linearGradient(
                    colors = listOf(base.copy(alpha = 0f), shine, base.copy(alpha = 0f)),
                    start = Offset(x * 600f - 300f, 0f),
                    end = Offset(x * 600f + 300f, 0f),
                )
            )
    )
}

/** Linha de lista em carregamento: ícone + título + subtítulo. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, titleFraction: Float = 0.55f) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(Modifier.size(16.dp), AiTheme.shapes.xs)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(titleFraction).height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(titleFraction * 1.3f).height(9.dp))
        }
    }
}

/** Mensagem de erro com botão de tentar de novo. */
@Composable
fun RetryPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = AiTheme.colors
    Column(
        modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Lucide.TriangleAlert, null, Modifier.size(16.dp), tint = c.danger)
            Text(message, style = AiTheme.typography.bodySmall, color = c.fg2)
        }
        AiButton(
            text = stringResource(R.string.composer_retry),
            onClick = onRetry,
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Medium,
            leadingIcon = Lucide.RefreshCw,
        )
    }
}

// ------------------------------------------------------------------ permissões

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Pede uma permissão no momento do uso: explica antes, chama o sistema e, se a negativa for
 * permanente, oferece abrir os ajustes do app.
 */
@Stable
class PermissionGate internal constructor(
    private val context: Context,
    private val permission: String,
) {
    internal var pending: (() -> Unit)? = null
    internal var showRationale by mutableStateOf(false)
    internal var showSettings by mutableStateOf(false)
    internal var launch: (() -> Unit)? = null

    fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Executa [action] já com a permissão concedida (pedindo antes, se for preciso). */
    fun run(action: () -> Unit) {
        if (isGranted()) {
            action()
            return
        }
        pending = action
        showRationale = true
    }

    internal fun permanentlyDenied(): Boolean {
        val activity = context.findActivity() ?: return false
        return !activity.shouldShowRequestPermissionRationale(permission)
    }

    internal fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

@Composable
fun rememberPermissionGate(
    permission: String,
    rationaleTitle: String,
    rationaleText: String,
    settingsText: String,
    onDenied: () -> Unit,
): PermissionGate {
    val context = LocalContext.current
    val gate = remember(permission) { PermissionGate(context, permission) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = gate.pending
        gate.pending = null
        when {
            granted -> action?.invoke()
            gate.permanentlyDenied() -> gate.showSettings = true
            else -> onDenied()
        }
    }
    gate.launch = { launcher.launch(permission) }

    if (gate.showRationale) {
        ConfirmDialog(
            title = rationaleTitle,
            text = rationaleText,
            confirmLabel = stringResource(R.string.composer_permission_continue),
            dismissLabel = stringResource(R.string.composer_permission_not_now),
            onConfirm = {
                gate.showRationale = false
                gate.launch?.invoke()
            },
            onDismiss = {
                gate.showRationale = false
                gate.pending = null
                onDenied()
            },
        )
    }
    if (gate.showSettings) {
        ConfirmDialog(
            title = rationaleTitle,
            text = settingsText,
            confirmLabel = stringResource(R.string.composer_permission_open_settings),
            dismissLabel = stringResource(R.string.composer_permission_not_now),
            onConfirm = {
                gate.showSettings = false
                gate.openSettings()
            },
            onDismiss = {
                gate.showSettings = false
                onDenied()
            },
        )
    }
    return gate
}

// ------------------------------------------------------------------ miniaturas

/** Carrega uma miniatura (girada pelo EXIF) fora da main thread; `null` enquanto carrega ou se falhar. */
@Composable
fun rememberThumbnail(uri: String?, maxSide: Dp = 72.dp): Painter? {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { maxSide.roundToPx() }.coerceAtLeast(32)
    val state = produceState<Painter?>(initialValue = null, uri, px) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { decodeThumbnail(context, Uri.parse(uri), px) }.getOrNull()?.let { BitmapPainter(it.asImageBitmap()) }
        }
    }
    return state.value
}

internal fun decodeThumbnail(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide) }
    val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val rotation = runCatching {
        resolver.openInputStream(uri)?.use {
            exifRotation(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        }
    }.getOrNull() ?: 0
    if (rotation == 0) return bmp
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    if (rotated !== bmp) bmp.recycle()
    return rotated
}
