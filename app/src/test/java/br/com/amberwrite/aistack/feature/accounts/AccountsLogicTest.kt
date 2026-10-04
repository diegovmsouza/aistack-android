package br.com.amberwrite.aistack.feature.accounts

import br.com.amberwrite.aistack.data.model.AccountStatus
import br.com.amberwrite.aistack.data.model.Provider
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsLogicTest {

    private fun account(
        provider: String,
        slot: String,
        usage: String? = null,
        authState: String? = "authenticated",
        installed: Boolean = true,
        label: String? = null,
        email: String? = null
    ) = AccountStatus(
        provider = Provider.fromId(provider),
        providerId = provider,
        slot = slot,
        label = label,
        email = email,
        plan = null,
        authState = authState,
        usage = usage?.let { JsonParser.parseString(it) },
        usageAt = null,
        installed = installed
    )

    @Test
    fun `estado de login considera a CLI instalada`() {
        assertEquals(AuthStatus.NotInstalled, AccountsLogic.authStatus("authenticated", installed = false))
        assertEquals(AuthStatus.Authenticated, AccountsLogic.authStatus("Authenticated", installed = true))
        assertEquals(AuthStatus.LoggedOut, AccountsLogic.authStatus("logged_out", installed = true))
        assertEquals(AuthStatus.Error, AccountsLogic.authStatus("error", installed = true))
        assertEquals(AuthStatus.Unknown, AccountsLogic.authStatus(null, installed = true))
    }

    @Test
    fun `janelas ordenadas 5 h antes da semana e porcentagem limitada`() {
        val a = account(
            "claude", "a",
            """{"status":"warning","windows":[
               {"kind":"weekly","label":"Semana","usedPct":130.0},
               {"kind":"fiveHour","label":"5 h","usedPct":42.4}]}"""
        )
        val v = AccountsLogic.toView(a)
        assertEquals(LimitStatus.Warning, v.limit)
        assertEquals(listOf(WindowKind.FiveHour, WindowKind.Weekly), v.windows.map { it.kind })
        assertEquals(42, v.windows[0].usedPct)
        assertEquals(100, v.windows[1].usedPct)
        assertEquals(1f, v.windows[1].fraction!!, 0.0001f)
    }

    @Test
    fun `agrupa por provedor na ordem canônica e ordena os slots`() {
        val groups = AccountsLogic.group(
            listOf(
                account("codex", "b"),
                account("zeta", "a"),
                account("claude", "b", authState = "logged_out"),
                account("codex", "a"),
                account("claude", "a")
            )
        )
        assertEquals(listOf("claude", "codex", "zeta"), groups.map { it.providerId })
        assertEquals(listOf("A", "B"), groups[0].accounts.map { it.slotLabel })
        assertEquals(1, groups[0].signedIn)
        assertEquals("zeta", groups[2].name)
    }

    @Test
    fun `título usa rótulo, depois e-mail, depois o provedor`() {
        assertEquals("Trabalho", AccountsLogic.toView(account("claude", "a", label = "Trabalho", email = "x@y")).title)
        assertEquals("x@y", AccountsLogic.toView(account("claude", "a", email = "x@y")).title)
        assertNull(AccountsLogic.toView(account("claude", "a", email = "x@y")).email)
        assertEquals("Claude", AccountsLogic.toView(account("claude", "")).title)
        assertEquals("A", AccountsLogic.toView(account("claude", "")).slotLabel)
    }

    @Test
    fun `tempo até renovar e texto curto`() {
        val min = 60_000L
        val now = 1_000_000_000L
        assertNull(AccountsLogic.resetBreakdown(null, now))
        assertNull(AccountsLogic.resetBreakdown(now - 1, now))
        assertTrue(AccountsLogic.resetBreakdown(now + 10_000, now)!!.isSoon)
        assertEquals("instantes", AccountsLogic.formatReset(AccountsLogic.resetBreakdown(now + 10_000, now)!!))
        assertEquals("8 min", AccountsLogic.formatReset(AccountsLogic.resetBreakdown(now + 7 * min + 1, now)!!))
        assertEquals("3 h 12 min", AccountsLogic.formatReset(AccountsLogic.resetBreakdown(now + 192 * min, now)!!))
        assertEquals("2 d 4 h", AccountsLogic.formatReset(AccountsLogic.resetBreakdown(now + (52 * 60) * min, now)!!))
        assertEquals("1 d", AccountsLogic.formatReset(AccountsLogic.resetBreakdown(now + 24 * 60 * min, now)!!))
    }

    @Test
    fun `próxima renovação ignora as que já passaram`() {
        val now = 1_000_000L
        val groups = AccountsLogic.group(
            listOf(
                account("claude", "a", """[{"kind":"fiveHour","label":"5 h","usedPct":10,"resetsAt":${now + 5000}},
                                          {"kind":"weekly","label":"S","usedPct":10,"resetsAt":${now - 5000}}]"""),
                account("codex", "a", """[{"kind":"weekly","label":"S","usedPct":10,"resetsAt":${now + 9000}}]""")
            )
        )
        assertEquals(now + 5000, AccountsLogic.nextReset(groups, now))
    }
}
