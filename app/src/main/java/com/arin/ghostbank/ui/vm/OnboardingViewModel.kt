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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val page: Int = 0,
    val level: StealthLevel = StealthLevel.STANDARD,
    val number: String = "",
    val ifsc: String = "",
    val holder: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val finished: Boolean = false
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun next() = _state.update { it.copy(page = (it.page + 1).coerceAtMost(2)) }
    fun back() = _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
    fun setLevel(level: StealthLevel) = _state.update { it.copy(level = level) }

    fun setNumber(value: String) = _state.update {
        it.copy(number = value.filter { c -> c.isDigit() }.take(18), error = null)
    }

    fun setIfsc(value: String) = _state.update {
        it.copy(ifsc = value.uppercase().filter { c -> c.isLetterOrDigit() }.take(11), error = null)
    }

    fun setHolder(value: String) = _state.update { it.copy(holder = value.take(40)) }

    fun finish() {
        val current = _state.value
        val number = current.number.trim()
        val ifsc = current.ifsc.trim().uppercase()
        val bank = Banks.byIfsc(ifsc)
        val problem = when {
            number.length < 9 -> "Account number looks too short"
            !Banks.isValidIfsc(ifsc) -> BranchResolver.formatHint(ifsc) ?: "IFSC must be 4 letters, a 0, then 6 characters"
            bank == null -> "That IFSC is not one of the 27 supported banks"
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            container.accounts.saveDestination(number, ifsc, current.holder.ifBlank { null }, bank!!.code)
            container.settings.setStealthLevel(current.level)
            container.settings.setDestinationSaved(true)
            container.settings.setOnboardingDone(true)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}
