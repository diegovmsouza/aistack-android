package br.com.amberwrite.aistack.service

import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.core.rpc.HostEvent
import br.com.amberwrite.aistack.core.rpc.RpcCaller
import br.com.amberwrite.aistack.core.rpc.RpcException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Mudanças de nível de assinatura que o [LiveDetailSubscriber] precisa fazer. */
data class SubscriptionPlan(val toFull: Set<String>, val toSummary: Set<String>) {
    val isEmpty: Boolean get() = toFull.isEmpty() && toSummary.isEmpty()

    companion object {
        /**
         * [current]: conversas que este assinante pôs em `full`; [wanted]: as que devem estar;
         * [open]: abertas no chat (o [br.com.amberwrite.aistack.data.repo.ChatRepo] cuida delas,
         * então nunca são rebaixadas daqui).
         */
        fun of(current: Set<String>, wanted: Set<String>, open: Set<String>): SubscriptionPlan =
            SubscriptionPlan(
                toFull = wanted - current - open,
                toSummary = current - wanted - open,
            )
    }
}

/**
 * Com o app em segundo plano, assina em nível `full` as conversas ocupadas para o Live Update
 * mostrar a ferramenta atual e os sub-agentes (no nível `summary` só chegam início/fim de
 * turno e pedidos). Volta a `summary` quando o turno acaba ou o app vem para a frente.
 *
 * Roda enquanto o [ConnectionService] estiver de pé; sem o serviço, o Live Update mostra só o
 * cronômetro (degradação aceitável). Um `subscribe all` (reconexão, [HostEvent.Resync]) apaga
 * os ajustes por conversa no host, então o conjunto local é zerado e refeito.
 */
class LiveDetailSubscriber(
    private val rpc: RpcCaller,
    private val busyIds: Flow<Set<String>>,
    private val isForeground: StateFlow<Boolean>,
    private val openIds: () -> Set<String>,
    private val settleMs: Long = 2_000,
) {
    private val generation = MutableStateFlow(0)
    private val full = HashSet<String>()
    private var unsupported = false

    fun start(scope: CoroutineScope): Job = scope.launch {
        launch {
            rpc.events.collect { ev ->
                if (ev is HostEvent.Connected || ev is HostEvent.Resync || ev is HostEvent.Disconnected) {
                    synchronized(full) { full.clear() }
                    generation.value++
                }
            }
        }
        combine(rpc.connected, isForeground, busyIds.distinctUntilChanged(), generation) { c, fg, busy, _ ->
            Triple(c, fg, busy)
        }.collectLatest { (connected, foreground, busy) ->
            if (!connected) {
                synchronized(full) { full.clear() }
                return@collectLatest
            }
            if (unsupported) return@collectLatest
            // Espera o estado assentar (e o `subscribe all` da reconexão ir primeiro).
            delay(settleMs)
            val current = synchronized(full) { full.toSet() }
            val plan = SubscriptionPlan.of(current, if (foreground) emptySet() else busy, openIds())
            if (plan.isEmpty) return@collectLatest
            apply(plan)
        }
    }

    private suspend fun apply(plan: SubscriptionPlan) {
        try {
            if (plan.toFull.isNotEmpty()) {
                rpc.call("subscribe", mapOf("conversations" to plan.toFull.toList(), "level" to "full"))
                synchronized(full) { full += plan.toFull }
            }
            if (plan.toSummary.isNotEmpty()) {
                rpc.call("subscribe", mapOf("conversations" to plan.toSummary.toList(), "level" to "summary"))
                synchronized(full) { full -= plan.toSummary }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            if (e.isUnknownMethod) unsupported = true
            // Outras falhas: a próxima mudança de estado tenta de novo.
        }
    }

    companion object {
        fun forContainer(container: AppContainer): LiveDetailSubscriber = LiveDetailSubscriber(
            rpc = container.rpc,
            busyIds = container.pendingRepo.items.map { list -> list.filter { it.busy }.map { it.conversationId }.toSet() },
            isForeground = container.isForeground,
            openIds = { container.chatRepo.openIds },
        )
    }
}
