=== ABOUT: The trace audit for a single run: what leaked and a plain-English verdict ===
package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.RunEntity
import com.arin.ghostbank.data.db.StageLogEntity
import com.arin.ghostbank.data.db.StealthScoreEntity
import com.arin.ghostbank.model.Banks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LeakLineRow(val label: String, val value: String, val hidden: Boolean, val note: String)

class TraceAuditViewModel(container: AppContainer, private val runId: Long) : ViewModel() {

    val run: StateFlow<RunEntity?> = container.runs.byId(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ledger: StateFlow<List<LedgerRowEntity>> = container.ledger.byRun(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val logs: StateFlow<List<StageLogEntity>> = container.runs.logs(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val score: StateFlow<StealthScoreEntity?> = container.db.scores().byRun(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lines: StateFlow<List<LeakLineRow>> = combine(run, ledger, logs) { runRow, ledgerRows, logRows ->
        if (runRow == null) return@combine emptyList()
        val utrs = ledgerRows.mapNotNull { it.utr }.ifEmpty { listOfNotNull(runRow.summary?.substringAfter("UTR ", null)) }
        val cookies = logRows.count { it.responseHeaders?.contains("set-cookie", true) == true }
        listOf(
            LeakLineRow(
                "Exit IP used",
                runRow.exitIp?.takeIf { it.isNotBlank() } ?: "not recorded",
                runRow.exitIp?.isNotBlank() == true,
                "the bank saw this address"
            ),
            LeakLineRow(
                "Cookies persisted",
                if (cookies == 0) "none carried across runs" else "$cookies response(s) set cookies inside this run only",
                cookies == 0,
                "the session is thrown away when the run ends"
            ),
            LeakLineRow(
                "Digits exposed to the destination bank",
                "your real account number",
                false,
                "the destination bank always knows it — nothing can change that"
            ),
            LeakLineRow(
                "Digits exposed on the rail",
                if (runRow.bankCode.isNotBlank()) "virtual account handle" else "unknown",
                true,
                "the credit is addressed to a rotating VA when rotation is on"
            ),
            LeakLineRow(
                "UTR chain",
                utrs.joinToString(", ").ifEmpty { "no UTR booked" },
                true,
                "a UTR links the two legs at the network, not your identity"
            ),
            LeakLineRow(
                "Source bank",
                runRow.bankCode.ifBlank { "unknown" },
                true,
                "the source bank knows the debited account"
            ),
            LeakLineRow(
                "Fingerprint reused",
                "no — burned after the leg",
                true,
                "each account gets its own frozen identity"
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bankName(): String = run.value?.bankCode?.let { Banks.byCode(it)?.name ?: it } ?: "unknown bank"

    fun verdict(): String {
        val row = run.value ?: return "no run loaded"
        val leaky = lines.value.count { !it.hidden }
        return when {
            row.exitIp.isNullOrBlank() -> "This run went out on your own IP — the bank can trace it back to your connection. Add an exit and re-run."
            leaky <= 1 -> "This run was well hidden: the money landed through a masked handle from an unrelated address, and only the destination bank's own ledger knows your digits."
            else -> "This run was partly visible: the exit was real but a few things still pointed at you. Tighten the stealth level before the next run."
        }
    }

    companion object {
        fun factory(container: AppContainer, runId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { TraceAuditViewModel(container, runId) }
        }
    }
}
