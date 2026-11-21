# MEDIA CENTER — CLAUDE UI/UX IMPLEMENTATION DIRECTIVE

**Date:** 2026-11-21  
**Status:** Owner-approved product decisions; implementation subject to code inspection and safe development process  
**Applies to:** PRETORIAN source project, public-facing app **Media Center**  
**Companion authority:** `MEDIA_CENTER_PRIME_DIRECTIVE_v1.2.md` (retain all existing rules, including Beta by Default and Monochrome Silver).

## Instruction to Claude

Read this directive and the Prime Directive fully. Inspect the current implementation and relevant tests before changing code. Treat the decisions below as the approved target behavior. Do not invent new dashboards, menu layers, notification centers, screensaver replacements, or public releases. Where an implementation detail is not specified, choose the smallest solution consistent with the Prime Directive, and flag genuine ambiguities. Make changes incrementally and test them. Preserve playlists, favorites, watch progress, existing settings, Android application identity, signing and update compatibility. Never publish Stable without the owner's explicit instruction **“Publish Stable”**.

## 1. Home screen — navigation ONLY

- On **every** launch, including first launch, show the approved Monochrome Silver background and **only the left-side navigation column**. The remaining area stays visually empty (background only).
- Initial main entries, in order: **TV, MOVIES, SERIES, CATCH UP, SEARCH, SETTINGS**. Do not silently add Home, Sports, Notifications, recommendations, or other top-level entries.
- No auto-opened section, dashboard, preview, poster, movie row, EPG, content cards, recently watched list, or favorites display on Home.
- A category opens only after deliberate user activation (OK). The next navigation stage **replaces** the previous stage, following the established one-menu-at-a-time philosophy. BACK returns one stage with appropriate focus/scroll restoration.
- **Continue Watching, Recently Watched, Favorites, Provider Groups** belong inside relevant categories as applicable; never display their content on Home.
- The approved six-entry main menu takes precedence over older examples listing a separate top-level Home or Sports entry. Do not delete the underlying sports feature merely because it is not a top-level Home entry; its navigation placement requires review if not already defined.

## 2. Category notification badges

- Show a small restrained numeric badge **beside the relevant Home category** only when reliable new/changed items exist (e.g., new movie, new episode, relevant playlist updates). No content preview or extra panel on Home.
- Maintain distinct, stable identities for new items to avoid duplicate counts on repeated playlist refreshes. Do not generate misleading counts when source data is insufficient.
- A badge count **decreases as the user actually visits/reviews individual new items**, not merely by opening the parent category. Define 'reviewed' in a consistent, testable way per content type; avoid counting an item twice.
- **Long OK** on a category with a badge offers **Clear Notifications**. Clearing removes only that category's current new-item indicators, not content, favorites, playlists, or watch history. Standard OK still opens the category. The context menu must be remote-accessible and preserve focus on dismissal.
- The owner approves **both** incremental auto-decrement and manual clear. Clearing/reading state persists across normal restarts and upgrades; genuinely new items can generate new badges later.
- Badges must not steal focus or interrupt playback.

## 3. General/system notifications

- Keep **content discovery notifications out of General Notifications**. Category badges are their home.
- Reserve general/system notifications for important actionable conditions: Stable update available/failed, meaningful crash/error, file receiver transfer requiring attention or failure, loss of internet, verifiably detected VPN outage, playlist/provider failure, etc.
- **Selecting a system notification must navigate directly to its actual source, relevant settings, or the action needed to resolve it**, not to an unrelated generic screen. If the source cannot be opened, show a concise contextual explanation rather than a dead link.
- No unnecessary persistent notification dashboard, extra top-level Notifications entry, or always-visible bell is approved. Reuse existing appropriate notification access/indication; if none exists, propose a minimal access design for owner approval before adding a new UI element.
- Never steal focus from active playback for nonblocking notifications. Do not claim VPN failure unless the app can reliably detect it. Do not expose sensitive diagnostics to the viewer.

## 4. TV, EPG, and Catch Up

- A non-working channel: show a simple **“Channel unavailable”** message (localized), without stack traces or needless screens. Preserve navigation and allow another channel selection.
- Missing EPG: **no warning or nuisance message**. Omit unavailable EPG information and preserve usable playback/navigation.
- In **CATCH UP**, only show channels for which catch-up is genuinely available. If no eligible channels exist, use a minimal empty state, not false availability.
- Keep existing user-initiated preview/fullscreen behavior and remote navigation from the Prime Directive and approved TV specification.

## 5. Movies, Series, and Continue Watching

