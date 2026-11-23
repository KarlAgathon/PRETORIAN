# PRETORIAN — SPORTS MODULE & SPORT SETTINGS
## FINAL IMPLEMENTATION SPECIFICATION — STABLE RELEASE

**Project:** PRETORIAN Android TV Media Center  
**Scope:** SPORTS dashboard, Match Details, Broadcast Discovery, Match Follow, SETTINGS → SPORT  
**Priority:** Stability, correct data, responsive D-pad navigation, consistent design  
**Target:** Stable release, not an experimental prototype

---

# 1. IMPLEMENTATION OBJECTIVE

Claude, we are ready to implement the SPORTS module and SETTINGS → SPORT.

The previous SPORTS implementations did not meet our expectations. This specification replaces earlier conflicting SPORTS design and navigation instructions.

Build a clean, stable, fully integrated SPORTS experience that follows the existing PRETORIAN architecture.

**Do not redesign unrelated application modules.**

Preserve HOME, TV, MOVIES, SERIES, SEARCH, CATCH-UP, playback, Favorites, History, and all previously approved features.

Use the attached SPORTS dashboard, Match Follow, notification, and Sport Settings screenshots as visual references.

Screenshots demonstrate layout and appearance. They are not sources of real match results, broadcast rights, or provider data.

---

# 2. GLOBAL DESIGN — NON-NEGOTIABLE

Use the existing official PRETORIAN Pure Graphite background.

This is the single global browsing background across PRETORIAN.

Do not introduce another background for SPORTS or SETTINGS.

Use the established visual language:

- Neutral dark graphite and charcoal.
- White and soft silver typography.
- Subtle translucent surfaces.
- Thin silver-white focus outlines.
- Restrained rounded corners.
- Existing PRETORIAN icons wherever available.
- Consistent typography, spacing, and focus behavior.
- No neon, purple, excessive glow, or unnecessary animations.

Reuse existing application components and styling.

Do not replace approved icons or modify unrelated UI.

All layouts must remain readable and unclipped at 1080p and 4K.

---

# PART A — SPORTS DASHBOARD

## 3. ONE SCREEN, TWO COLUMNS

When the user selects SPORTS from the main navigation, replace the previous menu completely.

The SPORTS dashboard occupies the available screen.

Use two main columns:

**LEFT — MATCHES**

A vertically scrollable list of matches.

**RIGHT — SELECTED MATCH**

Detailed information about the confirmed selected match.

Do not create additional navigation screens or unnecessary intermediate menus.

The left match list remains visible while the right side updates.

---

## 4. MATCH LIST

Each match row should show, when available:

- Competition or league identification.
- Home team.
- Away team.
- Match time or current status.
- Live score when available.
- Clear focus state.
- Small Follow indicator if the user follows that match.

Prioritize live matches and matches relevant to the user's Sport Settings.

Do not fabricate fixtures or results.

### Remote navigation

UP/DOWN moves through the match list.

A single OK confirms the focused match and updates the right column.

The right column must not rebuild or flicker unnecessarily.

Changing the confirmed match must not open a new page.

BACK returns one logical navigation level.

---

## 5. MATCH DETAILS — TOP RIGHT

The upper-right panel displays the confirmed match.

Include real available information:

- Competition and round.
- Team names and official identifiers.
- Team crests.
- Team jerseys if reliable image assets are available.
- Match status.
- Live score.
- Match minute.
- Half-time.
- Full-time.
- Extra time.
- Penalty shootout.
- Goals.
- Yellow and red cards.
- Other significant match events.

Do not invent player names, goals, statistics, jerseys, or results.

If a specific field is unavailable, omit it gracefully.

The match panel must remain visually stable as live data updates.

---

## 6. MATCH BROADCASTS — LOWER RIGHT

The lower-right panel initially prioritizes channels broadcasting the selected match.

Separate two types of results.

### A. Channels available in the user's IPTV playlist

Display:

- Actual channel name.
- Existing channel logo, when available.
- Stream quality when reliably known.
- Play action.

