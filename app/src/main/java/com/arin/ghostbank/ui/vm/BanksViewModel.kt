package com.arin.ghostbank.ui.vm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.data.db.AccountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class BanksViewModel(private val container: AppContainer) : ViewModel() {

    val destination: StateFlow<AccountEntity?> = container.accounts.destination()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Settings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online = _online.asStateFlow()

    init {
        refreshOnline()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun refreshOnline() {
        val manager = container.context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
        _online.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BanksViewModel(container) }
        }
    }
}
