=== ABOUT: Starts the app, makes the notification channel and re-arms the background job ===
package com.arin.ghostbank

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arin.ghostbank.work.HarvestWorker
import com.arin.ghostbank.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class GhostBankApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        scope.launch {
            val settings = container.settings.flow.first()
            if (settings.backgroundHarvest) scheduleBackground()
        }
    }

    fun scheduleBackground() {
        val request = PeriodicWorkRequestBuilder<HarvestWorker>(60, TimeUnit.MINUTES)
            .setInitialDelay((2..25).random().toLong(), TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelBackground() {
        WorkManager.getInstance(this).cancelUniqueWork(TAG)
    }

    companion object {
        const val TAG = "ghostbank_background_harvest"
    }
}
