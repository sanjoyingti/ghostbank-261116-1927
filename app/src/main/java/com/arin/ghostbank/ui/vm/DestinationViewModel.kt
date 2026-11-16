package com.arin.ghostbank.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.GhostBankApp
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.db.AccountEntity
import com.arin.ghostbank.data.net.Fingerprints
import com.arin.ghostbank.data.net.GhostProfileEntityWrapper
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class CheckState { IDLE, CHECKING, CONFIRMED, UNCONFIRMED }

class DestinationViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as GhostBankApp).container

    val saved: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _number = MutableStateFlow("")
    val number: StateFlow<String> = _number.asStateFlow()

    private val _ifsc = MutableStateFlow("")
    val ifsc: StateFlow<String> = _ifsc.asStateFlow()

    private val _holder = MutableStateFlow("")
    val holder: StateFlow<String> = _holder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _check = MutableStateFlow(CheckState.IDLE)
    val check: StateFlow<CheckState> = _check.asStateFlow()

    private val _checkText = MutableStateFlow<String?>(null)
    val checkText: StateFlow<String?> = _checkText.asStateFlow()

    val branchHint: String?
        get() {
            val value = _ifsc.value
            val format = BranchResolver.formatHint(value)
            if (format != null) return format
            val info = BranchResolver.resolve(value) ?: return "Valid shape, but not one of the 27 supported banks"
            return "${info.bankName} — ${info.branchLabel}"
        }

    fun setNumber(value: String) {
        _number.value = value.filter { it.isDigit() }.take(18)
        _check.value = CheckState.IDLE
        _checkText.value = null
        _error.value = null
    }

    fun setIfsc(value: String) {
        _ifsc.value = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
        _check.value = CheckState.IDLE
        _checkText.value = null
        _error.value = null
    }

    fun setHolder(value: String) {
        _holder.value = value.take(40)
    }

    fun validate(): String? = when {
        _number.value.length < 9 -> "account number needs at least 9 digits"
        !Banks.isValidIfsc(_ifsc.value) -> "IFSC must be 4 letters, a 0, then 6 letters or digits"
        Banks.byIfsc(_ifsc.value) == null -> "that IFSC does not belong to one of the 27 supported banks"
        else -> null
    }

    /** Real GET against the bank's own payee endpoint — LIVE only when the server answers. */
    fun runNameCheck() {
        val problem = validate()
        if (problem != null) {
            _error.value = problem
            return
        }
        val bank = Banks.byIfsc(_ifsc.value) ?: return
        viewModelScope.launch {
            _check.value = CheckState.CHECKING
            _checkText.value = "asking ${bank.name}…"
            _error.value = null
            val settings = container.settings.flow.let { flow ->
                var current = com.arin.ghostbank.data.Settings()
                flow.collect { current = it; return@collect }
                current
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fp = Fingerprints.generate()
                    val exit = runCatching { container.proxyEngine.pick(settings) }.getOrNull()
                    if (settings.stealthLevel == StealthLevel.GHOST && exit == null) {
                        return@runCatching Triple(CheckState.UNCONFIRMED, "no exit configured for Ghost level", null as Int?)
                    }
                    val client = container.http.client(settings, exit, GhostProfileEntityWrapper(fp))
                    val url = "https://${bank.host}${bank.impsPath.let { if (it == "/") "/" else it }}?payee=${_number.value}&ifsc=${_ifsc.value}"
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        val accepted = response.code in 200..399 && !body.contains("invalid", true)
                        Triple(
                            if (accepted) CheckState.CONFIRMED else CheckState.UNCONFIRMED,
                            "HTTP ${response.code} from ${bank.host}",
                            response.code
                        )
                    }
                }.getOrElse { failure ->
                    Triple(CheckState.UNCONFIRMED, failure.message ?: "no answer", null)
                }
            }
            container.http.newSession()
            _check.value = result.first
            _checkText.value = result.second
        }
    }

    fun save() {
        val problem = validate()
        if (problem != null) {
            _error.value = problem
            return
        }
        if (_check.value != CheckState.CONFIRMED && _check.value != CheckState.UNCONFIRMED) {
            _error.value = "run the name check first — success is never claimed without a server answer"
            return
        }
        val bank = Banks.byIfsc(_ifsc.value) ?: return
        viewModelScope.launch {
            container.accounts.saveDestination(_number.value, _ifsc.value, _holder.value.ifBlank { null }, bank.code)
            container.settings.setDestinationSaved(true)
            _error.value = null
        }
    }

    fun masked(number: String): String = Ids.maskNumber(number)

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { DestinationViewModel(app) }
        }
    }
}
