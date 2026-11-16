package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.net.PayeeChecker
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.Banks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    private val checker = PayeeChecker(container.http, container.proxyEngine)

    val saved = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _number = MutableStateFlow("")
    val number = _number.asStateFlow()

    private val _ifsc = MutableStateFlow("")
    val ifsc = _ifsc.asStateFlow()

    private val _holder = MutableStateFlow("")
    val holder = _holder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _checkBadge = MutableStateFlow<Badge?>(null)
    val checkBadge = _checkBadge.asStateFlow()

    private val _checkMessage = MutableStateFlow<String?>(null)
    val checkMessage = _checkMessage.asStateFlow()

    private val _raw = MutableStateFlow<String?>(null)
    val raw = _raw.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun load(entity: AccountEntity?) {
        if (entity == null || _number.value.isNotBlank()) return
        _number.value = entity.number
        _ifsc.value = entity.ifsc
        _holder.value = entity.holderName.orEmpty()
    }

    fun setNumber(value: String) {
        _number.value = value.filter { it.isDigit() }.take(18)
        _checkBadge.value = null
    }

    fun setIfsc(value: String) {
        _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
        _checkBadge.value = null
    }

    fun setHolder(value: String) {
        _holder.value = value.take(40)
    }

    val branch: String? get() = BranchResolver.resolve(_ifsc.value)?.let { "${it.bankName} · ${it.branchLabel}" }
    val ifscHint: String? get() = BranchResolver.formatHint(_ifsc.value)

    fun runNameCheck() = viewModelScope.launch {
        val bank = Banks.byIfsc(_ifsc.value) ?: run {
            _error.value = "resolve the IFSC first"
            return@launch
        }
        if (_number.value.length < 9) {
            _error.value = "account number needs at least 9 digits"
            return@launch
        }
        _error.value = null
        _busy.value = true
        _checkMessage.value = "asking ${bank.name} …"
        val settings = container.settings.flow.first()
        val result = checker.check(settings, bank, _number.value, _ifsc.value)
        _checkBadge.value = result.badge
        _checkMessage.value = result.message
        _raw.value = result.raw
        _busy.value = false
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
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
        val bank = Banks.byIfsc(code) ?: run {
            _error.value = "that IFSC is not one of the 27 supported banks"
            return@launch
        }
        container.accounts.saveDestination(digits, code, _holder.value.ifBlank { null }, bank.code)
        container.settings.setDestinationSaved(true)
        _error.value = null
        onSaved()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DestinationViewModel(container) }
        }
    }
}
