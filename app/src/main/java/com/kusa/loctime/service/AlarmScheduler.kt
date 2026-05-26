package com.kusa.loctime.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kusa.loctime.data.entity.TimeEntryEntity
import com.kusa.loctime.receiver.AlarmReceiver
import java.util.Calendar

// AlarmManager を使って時刻アラームの登録・解除を行うクラス。
object AlarmScheduler {

    // オフセットを考慮してアラームを登録する。
    fun schedule(context: Context, entry: TimeEntryEntity, offsetMinutes: Int = 0) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = buildIntent(context, entry, offsetMinutes).let {
            PendingIntent.getBroadcast(
                context, entry.id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        // 本来の時刻からオフセット分を引いた（または足した）時刻に発火させる
        val triggerTime = nextTriggerMillis(entry.hour, entry.minute, offsetMinutes)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancel(context: Context, entry: TimeEntryEntity) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // キャンセル時は offset は 0 でよい（requestCodeの一致が重要）
        val pendingIntent = PendingIntent.getBroadcast(
            context, entry.id, buildIntent(context, entry, 0),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    private fun buildIntent(context: Context, entry: TimeEntryEntity, offsetMinutes: Int) =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ENTRY_ID, entry.id)
            putExtra(AlarmReceiver.EXTRA_LOCATION_ID, entry.locationId)
            putExtra(AlarmReceiver.EXTRA_MESSAGE, entry.message)
            putExtra("offset_minutes", offsetMinutes)
        }

    private fun nextTriggerMillis(hour: Int, minute: Int, offsetMinutes: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // オフセットを適用
            add(Calendar.MINUTE, offsetMinutes)
            
            // すでに過ぎている場合は翌日の同時刻（＋オフセット）へ
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }
}
