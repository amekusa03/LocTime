package com.kusa.loctime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 端末再起動時に発火するブロードキャストレシーバー。
// AlarmManager のアラームは再起動でリセットされるため、起動時に有効な全アラームを再登録する。
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync() でブロードキャストの処理時間を延長する（DB読み込みに時間がかかるため）
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // isEnabled=true の全時刻エントリ（と場所のオフセット）を取得してアラームを再スケジュールする
                val entries = AppDatabase.getInstance(context).timeEntryDao().getAllEnabledWithOffset()
                entries.forEach { AlarmScheduler.schedule(context, it.entry, it.offsetMinutes) }
            } finally {
                pending.finish()
            }
        }
    }
}
