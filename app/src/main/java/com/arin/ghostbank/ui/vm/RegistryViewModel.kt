package com.arin.ghostbank.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.db.RegistryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RegistryViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")

    val rows: StateFlow<List<RegistryEntity>> = query
        .flatMapLatest { text ->
            if (text.isBlank()) container.registry.all() else container.registry.search(text)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun force(id: Long) {
        viewModelScope.launch { container.registry.forceReset(id) }
    }

    fun wipe() {
        viewModelScope.launch { container.registry.wipe() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RegistryViewModel(container) }
        }
    }
}
