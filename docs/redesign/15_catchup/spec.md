# PRETORIAN — CATCH-UP SYSTEM
## FINAL DESIGN, NAVIGATION & IMPLEMENTATION SPECIFICATION

**Project:** PRETORIAN Android TV Media Center  
**Module:** TV / Catch-Up  
**Design language:** Official Pure Graphite + existing PRETORIAN TV player controls  
**References:** Two attached Catch-Up screenshots and the previously approved TV player screenshot  
**Status:** Design specification — do not implement until explicitly authorized

---

## 1. OBJECTIVE

Claude, we have approved the Catch-Up design for PRETORIAN.

Catch-Up must provide access to previously broadcast programs whenever the IPTV provider supports archive playback.

There are two ways to access the same Catch-Up system:

1. **From fullscreen TV playback:** a compact floating archive panel above the existing player dock.
2. **From the TV navigation section:** a full category-style browser where users can select any channel with Catch-Up support and browse its archive.

Both entry points must share the same underlying archive data, availability checks, playback logic and error handling.

**Do not implement two independent Catch-Up engines.**

---

## 2. CRITICAL — REFERENCE SCREENSHOTS

The attached screenshots demonstrate the intended layout and visual atmosphere.

However, they contain some illustrative elements that MUST NOT be copied literally.

### Screenshot 1 — TV Navigation Catch-Up

Use as a reference for:

- Two-column layout.
- Channel list on the left.
- Selected-channel information on the right.
- Subtle silver-white focus.
- Dark graphite surfaces.
- Browse Archive action.

### Screenshot 2 — Fullscreen TV Catch-Up

Use as a reference for:

- Floating Catch-Up panel.
- Compact date selector.
- Three visible program entries.
- Transparent smoky-glass appearance.
- Live TV continuing behind the interface.

### Mandatory corrections

The screenshots contain an older player dock arrangement.

**DO NOT recreate that old dock.**

The current approved dock contains exactly:

**Favorites · EPG · Picture · Catch Up · History · More**

Preserve the existing approved icons, labels, positions, dimensions, focus styling and functionality.

Do not replace them with:

`EPG · Favorit · Audio · Titlovi · Format · Više`

Those are obsolete references.

The screenshots also contain example channels and programs.

Never hardcode these examples.

If DAZN 1 is currently playing, its Catch-Up panel must display DAZN 1 archive information, not HRT 2.

---

# PART A — GLOBAL DESIGN RULES

## 3. PURE GRAPHITE BACKGROUND

The TV navigation Catch-Up browser must use the official PRETORIAN Pure Graphite background asset.

This is the same global background used throughout the application.

Do not introduce:

- Mountain landscapes.
- Blue concrete rooms.
- New gradient backgrounds.
- Purple or neon accents.
- Bright colored panels.
- Decorative scenery.
- A separate Catch-Up theme.

Use the same graphite, charcoal, soft white and silver-gray palette as the existing application.

### Fullscreen exception

When Catch-Up is opened during fullscreen TV playback, the live video remains the background.

The floating panel uses a dark translucent smoky-glass surface.

Do not replace the live broadcast with the Pure Graphite image.

---

## 4. SHARED VISUAL LANGUAGE

Both Catch-Up interfaces must follow the same design system:

- Dark charcoal / graphite surfaces.
- Subtle transparency.
- Thin silver-gray borders.
- Soft white primary text.
- Muted gray secondary information.
- Restrained rounded corners.
- Slightly brighter focused items.
- Thin silver-white focus outline.
- No excessive glow.
- No large opaque dialogs.
- No unnecessary decorative boxes.

Reuse existing PRETORIAN components and styling wherever possible.

Do not create an unrelated visual system for Catch-Up.

---

# PART B — CATCH-UP FROM FULLSCREEN TV

## 5. OPENING CATCH-UP

During fullscreen TV playback:

1. Press UP or DOWN to display the existing bottom player dock.
2. Navigate to the existing Catch Up option.
3. Press OK.
4. Open the compact Catch-Up panel upward, directly above the dock.

The TV broadcast must remain visible behind the panel.

The dock must remain in its existing position.

Do not open a separate fullscreen Catch-Up page.

Do not replace or redesign the dock.

---

## 6. PLAYER DOCK — NON-NEGOTIABLE

The approved six options are:

1. Favorites
2. EPG
3. Picture
4. Catch Up
5. History
6. More

**Keep this exact order.**

Use the existing approved PRETORIAN icon assets and their current styling.

Do not invent new icons.

Do not rename the actions.

