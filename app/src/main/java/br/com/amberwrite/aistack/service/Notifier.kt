package br.com.amberwrite.aistack.service

import android.app.Notification
import br.com.amberwrite.aistack.core.relay.ConnectionState

/**
 * Notificações do app. Interface para que a lógica de "quando notificar"
 * ([NotificationCoordinator]) possa ser testada sem o Android: tudo entra como modelo
 * semântico ([NotificationModels.kt]) e só a implementação real formata texto.
 */
interface Notifier {
    /** Pedido de permissão ou pergunta (`AskUserQuestion`), canal de pendências. */
    fun showPermission(notice: PermissionNotice)

    fun cancelPermission(conversationId: String, requestId: String)

    /** Pergunta de ferramenta aberta no fim do turno (a resposta vira mensagem). */
    fun showToolQuestion(notice: ToolQuestionNotice)

    fun cancelToolQuestion(conversationId: String)

    /** Resumo do grupo de pendências; `null` remove. */
    fun showPendingSummary(summary: PendingSummary?)

    /** Live Update de um turno em andamento (canal de progresso). */
    fun showLive(notice: LiveNotice)

    fun cancelLive(conversationId: String)

    /** Turno concluído ou com erro (canal de concluídos). */
    fun showDone(notice: DoneNotice)

    fun cancelDone(conversationId: String)

    /** Notificação discreta do serviço de conexão. */
    fun connectionNotification(state: ConnectionState): Notification
}
