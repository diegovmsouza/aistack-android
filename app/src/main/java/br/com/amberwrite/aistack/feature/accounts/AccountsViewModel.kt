package br.com.amberwrite.aistack.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.data.repo.AccountsRepo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Contas dos provedores, cotas, servidores MCP e versão do desktop (somente leitura). */
class AccountsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<AccountsRepo.State> = container.accountsRepo.state

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            runCatching { container.accountsRepo.reload() }
            runCatching { container.accountsRepo.loadExtras() }
        }
    }
}