Selecting a confirmed playlist channel starts that channel using the existing TV player.

### B. Broadcasters not available in the playlist

Display separately as:

**ALSO BROADCASTING — NOT IN YOUR LIST**

These entries are informational.

Do not show a playable action without a valid playable stream in the user's configured sources.

Do not claim that a broadcaster carries a match unless the data source supports that association.

Do not infer confirmed broadcasts solely from channel names.

---

## 7. SMART ADAPTIVE LOWER PANEL

This is an important approved design rule.

The lower-right panel must adapt to the available broadcast information.

### Scenario A — Multiple confirmed channels

Prioritize the channel list.

Allow smooth vertical scrolling if necessary.

### Scenario B — Only one or two channels

Display those channels first.

Use the remaining space for real match statistics or chronological match events.

### Scenario C — No confirmed channels

Display a discreet message:

**No broadcast channel found**

Below it, use the available space for:

- Match statistics.
- Possession.
- Shots on target.
- Corners.
- Match events.
- Other reliable match information already available.

### Scenario D — No channels and no detailed statistics

Show a compact availability message and any verified basic match information.

Do not invent content to fill empty space.

Do not insert unrelated news, advertisements, random fixtures, or decorative placeholders.

### Layout stability

Keep the top match-details panel in a consistent position.

Adapt only the lower-right content area.

Avoid abrupt resizing or jumping when new information arrives.

---

# PART B — MATCH FOLLOW

## 8. LONG-PRESS MATCH ACTION

When a match is focused in the left list:

**Long OK** opens a small contextual floating action.

If the match is not followed:

**Follow this match**

If the match is already followed:

**Unfollow this match**

The action should use the approved PRETORIAN smoky-glass styling.

Do not open a separate screen.

Do not add unnecessary confirmation dialogs.

After selection, save the new Follow state immediately.

Show a subtle Follow indicator beside followed matches.

Allow multiple matches to be followed simultaneously.

### Important

A long press must not also trigger the normal single-OK match-selection action.

Use the existing application's remote-control key handling where possible.

Prevent accidental duplicate actions from key repeat.

---

## 9. MATCH FOLLOW NOTIFICATIONS

When a followed match has a significant event, PRETORIAN displays a compact overlay.

This overlay may appear while the user is watching:

- Live TV.
- Movies.
- Series.

The overlay must not interrupt playback.

### Appearance

Use the approved notification screenshot.

Position: upper-right corner.

Style:

- Compact.
- Translucent graphite.
- Thin silver outline.
- White typography.
- Small SPORTS identifier.
- No oversized animation or intrusive effects.

Example:

**GOAL!**

Ronaldo · 71'

Portugal – Germany

**1 : 0**

Use actual event data only.

If the scorer is unavailable, omit the name.

---

## 10. NOTIFICATION BEHAVIOR

Default display duration: **3 seconds**.

The user can change this in SETTINGS → SPORT.

Notifications must:

- Automatically disappear.
- Never pause playback.
- Never change D-pad focus.
- Never require confirmation.
- Never open another screen.
- Never enter General Notifications.
- Never appear in the main notification bell history.
- Respect Do Not Disturb.
- Respect selected playback contexts.
- Respect selected event types.

If the user is already watching the same match through a reliably identified TV broadcast, suppress duplicate Match Follow overlays for that match.

Do not guess that the user is watching a match merely because a channel has a sports-related name.

---

## 11. SUPPORTED FOLLOW EVENTS

The user can independently enable or disable:

- Goals.
- Cards.
- Penalties.
- Half-time.
- Full-time.

Cards should support yellow and red card events where available.

Penalties should distinguish awarded, scored, and missed events when the provider supplies that information.

Extra-time and shootout status should be displayed in match details. Do not introduce additional notification toggles without approval.

### Event deduplication

A single real-world event must generate no more than one notification.

Use stable event identifiers when available.

Otherwise use a carefully designed deduplication strategy based on match identity, event type, event time, and source metadata.

