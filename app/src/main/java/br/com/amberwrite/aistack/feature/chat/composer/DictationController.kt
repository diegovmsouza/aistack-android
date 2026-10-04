package br.com.amberwrite.aistack.feature.chat.composer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Ditado com o [SpeechRecognizer] do sistema em pt-BR, com resultados parciais. Os callbacks
 * chegam na main thread (exigência da API) e só repassam dados ao [ComposerViewModel].
 */
class DictationController(context: Context, private val vm: ComposerViewModel) {
    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(app)

    fun start() {
        if (!available) {
            vm.showNotice(ComposerNotice.NoRecognizer)
            return
        }
        cancelInternal()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(app)
        } catch (_: Throwable) {
            vm.showNotice(ComposerNotice.NoRecognizer)
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        vm.onDictationStarting()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        }
        try {
            r.startListening(intent)
        } catch (_: Throwable) {
            cancelInternal()
            vm.onDictationError(ComposerNotice.DictationFailed)
        }
    }

    /** Para de ouvir e deixa o reconhecedor entregar o resultado final. */
    fun stop() {
        val r = recognizer ?: return vm.onDictationStopped()
        runCatching { r.stopListening() }
    }

    /** Descarta o ditado em curso (o texto parcial já inserido permanece). */
    fun cancel() {
        cancelInternal()
        vm.onDictationStopped()
    }

    fun release() {
        cancelInternal()
    }

    private fun cancelInternal() {
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = vm.onDictationReady()
        override fun onBeginningOfSpeech() = vm.onDictationReady()
        override fun onRmsChanged(rmsdB: Float) = vm.onDictationLevel(rmsdB)
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = vm.onDictationEndOfSpeech()
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(vm::onDictationPartial)
        }

        override fun onResults(results: Bundle?) {
            vm.onDictationResult(firstResult(results).orEmpty())
            cancelInternal()
        }

        override fun onError(error: Int) {
            cancelInternal()
            vm.onDictationError(noticeFor(error))
        }
    }

    private fun firstResult(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        const val LANGUAGE = "pt-BR"

        /** Erros que merecem aviso; “nada ouvido” encerra em silêncio. */
        fun noticeFor(error: Int): ComposerNotice? = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> null
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ComposerNotice.MicDenied
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> ComposerNotice.DictationNetwork
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ComposerNotice.DictationBusy
            12, 13 -> ComposerNotice.NoRecognizer // idioma não suportado / indisponível
            else -> ComposerNotice.DictationFailed
        }
    }
}

/** Controlador ligado ao ciclo de vida do composable (libera o reconhecedor ao sair). */
@Composable
fun rememberDictationController(vm: ComposerViewModel): DictationController {
    val context = LocalContext.current
    val controller = remember(vm) { DictationController(context, vm) }
    DisposableEffect(controller) {
        onDispose {
            controller.release()
            vm.onDictationStopped()
        }
    }
    return controller
}
