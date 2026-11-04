package com.banishwor.antminerrelay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.banishwor.antminerrelay.storage.LiteRelayStorage
import com.banishwor.antminerrelay.storage.LiteState

/**
 * The old-Android watchdog: an AlarmManager inexact repeating alarm
 * (the API 22-appropriate mechanism — no JobScheduler, no WorkManager)
 * that checks the relay service is alive and restarts it if not.
 *
 * Liveness is judged from [LiteState]: the persisted running flag, plus
 * the freshness of the engine's status reports. A running engine
 * reports every 30 s cycle (standby states included), so a status
 * older than [STALE_MS] while the flag says "running" means the
 * process died without onDestroy, or the loop wedged — either way a
 * fresh start is the remedy.
 *
 * The hard limit, by Android design: after the user Force-stops the
 * app, NOTHING in it runs — no alarms, no receivers — until the app is
 * opened once by hand. The watchdog cannot and does not try to defeat
 * that; the release notes say so plainly.
 */
object Watchdog {

    /** How old the last engine status may be before the service counts as wedged. */
    const val STALE_MS = 10 * 60 * 1000L

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WatchdogReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** The watchdog's alarm target: revive the service when the relay is on but the service isn't healthy. */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val storage = LiteRelayStorage(context.applicationContext)
        if (!storage.loadCloudConfig().isSyncEnabled) return

        val running = LiteState.isServiceRunning(context)
        val (_, _, statusUpdatedMs) = LiteState.loadStatus(context)
        val stale = statusUpdatedMs > 0L &&
            System.currentTimeMillis() - statusUpdatedMs > Watchdog.STALE_MS

        if (!running || stale) {
            LiteRelayService.start(context)
        }
    }
}
