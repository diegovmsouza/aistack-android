package br.com.amberwrite.aistack.core.rpc

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/*
 * Leitura tolerante de JSON: o host pode omitir campos, mandar `null`, acrescentar campos
 * novos ou (em eventos truncados) transformar um campo não-string em string. Nenhum destes
 * acessores lança exceção; devolvem `null` quando o valor não é utilizável.
 */

fun parseJsonOrNull(text: String): JsonElement? =
    try { JsonParser.parseString(text) } catch (e: Exception) { null }

val JsonElement?.isNullish: Boolean get() = this == null || this is JsonNull

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

fun JsonElement?.asStr(): String? {
    val p = this as? JsonPrimitive ?: return null
    return if (p.isString || p.isNumber || p.isBoolean) p.asString else null
}

fun JsonElement?.asLongOrNull(): Long? {
    val p = this as? JsonPrimitive ?: return null
    return try {
        when {
            p.isNumber -> p.asNumber.toDouble().let { if (it.isNaN()) null else it.toLong() }
            p.isString -> p.asString.trim().toDoubleOrNull()?.toLong()
            else -> null
        }
    } catch (e: Exception) { null }
}

fun JsonElement?.asDoubleOrNull(): Double? {
    val p = this as? JsonPrimitive ?: return null
    return try {
        when {
            p.isNumber -> p.asDouble
            p.isString -> p.asString.trim().toDoubleOrNull()
            else -> null
        }
    } catch (e: Exception) { null }
}

fun JsonElement?.asBoolOrNull(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    return when {
        p.isBoolean -> p.asBoolean
        p.isString -> p.asString.trim().lowercase().toBooleanStrictOrNull()
        p.isNumber -> p.asDouble != 0.0
        else -> null
    }
}

fun JsonObject.opt(key: String): JsonElement? = get(key)?.takeUnless { it is JsonNull }
fun JsonObject.str(key: String): String? = opt(key).asStr()
fun JsonObject.long(key: String): Long? = opt(key).asLongOrNull()
fun JsonObject.int(key: String): Int? = opt(key).asLongOrNull()?.toInt()
fun JsonObject.double(key: String): Double? = opt(key).asDoubleOrNull()
fun JsonObject.bool(key: String): Boolean? = opt(key).asBoolOrNull()
fun JsonObject.obj(key: String): JsonObject? = opt(key).asObj()
fun JsonObject.arr(key: String): JsonArray? = opt(key).asArr()

fun JsonObject.strList(key: String): List<String> =
    arr(key)?.mapNotNull { it.asStr() } ?: emptyList()

fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

/**
 * Converte um carimbo de tempo para epoch em ms. Aceita número em ms, número em texto e
 * texto RFC 3339 / ISO-8601 (`2026-05-01T12:00:00Z`, `…+03:00`, com ou sem fração).
 */
fun JsonElement?.asEpochMillis(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isNumber) return asLongOrNull()
    if (!p.isString) return null
    val s = p.asString.trim()
    if (s.isEmpty()) return null
    s.toDoubleOrNull()?.let { return it.toLong() }
    return try {
        OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try { Instant.parse(s).toEpochMilli() } catch (e2: Exception) { null }
    }
}

fun JsonObject.epochMillis(key: String): Long? = opt(key).asEpochMillis()

/** Texto legível para um valor qualquer (string crua ou JSON compacto). */
fun JsonElement?.displayText(): String = when {
    this == null || this is JsonNull -> ""
    this is JsonPrimitive && isString -> asString
    else -> toString()
}
