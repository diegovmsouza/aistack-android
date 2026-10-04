package br.com.amberwrite.aistack.feature.sessions

import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.ConversationOrigin
import br.com.amberwrite.aistack.data.model.PendingConversation
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Lógica pura da lista de conversas (agrupamento por data, busca, filtro e status).
 * Sem Android: testada na JVM em SessionsLogicTest.
 */

/** Grupos de data da lista, na ordem em que aparecem. */
enum class DateGroup { Today, Yesterday, Last7Days, Older }

/** Status ao vivo de uma conversa, derivado das pendências do host. */
enum class SessionStatus { Idle, Busy, Pending }

/** Uma linha da lista: a conversa e o que a tela precisa já calculado. */
data class SessionItem(
    val conversation: Conversation,
    val status: SessionStatus,
    val pendingCount: Int,
    val preview: String?,
    val projectName: String,
    val fromMobile: Boolean
) {
    val id: String get() = conversation.id
}

data class SessionSection(val group: DateGroup, val items: List<SessionItem>)

/** Nome curto do projeto (última pasta do caminho). */
fun projectNameOf(path: String): String {
    val trimmed = path.trimEnd('/', '\\')
    return trimmed.substringAfterLast('/').substringAfterLast('\\').ifBlank { trimmed.ifBlank { path } }
}

/** Grupo de data de [epochMs] em relação a [now], no fuso [zone]. Datas futuras contam como hoje. */
fun dateGroupOf(epochMs: Long, now: Long, zone: ZoneId): DateGroup {
    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val diff = ChronoUnit.DAYS.between(day, today)
    return when {
        diff <= 0L -> DateGroup.Today
        diff == 1L -> DateGroup.Yesterday
        diff < 7L -> DateGroup.Last7Days
        else -> DateGroup.Older
    }
}

/** Remove acentos e caixa para a busca ("ação" casa com "acao"). */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()
        .trim()

private val DIACRITICS = Regex("\\p{Mn}+")

/** A conversa casa com a busca se todas as palavras aparecem no título, projeto, modelo ou prévia. */
fun matchesQuery(c: Conversation, preview: String?, query: String): Boolean {
    val terms = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return true
    val haystack = normalizeForSearch(
        listOfNotNull(c.displayTitle, c.projectPath, c.model, c.provider.displayName, preview).joinToString(" ")
    )
    return terms.all { haystack.contains(it) }
}

/** Status a partir das pendências: permissão aberta tem prioridade sobre "trabalhando". */
fun statusOf(pending: PendingConversation?): SessionStatus = when {
    pending == null -> SessionStatus.Idle
    pending.permissions.isNotEmpty() -> SessionStatus.Pending
    pending.busy -> SessionStatus.Busy
    else -> SessionStatus.Idle
}

/** Projetos distintos (caminho completo), do mais recente ao mais antigo. */
fun projectsOf(conversations: List<Conversation>): List<String> =
    conversations.sortedByDescending { it.updatedAt }
        .map { it.projectPath }
        .filter { it.isNotBlank() }
        .distinct()

/**
 * Monta as seções da lista: filtra por [project] (caminho exato) e por [query], ordena da mais
 * recente para a mais antiga e agrupa por data. Seções vazias não aparecem.
 */
fun buildSessionSections(
    conversations: List<Conversation>,
    pending: List<PendingConversation>,
    previews: Map<String, String>,
    query: String,
    project: String?,
    now: Long,
    zone: ZoneId
): List<SessionSection> {
    val pendingById = pending.associateBy { it.conversationId }
    val items = conversations
        .asSequence()
        .filter { project == null || it.projectPath == project }
        .filter { matchesQuery(it, previews[it.id], query) }
        .sortedByDescending { it.updatedAt }
        .map { c ->
            val p = pendingById[c.id]
            SessionItem(
                conversation = c,
                status = statusOf(p),
                pendingCount = p?.permissions?.size ?: 0,
                preview = previews[c.id]?.let(::cleanPreview)?.takeIf { it.isNotBlank() },
                projectName = projectNameOf(c.projectPath),
                fromMobile = c.origin == ConversationOrigin.MOBILE
            )
        }
        .toList()
    return items.groupBy { dateGroupOf(it.conversation.updatedAt, now, zone) }
        .toSortedMap()
        .map { (group, list) -> SessionSection(group, list) }
}

/** Prévia em uma linha: sem quebras, markdown leve e espaços repetidos. */
fun cleanPreview(text: String): String =
    text.replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("[`*_#>]+"), "")
        .replace(Regex(" {2,}"), " ")
        .trim()
        .let { if (it.length > PREVIEW_MAX) it.take(PREVIEW_MAX).trimEnd() + "…" else it }

const val PREVIEW_MAX = 160

/** O que a área da lista mostra. */
enum class ListContent { Loading, Offline, Error, Empty, NoResults, List }

/**
 * Decide entre esqueleto, estados vazios e a lista. A lista (mesmo velha) sempre vence:
 * erros e quedas de conexão aparecem no banner, sem esconder o que já foi carregado.
 */
fun listContentOf(
    totalCount: Int,
    visibleCount: Int,
    loaded: Boolean,
    error: String?,
    connection: ConnectionState
): ListContent {
    val down = when (connection) {
        is ConnectionState.HostOffline,
        is ConnectionState.AuthRejected,
        is ConnectionState.Error,
        ConnectionState.Revoked -> true
        else -> false
    }
    return when {
        totalCount > 0 && visibleCount > 0 -> ListContent.List
        totalCount > 0 -> ListContent.NoResults
        error != null -> ListContent.Error
        !loaded && down -> ListContent.Offline
        !loaded -> ListContent.Loading
        else -> ListContent.Empty
    }
}
