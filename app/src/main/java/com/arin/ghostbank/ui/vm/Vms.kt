package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The number in the sticky header: the newest stealth score, or 100 before the first run. */
class HeaderViewModel(container: AppContainer) : ViewModel() {

    val score: StateFlow<Int> = container.db.scores().recent()
        .map { rows -> rows.firstOrNull()?.score ?: 100 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { HeaderViewModel(container) } }
    }
}

/** Holds the settings and flips the boot flag once the stored values have been read. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _booted = MutableStateFlow(false)
    val booted: StateFlow<Boolean> = _booted.asStateFlow()

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    init {
        viewModelScope.launch {
            container.settings.flow.first()
            _booted.value = true
        }
    }

    fun setLevel(value: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(value) }
    fun setTor(value: Boolean) = viewModelScope.launch { container.settings.setTorMode(value) }
    fun setDoh(value: String) = viewModelScope.launch { container.settings.setDoh(value) }
    fun setDiurnal(value: Boolean) = viewModelScope.launch { container.settings.setDiurnal(value) }
    fun setRpm(value: Int) = viewModelScope.launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = viewModelScope.launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = viewModelScope.launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = viewModelScope.launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = viewModelScope.launch { container.settings.setBackoff(value) }
    fun setVa(value: Boolean) = viewModelScope.launch { container.settings.setVaRotation(value) }
    fun setFragMin(value: Int) = viewModelScope.launch { container.settings.setFragMin(value) }
    fun setFragMax(value: Int) = viewModelScope.launch { container.settings.setFragMax(value) }
    fun setBackground(value: Boolean) = viewModelScope.launch { container.settings.setBackground(value) }
    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }
    fun setOnboardingDone(value: Boolean) = viewModelScope.launch { container.settings.setOnboardingDone(value) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(container) } }
    }
}
