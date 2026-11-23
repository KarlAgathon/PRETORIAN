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

## Next
Beta 1 (A0 + A, built from branch `redesign-beta/1`) → owner Mi Box test → only then Phase B.
