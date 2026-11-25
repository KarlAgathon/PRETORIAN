# PRETORIAN — Extended Diagnostics II · Implementation report

- Baseline: Stable **3.0.0.6** (versionCode 306), commit `9fa367c` (verified as the latest release `v3.0.0.6`).
- Branch: `diagnostics-ii`. New version: **3.0.0.7** (versionCode 307). Final commit: see the release tag `v3.0.0.7`.
- Scope: diagnostics infrastructure only. No change to playback, navigation, UI or design.

## Files

New: `DiagII.kt` (collectors), `DiagLife.kt` (sessions, updates, exits, exports), `DiagReportII.kt` (sections 51–70),
`app/src/test/.../DiagII3007Test.kt`, this report.
Modified (observation hooks only): `Player.kt` (second analytics listener, playback type label, track-probe label),
`DiagCollect.kt` (network/nav/image hooks), `Metrics.kt` (16 bounded tables), `Upkeep.kt` (memory row, image summary,
maintenance timing), `App.kt` (start, frame metrics, key timing, trim, image HTTP observer), `Data.kt` (HTTP helper timing,
live list quality), `Meta.kt` (HTTP helper timing, subtitle download timing, encoding fallback count), `CatalogDb.kt`
(import quality, transaction outcome, search/EPG timing, migration, errors), `Backup.kt` (outcome timing), `ChannelHealth.kt`
(cooldown and prevented-request hooks), `Ui.kt` (focus failure count), `Stores.kt` (`Watch.stats` counts), `Secure.kt`
(bare IPv4 server masked in links), `Diagnostics.kt` (summary on top, sections 51–69, export/QR outcome, build commit),
`build.gradle.kts` (version, `GIT_COMMIT` build field), `tools/i18n_keep.txt` (table headers).

## Architecture

- Every collector runs inside `DiagCollect.guard`: its first error switches only that collector off (listed in section 49/70).
- Storage: 16 new CSV tables through the existing `PerfLog` writer (one low-priority thread, bounded queue, free-space floor).
  Each new table is 96–256 KB plus one older copy (about 5 MB worst case for all), inside the unchanged 50 MB ceiling.
- Every row carries the session id; the `sessions` table maps session → version, versionCode, build commit. Rows recorded
  before 3.0.0.7 have no mapping and stay "version unknown" (never back-assigned).
- Sampling: memory every 5 min with the PSS the monitor already takes (graphics every 30 min); buffer only on existing
  player events, at most every 2 s; frames aggregated per screen (no frame stored on its own).

## Status per section

