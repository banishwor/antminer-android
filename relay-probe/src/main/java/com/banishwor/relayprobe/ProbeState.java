package com.banishwor.relayprobe;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * All probe state lives in SharedPreferences so it survives process death,
 * reboots and Recents clears — that survival is exactly what Test 4 measures.
 * commit() (not apply()) is used for heartbeat writes: the counters are the
 * evidence, and they must be on disk before any sudden kill.
 */
final class ProbeState {

    static final String PREFS = "probe_state";

    static final String KEY_SERVICE_STARTS = "service_starts";
    static final String KEY_BOOT_STARTS = "boot_starts";
    static final String KEY_BEATS = "beats";
    static final String KEY_LAST_BEAT_MS = "last_beat_ms";
    static final String KEY_TLS_RESULT = "tls_result";
    static final String KEY_VAULT_RESULT = "vault_result";

    private ProbeState() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void bump(Context ctx, String key) {
        SharedPreferences p = prefs(ctx);
        p.edit().putLong(key, p.getLong(key, 0L) + 1L).commit();
    }

    static void recordBeat(Context ctx) {
        SharedPreferences p = prefs(ctx);
        p.edit()
                .putLong(KEY_BEATS, p.getLong(KEY_BEATS, 0L) + 1L)
                .putLong(KEY_LAST_BEAT_MS, System.currentTimeMillis())
                .commit();
    }

    static String formatTime(long epochMs) {
        if (epochMs <= 0L) return "never";
        return new SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(new Date(epochMs));
    }
}
