package com.arin.ghostbank.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ghostbank_settings")

data class Settings(
    val stealthLevel: StealthLevel = StealthLevel.STANDARD,
    val torMode: Boolean = false,
    val dohResolver: String = "https://cloudflare-dns.com/dns-query",
    val diurnalWindow: Boolean = false,
    val maxRequestsPerMinute: Int = 20,
    val maxAccountsPerHour: Int = 120,
    val entropyBudget: Int = 220,
    val retries: Int = 3,
    val backoffMs: Long = 1_200,
    val vaRotation: Boolean = true,
    val fragMin: Int = 2,
    val fragMax: Int = 5,
    val onboardingDone: Boolean = false,
    val backgroundHarvest: Boolean = false,
    val dailyHardCap: Int = 100,
    val destinationSaved: Boolean = false,
    val lastExitCountry: String? = null,
    val lastExitIp: String? = null
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val LEVEL = stringPreferencesKey("stealth_level")
        val TOR = booleanPreferencesKey("tor_mode")
        val DOH = stringPreferencesKey("doh_resolver")
        val DIURNAL = booleanPreferencesKey("diurnal")
        val RPM = intPreferencesKey("max_rpm")
        val APH = intPreferencesKey("max_aph")
        val BUDGET = intPreferencesKey("entropy_budget")
        val RETRIES = intPreferencesKey("retries")
        val BACKOFF = longPreferencesKey("backoff_ms")
        val VA = booleanPreferencesKey("va_rotation")
        val FRAG_MIN = intPreferencesKey("frag_min")
        val FRAG_MAX = intPreferencesKey("frag_max")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val BACKGROUND = booleanPreferencesKey("background_harvest")
        val DAILY_CAP = intPreferencesKey("daily_cap")
        val DEST_SAVED = booleanPreferencesKey("dest_saved")
        val EXIT_COUNTRY = stringPreferencesKey("exit_country")
        val EXIT_IP = stringPreferencesKey("exit_ip")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            stealthLevel = runCatching { StealthLevel.valueOf(p[Keys.LEVEL] ?: "STANDARD") }.getOrDefault(StealthLevel.STANDARD),
            torMode = p[Keys.TOR] ?: false,
            dohResolver = p[Keys.DOH] ?: "https://cloudflare-dns.com/dns-query",
            diurnalWindow = p[Keys.DIURNAL] ?: false,
            maxRequestsPerMinute = p[Keys.RPM] ?: 20,
            maxAccountsPerHour = p[Keys.APH] ?: 120,
            entropyBudget = p[Keys.BUDGET] ?: 220,
            retries = p[Keys.RETRIES] ?: 3,
            backoffMs = p[Keys.BACKOFF] ?: 1_200,
            vaRotation = p[Keys.VA] ?: true,
            fragMin = p[Keys.FRAG_MIN] ?: 2,
            fragMax = p[Keys.FRAG_MAX] ?: 5,
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            backgroundHarvest = p[Keys.BACKGROUND] ?: false,
            dailyHardCap = p[Keys.DAILY_CAP] ?: 100,
            destinationSaved = p[Keys.DEST_SAVED] ?: false,
            lastExitCountry = p[Keys.EXIT_COUNTRY],
            lastExitIp = p[Keys.EXIT_IP]
        )
    }

    suspend fun setStealthLevel(level: StealthLevel) = context.dataStore.edit { it[Keys.LEVEL] = level.name }
    suspend fun setTorMode(on: Boolean) = context.dataStore.edit { it[Keys.TOR] = on }
    suspend fun setDoh(resolver: String) = context.dataStore.edit { it[Keys.DOH] = resolver }
    suspend fun setDiurnal(on: Boolean) = context.dataStore.edit { it[Keys.DIURNAL] = on }
    suspend fun setRpm(value: Int) = context.dataStore.edit { it[Keys.RPM] = value.coerceIn(1, 120) }
    suspend fun setAph(value: Int) = context.dataStore.edit { it[Keys.APH] = value.coerceIn(1, 600) }
    suspend fun setBudget(value: Int) = context.dataStore.edit { it[Keys.BUDGET] = value.coerceIn(10, 2000) }
    suspend fun setRetries(value: Int) = context.dataStore.edit { it[Keys.RETRIES] = value.coerceIn(0, 8) }
    suspend fun setBackoff(value: Long) = context.dataStore.edit { it[Keys.BACKOFF] = value.coerceIn(100, 30_000) }
    suspend fun setVaRotation(on: Boolean) = context.dataStore.edit { it[Keys.VA] = on }
    suspend fun setFragMin(value: Int) = context.dataStore.edit { it[Keys.FRAG_MIN] = value.coerceIn(1, 5) }
    suspend fun setFragMax(value: Int) = context.dataStore.edit { it[Keys.FRAG_MAX] = value.coerceIn(1, 5) }
    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING] = done }
    suspend fun setBackground(on: Boolean) = context.dataStore.edit { it[Keys.BACKGROUND] = on }
    suspend fun setDailyCap(value: Int) = context.dataStore.edit { it[Keys.DAILY_CAP] = value.coerceIn(1, 500) }
    suspend fun setDestinationSaved(saved: Boolean) = context.dataStore.edit { it[Keys.DEST_SAVED] = saved }
    suspend fun setLastExit(ip: String?, country: String?) = context.dataStore.edit {
        it[Keys.EXIT_IP] = ip ?: ""
        it[Keys.EXIT_COUNTRY] = country ?: ""
    }
}
