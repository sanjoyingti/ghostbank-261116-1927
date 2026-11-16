package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _page = MutableStateFlow(0)
    val page = _page.asStateFlow()

    private val _level = MutableStateFlow(StealthLevel.STANDARD)
    val level = _level.asStateFlow()

    private val _number = MutableStateFlow("")
    val number = _number.asStateFlow()

    private val _ifsc = MutableStateFlow("")
    val ifsc = _ifsc.asStateFlow()

    private val _holder = MutableStateFlow("")
    val holder = _holder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun next() {
        _page.value = (_page.value + 1).coerceAtMost(2)
        _error.value = null
    }

    fun back() {
        _page.value = (_page.value - 1).coerceAtLeast(0)
    }

    fun setLevel(value: StealthLevel) {
        _level.value = value
    }

    fun setNumber(value: String) {
        _number.value = value.filter { it.isDigit() }.take(18)
    }

    fun setIfsc(value: String) {
        _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
    }

    fun setHolder(value: String) {
        _holder.value = value.take(40)
    }

    val ifscHint: String?
        get() = BranchResolver.formatHint(_ifsc.value)

    val branch: String?
        get() = BranchResolver.resolve(_ifsc.value)?.let { "${it.bankName} · ${it.branchLabel}" }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val digits = _number.value
        val code = _ifsc.value
        if (digits.length < 9) {
            _error.value = "account number needs at least 9 digits"
            return@launch
        }
        if (!Banks.isValidIfsc(code)) {
            _error.value = "IFSC shape must be 4 letters, a 0, then 6 letters or digits"
            return@launch
        }
        val bank = Banks.byIfsc(code)
        if (bank == null) {
            _error.value = "that IFSC is not one of the 27 supported banks"
            return@launch
        }
        _busy.value = true
        container.accounts.saveDestination(digits, code, _holder.value.ifBlank { null }, bank.code)
        container.settings.setStealthLevel(_level.value)
        if (_level.value == StealthLevel.GHOST) container.settings.setTorMode(true)
        container.settings.setDestinationSaved(true)
        container.settings.setOnboardingDone(true)
        _busy.value = false
        onDone()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}
