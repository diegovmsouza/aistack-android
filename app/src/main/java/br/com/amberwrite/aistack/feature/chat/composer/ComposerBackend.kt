package br.com.amberwrite.aistack.feature.chat.composer

import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.data.model.Attachment
import br.com.amberwrite.aistack.data.model.Conversation
import br.com.amberwrite.aistack.data.model.DirListing
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.data.model.SlashCommand
import br.com.amberwrite.aistack.data.model.UploadedFile
import br.com.amberwrite.aistack.data.repo.FilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Tudo o que o composer usa dos repositórios, atrás de uma interface para que o ViewModel seja
 * testável na JVM com um backend falso.
 */
interface ComposerBackend {
    /** Conversa atual (modelo, esforço, provedor e pasta do projeto), atualizada ao vivo. */
    fun conversation(id: String): Flow<Conversation?>

    suspend fun send(id: String, text: String, attachments: List<Attachment>)
    suspend fun queue(id: String, text: String, attachments: List<Attachment>)
    suspend fun sendNow(id: String, text: String, attachments: List<Attachment>)

    suspend fun listSlashCommands(provider: Provider, projectPath: String?): List<SlashCommand>
    suspend fun listDir(path: String?): DirListing
    suspend fun getCatalog(provider: Provider, refresh: Boolean): List<ModelInfo>
    suspend fun setOptions(id: String, model: String?, effort: String?): Conversation?
    suspend fun saveUpload(bytes: ByteArray, name: String, mime: String): UploadedFile

    /** Teto de upload do host (bytes). */
    val uploadMaxBytes: Int get() = FilesRepo.UPLOAD_MAX_BYTES
}

/** Implementação real sobre o [AppContainer]. */
class ContainerComposerBackend(private val container: AppContainer) : ComposerBackend {

    override fun conversation(id: String): Flow<Conversation?> {
        val fromChat: Flow<Conversation?> = container.chatRepo.state(id)?.map { it.conversation } ?: flowOf(null)
        return combine(container.sessionsRepo.state, fromChat) { s, chat ->
            s.conversations.firstOrNull { it.id == id } ?: chat
        }.distinctUntilChanged()
    }

    override suspend fun send(id: String, text: String, attachments: List<Attachment>) =
        container.chatRepo.send(id, text, attachments)

    override suspend fun queue(id: String, text: String, attachments: List<Attachment>) =
        container.chatRepo.queue(id, text, attachments)

    override suspend fun sendNow(id: String, text: String, attachments: List<Attachment>) =
        container.chatRepo.sendNow(id, text, attachments)

    override suspend fun listSlashCommands(provider: Provider, projectPath: String?): List<SlashCommand> =
        container.sessionsRepo.listSlashCommands(provider, projectPath)

    override suspend fun listDir(path: String?): DirListing = container.filesRepo.listDir(path)

    override suspend fun getCatalog(provider: Provider, refresh: Boolean): List<ModelInfo> =
        container.sessionsRepo.getCatalog(provider, refresh)

    override suspend fun setOptions(id: String, model: String?, effort: String?): Conversation? =
        container.sessionsRepo.setConversationOptions(id, model = model, effort = effort)

    override suspend fun saveUpload(bytes: ByteArray, name: String, mime: String): UploadedFile =
        container.filesRepo.saveUpload(bytes, name, mime)
}
