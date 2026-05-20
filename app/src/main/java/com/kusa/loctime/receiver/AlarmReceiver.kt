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

// AlarmManager から発火されるブロードキャストレシーバー。
// 指定時刻になると onReceive が呼ばれ、現在地を取得して登録済みの場所の範囲内か判定する。
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_LOCATION_ID = "location_id"
        const val EXTRA_MESSAGE = "message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1)
        val locationId = intent.getIntExtra(EXTRA_LOCATION_ID, -1)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        if (entryId == -1 || locationId == -1) return

        // goAsync() でブロードキャストの処理時間を延長する（デフォルトの10秒制限を回避）
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val locationEntity = db.locationDao().getById(locationId) ?: return@launch
                val entry = db.timeEntryDao().getById(entryId) ?: return@launch

                // 無効化されたエントリは通知しない
                if (!entry.isEnabled) return@launch

                // 現在地を取得する。権限がない場合は SecurityException が発生する。
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val currentLocation: Location? = try {
                    val cts = CancellationTokenSource()
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: SecurityException) {
                    // 位置情報権限がない場合。ユーザーに権限設定を促す通知を表示する。
                    NotificationHelper.showPermissionNotification(context)
                    null
                }

                if (currentLocation != null) {
                    val results = FloatArray(1)
                    // 現在地と登録場所の距離（メートル）を計算する
                    Location.distanceBetween(
                        currentLocation.latitude, currentLocation.longitude,
                        locationEntity.latitude, locationEntity.longitude,
                        results
                    )
                    // 距離が設定半径以内なら通知を表示する
                    if (results[0] <= locationEntity.radiusMeters) {
                        val timeLabel = "%d:%02d".format(entry.hour, entry.minute)
                        NotificationHelper.showNotification(
                            context, entryId, message, timeLabel
                        )
                    }
                }

                // 翌日同時刻に再スケジュール
                AlarmScheduler.schedule(context, entry)
            } finally {
                pending.finish()
            }
        }
    }
}
