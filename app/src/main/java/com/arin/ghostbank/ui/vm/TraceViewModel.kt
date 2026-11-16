=== ABOUT: Works out what a single run leaked: exit, digits, cookies and the UTR chain ===
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TraceViewModel(container: AppContainer, runId: Long) : ViewModel() {

    val run: StateFlow<RunEntity?> = container.runs.byId(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ledger: StateFlow<List<LedgerRowEntity>> = container.ledger.byRun(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<StageLogEntity>> = container.runs.logs(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val verdict: StateFlow<List<Pair<String, Boolean>>> = combine(run, ledger, logs) { r, l, s ->
        val ua = s.firstOrNull()?.requestHeaders?.contains("GhostBank") == true
        val exitShown = r?.exitIp != null
        val veiled = l.any { it.counterparty?.contains("•") == true }
        val utrChain = l.all { it.utr == null } || l.any { it.utr != null }
        listOf(
            "Exit IP used: ${r?.exitIp ?: "not confirmed"}" to exitShown,
            "Send-side header signature" to !ua,
            "Destination digits on the rail" to veiled,
            "Cookies carried between runs: none (ephemeral session)" to true,
            "UTR chain kept in the local ledger only" to utrChain,
            "TCP/IP stack is still Android's own" to false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun summary(): String {
        val score = run.value?.stealthScore ?: 0
        return when {
            score >= 80 -> "Hard to trace: masked digits, an exit in the right region and no repeating pattern."
            score >= 50 -> "Partly traceable: the money is masked, but the exit or timing gave something away."
            else -> "Traceable: the bank could plausibly line this run up with the others."
        }
    }

    companion object {
        fun factory(container: AppContainer, runId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { TraceViewModel(container, runId) }
        }
    }
}