Do not alter the behavior of the other five controls.

The Catch-Up feature must integrate into the existing dock rather than replacing it.

---

## 7. COMPACT CATCH-UP PANEL

The panel must appear above the player dock.

### Header

Display:

**CATCH UP**

Alongside the actual name of the currently playing channel.

If the provider exposes a reliable archive duration, display the available number of days.

Example:

`CATCH UP · DAZN 1 HD · 7 days`

The number of days must come from provider capabilities or actual archive availability.

Do not assume every channel has seven days of recordings.

### Panel dimensions

- Compact relative to the TV screen.
- Centered horizontally.
- Positioned above the dock.
- Large enough for readable program titles.
- Small enough to preserve substantial visibility of the live broadcast.
- No overlap with the bottom dock.
- No clipping at 1080p or 4K.

Use the attached screenshot as a proportional guide, not a rigid pixel template.

---

## 8. DATE SELECTOR

Display up to four available date tabs at once.

For example:

`Today · Yesterday · Monday · Sunday`

The user can navigate horizontally through the dates available from the provider.

### Requirements

- LEFT/RIGHT changes the selected date when focus is on the date selector.
- Display only dates supported by the archive.
- Do not create empty future dates.
- Do not display seven days if only two are supported.
- Use a thin silver outline for the selected/focused date.
- Keep the layout stable when changing dates.

Date labels must respect the selected application language.

---

## 9. PROGRAM LIST — THREE VISIBLE ENTRIES

Display three program rows simultaneously.

Additional programs must be accessible through vertical scrolling.

Each row contains:

- Start time.
- End time, if available.
- Program title.
- Short secondary information, if available.
- Duration, if known.
- A subtle playback indicator when the recording is available.

### Focused row

The selected program receives:

- Slightly brighter graphite background.
- Thin silver-white border.
- Clear, readable text.

Do not use bright colors or oversized highlights.

### Data accuracy

All program information must come from real EPG and archive metadata.

Do not generate fictional programs.

Do not display a playable icon for unavailable recordings.

---

## 10. PLAYER CATCH-UP REMOTE NAVIGATION

### UP / DOWN

Move through the available program entries.

At the first program, UP transfers focus to the selected date tab.

DOWN from the date selector returns to the program list.

### LEFT / RIGHT

When focus is on the date selector, move between available dates.

Do not change the currently playing TV channel while the Catch-Up panel is open.

### OK on a program

Start archive playback only when the selected program is actually available.

### BACK

Follow this exact hierarchy:

1. If a nested Catch-Up control is open, close that control.
2. Otherwise, close the Catch-Up panel and restore focus to Catch Up in the dock.
3. Another BACK closes the dock and returns to unobstructed fullscreen TV.

**BACK must never trap the user inside Catch-Up.**

Preserve the existing global back-navigation architecture.

---

## 11. CURRENT PROGRAM — START OVER

If the IPTV provider supports restarting the currently airing program, the current program may appear as an available archive entry under Today.

Selecting it starts playback from the beginning.

Do not add a new Start Over icon to the dock.

Do not create a separate Start Over screen.

If restart playback is unsupported, do not offer a nonfunctional action.

---

## 12. CHANNEL WITHOUT CATCH-UP

When the current channel does not support Catch-Up:

- Do not display fictional dates.
- Do not display fictional program entries.
- Do not present an active Play action.
- Do not crash or freeze the player.

Use the application's existing unavailable/disabled-state treatment.

A short message such as "Catch-Up unavailable for this channel" is acceptable when the user explicitly opens the function.

Do not leave a large empty archive panel visible.

---

# PART C — CATCH-UP FROM TV NAVIGATION

## 13. ENTRY POINT

Catch-Up must also be accessible from the TV group navigation.

Approved navigation path:

**TV → Catch Up → Channel → Archive → Program**

Catch Up is a special TV group entry.

It is NOT a new eighth main navigation category.

The global main navigation remains unchanged.

---

## 14. ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT

This is a fundamental PRETORIAN rule.

When the user selects Catch Up from the TV groups:

**The TV group list disappears completely.**

The Catch-Up browser replaces it.

Do not keep the previous group list visible as a third column.

Do not stack multiple navigation panels.

The new screen contains only:

- The list of channels with archive support.
- The selected-channel preview/information area.

When the user opens the archive, the archive view replaces the channel-selection view.

BACK returns exactly one logical level.

---

## 15. TV CATCH-UP BROWSER — TWO COLUMNS

Use the attached navigation screenshot as the visual reference.