| § | Title | Status | Notes |
|---|---|---|---|
| 51 | Stream connection intelligence | IMPLEMENTED (partial for sockets) | Session/stream/request ids, open reasons, request start/complete/cancel/fail, active/peak, redirects, Media3 retries, recovery, overlaps between players with duration, close→open gap, preview↔fullscreen gaps, cooldowns, prevented requests, 458/429/509. Socket lifecycle: API LIMITED. Provider live count: NOT SUPPORTED (would need a request). |
| 52 | Advanced network | PARTIALLY IMPLEMENTED | Playback: duration, bytes, throughput, status, errors. App requests (HttpURLConnection): total + time to first response, status, bytes, errors, redirects. Images (OkHttp): DNS, connect, TLS, TTFB. DNS/TCP/TLS for playback and app requests: API LIMITED. Wi-Fi signal/link: API 29+. IP family per request: API LIMITED. |
| 53 | Extended VPN | IMPLEMENTED (as observable) | Time per configuration, activations, interruptions, reconnections, QoE/throughput per config, rebuffering after transitions. Provider/protocol/location/kill switch/split tunnel/routing: NOT SUPPORTED. |
| 54 | Playback QoE | IMPLEMENTED | Per type (TV, preview, movie, series, catch-up, track probe), per scope (session, version, history); seeks excluded; denominators printed. Trailers play outside the app: NOT OBSERVED. |
| 55 | Buffer intelligence | IMPLEMENTED | Configured profiles, event-driven samples, min/max/time-weighted mean, state at failure, throughput/bitrate ratio, suspected causes labelled. |
| 56 | Video decoder | IMPLEMENTED | Decoder used + hw/sw, init time, reinit, release, observed fallback, format/HDR changes, drops, failure correlation table. Maximum consecutive drops: per Media3 report batch (API LIMITED). |
| 57 | Audio | IMPLEMENTED (as observable) | Decoder, source format, configured output (PCM vs bitstream), underruns, sink/codec errors, track/language changes, focus loss, device-reported encodings. A/V sync, actual passthrough: NOT SUPPORTED. |
| 58 | Subtitles | PARTIALLY IMPLEMENTED | Offered/selected formats and languages, embedded vs external, download time/outcome, encoding fallback, load failures. Media3 parse errors separately: API LIMITED. |
| 59 | UI rendering / jank | IMPLEMENTED (Android 7+) | FrameMetrics per screen with real refresh rate; first drawn frame after navigation (Android 8+); key → next frame. |
| 60 | Remote and navigation | PARTIALLY IMPLEMENTED | Focus target missing, retries, navigation landing elsewhere. Unexpected BACK and scroll-restore failures: no dedicated hook (TEST REQUIRED). |
| 61 | Memory leak detection | IMPLEMENTED | Heap/native/PSS/RSS/graphics/threads/FDs/GC/RAM/cache every 5 min; player create/release deltas; idle comparisons at 1/6/12/24/72 h/7 d of uptime; suspected-growth rules separate cache growth. |
| 62 | Database integrity | IMPLEMENTED (passive) | Sizes, error classes, rollbacks, migration, import/EPG/search/backup/restore/maintenance timing, record counts (skipped during playback). No automatic integrity check or repair. |
| 63 | Playlist quality | IMPLEMENTED | Xtream catalog (movies/series), Xtream live lists, M3U: items, categories, duplicates, invalid, missing optional fields, duration, previous count. Per-item add/update/remove: NOT SUPPORTED (whole replace). |
| 64 | Device and firmware | IMPLEMENTED | Model, Android/API/patch, firmware and change detection, SoC (API 31+), ABIs, process bitness, RAM, low-RAM, GL ES, Vulkan, Media3, display modes/HDR, storage. GPU renderer: NOT SUPPORTED without a graphics context. |
| 65 | Application exit | IMPLEMENTED (API 30+) | Deduplicated, classified; ambiguous stays ambiguous; previous session/version for the newest exit only. Below Android 11: NOT SUPPORTED. |
| 66 | Update and migration | IMPLEMENTED | Previous/new version and code, commit, first screen time, device ID kept, aggregate count verification. Preference migration: not separately instrumented. APK integrity: not claimed. |
| 67 | Technical settings snapshot | IMPLEMENTED | Explicit allow-list, configured vs actual. |
| 68 | Version comparison | IMPLEMENTED | Current vs previous version on the same device, sample counts, same state/network, % only with enough samples, rates only with ≥2 h on both sides. First comparison possible after the next update. |
| 69 | Export and QR security | IMPLEMENTED | Audit text, export duration/size/outcome/SHA-256 prefix, integrity check, QR shown/failed, file served; IPv4 servers masked; regression tests. |
| 70 | Summary | IMPLEMENTED | Top of the report, deterministic rules, status per area, findings with section, cause, confidence. |

## Compatibility

minSdk 24 unchanged. Every API above 24 is guarded (VSYNC 26, Wi-Fi transport info 29, exit reasons 30, SoC 31).
Fire TV Stick 2017 (Fire OS 6, Android 7.1) runs every collector except the Android 8+ frame timestamp and the Android 10+/11+
items, which report NOT SUPPORTED.

## Verification

- CI: translation check, 97 unit tests (7 new), unsigned release build — success.
- Zero additional provider traffic: all network hooks sit inside existing request code; no new request, connection, player
  or poll is created (code review). Not yet verified with a network capture on a device.
- Physical device tests: NOT PERFORMED here (no device access). CI is not device validation.
- Overhead: not measured on a device; sampling chosen conservatively.

## Rollback

Install 3.0.0.6 from Settings › Updates › Previous version. The new tables are ignored by older versions; nothing else changes.

## Remaining risks

Untested on real boxes; frame-metrics listener cost on the weakest box unknown; HTTP timing hooks wrap existing helpers
(exceptions are re-thrown unchanged).
