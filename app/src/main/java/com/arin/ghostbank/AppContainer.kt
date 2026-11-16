package com.arin.ghostbank

import android.content.Context
import com.arin.ghostbank.data.ExportHelper
import com.arin.ghostbank.data.SettingsStore
import com.arin.ghostbank.data.db.GhostDatabase
import com.arin.ghostbank.data.net.GhostHttp
import com.arin.ghostbank.data.net.LeakLab
import com.arin.ghostbank.data.net.ProxyEngine
import com.arin.ghostbank.data.net.RunEngine
import com.arin.ghostbank.data.net.VelocityGate
import com.arin.ghostbank.data.repo.AccountRepository
import com.arin.ghostbank.data.repo.LedgerRepository
import com.arin.ghostbank.data.repo.RegistryRepository
import com.arin.ghostbank.data.repo.RunRepository

class AppContainer(context: Context) {

    val context: Context = context.applicationContext

    val db: GhostDatabase by lazy { GhostDatabase.get(context) }
    val settings by lazy { SettingsStore(context) }
    val http by lazy { GhostHttp() }
    val velocity by lazy { VelocityGate() }
    val proxyEngine by lazy { ProxyEngine(db.proxies()) }
    val accounts by lazy { AccountRepository(db.accounts()) }
    val registry by lazy { RegistryRepository(db.registry()) }
    val ledger by lazy { LedgerRepository(db.ledger()) }
    val runs by lazy { RunRepository(db.runs(), db.stages(), db.legs()) }
    val engine by lazy { RunEngine(db, proxyEngine, http, velocity) }
    val leakLab by lazy { LeakLab(http, proxyEngine) }
    val exporter by lazy { ExportHelper(context) }
}
