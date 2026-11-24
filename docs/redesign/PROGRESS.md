# PRETORIAN Settings redesign – progress (A0–H)

Source of truth: *PRETORIAN – Settings Redesign MASTER SPECIFICATION* (Claude Doc, owner-approved 2026-11-23).
Protected baseline: Stable 2.6.0 – `main` @ `cfcb8c3` (untouched). Development branch: `redesign/a0-h`.
Checkpoints are branches `checkpoint/<phase>` that are never moved (tags cannot be pushed from the development session).

## Rules
- Order A0 → A → B → C → D → E → F → G → H; stop after Beta 1 (A0 + A) until the owner confirms the Mi Box test.
- Beta = package `com.pretorian.tv.beta` (own data, no Stable updates, no start with the box, backups `MediaCenterBeta-backup-*` in `Download/MediaCenter Beta`).
- Nothing is marked "tested on the Mi Box" unless the owner tested it. No merge into `main`, no Stable release without separate approval.

## Phases

| Phase | Commit | Checkpoint | CI (unit tests, release + beta build, i18n) | Mi Box |
| --- | --- | --- | --- | --- |
| Setup – beta package, beta workflow | `8fcdbf9`, `b9bd0d3` | – | green | – |
| A0 – Diagnostics foundation & QR export | `4ed0849` + fix `05da534` | `checkpoint/A0` = `05da534` | green (DiagnosticsTest 6 tests) | NOT TESTED |
| A – Settings frame | `c835310` | `checkpoint/A` = `c835310` | green (SettingsNavTest 9 tests) | NOT TESTED |

## A0 – what changed
- Diagnostics › "Izvezi izvještaj": cleaned TXT report → one-time QR download from the existing PhoneLink server (landing page + one download, 5 min, max 5 bad requests, stops on Back/close/done), fallback "Spremi u folder" (existing folder picker).
- `Redact`: Xtream links without prefix, `Authorization`/`Bearer`, Stalker `sn`/`device_id`/`signature`, Google API keys; `SecretScan`: the box's real secret values are replaced, the export is refused if that cannot be confirmed.
- `DiagEvents`: last 200 events (player errors, playlist/EPG failures, crashes), redacted on entry; appended to crash reports.
- `DiagSpace`: no diagnostic writing below 20 MB free. CrashGuard, the 10-report archive and the playlist QR form are unchanged.

## A – what changed
- `SettingsNav.kt`: page stack per Settings copy on the app stack; Back = one level; focus key + scroll kept per entry; deep links open the full path (update → Sistem › Ažuriranje, Back via Sistem); a page opened from Settings search returns to the results.
- Main list: Traži postavku + 8 categories (Playliste, Reprodukcija, Izgled i jezik, Početna i navigacija, Roditeljska kontrola, Sport, Sistem i održavanje, Obavještenja i podsjetnici). App rail hidden in Settings. No two-pane layout, no Basic/Advanced mode.
- `SettingsKit.kt`: `SetList` (LazyColumn, stable keys, focus-following scroll with half-row margin), `SetNavRow`, `SetConfirm` (focus on Odustani), `SetActionPair`; `SetRow` restyled (20/15 sp, 68 dp, pale focus pill, no boxes); compact tier below 540 dp.
- Existing pages run unchanged inside the frame (Playlists page and its popup actions unchanged until Phase B).
- Plejer › "Više opcija": external player, subtitle/audio language order, file manager (moved unchanged from the old "Napredno").

## Known limitations / open
- Checkpoints are branches, not annotated tags (tag push is blocked by the session proxy).
- Focus, scroll and Back behaviour can only be confirmed on the Mi Box (no UI tests yet).
- Old-style controls inside pages (switches, drop-downs, sliders) keep their 2.6.0 look until phases E–G.

