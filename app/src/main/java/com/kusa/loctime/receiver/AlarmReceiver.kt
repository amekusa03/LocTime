package com.kusa.loctime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.service.AlarmScheduler
import com.kusa.loctime.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_LOCATION_ID = "location_id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_OFFSET = "offset_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1)
        val locationId = intent.getIntExtra(EXTRA_LOCATION_ID, -1)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val offset = intent.getIntExtra(EXTRA_OFFSET, 0)
        
        if (entryId == -1 || locationId == -1) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val locationEntity = db.locationDao().getById(locationId) ?: return@launch
                val entry = db.timeEntryDao().getById(entryId) ?: return@launch

                if (!entry.isEnabled) return@launch

                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val currentLocation: Location? = try {
                    val cts = CancellationTokenSource()
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: SecurityException) {
                    NotificationHelper.showPermissionNotification(context)
                    null
                }

                if (currentLocation != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        currentLocation.latitude, currentLocation.longitude,
                        locationEntity.latitude, locationEntity.longitude,
                        results
                    )
                    if (results[0] <= locationEntity.radiusMeters) {
                        val timeLabel = "%d:%02d".format(entry.hour, entry.minute)
                        NotificationHelper.showNotification(
                            context, entryId, message, timeLabel
                        )
                    }
                }

                // 翌日の再スケジュール時にも、その場所の最新のオフセットを適用する
                AlarmScheduler.schedule(context, entry, locationEntity.offsetMinutes)
            } finally {
                pending.finish()
            }
        }
    }
}
