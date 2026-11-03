package com.banishwor.relayprobe;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

/**
 * Test 4's worker: a plain API 22 foreground service with a persistent
 * notification and a 30-second heartbeat persisted to SharedPreferences.
 *
 * No WAKE_LOCK is held on purpose — how the heartbeat behaves under stock
 * Android 5.1 power management (and Funtouch OS background rules) is part
 * of what this probe exists to learn. START_STICKY asks the system to
 * recreate the service if it is killed for memory, which on a 1 GB phone
 * is a realistic event, not an edge case.
 */
public class HeartbeatService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final long HEARTBEAT_MS = 30_000L;

    /** Set while this process hosts the service; the UI cross-checks it
     *  against the last-beat age, which stays meaningful after process death. */
    static volatile boolean runningInThisProcess = false;

    private HandlerThread thread;
    private Handler handler;

    private final Runnable beat = new Runnable() {
        @Override
        public void run() {
            ProbeState.recordBeat(HeartbeatService.this);
            if (handler != null) {
                handler.postDelayed(this, HEARTBEAT_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        runningInThisProcess = true;
        thread = new HandlerThread("relay-probe-heartbeat");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ProbeState.bump(this, ProbeState.KEY_SERVICE_STARTS);
        startForeground(NOTIFICATION_ID, buildNotification());
        // Beat immediately so a fresh start is visible at once, then every 30 s.
        handler.removeCallbacks(beat);
        handler.post(beat);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        runningInThisProcess = false;
        if (handler != null) {
            handler.removeCallbacks(beat);
        }
        if (thread != null) {
            thread.quit();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, 0);
        return new Notification.Builder(this)
                .setContentTitle("Relay Probe — service running")
                .setContentText("Heartbeat every 30 seconds")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }
}
