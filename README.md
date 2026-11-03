# Antminer — Monitor Your Miners From Your Phone

A native Android app (Kotlin + Jetpack Compose) for monitoring Bitmain Antminer ASIC miners from your phone — live hashrate, temperatures, fans, chip health, pools and logs when you're on the same Wi-Fi, and a cloud relay so you can check your miners from anywhere.

**Current release: v0.4.0** — see [Releases](https://github.com/banishwor/antminer-android/releases).

---

## ⚠️ Disclaimer

**This is an unofficial, personal project. It is NOT an official Antminer or Bitmain product.**

I am **not affiliated with, endorsed by, or sponsored by Bitmain** or the Antminer brand. "Antminer" is a trademark of Bitmain Technologies Ltd.; this app only uses the name to describe what hardware it works with. Use it at your own risk.

## Why I made this

Checking a miner normally means sitting down at a computer and opening the miner's web page. I wanted something handier: open an app on my phone and see, at a glance, whether my miners are hashing, overheating, or offline — at home or away. So I built this for my own use, and I'm sharing it in case it helps other small miners too.

— Banishwor Athokpam

---

## What it does

### One combined fleet
- **Local + remote in a single list.** Miners you're connected to on this Wi-Fi and miners you (or people who share with you) relay to the cloud appear together in one fleet, ranked by hashrate, offline machines last.
- The same machine always appears **once** — local and cloud copies are matched by the machine's pool worker name, and live local data wins over the cloud snapshot.
- Offline remote miners show when they were last updated.

### Live monitoring (on the miner's network)
- **Home** — a summary hero for the selected miner (status, live hashrate + average, uptime, one health indicator) over the ranked fleet list.
- **Hardware** — per-board detail: chip and PCB temperatures, fan RPMs, and the full ASIC chip matrix.
- **Pools** — pool connections and share counts (live locally; primary pool when remote).
- **Logs** — the miner's live log on location, plus a daily-log (.tar) diagnostic analyzer.
- **Threshold alerts** — high temperature, fan failure, hashrate drop, missing chains, stale shares.
- Local and remote detail are the **same full detail** — everything is uploaded to the cloud. Only log text stays on location (it's never uploaded).

### Auto-connect — known miners only
- The app **never scans your network**. It only reconnects to miners it already knows: ones you added, and ones in your cloud fleet.
- It tries your saved password first, then the factory default, and verifies the machine's worker identity before claiming it — a different known machine at the same IP is recognised as itself, and unknown machines are never added.
- Auto-connect runs when Wi-Fi connects, when the app opens, on each cloud sync, and inside the background relay.
- A miner that needs its password shows up as a tappable **"needs password"** card — enter it once and it auto-connects from then on.

### Cloud relay (check from anywhere)
- One **Relay** switch (on by default). When it's on, the app uploads your miners' full telemetry to a Google Sheet through a small Apps Script endpoint, and a background service keeps uploading 24/7.
- Viewing is separate from relaying — you can use the app just to view (your own miners, ones shared with you, or everyone's under a master name) without uploading anything.
- A relay name can be a **master** name (default: `Master`) that sees everyone's miners; other users see their own plus any shared with them.
- Rows are keyed by worker name with upsert, so the sheet never grows duplicates. Setup and wire format: [`docs/apps-script/`](docs/apps-script/).
- **Cross-user safety:** a machine belonging to another relay user is fully usable locally, but is never uploaded under your name.

### Relay-only mode
- For a cheap spare phone that lives at the miner location: flip **Relay only** in Settings → Relay & Cloud and the app becomes a single status screen — who it's relaying as, each miner's upload state, last successful upload, errors, auto-connect state, and one Exit button.
- Background relaying, auto-connect and uploads work exactly as in full mode; the update checker stays quiet so nothing pops up on an unattended phone.

### Nicknames that stay on your phone
- Give any machine in the fleet a nickname — even someone else's — with the pencil on its row or detail header.
- Nicknames are stored **only on your phone**, keyed by worker name. They are never uploaded; the sheet always keeps the real worker name.

### Always improving the small things
- **Skeleton loading** — on cold start, Home and the relay-only screen show skeleton placeholders until the first real data lands, instead of flashing empty or misleading states.
- **In-app updates** — the app checks GitHub Releases on open (and manually in Settings → App Updates), including prereleases. When a newer version exists, tap **Download** in the update dialog: the APK downloads inside the app with a progress bar (cancellable), then **Install Now** hands it to the system installer — no browser round-trip. If Android asks you to allow installs from this app first, the dialog sends you to that one switch; allow it and tap Install again. In relay-only mode the checker stays completely quiet.

## Appearance

- **Violet** — the default style. Light theme on a soft lavender-white, and a dark theme with true AMOLED black (`#000000`) backgrounds.
- **Glass** — the "Grouped Sheets" design: content sits in flat, rounded panels with hairline borders on a grouped grey base (`#F2F2F7` in Light; true `#000000` with `#1C1C1E` panels in Dark), Apple blue (`#007AFF` / `#0A84FF`) as the only accent, iOS-style switches and segmented controls, and status carried by labels plus monochrome treatment. The only glass left is the floating capsule dock at the bottom — on low-RAM phones it automatically falls back to a lighter version with no live blur, so cheap relay phones stay smooth. The relay-only screen uses the same grouped design in a fully opaque form.

## Screens

| Tab | What it shows |
| :--- | :--- |
| **Home** | Fleet summary hero + ranked fleet list (local and remote combined) |
| **Hardware** | Boards, chip matrix, chip/PCB temperatures, fan RPMs for the selected miner |
| **Pools** | Pool URLs, workers, status and shares for the selected miner |
| **Logs** | Live miner log (on location) and daily-log analysis |
| **Settings** | Appearance, Relay & Cloud (incl. Relay only), cloud setup, miner profiles, app updates |

## Download & install

1. Get the APK from [GitHub Releases](https://github.com/banishwor/antminer-android/releases) (one APK per release).
2. Install it on your phone (you may need to allow installs from your browser/files app).
3. Open it, pick your relay name, and connect to your first miner with **Add a miner**.

Works with Antminers that expose the standard CGI API (`/cgi-bin/stats.cgi` etc.) — developed and tested mainly with the **S19k Pro** on stock firmware.

## Privacy & security notes

- Miner passwords are stored **encrypted on the phone** and are only ever sent to the miner itself.
- Miners serve plain HTTP on the local network (that's how their firmware works), so local traffic is cleartext HTTP, limited to private network ranges. Cloud uploads always go over HTTPS.
- The cloud sheet holds miner telemetry (hashrate, temperatures, fans, pool info) — no passwords are ever uploaded.

## Building from source

```bash
git clone https://github.com/banishwor/antminer-android.git
cd antminer-android
./gradlew assembleDebug
```

- JDK 21, Android SDK (minSdk 26).
- The test suite (208 unit tests: parsers, relay/sync logic, fleet merging, UI state rules) runs with `./gradlew testDebugUnitTest`.

## Project layout

- `app/` — the Android app (single activity, Compose UI, one `DashboardViewModel` state flow).
- `docs/apps-script/` — the cloud relay endpoint contract + reference Apps Script (`Code.gs`).
- `logs examples/` — sample Antminer diagnostic archives used by the log analyzer tests.