- When a film or episode is **finished**, automatically remove it from **Continue Watching**. Completion must be detected consistently; avoid accidentally marking an interrupted stream as completed.
- Context action **Remove from Continue Watching** manually removes the entry **and resets its saved playback progress** (resume position). It must not delete the actual movie/episode or playlist.
- Preserve the separately agreed long-OK contextual actions (Continue, Play from Beginning, Remove from Continue Watching) wherever applicable.
- Do not assume removing from Continue Watching erases all watched history or favorites; these are separate states.
- Continue Watching is the first relevant section **inside** Movies or Series when populated, never a Home dashboard row.

## 6. Search — strictly category scoped

- Search has **only** these scopes: **TV, MOVIES, SERIES, SPORT, SETTINGS**. **Never an ALL/global combined results scope.**
- Search results are grouped/filtered by the selected scope; a result opens/navigates to the matching item or setting, not a new redundant information screen.
- For TV channel results, additionally offer **Add to Favorites** as a contextual action.
- BACK from a destination should return to the prior search context with query, chosen scope, focus and scroll restored where feasible.
- Note that **SPORT** is a search scope, not authorization to add SPORT to the six-item Home main menu. Confirm existing Sports module routing if needed.

## 7. Screensaver — REMOVE COMPLETELY

- The owner has **revoked** earlier screensaver plans. Remove/disable the app's custom screensaver feature entirely: UI entries, timers, background work, overlay, data feeds, continue-watching screensaver actions and related hooks, to the extent they exist.
- Do not replace it with another ambient/dashboard feature. Do not attempt to disable or override the Android operating system's own screen saver or power-management features.
- Before deletion, audit dependencies and preserve unrelated playback, notifications and lifecycle behavior. Remove dead code only after confirming it is unused; test app idle, pause, playback, resume and BACK behavior.

## 8. Offline, no server, no playlist

- If the internet is unavailable, show a **small, nonintrusive lower-right message** such as **“No internet. Playback unavailable.”** (localized). Do not open a modal or hijack focus.
- **Do not stop an already functioning stream solely because connectivity monitoring reports offline**. Playback should continue while it actually works. Phrase the message accurately when playback remains possible; do not assert that playback is impossible if a local/cached or existing stream is still running.
- With no playlist or no usable provider/server, the app itself opens normally. Category views are empty or show a brief contextual prompt to add a playlist when relevant; do not fake content or repeatedly throw error dialogs.
- Distinguish no playlist (setup needed) from a configured playlist whose server is temporarily unavailable (recoverable error). Settings must remain reachable.

## 9. Settings — six logical groups, no unnecessary features

Organize existing relevant settings under:

1. **Playlists & Providers** — add/edit/refresh playlists and providers.
2. **Player & Playback** — player behavior, audio/subtitles/format where applicable.
3. **Appearance & Language** — existing appearance/language options consistent with Monochrome Silver.
4. **Privacy & Parental Controls** — PIN/content restrictions and opt-in crash reporting where implemented.
5. **Backup & Restore** — existing backup and recovery functions.
6. **About & Updates** — app version, stable updates, safe diagnostics/installation ID if approved.

Do not create nonfunctional settings or duplicate existing controls. Inspect current Settings and map existing options into these groups; surface any missing/ambiguous placements before substantial restructuring. The Settings navigation also follows one-menu-at-a-time and TV remote focus rules.

## 10. Quality and release gates

- Verify initial and subsequent Home launches, six-item menu order, no right-side content, navigation/focus restoration, badge creation/decrement/clear/persistence, long-OK behavior, scoped Search, TV error/no-EPG/catch-up filtering, Continue Watching completion/reset, offline behavior, settings navigation, and full screensaver removal.
- Test on an Android TV device or emulator where possible. Report precisely what was verified and what still requires the owner's ADB device test.
- Follow **Beta by Default**: build a **private Beta APK** and provide a private artifact/link, version, checksum and ADB installation instructions for the owner. Do not add it to public GitHub releases or the public update feed.
- Do not publish Stable, alter repository visibility, change package ID/signing, delete user data, or silently change update infrastructure.
- Keep Sentry privacy/consent requirements and other security constraints from Prime Directive v1.2 intact. Screensaver removal does not authorize removing unrelated diagnostics.

## Required Claude response

1. List existing code locations that implement each affected feature and any conflicts with these approved requirements.
2. State the smallest safe implementation plan and any decision that genuinely requires owner confirmation.
3. Implement in small testable steps, with explicit file-level change summary.
4. Provide build/test outcomes and any remaining risks.
5. Supply **Private Beta only** for ADB testing; **never publish Stable without explicit approval**.

**This document updates the approved UI/UX specification. It does not replace Prime Directive v1.2 or authorize a public release.**