Do not repeatedly notify the user when the same event is returned by successive updates.

Avoid showing stale events as if they just happened.

---

# PART C — SETTINGS → SPORT

## 12. SETTINGS NAVIGATION

The SPORTS settings are accessed through:

**SETTINGS → SPORT**

Use the approved Pure Graphite Settings layout.

The main Sport Settings page contains exactly these sections:

1. SPORT
2. COUNTRY
3. NATIONAL TEAM
4. FAVORITES
5. COMPETITION TYPE
6. NOTIFICATIONS

Do not add unnecessary main settings categories.

Use existing PRETORIAN icons and focus styling.

All selections are saved automatically.

No Save button is required.

BACK returns one logical level.

---

## 13. SPORT — PRIMARY MASTER FILTER

This is the most important filter.

SPORT supports multiple selection.

Examples include Football, Basketball, Tennis, Volleyball, and other sports actually supported by the configured sports data source.

The selected sports determine:

- Which sports are queried.
- Which sports appear on the SPORTS dashboard.
- Which sports are relevant to the remaining filters.
- Which sports can contribute to match discovery.

The master filter must be applied consistently across the SPORTS system.

### Requirements

- Multiple sports may be selected.
- Selections are persistent.
- Changing sports updates dependent searches and relevant dashboard content.
- No unnecessary confirmation dialogs.
- No forced deletion of previously saved countries, teams, or competitions.

If no sport is selected, display a clear empty state rather than querying every sport unexpectedly.

Do not offer a sport as fully functional if the configured data source cannot provide its required fixtures.

---

## 14. COUNTRY — SEARCH-FIRST MULTISELECT

Do not display a large predefined country list.

When COUNTRY is opened:

1. Show Search at the top.
2. Search available countries relevant to the selected sports.
3. Display matching results.
4. OK on a result adds that country.
5. Show already selected countries below Search.
6. Allow each selected country to be removed individually.

Example:

Search countries...

**SELECTED COUNTRIES**

Germany — Remove  
Spain — Remove  
Portugal — Remove

The list must update immediately.

No Save button.

No confirmation dialog for ordinary addition/removal.

### Dashboard behavior

Matches associated with selected countries receive higher priority.

Country selection is a preference, not an automatic exclusion of every other match.

---

## 15. NATIONAL TEAM — SEARCH-FIRST MULTISELECT

Use the same interaction pattern as COUNTRY.

When opened:

- Search field first.
- Search national teams.
- Filter results according to selected sports.
- OK adds a team.
- Selected teams appear below.
- Individual removal is available.
- Multiple selections are supported.

Example:

Germany  
Portugal  
Croatia

National-team selections influence match prioritization on the dashboard.

Do not confuse a national team with a club that has a similar name.

Use stable team identifiers.

---

## 16. FAVORITES — SEARCH-FIRST MULTISELECT

FAVORITES allows the user to follow preferred clubs and teams for dashboard prioritization.

Use the same search-first interaction.

- Search available teams.
- Add multiple teams.
- Show selected teams below.
- Remove each team individually.
- Save changes immediately.

Examples:

Bayern München  
Real Madrid  
Arsenal

The available search results must respect the SPORT master filter.

A team selected here is a persistent dashboard favorite.

**Important distinction:** adding a favorite team does not automatically activate temporary Match Follow notifications for every fixture. Match Follow remains a separate per-match action.

---

## 17. COMPETITION TYPE — SEARCH-FIRST MULTISELECT

This section manages preferred leagues and competitions.

Examples:

- Bundesliga.
- UEFA Champions League.
- FIFA World Cup.
- UEFA Nations League.
- Other competitions supported by the data source.

Do not display a long predefined competition list.

Use:

1. Search.
2. Matching results.
3. OK to add.
4. Selected competitions below.
5. Individual removal.

Allow multiple competitions.

Selections influence SPORTS dashboard prioritization.

Competition search must reflect the selected sports.

Use real competition identifiers rather than name-only matching.

