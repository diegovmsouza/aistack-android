package br.com.amberwrite.aistack.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Lê a resposta do RPC `listAccounts` (ver `AccountStatus` em `src-tauri/src/runtime.rs`). */
object AccountParser {
    fun parse(array: JsonArray?): List<AccountStatus> =
        array?.mapNotNull { runCatching { one(it.asJsonObject) }.getOrNull() } ?: emptyList()

    private fun one(o: JsonObject): AccountStatus? {
        val provider = o.str("provider") ?: return null
        val slot = o.str("slot") ?: return null
        val usage = o.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
        val windows = usage?.get("windows")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { w ->
            val wo = w.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            // Sem percentual no JSON não há dado: a janela não entra (a gaveta mostra "—", não 0%).
            val pct = wo.get("usedPct")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@mapNotNull null
            UsageWindow(
                kind = wo.str("kind") ?: "other",
                label = wo.str("label") ?: "Janela",
                usedPct = pct,
                resetsAt = wo.get("resetsAt")?.takeIf { it.isJsonPrimitive }?.asLong
            )
        } ?: emptyList()
        return AccountStatus(
            provider = Provider.fromId(provider),
            slot = slot,
            email = o.str("email"),
            label = o.str("label"),
            plan = o.str("plan") ?: usage?.str("plan"),
            windows = windows
        )
    }

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonElement)?.takeIf { it.isJsonPrimitive }?.asString
}
