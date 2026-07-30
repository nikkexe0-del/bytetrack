# byte!track

Android data-usage + screen-time tracker with an iOS-style "Liquid Glass" UI. Black/orange pixel-bolt icon, Inter typography, Jetpack Compose throughout.

## What it does

1. **Data tracking** — per-app usage broken out by Wi-Fi / Mobile / Hotspot, polled every 60s by a foreground service so it keeps working in the background. Backed by `NetworkStatsManager`, the same system API Android's own Settings > Data Usage screen uses.
2. **Screen time tracking** — derived from `UsageStatsManager`'s raw foreground/background event stream (not the pre-aggregated totals, which are coarser), so sessions line up with the same timeline as data usage.
3. **Liquid Glass UI** — `GlassCard` (`ui/components/GlassCard.kt`) does a *real* backdrop blur via `RenderEffect` on Android 12+, with a translucent-scrim fallback on older versions. Spring-based scale + glow on press for the "fluid" feel. Rounded 18–32dp shape scale, real bundled Inter variable font (OFL-licensed, not a placeholder).
4. **byte!track** branding — black/orange pixel-art bolt icon, adaptive icon set for API 26+, legacy PNGs for older devices.
5. **CI** — `.github/workflows/android-build.yml` builds both debug and unsigned-release APKs on every push and uploads them as workflow artifacts; tagging a commit `vX.Y.Z` also cuts a GitHub Release with the APKs attached.

## Getting it running

```
git clone <your-repo>
cd byte-track
```

Open in Android Studio (Koala+ recommended) and let it sync — no wrapper jar is committed (kept the repo binary-free), so either:
- run `gradle wrapper` once locally to generate one, or
- just build via Android Studio, which bundles its own Gradle, or
- let CI build it — the workflow installs Gradle 8.9 directly.

Minimum SDK 26 (Android 8.0), target/compile SDK 34.

### The one permission it needs

`PACKAGE_USAGE_STATS` can't be granted through a normal runtime dialog — Android requires the user to flip it on manually under **Settings > Apps > Special app access > Usage access**. The app's `PermissionsScreen` detects this via `AppOpsManager` and deep-links straight to that settings page. One permission covers both the data tracker and the screen-time tracker.

You'll also get a standard `POST_NOTIFICATIONS` prompt on Android 13+ (needed to show the "tracking active" foreground-service notification — it's set to minimum importance so it won't buzz or badge).

## Architecture

```
data/local/       Room entities, DAOs, database (DataUsageSample, ScreenTimeSession)
data/repository/   NetworkUsageRepository, ScreenTimeRepository, PermissionsRepository, AppInfoRepository
service/            UsageTrackingService (foreground poll loop), BootReceiver
worker/             SyncWorker — WorkManager backup poll every 15 min in case the OS kills the foreground service
ui/theme/           Color.kt, Type.kt (Inter), Theme.kt, shape scale
ui/components/       GlassCard, Atoms (pills/legend dots), UsageTimelineChart (dependency-free Canvas chart)
ui/screens/         DashboardScreen, AppsScreen, PermissionsScreen
ui/                 MainViewModel, ByteTrackNavHost (floating glass tab bar)
```

## Honest limitations (please read before you ship this)

- **Hotspot tracking is an approximation.** Android does not expose a per-connected-device breakdown of who used your hotspot and how much. What this app *can* do accurately: detect when tethering is active and tag your own device's mobile-transport bytes as "Hotspot" for that window, so you can separate "data I used directly" from "data that left through my hotspot" — it is not a certified per-client metering tool.
- **60-second poll interval** while the foreground service is alive is a deliberate tradeoff between timeline resolution and battery. The WorkManager backup only guarantees a 15-minute floor if the OS kills the service (some OEM battery managers — Xiaomi/OnePlus/Oppo especially — are aggressive; you may need to disable battery optimization for byte!track manually for the foreground service to survive long background stretches).
- **`NetworkStatsManager.querySummary`** occasionally throws transient errors on some OEM ROMs; the repo swallows and retries next cycle rather than crashing, but it means a poll can silently be skipped once in a while.
- **Notification daily goals are hardcoded defaults** (2 GB data, 4 hours screen time) — there's no settings screen yet to customize them. `UsageTrackingService.DEFAULT_DAILY_DATA_GOAL_BYTES` / `DEFAULT_DAILY_SCREEN_TIME_GOAL_MS` are the constants to wire a settings screen into.
- Not yet wired: settings screen, per-app deep-dive/detail screen, weekly/monthly view toggle, data limit alerts. The Dashboard and Apps screens cover today's data; extending the date-range picker is straightforward given the DAO queries already take arbitrary `from`/`to` millis.

## Fixed since first draft

- **App names showing as `uid:XXXX` instead of real labels** — Android 11+'s package-visibility restrictions silently blocked `getPackagesForUid()` from resolving other apps unless declared. Added `QUERY_ALL_PACKAGES` to the manifest, which is the correct permission category for an on-device usage tracker (same class Play Console approves for this use case).
- **Screen time reading under 1 minute for everything** — the real bug: each poll only scanned events inside its own ~60s window, and the "currently open session" map was a fresh local variable every call. An app that had already been in the foreground before a poll's window started had no `MOVE_TO_FOREGROUND` event inside that window to anchor to, so continuous usage silently lost all its time past the first minute. Fixed by persisting the currently-foreground package across polls (SharedPreferences) and seeding each window with it.
- **Text unreadable on glass cards** — root cause was structural, not cosmetic: no screen was wrapped in a Compose `Surface`, so any `Text()` without an explicit color fell back to Compose's default `LocalContentColor` (black) regardless of the dark theme. Wrapped the whole app in a themed `Surface` in `MainActivity`, and switched `GlassCard`'s default tint from a light overlay to a dark scrim (`GlassScrim`) so legibility doesn't depend on what color the blur happens to sample from behind it.
- **No color-coding for Wi-Fi/Mobile/Hotspot** — added `appNetworkBreakdownBetween` DAO query, threaded per-network-type bytes through `AppRow`, and added `NetworkBreakdownBar` (stacked color bar) + `NetworkBreakdownLabel` (colored byte chips) components, used consistently across the dashboard hero stat, timeline legend, and every app row.
- **Notification** is now a persistent (`setOngoing(true)`, non-swipeable), custom `RemoteViews` layout with two live progress bars — data usage and screen time against daily goals — refreshed every poll cycle, silent (`setOnlyAlertOnce(true)`, `IMPORTANCE_LOW`) so it doesn't buzz on every 60s update.
- **Footer credits** added (`CreditsFooter` component) — app icon on the left, "built and maintained by Nikshep Doggalli" / Instagram / portfolio link on the right, tappable through to the actual URLs. Sits as the last item in both the Dashboard and Apps scrollable lists.

## Next steps if you want to keep building

- Add a `TimelineScreen` with a date-range selector (the `hourlyBuckets` DAO query already takes arbitrary bounds)
- Add per-app tap-through detail (network breakdown pie via `networkBreakdownForApp`)
- Wire real device-frame testing on API 26 vs API 34 — RenderEffect blur only activates on 31+, worth checking the fallback actually looks good on your test devices
- Add app icons to the Apps list rows (currently just labels) via `PackageManager.getApplicationIcon`
