package com.banishwor.relayprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Test 4's restart path: when the phone finishes booting, start the
 * heartbeat service. The boot-triggered start is counted separately in
 * SharedPreferences so the probe screen can prove the receiver fired
 * (a reboot that raises "Boot starts" and "Service starts" and resumes
 * the beat counter is a pass; Funtouch OS may still block it, which is
 * also a result worth knowing).
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ProbeState.bump(context, ProbeState.KEY_BOOT_STARTS);
            context.startService(new Intent(context, HeartbeatService.class));
        }
    }
}
