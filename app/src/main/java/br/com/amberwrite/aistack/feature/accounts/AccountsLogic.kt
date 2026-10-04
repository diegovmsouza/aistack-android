package br.com.amberwrite.aistack.feature.accounts

import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.data.model.Provider
import br.com.amberwrite.aistack.data.model.UsageWindow
import com.google.gson.JsonObject

/** Situação de login de uma conta (`authState` do desktop + CLI instalada). */
enum class AuthStatus { Authenticated, LoggedOut, Error, NotInstalled, Unknown }

/** Situação do limite de uso (`usage.status`). */
enum class LimitStatus { Ok, Warning, Rejected, Unknown }

/** Tipo de janela de cota, na ordem em que aparece no cartão. */
enum class WindowKind { FiveHour, Weekly, Monthly, Other }

/** Tempo até a renovação de uma janela, quebrado para exibição. */
data class ResetIn(val days: Long, val hours: Long, val minutes: Long) {
    val isSoon: Boolean get() = days == 0L && hours == 0L && minutes == 0L
}

data class WindowView(
    val kind: WindowKind,
    val label: String,
    /** 0..1, ou `null` quando o desktop não sabe. */
    val fraction: Float?,
    val usedPct: Int?,
    val resetsAt: Long?
)

data class AccountView(
    val key: String,
    val providerId: String,
    val slot: String,
    /** Rótulo do slot (A, B, …). */
    val slotLabel: String,
    val title: String,
    val email: String?,
    val plan: String?,
    val auth: AuthStatus,
    val limit: LimitStatus,
    val windows: List<WindowView>,
    val usageAt: Long?
)

data class ProviderGroup(
    val providerId: String,
    val name: String,
    val accounts: List<AccountView>
) {
    val signedIn: Int get() = accounts.count { it.auth == AuthStatus.Authenticated }
}

/** Regras puras da tela de contas (testáveis na JVM). */
object AccountsLogic {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun authStatus(authState: String?, installed: Boolean): AuthStatus {
        if (!installed) return AuthStatus.NotInstalled
        return when (authState?.trim()?.lowercase()) {
            "authenticated", "ok", "logged_in" -> AuthStatus.Authenticated
            "logged_out", "loggedout", "unauthenticated" -> AuthStatus.LoggedOut
            "error" -> AuthStatus.Error
            else -> AuthStatus.Unknown
        }
    }

    fun limitStatus(account: AccountStatus): LimitStatus {
        val raw = (account.usage as? JsonObject)?.get("status")
            ?.takeIf { it.isJsonPrimitive }?.asString
        return when (raw?.lowercase()) {
            "ok" -> LimitStatus.Ok
            "warning" -> LimitStatus.Warning
            "rejected" -> LimitStatus.Rejected
            else -> LimitStatus.Unknown
        }
    }

    fun windowKind(kind: String?): WindowKind = when (kind?.lowercase()) {
        "fivehour", "five_hour", "5h" -> WindowKind.FiveHour
        "weekly", "week", "7d" -> WindowKind.Weekly
        "monthly", "month" -> WindowKind.Monthly
        else -> WindowKind.Other
    }

    /** Janelas ordenadas: 5 h, semanal, mensal e as demais (estável dentro de cada tipo). */
    fun windows(list: List<UsageWindow>): List<WindowView> =
        list.map { w ->
            val pct = w.usedPct?.takeIf { !it.isNaN() }?.coerceIn(0.0, 100.0)
            WindowView(
                kind = windowKind(w.kind),
                label = w.label,
                fraction = pct?.let { (it / 100.0).toFloat() },
                usedPct = pct?.let { kotlin.math.round(it).toInt() },
                resetsAt = w.resetsAt
            )
        }.sortedBy { it.kind.ordinal }

    /** "a" → "A"; slots vazios viram "A". */
    fun slotLabel(slot: String): String = slot.trim().ifEmpty { "a" }.uppercase()

    fun toView(a: AccountStatus): AccountView {
        val title = a.label?.takeIf { it.isNotBlank() } ?: a.email?.takeIf { it.isNotBlank() } ?: a.provider.displayNameOr(a.providerId)
        return AccountView(
            key = a.providerId + ":" + a.slot,
            providerId = a.providerId,
            slot = a.slot,
            slotLabel = slotLabel(a.slot),
            title = title,
            // Sem repetir o e-mail quando ele já é o título.
            email = a.email?.takeIf { it.isNotBlank() && it != title },
            plan = a.plan?.takeIf { it.isNotBlank() },
            auth = authStatus(a.authState, a.installed),
            limit = limitStatus(a),
            windows = windows(a.usageWindows),
            usageAt = a.usageAt
        )
    }

    private fun Provider.displayNameOr(id: String): String = if (this == Provider.UNKNOWN) id else displayName

    /**
     * Agrupa por provedor, na ordem canônica dos provedores (os desconhecidos no fim, por nome),
     * e ordena os slots dentro de cada grupo (A, B, …).
     */
    fun group(accounts: List<AccountStatus>): List<ProviderGroup> {
        val order = Provider.selectable.map { it.id }
        return accounts
            .groupBy { it.providerId.lowercase() }
            .map { (id, list) ->
                val provider = Provider.fromId(id)
                ProviderGroup(
                    providerId = list.first().providerId,
                    name = if (provider == Provider.UNKNOWN) list.first().providerId else provider.displayName,
                    accounts = list.sortedBy { it.slot.lowercase() }.map(::toView)
                )
            }
            .sortedWith(compareBy<ProviderGroup>({ order.indexOf(it.providerId.lowercase()).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.name.lowercase() }))
    }

    /** Quanto falta para [resetsAt]; `null` se não há data ou ela já passou. Arredonda os minutos para cima. */
    fun resetBreakdown(resetsAt: Long?, now: Long): ResetIn? {
        if (resetsAt == null || resetsAt <= 0) return null
        val diff = resetsAt - now
        if (diff <= 0) return null
        if (diff < MINUTE) return ResetIn(0, 0, 0)
        val totalMinutes = (diff + MINUTE - 1) / MINUTE
        val days = totalMinutes / (DAY / MINUTE)
        val hours = (totalMinutes % (DAY / MINUTE)) / 60
        val minutes = totalMinutes % 60
        return ResetIn(days, hours, minutes)
    }

    /** Texto curto pt-BR: "2 d 4 h", "3 h 12 min", "8 min", "instantes". */
    fun formatReset(r: ResetIn): String = when {
        r.isSoon -> "instantes"
        r.days > 0 -> if (r.hours > 0) "${r.days} d ${r.hours} h" else "${r.days} d"
        r.hours > 0 -> if (r.minutes > 0) "${r.hours} h ${r.minutes} min" else "${r.hours} h"
        else -> "${r.minutes} min"
    }

    /** Próxima renovação entre todas as janelas (para o cabeçalho). */
    fun nextReset(groups: List<ProviderGroup>, now: Long): Long? =
        groups.asSequence()
            .flatMap { it.accounts.asSequence() }
            .flatMap { it.windows.asSequence() }
            .mapNotNull { it.resetsAt }
            .filter { it > now }
            .minOrNull()
}
