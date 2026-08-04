package com.kusa.loctime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.service.AlarmScheduler
import com.kusa.loctime.service.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
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

        Log.d(TAG, "Alarm received: entryId=$entryId, locationId=$locationId")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val locationEntity = db.locationDao().getById(locationId) ?: return@launch
                val entry = db.timeEntryDao().getById(entryId) ?: return@launch

                if (!entry.isEnabled) {
                    Log.d(TAG, "Entry $entryId is disabled, skipping")
                    return@launch
                }

                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                
                // 位置取得ロジックの改善（リトライと lastLocation の活用）
                var currentLocation: Location? = null
                
                // 1. まずは直近のキャッシュされた位置情報を確認
                try {
                    currentLocation = fusedClient.lastLocation.await()
                    if (currentLocation != null) {
                        Log.d(TAG, "Using lastLocation: ${currentLocation.latitude},${currentLocation.longitude}")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Location permission missing", e)
                    NotificationHelper.showPermissionNotification(context)
                    return@launch
                }

                // 2. キャッシュがない、または古い可能性を考慮して最新の位置取得を試みる（最大3回リトライ）
                var retryCount = 0
                val maxRetries = 3
                while (retryCount < maxRetries) {
                    try {
                        val cts = CancellationTokenSource()
                        val freshLocation = fusedClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cts.token
                        ).await()
                        
                        if (freshLocation != null) {
                            currentLocation = freshLocation
                            Log.d(TAG, "Fetched fresh location: ${currentLocation.latitude},${currentLocation.longitude} (attempt ${retryCount + 1})")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get fresh location (attempt ${retryCount + 1}): ${e.message}")
                    }
                    retryCount++
                    if (retryCount < maxRetries) delay(2000) // 2秒待ってリトライ
                }

                if (currentLocation != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        currentLocation.latitude, currentLocation.longitude,
                        locationEntity.latitude, locationEntity.longitude,
                        results
                    )
                    val distance = results[0]
                    Log.d(TAG, "Current location: ${currentLocation.latitude},${currentLocation.longitude}, Distance to ${locationEntity.name}: ${distance}m (Radius: ${locationEntity.radiusMeters}m)")

                    if (distance <= locationEntity.radiusMeters) {
                        val offsetLabel = when {
                            locationEntity.offsetMinutes < 0 -> "（${-locationEntity.offsetMinutes}分前）"
                            locationEntity.offsetMinutes > 0 -> "（${locationEntity.offsetMinutes}分後）"
                            else -> ""
                        }
                        val timeLabel = "%d:%02d%s".format(entry.hour, entry.minute, offsetLabel)
                        Log.i(TAG, "Showing notification for ${locationEntity.name}: $message")

                        val title = if (message.isNotBlank()) message else timeLabel
                        val content = if (message.isNotBlank()) "${locationEntity.name} $timeLabel" else locationEntity.name

                        NotificationHelper.showNotification(
                            context, entryId, title, content
                        )
                    } else {
                        Log.d(TAG, "Out of range, skipping notification")
                    }
                } else {
                    Log.w(TAG, "Could not get current location")
                }

                // 翌日の再スケジュール時にも、その場所の最新のオフセットを適用する
                AlarmScheduler.schedule(context, entry, locationEntity.offsetMinutes)
            } finally {
                pending.finish()
            }
        }
    }
}
