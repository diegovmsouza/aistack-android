package br.com.amberwrite.aistack.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.amberwrite.aistack.AppContainer
import br.com.amberwrite.aistack.data.repo.DevicesRepo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Aparelhos pareados com o desktop e estado do relay (somente leitura). */
class DevicesViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<DevicesRepo.State> = container.devicesRepo.state
    val currentDeviceId: String get() = container.devicesRepo.currentDeviceId

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { runCatching { container.devicesRepo.reload() } }
    }
}
