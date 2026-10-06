package br.com.amberwrite.aistack.data.repo

import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.RpcException
import br.com.amberwrite.aistack.core.rpc.asObj
import br.com.amberwrite.aistack.core.rpc.bool
import br.com.amberwrite.aistack.core.rpc.str
import br.com.amberwrite.aistack.data.model.DirListing
import br.com.amberwrite.aistack.data.model.FileContent
import br.com.amberwrite.aistack.data.model.UploadedFile
import java.util.Base64

/** Navegação de pastas, leitura de arquivos e envio de anexos para o desktop. */
class FilesRepo(private val rpc: RpcCaller) {

    /** Lista uma pasta. `path` nulo devolve as raízes permitidas (projetos e `extraDirs`). */
    /** Acesso do aparelho à pasta pessoal do PC; `null` em desktop antigo (sem o recurso). */
    suspend fun homeAccess(): HomeAccess? = try {
        HomeAccess.parse(rpc.call("getHomeAccess").asObj() ?: throw invalid())
    } catch (e: RpcException) {
        if (e.kind == RpcException.Kind.UNKNOWN_METHOD) null else throw e
    }

    suspend fun setHomeAccess(enabled: Boolean): HomeAccess =
        HomeAccess.parse(rpc.call("setHomeAccess", params("enabled" to enabled)).asObj() ?: throw invalid())

    /** Cria uma pasta nova no PC (dentro da pasta pessoal) e devolve o caminho dela. */
    suspend fun createDirectory(path: String): String =
        rpc.call("createDirectory", params("path" to path)).asObj()?.str("path") ?: throw invalid()

    suspend fun listDir(path: String?): DirListing {
        val res = rpc.call("listDir", params("path" to path))
        return DirListing.parse(res.asObj() ?: throw invalid())
    }

    /**
     * Lê um arquivo. Começa com 1 MiB (com `frag`) ou 32 KiB (sem) e cai pela metade se a
     * resposta não couber no túnel.
     */
    suspend fun readFile(path: String, maxBytes: Int? = null): FileContent {
        var limit = maxBytes ?: if (rpc.hostFrag) READ_MAX_FRAG else READ_MAX_NO_FRAG
        while (true) {
            try {
                val res = rpc.call("readFile", params("path" to path, "maxBytes" to limit))
                return FileContent.parse(res.asObj() ?: throw invalid(), path)
            } catch (e: RpcException) {
                if (e.kind == RpcException.Kind.TOO_LARGE && limit > READ_MIN) {
                    limit /= 2
                    continue
                }
                throw e
            }
        }
    }

    /**
     * Envia um anexo (base64 padrão). Recusa localmente acima de [UPLOAD_MAX_BYTES], o teto do
     * host, para não gastar dados à toa.
     */
    suspend fun saveUpload(bytes: ByteArray, name: String, mime: String): UploadedFile {
        if (bytes.size > UPLOAD_MAX_BYTES) {
            throw RpcException(
                RpcException.Kind.TOO_LARGE,
                "Arquivo grande demais (máximo de ${UPLOAD_MAX_BYTES / 1_000_000} MB).",
                "saveUpload"
            )
        }
        val data = Base64.getEncoder().encodeToString(bytes)
        val res = rpc.call("saveUpload", params("name" to name, "mime" to mime, "data" to data)).asObj()
            ?: throw invalid()
        val path = res.str("path") ?: throw invalid()
        return UploadedFile(path, res.str("mime") ?: mime)
    }

    private fun invalid() = RpcException(RpcException.Kind.REMOTE, "Resposta inválida do desktop.")

    companion object {
        const val READ_MAX_FRAG = 1 shl 20
        const val READ_MAX_NO_FRAG = 32_768
        const val READ_MIN = 1024
        const val UPLOAD_MAX_BYTES = 6_000_000
    }
}

/** Pasta pessoal do PC liberada (ou não) para este aparelho. */
data class HomeAccess(val granted: Boolean, val home: String) {
    companion object {
        fun parse(o: com.google.gson.JsonObject) = HomeAccess(o.bool("granted") == true, o.str("home").orEmpty())
    }
}
