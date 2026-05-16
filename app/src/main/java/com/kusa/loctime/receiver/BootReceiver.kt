package com.kusa.loctime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = AppDatabase.getInstance(context).timeEntryDao().getAllEnabled()
                entries.forEach { AlarmScheduler.schedule(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
