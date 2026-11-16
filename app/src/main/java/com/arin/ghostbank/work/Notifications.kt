=== ABOUT: The one notification channel used by the optional background harvest ===
package com.arin.ghostbank.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.arin.ghostbank.R

object Notifications {

    const val CHANNEL = "ghostbank_harvest"
    const val ID = 4201

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Background harvest", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress of scheduled ghost harvests"
                }
            )
        }
    }

    fun build(context: Context, title: String, text: String, ongoing: Boolean) =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
