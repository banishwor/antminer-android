package com.banishwor.antminerrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.banishwor.antminerrelay.storage.LiteRelayStorage
import com.banishwor.antminerrelay.storage.LiteState

/**
 * Boot restart, mirroring the :relay-probe receiver that passed Gate A
 * on the Y21L: count the boot-triggered arrival (persisted with
 * commit(), so the status screen can prove the receiver fired), then
 * start the relay service when the Relay switch is on and re-arm the
 * watchdog either way.
 *
 * On Funtouch OS this broadcast only reaches the app after the user
 * enables Autostart for Antminer Relay Lite in i-Manager — the
 * first-run checklist on the setup screen says exactly that.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        LiteState.bumpBootStarts(context.applicationContext)
        Watchdog.schedule(context.applicationContext)
        val storage = LiteRelayStorage(context.applicationContext)
        if (storage.loadCloudConfig().isSyncEnabled) {
            LiteRelayService.start(context.applicationContext)
        }
    }
}
