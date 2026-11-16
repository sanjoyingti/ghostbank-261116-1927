@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.LedgerRowEntity
import com.arin.ghostbank.data.db.RunEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class TraceItem(val label: String, val value: String, val hidden: Boolean, val why: String)

class TraceViewModel(container: AppContainer, runId: Long) : ViewModel() {

    val run: StateFlow<RunEntity?> = container.runs.byId(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ledger: StateFlow<List<LedgerRowEntity>> = container.ledger.byRun(runId)
        .flatMapLatest { flowOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun items(current: RunEntity?, rows: List<LedgerRowEntity>): List<TraceItem> {
        val utrs = rows.mapNotNull { it.utr }
        return listOf(
            TraceItem("Exit IP used", current?.exitIp ?: "raw device IP", current?.exitIp != null, "an exit keeps your own address off the bank's log"),
            TraceItem("Exit country", current?.exitCountry ?: "unknown", current?.exitCountry == "IN", "must match the bank's region or the bank steps up"),
            TraceItem("Cookies persisted", "0 (ephemeral session)", true, "nothing is carried between runs, so runs cannot be joined"),
            TraceItem("Destination digits", "last 4 visible, ${'$'}{VIRTUAL_COVER}", false, "the destination bank always knows the real account number"),
            TraceItem("UTR chain", if (utrs.isEmpty()) "none issued" else utrs.joinToString(", "), utrs.isNotEmpty(), "the UTR links debit and credit on the rail"),
            TraceItem("Stealth score", "${current?.stealthScore ?: 0}/100", (current?.stealthScore ?: 0) >= 70, "the score is built from what actually leaked")
        )
    }

    fun summary(current: RunEntity?): String {
        val score = current?.stealthScore ?: 0
        return when {
            score >= 85 -> "Hard to trace: browser fingerprint held, exit matched, no cookies carried."
            score >= 60 -> "Mostly hidden: masking held, but the exit or the timing could be tightened."
            else -> "Traceable: the bank could plausibly link this run to your device."
        }
    }

    companion object {
        private const val VIRTUAL_COVER = "rotating virtual accounts"
        fun factory(container: AppContainer, runId: Long) = viewModelFactory {
            initializer { TraceViewModel(container, runId) }
        }
    }
}
