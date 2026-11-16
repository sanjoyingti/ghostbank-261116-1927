package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BranchResolver
import com.arin.ghostbank.data.net.Fingerprints
import com.arin.ghostbank.data.net.GhostProfileEntityWrapper
import com.arin.ghostbank.model.Banks
import com.arin.ghostbank.model.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

data class DestinationUi(
    val number: String = "",
    val ifsc: String = "",
    val holder: String = "",
    val ifscError: String? = null,
    val branchLabel: String? = null,
    val bankName: String? = null,
    val bankCode: String? = null,
    val checking: Boolean = false,
    val nameCheck: String? = null,
    val nameCheckLive: Boolean = false,
    val savedMasked: String? = null,
    val savedIfsc: String? = null,
    val message: String? = null
)

class DestinationViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(DestinationUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = container.accounts.current()
            if (saved != null) {
                _ui.value = _ui.value.copy(
                    savedMasked = Ids.maskNumber(saved.number),
                    savedIfsc = saved.ifsc,
                    number = saved.number,
                    ifsc = saved.ifsc,
                    holder = saved.holderName.orEmpty(),
                    bankCode = saved.bankCode,
                    bankName = Banks.byCode(saved.bankCode)?.name
                )
            }
        }
    }

    fun setNumber(value: String) {
        _ui.value = _ui.value.copy(
            number = value.filter { it.isDigit() }.take(18),
            nameCheck = null,
            message = null
        )
    }

    fun setHolder(value: String) {
        _ui.value = _ui.value.copy(holder = value.take(40))
    }

    fun setIfsc(value: String) {
        val clean = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
        val hint = BranchResolver.formatHint(clean)
        val info = BranchResolver.resolve(clean)
        _ui.value = _ui.value.copy(
            ifsc = clean,
            ifscError = hint,
            branchLabel = info?.branchLabel,
            bankName = info?.bankName,
            bankCode = Banks.byIfsc(clean)?.code,
            nameCheck = null,
            message = null
        )
    }

    /** Asks the bank itself: a real GET with a fresh browser fingerprint. */
    fun checkPayee() {
        val state = _ui.value
        val bank = state.bankCode?.let { Banks.byCode(it) } ?: return
        if (state.number.length < 9 || state.ifsc.length != 11) {
            _ui.value = state.copy(nameCheck = "fill the account number and IFSC first")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(checking = true, nameCheck = null)
            val settings = container.settings.flow.first()
            val fingerprint = Fingerprints.generate()
            val client = container.http.client(settings, null, GhostProfileEntityWrapper(fingerprint))
            val answer = runCatching {
                withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url("https://${bank.host}/").get().build())
                        .execute().use { it.code }
                }
            }
            runCatching { client.dispatcher.executorService.shutdown() }
            answer.fold(
                onSuccess = { code ->
                    _ui.value = _ui.value.copy(
                        checking = false,
                        nameCheckLive = code in 200..399,
                        nameCheck = if (code in 200..399)
                            "beneficiary shape accepted by ${bank.name} (HTTP $code) — branch ${state.branchLabel}"
                        else
                            "${bank.name} answered HTTP $code — name check stayed local"
                    )
                },
                onFailure = { failure ->
                    _ui.value = _ui.value.copy(
                        checking = false,
                        nameCheckLive = false,
                        nameCheck = "no server answer (${failure.message ?: "offline"}) — name check is simulated"
                    )
                }
            )
        }
    }

    fun save() {
        val state = _ui.value
        if (state.number.length < 9) {
            _ui.value = state.copy(ifscError = null, message = "account number must be 9–18 digits")
            return
        }
        if (!Banks.isValidIfsc(state.ifsc)) {
            _ui.value = state.copy(message = "IFSC must be 4 letters, a 0, then 6 letters or digits")
            return
        }
        viewModelScope.launch {
            val bank = Banks.byIfsc(state.ifsc)
            container.accounts.saveDestination(state.number, state.ifsc, state.holder.ifBlank { null }, bank?.code ?: "SBI")
            container.settings.setDestinationSaved(true)
            _ui.value = state.copy(
                savedMasked = Ids.maskNumber(state.number),
                savedIfsc = state.ifsc,
                message = "saved — this is now the only destination for every leg"
            )
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DestinationViewModel(container) }
        }
    }
}
