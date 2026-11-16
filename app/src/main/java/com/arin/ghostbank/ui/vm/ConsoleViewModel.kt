package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.StageLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ConsoleViewModel(container: AppContainer, private val runId: Long) : ViewModel() {

    private val logs = container.runs.logs(runId)

    val stageFilter = MutableStateFlow("")
    val statusFilter = MutableStateFlow(0)

    val rows: StateFlow<List<StageLogEntity>> =
        combine(logs, stageFilter, statusFilter) { list, stage, status ->
            list.filter { row ->
                (stage.isBlank() || row.stage.contains(stage, true)) &&
                    (status == 0 || row.httpStatus == status)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStage(value: String) {
        stageFilter.value = value
    }

    fun setStatus(value: Int) {
        statusFilter.value = value
    }

    fun dump(): String {
        val header = "GhostBank raw log — run $runId\n\n"
        return header + rows.value.joinToString("\n\n") { row ->
            buildString {
                appendLine("${row.stage}  ${row.badge}  HTTP ${row.httpStatus ?: "-"}  ${row.latencyMs ?: 0}ms")
                row.note?.let { appendLine("note: $it") }
                row.requestHeaders?.let { appendLine("--- request ---\n$it") }
                row.responseHeaders?.let { appendLine("--- response ---\n$it") }
                row.rawBody?.let { appendLine("--- body ---\n$it") }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, runId: Long) = viewModelFactory {
            initializer { ConsoleViewModel(container, runId) }
        }
    }
}
