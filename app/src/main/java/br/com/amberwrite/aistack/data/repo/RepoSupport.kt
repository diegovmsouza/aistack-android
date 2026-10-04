package br.com.amberwrite.aistack.data.repo

/** Monta os `params` de uma RPC omitindo chaves nulas (o host trata ausente como "não mudar"). */
internal fun params(vararg pairs: Pair<String, Any?>): Map<String, Any> {
    val out = LinkedHashMap<String, Any>(pairs.size)
    for ((k, v) in pairs) if (v != null) out[k] = v
    return out
}

/** Resultado de uma ação feita a partir da UI ou de uma notificação. */
sealed interface ActionResult {
    data object Ok : ActionResult
    /** O pedido já tinha sido resolvido em outro lugar (desktop ou outro aparelho). */
    data object AlreadyResolved : ActionResult
    data class Failed(val message: String) : ActionResult
}
