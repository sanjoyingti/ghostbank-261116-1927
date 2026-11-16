package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _index = MutableStateFlow(0)
    val index = _index.asStateFlow()

    val level = MutableStateFlow(StealthLevel.STANDARD)
    val number = MutableStateFlow("")
    val ifsc = MutableStateFlow("")
    val error = MutableStateFlow<String?>(null)

    fun next() {
        _index.value = (_index.value + 1).coerceAtMost(2)
    }

    fun back() {
        _index.value = (_index.value - 1).coerceAtLeast(0)
    }

    fun setLevel(value: StealthLevel) {
        level.value = value
    }

    fun setNumber(value: String) {
        number.value = value.filter { it.isDigit() }.take(18)
    }

    fun setIfsc(value: String) {
        ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
    }

    fun finish(onDone: () -> Unit) {
        val account = number.value
        val code = ifsc.value
        if (account.length < 9 || !Banks.isValidIfsc(code)) {
            error.value = "Enter your account number and a valid IFSC (4 letters, 0, 6 characters)"
            _index.value = 2
            return
        }
        viewModelScope.launch {
            container.settings.setStealthLevel(level.value)
            container.accounts.saveDestination(account, code, null, Banks.byIfsc(code)?.code ?: "SBI")
            container.settings.setDestinationSaved(true)
            container.settings.setOnboardingDone(true)
            onDone()
        }
    }

    fun skipAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            container.settings.setStealthLevel(level.value)
            container.settings.setOnboardingDone(true)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}