---

## 18. SHARED SEARCH COMPONENT

COUNTRY, NATIONAL TEAM, FAVORITES, and COMPETITION TYPE should reuse one common search-and-selection component.

The component must support:

- Fast keyboard input.
- Remote-control focus navigation.
- Search result filtering.
- Multiple selections.
- Persistent selected items.
- Individual removal.
- Immediate updates.
- Empty states.
- Loading states.
- Search error states.
- Stable focus restoration.

### Important

Do not duplicate four separate implementations with inconsistent behavior.

Do not require the user to clear a large preloaded list.

Search is the primary discovery method.

When a category is reopened, Search may start empty while previously selected items remain visible below.

---

## 19. DEPENDENT FILTERS

SPORT is the master filter.

When SPORT changes:

- Refresh relevant country search results.
- Refresh relevant national-team search results.
- Refresh relevant favorite-team search results.
- Refresh relevant competition search results.
- Update dashboard eligibility.

Do not delete stored selections merely because they are temporarily irrelevant.

Example:

The user selects Football and Basketball and saves several teams.

Later, the user temporarily selects Football only.

Basketball selections remain stored but do not influence the Football dashboard.

When Basketball is reenabled, those selections become relevant again.

This prevents accidental loss of user preferences.

---

# PART D — NOTIFICATION SETTINGS

## 20. DISPLAY DURATION

Provide these options:

- 2 seconds.
- 3 seconds.
- 5 seconds.
- 10 seconds.

Default: **3 seconds**.

Use a simple selector compatible with the Android TV remote.

The selected duration applies to Match Follow overlays only.

It must not alter other application notifications.

---

## 21. DO NOT DISTURB

Provide a simple ON/OFF control.

### ON

Suppress all Match Follow overlays.

Do not remove followed matches.

Do not delete user preferences.

### OFF

Allow Match Follow overlays according to the other notification settings.

Do Not Disturb takes precedence over the playback-context and event-type settings.

Do not create a separate scheduling screen or time-based DND system in this release.

---

## 22. SHOW NOTIFICATIONS DURING

Support multiple selections:

- Live TV.
- Movies.
- Series.

These settings control the contexts in which Match Follow overlays may appear.

Examples:

If Movies is disabled, no Match Follow overlays appear during movie playback.

If Live TV is enabled, overlays may appear during TV playback, unless suppressed by Do Not Disturb or same-match detection.

Selections are independent.

Do not automatically enable or disable one context when another changes.

---

## 23. EVENT TYPES

Provide five independently selectable event categories:

**GOAL**

Notify when a confirmed goal occurs.

**CARDS**

Notify for selected supported card events.

If separate yellow/red toggles are already implemented cleanly, preserve them; otherwise use the approved single Cards category.

**PENALTIES**

Notify for confirmed penalty events.

**HALF-TIME**

Notify when the match reaches half-time.

**FULL-TIME**

Notify when the match ends.

All categories support multiple selection.

Changes are saved immediately.

No Save button.

Do not add unrelated event categories to this screen.

---

# PART E — DATA AND TECHNICAL REQUIREMENTS

## 24. SPORTS DATA SOURCE

Before implementation, inspect the existing SPORTS data integrations.

Determine which information is actually available:

- Supported sports.
- Fixtures.
- Competitions.
- Countries.
- Teams.
- Scores.
- Match status.
- Events.
- Statistics.
- Team crests.
- Jerseys.
- Broadcast information.

Do not assume every data source supports all fields.

Do not introduce a paid service, new API key requirement, or external account without reporting the dependency.

If an essential capability is missing, identify the limitation clearly.

Do not silently substitute fabricated demo data.

---

## 25. BROADCAST DISCOVERY

Broadcast matching is a separate technical challenge.

Use reliable information to associate a match with actual broadcasters.

Then match confirmed broadcasters against channels in the user's IPTV playlist.

Avoid false positives.

A similarly named channel is not sufficient proof that it carries the selected event.

Separate:

