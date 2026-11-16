package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The number in the sticky header: the newest stealth score, or 100 before the first run. */
class HeaderViewModel(container: AppContainer) : ViewModel() {

    val score: StateFlow<Int> = container.db.scores().recent()
        .map { rows -> rows.firstOrNull()?.score ?: 100 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HeaderViewModel(container) }
        }
    }
}
