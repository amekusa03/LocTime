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
// 常時GPS監視はせず、指定時刻にのみ起動することでバッテリー消費を抑える設計。
object AlarmScheduler {

    // 時刻エントリに対応するアラームを翌発火時刻に登録する。
    // Android 12以上で正確なアラーム権限がない場合は setAndAllowWhileIdle にフォールバックする。
    fun schedule(context: Context, entry: TimeEntryEntity) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = buildIntent(context, entry).let {
            PendingIntent.getBroadcast(
                context, entry.id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val triggerTime = nextTriggerMillis(entry.hour, entry.minute)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // SCHEDULE_EXACT_ALARM 権限がない場合。精度は下がるが動作は継続する。
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            // 正確な時刻で発火させる（Dozeモード中も起動する）
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    // 時刻エントリに対応するアラームを解除する。
    // FLAG_NO_CREATE で既存の PendingIntent がなければ何もしない。
    fun cancel(context: Context, entry: TimeEntryEntity) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, entry.id, buildIntent(context, entry),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    // AlarmReceiver に渡す Intent を生成する。entry.id を requestCode にすることで各エントリを識別する。
    private fun buildIntent(context: Context, entry: TimeEntryEntity) =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ENTRY_ID, entry.id)
            putExtra(AlarmReceiver.EXTRA_LOCATION_ID, entry.locationId)
            putExtra(AlarmReceiver.EXTRA_MESSAGE, entry.message)
        }

    // 次回の発火時刻（ミリ秒）を計算する。
    // 指定時刻がすでに過ぎている場合は翌日の同時刻を返す。
    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