- Confirmed playlist channels.
- Confirmed external broadcasters.
- Unknown broadcast availability.

If verification is unavailable, say so in the UI.

Do not present unverified matches as confirmed broadcasts.

---

## 26. PERSISTENCE

Persist:

- Selected sports.
- Selected countries.
- Selected national teams.
- Favorite teams.
- Selected competitions.
- Notification duration.
- Do Not Disturb state.
- Enabled playback contexts.
- Enabled event categories.
- Followed match identifiers.

Settings must survive application restarts.

Use the application's existing persistence architecture.

Do not create unnecessary duplicate storage systems.

---

## 27. LIVE UPDATES AND PERFORMANCE

SPORTS must remain responsive while live match data is refreshed.

Requirements:

- Never block the UI thread with network requests.
- Avoid unnecessary full-screen recomposition.
- Cache appropriate metadata.
- Prevent stale responses from overwriting newer selections.
- Handle temporary network loss.
- Use sensible refresh intervals consistent with source limits.
- Avoid duplicate polling jobs.
- Stop unnecessary work when no longer needed.
- Avoid excessive battery and network usage.

The user must be able to scroll smoothly through matches while data refreshes.

---

## 28. MATCH FOLLOW LIFECYCLE

Match Follow must work while PRETORIAN is active, including fullscreen playback.

It must not require the SPORTS screen to remain open.

Followed matches should remain tracked across normal navigation.

Avoid unnecessary background services or Android system notifications.

Do not implement notification behavior outside PRETORIAN in this release.

When a followed match finishes, deliver the enabled final result notification if appropriate and stop active monitoring for that match.

Preserve the stored match-follow state long enough to avoid duplicate notifications after a restart.

---

## 29. FOCUS AND REMOTE CONTROL

This is a critical stability requirement.

Every interactive control must work correctly with an Android TV remote.

Test:

- UP.
- DOWN.
- LEFT.
- RIGHT.
- Short OK.
- Long OK.
- BACK.
- Repeated D-pad input.

Ensure:

- No focus traps.
- No unexpected screen jumps.
- No double activation.
- No accidental long-press action.
- No lost focus after search results update.
- No scrolling past the visible area.
- No unwanted playback interruption.

BACK must always return one logical level.

---

# PART F — STABLE RELEASE REQUIREMENTS

## 30. STABILITY FIRST

This must be a stable release.

Do not rush implementation by adding placeholders or incomplete features.

Do not make broad architectural changes unrelated to SPORTS.

Prefer existing tested components and patterns.

Keep the implementation modular and maintainable.

Avoid experimental dependencies.

Do not remove existing working functionality.

If a requested feature cannot be implemented reliably with available data, clearly report it rather than simulating functionality.

---

## 31. REGRESSION PROTECTION

Verify that the following remain functional:

- HOME.
- TV groups.
- TV channel browser.
- Fullscreen TV player.
- Existing six-option TV dock.
- EPG.
- Catch-Up.
- Favorites.
- Recently Watched.
- MOVIES.
- SERIES.
- SEARCH.
- Existing subtitle/audio/picture controls.
- Existing navigation and BACK handling.
- Existing global background and branding.

Do not change unrelated icons, layouts, or playback controls.

---

## 32. ACCEPTANCE TESTS

### SPORTS dashboard

- [ ] Pure Graphite background.
- [ ] Two-column layout.
- [ ] Match list scrolls smoothly.
- [ ] OK updates selected match details.
- [ ] No additional screen opens unnecessarily.
- [ ] Live scores update correctly.
- [ ] Missing data handled gracefully.
- [ ] Broadcast list displays confirmed results only.
- [ ] Adaptive lower panel handles many, few, or zero channels.

### Match Follow

- [ ] Long OK opens Follow action.
- [ ] Follow state saves correctly.
- [ ] Long OK on followed match offers Unfollow.
- [ ] Multiple matches can be followed.
- [ ] Overlay appears during allowed playback contexts.
- [ ] Overlay automatically disappears after configured duration.
- [ ] Playback continues uninterrupted.
- [ ] D-pad focus remains unchanged.
- [ ] No duplicate event notifications.
- [ ] Do Not Disturb suppresses overlays.
- [ ] No Match Follow events enter General Notifications.

