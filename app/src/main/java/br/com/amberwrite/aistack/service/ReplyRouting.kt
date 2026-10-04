package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.data.model.PermissionDecision
import br.com.amberwrite.aistack.data.model.PermissionRequest
import br.com.amberwrite.aistack.data.model.Question
import br.com.amberwrite.aistack.data.model.ToolQuestion
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Para onde vai uma resposta em texto livre (RemoteInput ou o campo "Responder" da caixa). */
sealed interface ReplyRoute {
    /** `answerPermission` com esta decisão. */
    data class Answer(val decision: PermissionDecision) : ReplyRoute

    /** `sendMessage` com este texto (pergunta de ferramenta, contrato 05 §14.2 (b)). */
    data class Message(val text: String) : ReplyRoute

    /** Não dá para responder agora. */
    data class Rejected(val reason: Reason) : ReplyRoute

    enum class Reason {
        /** Texto vazio. */
        Empty,

        /** Conversa ocupada: a pergunta de ferramenta não está mais aberta. */
        Busy,
    }
}

/**
 * Regras de roteamento das respostas (contrato 05 §14):
 *
 * - `AskUserQuestion` (via `permissionRequest`): `allow` com `answers {pergunta: rótulo}`.
 *   Um número ("2") ou o rótulo exato vira o rótulo da opção; outro texto vai como resposta
 *   livre ("Outro"). Em múltipla escolha, itens separados por vírgula/ponto e vírgula. Com
 *   várias perguntas, uma resposta por linha (ou separadas por ";"); se a quantidade não
 *   bater, o fallback do contrato é `deny` com a resposta como mensagem.
 * - Permissão comum: `deny` com a mensagem ("nega e explica" — o modelo lê o texto).
 * - Pergunta de ferramenta (`ask_question`/`AskFollowupQuestion`): `sendMessage` com o texto
 *   no formato do desktop (`"{n}. {rótulo}{: detalhe}"`), só com a conversa parada.
 */
object ReplyRouting {

    fun forPermission(request: PermissionRequest, text: String): ReplyRoute {
        val reply = text.trim()
        if (reply.isEmpty()) return ReplyRoute.Rejected(ReplyRoute.Reason.Empty)
        if (!request.isAskUserQuestion) return ReplyRoute.Answer(PermissionDecision.Deny(reply))
        val questions = request.questions
        if (questions.isEmpty()) return ReplyRoute.Answer(PermissionDecision.Deny(reply))
        val parts = split(reply, questions.size) ?: return ReplyRoute.Answer(PermissionDecision.Deny(reply))
        val answers = LinkedHashMap<String, String>()
        questions.forEachIndexed { i, q -> answers[q.question] = resolveLabels(q, parts[i]) }
        return ReplyRoute.Answer(PermissionDecision.Allow(answers = answers))
    }

    fun forToolQuestion(question: ToolQuestion, text: String, busy: Boolean): ReplyRoute {
        val reply = text.trim()
        if (reply.isEmpty()) return ReplyRoute.Rejected(ReplyRoute.Reason.Empty)
        if (busy) return ReplyRoute.Rejected(ReplyRoute.Reason.Busy)
        val qs = question.questions
        val parts = split(reply, qs.size) ?: return ReplyRoute.Message(reply)
        val lines = qs.mapIndexed { i, q ->
            val idx = optionIndex(q, parts[i])
            if (idx != null) ToolQuestion.answerText(q, idx) else parts[i]
        }
        return ReplyRoute.Message(lines.joinToString("\n"))
    }

    /** Escolha rápida (botão de opção) de uma pergunta de ferramenta. */
    fun toolQuestionChoice(question: Question, index: Int): ReplyRoute {
        val text = ToolQuestion.answerText(question, index)
        return if (text.isBlank()) ReplyRoute.Rejected(ReplyRoute.Reason.Empty) else ReplyRoute.Message(text)
    }

    /**
     * Índice 0-based da opção que [text] escolhe: "2", "2.", "2)", "2. Rótulo" ou o rótulo
     * exato (sem diferenciar maiúsculas). `null` = resposta livre.
     */
    fun optionIndex(question: Question, text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        NUMBER.matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            val rest = m.groupValues[2].trim()
            val idx = n - 1
            val opt = question.options.getOrNull(idx) ?: return@let
            if (rest.isEmpty() || rest.equals(opt.label, ignoreCase = true) || rest.startsWith(opt.label, ignoreCase = true)) return idx
        }
        val byLabel = question.options.indexOfFirst { it.label.trim().equals(t, ignoreCase = true) }
        return byLabel.takeIf { it >= 0 }
    }

    /** Valor de `answers` para uma pergunta: rótulo(s) da opção ou o texto livre. */
    fun resolveLabels(question: Question, text: String): String {
        val t = text.trim()
        optionIndex(question, t)?.let { return question.options[it].label }
        if (question.multiSelect) {
            val tokens = t.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.size > 1) {
                val idx = tokens.map { optionIndex(question, it) }
                if (idx.all { it != null }) return idx.distinct().joinToString(", ") { question.options[it!!].label }
            }
        }
        return t
    }

    /** Divide a resposta em [count] partes (linhas, ou ";"). `null` se não bater. */
    internal fun split(text: String, count: Int): List<String>? {
        if (count <= 1) return listOf(text)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size == count) return lines
        val semi = text.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (semi.size == count) return semi
        return null
    }

    private val NUMBER = Regex("""^(\d{1,2})\s*[.)\-:]?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
}

/**
 * Serialização de [ToolQuestion] para extras de `Intent` (a notificação precisa das opções
 * para rotear a resposta mesmo se o processo morrer). Formato `{questions:[…]}`, o mesmo que
 * [br.com.amberwrite.aistack.data.model.Question.parseAll] lê.
 */
object ToolQuestionJson {
    private const val TOOL = "ask_question"

    fun encode(question: ToolQuestion): String {
        val arr = JsonArray()
        question.questions.forEach { q ->
            arr.add(
                JsonObject().apply {
                    addProperty("question", q.question)
                    q.header?.let { addProperty("header", it) }
                    addProperty("multiSelect", q.multiSelect)
                    add("options", JsonArray().apply {
                        q.options.forEach { o ->
                            add(JsonObject().apply {
                                addProperty("label", o.label)
                                o.description?.let { addProperty("description", it) }
                            })
                        }
                    })
                }
            )
        }
        return JsonObject().apply { add("questions", arr) }.toString()
    }

    fun decode(json: String?): ToolQuestion? {
        if (json.isNullOrBlank()) return null
        val el = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        return ToolQuestion.from(TOOL, el)
    }
}
