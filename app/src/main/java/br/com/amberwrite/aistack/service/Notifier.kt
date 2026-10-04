package br.com.amberwrite.aistack.service

import android.app.Notification
import br.com.amberwrite.aistack.core.relay.ConnectionState
import br.com.amberwrite.aistack.data.model.PendingConversation
import br.com.amberwrite.aistack.data.model.PermissionRequest

/**
 * Notificações do app. Interface para que a lógica de "quando notificar"
 * ([NotificationCoordinator]) possa ser testada sem o Android.
 */
interface Notifier {
    /** Pedido de permissão ou pergunta. [error] repete o aviso quando a resposta falhou. */
    fun showPermission(request: PermissionRequest, conversationTitle: String, error: String? = null)

    fun cancelPermission(conversationId: String, requestId: String)

    /** Conversas ocupadas (canal de progresso). Lista vazia remove a notificação. */
    fun showProgress(busy: List<PendingConversation>)

    /** Turno terminou com o app em segundo plano. */
    fun showDone(conversationId: String, title: String, text: String, isError: Boolean)

    fun cancelDone(conversationId: String)

    /** Notificação discreta do serviço de conexão. */
    fun connectionNotification(state: ConnectionState): Notification
}