### SETTINGS → SPORT

- [ ] Multiple sports selectable.
- [ ] SPORT correctly controls dependent filters.
- [ ] COUNTRY search and multi-selection work.
- [ ] NATIONAL TEAM search and multi-selection work.
- [ ] FAVORITES search and multi-selection work.
- [ ] COMPETITION TYPE search and multi-selection work.
- [ ] Selected items can be removed individually.
- [ ] Search is responsive.
- [ ] Settings persist after restart.
- [ ] Notification duration works.
- [ ] Do Not Disturb works.
- [ ] Playback-context selection works.
- [ ] Event-type selection works.
- [ ] BACK and focus restoration work.

### Release quality

- [ ] No crashes.
- [ ] No ANRs.
- [ ] No navigation regressions.
- [ ] No layout clipping.
- [ ] No duplicate network polling.
- [ ] No unnecessary UI-thread blocking.
- [ ] Existing modules remain functional.

---

# 33. BUILD AND VALIDATION

Before calling this release stable:

1. Inspect the current codebase and identify existing reusable components.
2. Implement the SPORTS module using the established architecture.
3. Integrate SETTINGS → SPORT.
4. Integrate Match Follow with the existing player lifecycle.
5. Run available automated tests.
6. Build the application successfully.
7. Review compile errors and warnings.
8. Test navigation and state transitions.
9. Verify persistence.
10. Check for regressions in existing modules.

If Android TV device testing is unavailable, explicitly state that physical-device validation remains pending.

Do not claim a stable release solely because the application compiles.

---

# 34. FINAL IMPLEMENTATION REPORT

After implementation, provide a concise report containing:

- Modified files.
- New components.
- Existing components reused.
- Sports data sources used.
- Supported sports and data limitations.
- Broadcast matching approach.
- Match Follow monitoring approach.
- Persistence implementation.
- Notification behavior.
- Automated test results.
- Build result.
- Known limitations.
- Physical Android TV tests still required.

If any feature remains incomplete, identify it explicitly.

---

# FINAL APPROVED RULES

**ONE SPORTS DASHBOARD — TWO COLUMNS.**

**LEFT: MATCHES. RIGHT: SELECTED MATCH DETAILS AND BROADCASTS.**

**NO EXTRA SPORTS MENUS OR SCREENS.**

**SMART ADAPTIVE PANEL WHEN BROADCAST CHANNELS ARE MISSING.**

**SHORT OK SELECTS A MATCH. LONG OK OPENS FOLLOW / UNFOLLOW.**

**MATCH FOLLOW NOTIFICATIONS ARE COMPACT, TEMPORARY AND NON-INTRUSIVE.**

**DEFAULT NOTIFICATION DURATION: 3 SECONDS.**

**MATCH FOLLOW DOES NOT USE GENERAL NOTIFICATIONS.**

**SETTINGS → SPORT CONTROLS ALL SPORTS FILTERS AND NOTIFICATION PREFERENCES.**

**SPORT IS THE MASTER FILTER.**

**ALL OTHER SEARCH FILTERS SUPPORT MULTIPLE SELECTIONS.**

**COUNTRY, NATIONAL TEAM, FAVORITES AND COMPETITIONS ARE SEARCH-FIRST.**

**ALL SETTINGS SAVE AUTOMATICALLY.**

**PURE GRAPHITE IS THE ONLY BROWSING BACKGROUND.**

**PRESERVE ALL EXISTING PRETORIAN ICONS, PLAYER CONTROLS AND NAVIGATION.**

**STABILITY IS MORE IMPORTANT THAN FEATURE COUNT.**

Implement this as a stable, production-oriented PRETORIAN release. Do not add unapproved screens, redesign existing modules, or fabricate unavailable data.