## Beta 1 (A0 + A)
- Built from `28de23c` (branch `redesign-beta/1`), pre-release `redesign-beta-1` (not latest; Stable's updater ignores pre-releases), file `MediaCenterBeta-1.apk`.
- APK check (static): package `com.pretorian.tv.beta`, label "Media Center Beta", versionName `2.6.0-beta.2`, versionCode 900002, FileProvider `com.pretorian.tv.beta.files` (Stable: `com.pretorian.tv.files`), same signing certificate as Stable → installs next to Stable with separate data.
- Side-by-side install on the Mi Box: NOT TESTED (owner).

## Next
Beta 1 (A0 + A, built from branch `redesign-beta/1`) → owner Mi Box test → only then Phase B.

## Beta 2 (24.11.) — fixes 1–11 + phases B, C, D
- Fixes: U kinima (DE premiere ≤ 60 days, no re-releases, row hidden when empty); Nedavno dodano kept until opened or cleared (long OK › Očisti), bell deletes only notices; O uređaju grid; blur option removed; Grupe i kanali file-manager editor; subtitle offset window (first row of Titlovi) and per-title subtitle memory; channel status lamps (green / yellow / not checked) + light background check; live Više menu (6 rows); weather by postal code, nearest first.
- B: Playlists overview / detail (Informacije o izvoru, Uredi, Aktivna switch, Osvježi, EPG, Obriši), Promijeni redoslijed, RefreshCenter (pending 12 h, waits during playback), pl_check_<id>, local M3U copy (64 KB), form: Sačuvaj, Više opcija, MAC empty for new, activation switch, leave-with-changes question.
- C/D: playlist EPG menu (sources, Vremenski pomak, Osvježi EPG); offsets P (playlist) + channel override, migration marker epg_off_v2.
- Not yet: TV groups art (waiting for image), D2 "Pronađi kanal" + gchan index, multi-pid EPG assignment UI.

## 2.8.0 (24.11.) — diagnostics, reliability, completion
- Diagnostics: DiagStore (journal current.log + gzip segments, incidents with attached app log; 100 incidents / 10,000 events / 90 days / 50 MB / 100 MB free floor), ApplicationExitInfo (ANR, native), crash dialog with direct export, exports LATEST/FULL/ZIP via QR, PhoneLink sessions (atomic key, monotonic expiry, per-session stop, VPN/tunnel interfaces excluded), Redact + SecretScan extended.
- Channel health: SHA-256 keys, stale kept, playback evidence wins 30 min, files/health.json batched, scheduler focused→neighbours→rest, classification (auth/limit/no internet/outage = inconclusive).
- Find Channel (epgmap_<pid>_<key>), screensaver (H) with cached speeds, storage + 3 resets (G), notes master + quiet (F), connection timeout + subtitle behaviour + playback reset (E), help text, rollback checks.
- Not done: TV groups picture (no image supplied); E–G items still open: background/contrast/animation/clock-format options, temporary parental unlock, full Diagnostics 4-entry page, Serbian Latin.

## 2.9.0 (24.11.) — engineering audit, testing candidate
- Work.kt: app state (startup, playback incl. preview/paused, background, low memory, offline, safe mode, idle); heavy jobs (GuideSync, Upkeep, speed profile, RefreshCenter, channel checks) wait; one heavy job and one speed test at a time; network callback; safe mode auto-exit after 3 min.
- HealthLamp derivedStateOf per row; TV groups illustration (pretorian_tv_set.webp, transparent); streaming tiles without white plate.
- Removed: PlaylistsListV2, PlMenu, ToolBtn, CheckBox, mc_mark.png, ic_fill_* (4), shirt_fill.
- Not measured on a device: startup, CPU, RAM, first-frame times (pending the owner's box).

## 2.9.1 (24.11.) — gentle checks, E–G completed, testing version (feature freeze after this)
- ChannelHealth: focused ±6 only, 4.5 s gap, 60/h budget, LIMIT stops checks until restart, results 6 h.
- Player: VOD recovery resumes at the last position (position read before stop); "start" event with time to first frame per channel/film.
- E–G done: LookOpts (darker overlay, high contrast greys, motion off/subtle incl. Android animator scale, clock Auto/24/12, clock and date on/off), Lock.allOpen temporary unlock + relock on onStop.
- Ai keys sealed with Secret (old gemini_key migrated); SecretScan checks sealed and plain values.
- RefreshCenter.resume waits for the end of STARTUP; exit reasons for update/user exit logged as info; readable screen name.
- Not measured on a device: first-frame times are now logged; values wait for the owner's reports.

## 3.0.0 (24.11.) — Stable edition per 3.0 master directive; feature freeze after this (3.0.x = evidence-based fixes)
- §4 ProbeGuard: passive default (ch_probe_active=false), 30/rolling h per provider host hash, 6 s spacing, redirects counted, persisted stamps + cooldowns (pg_*), clock-back conservative, LIMIT/AUTH → 24 h cooldown (Retry-After honoured), probe socket closed when preview/player opens, 3 s settle, only Work IDLE.
- §5/6/9/10: zero-position guard in saveProgress, ENDED-early not marked done / no Auto Next, resume asked vs actual logged, first-frame vs READY via PerfLog. Track prefs already language/label based (verified).
- §8 LogoResolver (iptv-org logos.json + channels.json, streamed, compact TSV index, 14-day refresh via Work.heavy, strict country/uniqueness match) + LogoTrim (transparent margins only) used by TV list, history row, Home tiles, Onyx channel logo.
- §13–16 PerfLog (metrics/*.csv, 512 KB + 1 rotation each, inside diag ceiling), Work.heavy task rows, incident dedupe by signature (10 min), Full Diagnostic Report ZIP (incidents before events, 40 MB budget, MANIFEST with omissions, cancellable).
- Not done / blockers: Serbian Latin (no spec found), help/remote text audit, EPG conditional requests, per-endpoint backoff for artwork/metadata, player reuse preview→fullscreen (kept: preview stops first), device measurements.
