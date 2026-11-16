=== ABOUT: Fixed the half-written settings holder so the app can boot, remember your dial choices and hand them to every screen ===
package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val booted: StateFlow<Boolean> = container.settings.flow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLevel(level: StealthLevel) = viewModelScope.launch { container.settings.setStealthLevel(level) }
    fun setTor(on: Boolean) = viewModelScope.launch { container.settings.setTorMode(on) }
    fun setDoh(value: String) = viewModelScope.launch { container.settings.setDoh(value) }
    fun setDiurnal(on: Boolean) = viewModelScope.launch { container.settings.setDiurnal(on) }
    fun setRpm(value: Int) = viewModelScope.launch { container.settings.setRpm(value) }
    fun setAph(value: Int) = viewModelScope.launch { container.settings.setAph(value) }
    fun setBudget(value: Int) = viewModelScope.launch { container.settings.setBudget(value) }
    fun setRetries(value: Int) = viewModelScope.launch { container.settings.setRetries(value) }
    fun setBackoff(value: Long) = viewModelScope.launch { container.settings.setBackoff(value) }
    fun setVa(on: Boolean) = viewModelScope.launch { container.settings.setVaRotation(on) }
    fun setFragMin(value: Int) = viewModelScope.launch { container.settings.setFragMin(value) }
    fun setFragMax(value: Int) = viewModelScope.launch { container.settings.setFragMax(value) }
    fun setBackground(on: Boolean) = viewModelScope.launch { container.settings.setBackground(on) }
    fun setDailyCap(value: Int) = viewModelScope.launch { container.settings.setDailyCap(value) }
    fun setOnboardingDone(done: Boolean) = viewModelScope.launch { container.settings.setOnboardingDone(done) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
