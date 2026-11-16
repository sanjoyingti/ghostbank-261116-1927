=== ABOUT: The optional background job that harvests a few queued accounts on a randomised schedule ===
package com.arin.ghostbank.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arin.ghostbank.GhostBankApp
import com.arin.ghostbank.data.Settings
import com.arin.ghostbank.model.Badge
import com.arin.ghostbank.model.HarvestStatus
import com.arin.ghostbank.model.RunMode
import com.arin.ghostbank.model.RunStatus
import kotlinx.coroutines.flow.first

class HarvestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        Notifications.ensureChannel(applicationContext)
        return ForegroundInfo(
            Notifications.ID,
            Notifications.build(applicationContext, "GhostBank", "Preparing background harvest", true)
        )
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as GhostBankApp
        val container = app.container
        val settings = container.settings.flow.first()

        if (!settings.backgroundHarvest) return Result.success()
        val destination = container.accounts.current() ?: return Result.success()

        val prefs = applicationContext.getSharedPreferences("ghostbank_bg", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val storedDay = prefs.getString("day", "")
        var doneToday = if (storedDay == today) prefs.getInt("done", 0) else 0
        if (doneToday >= settings.dailyHardCap) return Result.success()

        // Wait inside the diurnal window if enabled.
        if (settings.diurnalWindow) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour < 9 || hour >= 23) return Result.success()
        }

        val bankCode = listOf("SBI", "HDFC", "ICICI", "AXIS", "KOTAK").random()
        val pending = container.registry.unfinished()
            .filter { it.status == HarvestStatus.PENDING.name }
            .take((settings.dailyHardCap - doneToday).coerceAtMost(5))
        if (pending.isEmpty()) return Result.success()

        setForeground(getForegroundInfo())

        val runId = container.db.runs().insert(
            com.arin.ghostbank.data.db.RunEntity(
                bankCode = bankCode,
                mode = RunMode.HARVEST.name,
                amountPaise = 1,
                idempotencyKey = com.arin.ghostbank.model.Ids.idempotencyKey(),
                status = RunStatus.RUNNING.name,
                badge = Badge.SIMULATED.name,
                stealthScore = 0,
                stealthLevel = settings.stealthLevel.name,
                startedAt = System.currentTimeMillis()
            )
        )

        container.engine.performHarvestRun(
            runId = runId,
            settings = settings,
            bankCode = bankCode,
            accountKeys = pending.map { it.id to (it.accountNumber to it.ifsc) }
        )

        doneToday += pending.size
        prefs.edit().putString("day", today).putInt("done", doneToday).apply()

        NotificationHelper.update(applicationContext, "Harvest finished", "$doneToday of ${settings.dailyHardCap} today")
        destinationData(container, settings)
        return Result.success()
    }

    private fun destinationData(container: com.arin.ghostbank.AppContainer, settings: Settings) = Unit
}

object NotificationHelper {
    fun update(context: Context, title: String, text: String) {
        Notifications.ensureChannel(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify(Notifications.ID, Notifications.build(context, title, text, false))
    }
}
