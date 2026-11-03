package com.banishwor.relayprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * Relay Lite — Step 1 probe.
 *
 * One screen, four tests, each with a persisted PASS/FAIL line so results
 * survive the reboots and Recents clears the tests themselves provoke:
 *
 *   1. Install &amp; launch on this API level (implicit — you are reading it).
 *   2. TLS: HTTPS GET to the production Apps Script endpoint from stock
 *      Android 5.1.1 (old CA store / TLS stack is the known risk).
 *   3. Credential vault: RSA-wrapped AES key design for API 22.
 *   4. Foreground service with 30 s heartbeat + BOOT_COMPLETED restart.
 *
 * Gate A: all four green → API 22 becomes Relay Lite's floor.
 */
public class MainActivity extends Activity {

    // The production Apps Script endpoint — same DEFAULT_WEBHOOK_URL the
    // app ships in CloudSyncConfig. Read-only GET (?user=Master) only;
    // this probe never POSTs to production.
    private static final String ENDPOINT =
            "https://script.google.com/macros/s/AKfycbyHfUeMDm_pnG_PzIeakyxFuWR1QtQIMGwuxj5y26QC_Vhf0i8H-u0EdngMXsDqGTyX1Q/exec";

    private TextView tlsText;
    private TextView vaultText;
    private TextView svcText;
    private TextView logText;
    private Button tlsButton;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshServiceStatus();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView header = (TextView) findViewById(R.id.headerText);
        header.setText(Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nRelay Probe v" + BuildConfig.VERSION_NAME);

        TextView test1 = (TextView) findViewById(R.id.test1Text);
        test1.setText("Test 1 — Install & launch: PASS — app running on API "
                + Build.VERSION.SDK_INT);

        tlsText = (TextView) findViewById(R.id.tlsText);
        vaultText = (TextView) findViewById(R.id.vaultText);
        svcText = (TextView) findViewById(R.id.svcText);
        logText = (TextView) findViewById(R.id.logText);
        tlsButton = (Button) findViewById(R.id.tlsButton);

        String savedTls = ProbeState.prefs(this).getString(ProbeState.KEY_TLS_RESULT, null);
        tlsText.setText(savedTls != null ? savedTls : "Not run yet");
        String savedVault = ProbeState.prefs(this).getString(ProbeState.KEY_VAULT_RESULT, null);
        vaultText.setText(savedVault != null ? savedVault
                : (Vault.hasSavedSecret(this) ? "Secret saved — not verified yet" : "Not run yet"));

        tlsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTlsTest();
            }
        });
        findViewById(R.id.vaultSaveButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runVaultSave();
            }
        });
        findViewById(R.id.vaultVerifyButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runVaultVerify();
            }
        });
        findViewById(R.id.svcStartButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, HeartbeatService.class));
                log("Service start requested");
                refreshServiceStatus();
            }
        });
        findViewById(R.id.svcStopButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, HeartbeatService.class));
                log("Service stop requested");
                refreshServiceStatus();
            }
        });

        log("Probe opened on API " + Build.VERSION.SDK_INT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServiceStatus();
        uiHandler.post(tick);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(tick);
        super.onPause();
    }

    // ── Test 2: TLS ─────────────────────────────────────────────────────

    private void runTlsTest() {
        tlsButton.setEnabled(false);
        tlsText.setText("Running…");
        log("TLS test started");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    URL url = new URL(ENDPOINT + "?user=Master");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    boolean tlsStack = conn instanceof HttpsURLConnection;
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "RelayProbe/0.1.0 (API "
                            + Build.VERSION.SDK_INT + ")");

                    int code = conn.getResponseCode();
                    InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    byte[] body = readAll(in);
                    conn.disconnect();

                    String minersNote;
                    try {
                        JSONObject json = new JSONObject(new String(body, "UTF-8"));
                        JSONArray miners = json.optJSONArray("miners");
                        minersNote = miners != null
                                ? "JSON OK — miners array: " + miners.length()
                                : "JSON OK — no miners array";
                    } catch (Exception parseError) {
                        minersNote = "body is not the expected JSON";
                    }
                    result = (code >= 200 && code < 300 ? "PASS" : "FAIL")
                            + " — HTTP " + code + ", " + body.length + " bytes, "
                            + minersNote + (tlsStack ? ", via HttpsURLConnection" : "");
                } catch (Exception e) {
                    result = "FAIL — " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final String finalResult = result;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tlsButton.setEnabled(true);
                        tlsText.setText(finalResult);
                        ProbeState.prefs(MainActivity.this).edit()
                                .putString(ProbeState.KEY_TLS_RESULT, finalResult).commit();
                        log("TLS test: " + finalResult);
                    }
                });
            }
        }, "relay-probe-tls").start();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    // ── Test 3: vault ───────────────────────────────────────────────────

    private void runVaultSave() {
        vaultText.setText("Saving…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    Vault.saveDummySecret(MainActivity.this);
                    result = "Saved — wrapped key + IV + ciphertext in SharedPreferences. "
                            + "Now tap \u201CForget memory & verify\u201D.";
                } catch (Exception e) {
                    result = "FAIL — " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final String finalResult = result;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        vaultText.setText(finalResult);
                        log("Vault save: " + finalResult);
                    }
                });
            }
        }, "relay-probe-vault-save").start();
    }

    private void runVaultVerify() {
        vaultText.setText("Verifying…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    // Vault is stateless: this re-reads SharedPreferences and
                    // the AndroidKeyStore from scratch — nothing is cached
                    // in memory from the save call.
                    boolean ok = Vault.verifyDummySecret(MainActivity.this);
                    result = ok
                            ? "PASS — secret decrypted from storage alone and matched"
                            : "FAIL — decrypted value did not match";
                } catch (Exception e) {
                    result = "FAIL — " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final String finalResult = result;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        vaultText.setText(finalResult);
                        ProbeState.prefs(MainActivity.this).edit()
                                .putString(ProbeState.KEY_VAULT_RESULT, finalResult).commit();
                        log("Vault verify: " + finalResult);
                    }
                });
            }
        }, "relay-probe-vault-verify").start();
    }

    // ── Test 4: service status ──────────────────────────────────────────

    private void refreshServiceStatus() {
        SharedPreferences p = ProbeState.prefs(this);
        long starts = p.getLong(ProbeState.KEY_SERVICE_STARTS, 0L);
        long boots = p.getLong(ProbeState.KEY_BOOT_STARTS, 0L);
        long beats = p.getLong(ProbeState.KEY_BEATS, 0L);
        long lastBeat = p.getLong(ProbeState.KEY_LAST_BEAT_MS, 0L);

        boolean listed = isServiceListed();
        String running = (HeartbeatService.runningInThisProcess || listed) ? "RUNNING" : "not running";
        String age = lastBeat > 0L
                ? ((System.currentTimeMillis() - lastBeat) / 1000L) + " s ago"
                : "—";
        svcText.setText("Service: " + running
                + "\nStarts: " + starts + " (boot-triggered: " + boots + ")"
                + "\nHeartbeats: " + beats
                + "\nLast beat: " + ProbeState.formatTime(lastBeat) + " (" + age + ")");
    }

    private boolean isServiceListed() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if (HeartbeatService.class.getName().equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Fall back to the in-process flag only.
        }
        return false;
    }

    // ── Log ─────────────────────────────────────────────────────────────

    private void log(String message) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logText.append("[" + stamp + "] " + message + "\n");
    }
}