### Left column — Channels with archive

Show a vertically scrollable list of channels that have confirmed Catch-Up capability.

Each entry may include:

- Existing channel logo.
- Actual channel name.
- Existing stream quality label when available.

Use real playlist/channel metadata.

Do not create placeholder channels.

### Right column — Selected channel

Show:

- Selected channel logo and name.
- Archive availability.
- Supported archive duration, if known.
- A compact preview of available programs.
- Browse Archive button.

Do not add a separate live-video preview unless explicitly approved later.

The right side is an information area, not a new screen.

---

## 16. CHANNEL LIST DESIGN

Use the same subtle individual-row design language as the approved category browsers.

### Unselected channel

- Dark translucent graphite background.
- Thin low-contrast border.
- Existing channel logo.
- Light-gray channel name.
- Compact quality information, if available.

### Focused channel

- Slightly brighter graphite background.
- Thin silver-white focus outline.
- Clearly readable name.
- No oversized scaling.
- No neon or bright glow.

Preserve smooth vertical scrolling.

The selected channel must remain fully visible.

---

## 17. SELECTED-CHANNEL INFORMATION

The right side updates when focus changes in the channel list.

Display actual information for the selected channel.

### Information hierarchy

**SELECTED CHANNEL**

Actual channel name and logo.

**Archive available**

Only when supported.

**Available days**

Use provider-supported information.

**Recent available programs**

Show a small preview of the real archive, if available.

**Browse Archive**

Primary action for opening the selected channel's archive.

### Performance

Do not reload the entire screen whenever the user moves to another channel.

Use cached data where possible.

If archive metadata requires an asynchronous request, keep navigation responsive and prevent stale responses from being displayed under the wrong channel.

---

## 18. CHANNEL SELECTION AND OPENING

Use the same approved interaction philosophy as the MOVIES and SERIES category browser.

### UP / DOWN

Move between channels and update the right-side information.

### Single OK on a channel

Move focus to Browse Archive.

### Double OK on a channel

Open that channel's archive directly.

### OK on Browse Archive

Open the selected channel's archive.

### LEFT from Browse Archive

Return focus to the selected channel.

### BACK from Browse Archive

Return focus to the channel list.

### BACK from channel list

Return to the TV group list.

Reuse the existing approved double-OK handling rules.

Prevent long-press key repeat from being interpreted as a double click.

Avoid duplicate navigation events.

---

# PART D — FULL ARCHIVE BROWSER

## 19. OPENING A CHANNEL ARCHIVE

When the user opens Browse Archive:

Replace the channel-selection screen with the archive browser for that channel.

Do not show the channel list beside the archive as an additional persistent navigation column.

### Archive content

Display:

- Channel name.
- Available date selector.
- Program list for the selected date.
- Program times.
- Program titles.
- Available playback indicators.

Use the same archive data and playback logic as the fullscreen compact panel.

The navigation version may use more vertical space and show additional program rows.

However, do not create a second, inconsistent archive system.

---

## 20. ARCHIVE NAVIGATION

- LEFT/RIGHT on the date selector: move between available dates.
- UP/DOWN: navigate programs.
- OK: play the selected available recording.
- BACK: return to the Catch-Up channel browser.
- BACK again: return to TV groups.

Restore the previously selected channel and list position when returning.

Do not jump to the first channel unnecessarily.

---

# PART E — SHARED FUNCTIONALITY

## 21. ONE SHARED CATCH-UP ENGINE

Both entry points must use the same underlying services for:

- Catch-Up capability detection.
- Archive date availability.
- EPG program retrieval.
- Archive stream URL generation.
- Archive playback.
- Playback errors.
- Resume position, if supported.
- Time and timezone handling.

Do not duplicate business logic.

Do not assume that EPG availability automatically means archive playback is available.

A program must be confirmed playable using the provider's actual Catch-Up mechanism.

---

## 22. PROVIDER COMPATIBILITY

Support only archive mechanisms that are genuinely available through the configured provider and existing application infrastructure.

Do not assume all IPTV playlists expose the same Catch-Up implementation.

Respect the provider's archive duration and restrictions.

Handle:

- Missing EPG.
- Missing archive metadata.
- Expired recordings.
- Unavailable playback URLs.
- Network errors.
- Unsupported archive formats.

Do not fabricate availability.

If the provider does not support Catch-Up, the app must handle that gracefully.

---

## 23. TIME AND DATE HANDLING

Archive navigation must use correct timestamps.

Account for:

- Provider timezone.
- Device timezone.
- Daylight-saving changes.
- Program start and end times.
- Archive retention limits.

Do not rely on hardcoded example dates.

The same recording must resolve consistently from both entry points.

---

## 24. PRESERVE EXISTING PLAYER BEHAVIOR

Do not change:

- Fullscreen playback.
- Existing six-option dock.
- Existing icon assets.
- Existing EPG behavior.
- Favorites.
- Picture controls.
- History.
- More.
- Channel switching.
- Player focus handling.
- Previously approved BACK behavior.

Catch-Up must be integrated without regressions.

The existing same-event alternative-channel panel must also remain independent and functional.

Do not replace it with Catch-Up.

---

# PART F — TESTING

## 25. REQUIRED ACCEPTANCE TESTS

### Fullscreen Catch-Up

- [ ] UP/DOWN opens the existing dock.
- [ ] Dock retains the approved six options.
- [ ] Catch Up opens above the dock.
- [ ] Live TV remains visible behind the panel.
- [ ] Panel shows the actual active channel.
- [ ] Only real archive dates appear.
- [ ] Three program entries are visible.
- [ ] Additional programs scroll correctly.
- [ ] Date navigation works.
- [ ] Available recordings play correctly.
- [ ] BACK closes the panel and restores dock focus.
- [ ] Another BACK closes the dock.
- [ ] No focus traps.

### TV Navigation Catch-Up

- [ ] Catch Up appears inside TV groups.
- [ ] Previous group list disappears completely.
- [ ] Official Pure Graphite background is used.
- [ ] Channels with archive support appear.
- [ ] Channel list is scrollable.
- [ ] Right-side information updates correctly.
- [ ] Single OK focuses Browse Archive.
- [ ] Double OK opens archive.
- [ ] Browse Archive opens the selected channel.
- [ ] Archive replaces the channel-selection screen.
- [ ] BACK restores the correct previous level.
- [ ] Previous channel focus and scroll position are restored.

### Provider and playback

- [ ] No fake channels or EPG entries.
- [ ] No unsupported recordings shown as playable.
- [ ] Unsupported channels are handled gracefully.
- [ ] Expired recordings fail gracefully.
- [ ] Archive timestamps are correct.
- [ ] No playback regression.
- [ ] No unrelated dock or icon changes.

---

## 26. IMPLEMENTATION REPORT

After implementation, provide:

1. Modified source files.
2. New components, if any.
3. Confirmation that existing icons and dock remain unchanged.
4. Explanation of shared Catch-Up logic.
5. Supported provider archive mechanisms.
6. How archive availability is verified.
7. How BACK and focus restoration work.
8. Tests performed.
9. Any unsupported provider capabilities.
10. Any behavior requiring verification on a physical Android TV device.

Do not claim physical-device testing unless it was actually performed.

---

# FINAL NON-NEGOTIABLE RULES

**ONE SHARED CATCH-UP SYSTEM — TWO ENTRY POINTS.**

**FULLSCREEN TV = COMPACT FLOATING PANEL ABOVE THE EXISTING DOCK.**

**TV NAVIGATION = TWO-COLUMN CHANNEL BROWSER ON PURE GRAPHITE.**

**OPENING AN ARCHIVE REPLACES THE PREVIOUS NAVIGATION LEVEL.**

**DO NOT CHANGE THE EXISTING PLAYER ICONS OR DOCK.**

**DOCK ORDER: FAVORITES · EPG · PICTURE · CATCH UP · HISTORY · MORE.**

**THREE VISIBLE PROGRAMS IN THE COMPACT PANEL.**

**SHOW ONLY ACTUAL PROVIDER-SUPPORTED ARCHIVE DATES AND RECORDINGS.**

**NEVER HARDCODE THE EXAMPLE CHANNELS OR PROGRAMS FROM SCREENSHOTS.**

**BACK ALWAYS RETURNS EXACTLY ONE LOGICAL LEVEL.**

**PRESERVE ALL EXISTING TV PLAYER FUNCTIONALITY.**

The attached screenshots are visual references only. Existing approved PRETORIAN assets, navigation rules and player controls take precedence wherever a screenshot differs from the specification.
---
## OWNER DECISIONS (2026-11-23)
1. Start Over: the current programme appears under Today only on channels with archive; if the server refuses, show a clear message.
2. Old Catch-up screen: resume positions stay; a "Continue watching" row on top of the channel list when unfinished programmes exist; the old channel search is removed (global Search finds channels).
3. "Catch Up" group entry sits directly below "Recently watched", above the provider groups.
