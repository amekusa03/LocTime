package com.kusa.loctime.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

// 通知チャンネルの作成と通知の表示を担うヘルパーオブジェクト。
object NotificationHelper {
    private const val CHANNEL_ID = "loctime_channel"
    private const val CHANNEL_NAME = "LocTime通知"

    // Android 8.0以上では通知チャンネルの事前登録が必要。アプリ起動時に呼び出す。
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // 位置情報権限が取得できなかった場合に表示する警告通知。
    // タップするとアプリの権限設定画面（システム設定）が開く。
    // ID に Int.MAX_VALUE を使うことで通常の時刻通知と競合しない。
    fun showPermissionNotification(context: Context) {
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("位置情報の権限が必要です")
            .setContentText("タップして権限を設定してください")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(Int.MAX_VALUE, notification)
    }

    // 場所・時刻の条件が合致したときに表示する通知。
    // setAutoCancel(true) でタップ時に自動消去。setTimeoutAfter で1分後に自動消去（Android 8.0以上）。
    // 通知ID には timeEntryId を使い、同一エントリの通知が重複しないようにする。
    fun showNotification(context: Context, id: Int, locationName: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(locationName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L) // 1分後に自動消去（Android 8.0以上のみ有効）
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
