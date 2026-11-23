# PRETORIAN — AUTOMATIC ALTERNATIVE CHANNELS
## FINAL FULLSCREEN TV PLAYER IMPLEMENTATION SPECIFICATION

**Project:** PRETORIAN Android TV Media Center  
**Feature:** Automatic Alternative Channels / Same Event  
**Design System:** PRETORIAN Pure Graphite  
**Reference:** Attached fullscreen sports screenshot  
**Status:** Approved specification — awaiting implementation authorization

---

## 1. OBJECTIVE

Claude, we want to introduce a new feature into PRETORIAN's existing fullscreen TV player:

**Automatic Alternative Channels — Same Event Detection**

The purpose is to help users quickly switch between available IPTV channels broadcasting the same live event.

For example, a user watching a Bundesliga match may discover that the same match is available on several channels with different stream qualities or commentary languages.

This allows the user to switch quickly if the current stream is buffering, has poor picture quality or has other playback problems.

The feature must be automatic, minimal and fully integrated into our existing fullscreen TV interface.

**Do not create a new screen, a separate Alternatives menu or a seventh Options Dock icon.**

---

## 2. PRESERVE THE EXISTING OPTIONS DOCK

The approved fullscreen Options Dock contains exactly six primary actions:

1. Favorites
2. EPG
3. Picture
4. Catch Up
5. History
6. More

**These six options must remain unchanged.**

Do not replace or rename them.

Do not introduce Audio, Subtitles, Aspect or Record as new primary Dock actions.

Existing secondary controls must remain in their previously approved locations.

The attached screenshot illustrates the floating-panel concept, but some labels and icons in the image differ from our final approved design.

**Our existing six-option Dock specification takes priority over the screenshot.**

---

## 3. AUTOMATIC ACTIVATION

The user must not need to click an Alternatives button.

The feature should work as follows:

1. The user watches a live TV channel in fullscreen.
2. The user presses DPAD UP or DOWN.
3. The existing fullscreen Options Dock opens.
4. PRETORIAN checks whether reliable alternative channels broadcasting the same event have been identified.
5. If alternatives are available, a compact floating panel automatically appears in the upper-right area of the screen.
6. The user can navigate to the panel and select an alternative channel using the remote.
7. Pressing OK switches directly to that channel.

**There must be no extra activation step.**

The Alternatives panel is a contextual companion to the existing Options Dock.

---

## 4. WHEN NO ALTERNATIVES ARE AVAILABLE

This is mandatory.

If no reliable alternative channels have been identified:

- Do not show the panel.
- Do not show an empty box.
- Do not show a placeholder.
- Do not show a message saying "No alternatives found".
- Do not reserve empty screen space.
- Do not show an additional Dock icon.

The fullscreen player should behave exactly as it does today.

**No alternatives = no additional UI.**

---

## 5. VISUAL DESIGN

Use the attached sports screenshot as a reference for the panel's general placement, proportions and visual integration.

However, preserve PRETORIAN's existing design language.

### Panel appearance

- Compact floating panel.
- Positioned on the right side of the screen, above the bottom Options Dock.
- Dark charcoal / smoky-glass background.
- Subtle transparency.
- Soft, restrained edges.
- Minimal border.
- White and muted gray typography.
- Thin silver-white focus indicator.
- Consistent spacing.
- No bright glow.
- No blue or purple neon.
- No oversized opaque box.

The panel should look like a natural part of PRETORIAN, not an Android system popup or a separate application.

### Suggested panel title

**SAME EVENT**

or the equivalent localized label.

Use the application's selected language.

Keep the title short and discreet.

---

## 6. PANEL CONTENT

Each alternative channel row should contain:

- Channel logo, if available.
- Channel name.
- Stream quality label, if available.
- Current channel indicator, when applicable.

Supported quality labels may include:

**SD · HD · FHD · 4K**

Only display quality information supported by real stream or playlist metadata.

Do not invent quality values.

A playlist label such as FHD is not proof that the actual decoded video is 1080p. If actual playback resolution is available, it may be used, but the distinction should remain accurate.

### Example

Current event: Bayern München vs Borussia Dortmund

Possible matching channels:

- DAZN 1 — FHD
- DAZN 2 — HD
- Magenta Sport 3 — FHD
- ZDF — HD

These names are illustrative only.

Do not hardcode these channels or assume they actually broadcast the same match.

The list must be generated from real available IPTV data.

---

## 7. CURRENT CHANNEL INDICATOR

If the currently playing channel is included in the panel, mark it clearly.

For example:

**PLAYING**

Use a subtle icon or text indicator.

Do not rely on bright colors.

The current channel must remain visually distinguishable from other channels.

Avoid duplicate entries representing the same playlist channel.

---

## 8. SAME EVENT DETECTION

This is the most important technical requirement.

PRETORIAN must not assume that two channels broadcast the same event simply because they are sports channels.

### Primary source: EPG

Use available EPG information to identify matching events.

Compare relevant data such as:

- Event title.
- Team or participant names.
- Competition.
- Event start time.
- Event end time.
- Channel schedule.

Normalize common formatting differences where appropriate.

For example:

- Bayern München vs Borussia Dortmund
- Bayern - Dortmund
- FC Bayern v BVB

These may refer to the same event, but matching must be based on sufficiently reliable evidence.

### Future SPORT module integration

Once the SPORT module is implemented, it may provide additional event identifiers and schedule information.

Design the feature so this integration can be added later without rebuilding the fullscreen player.

### Matching confidence

Use conservative matching.

Do not display unrelated channels based on weak keyword similarity.

Avoid false matches caused by:

- Generic titles such as "Football Live".
- Broad competition names.
- Repeated highlight shows.
- Different matches within the same competition.
- EPG entries that are stale or incomplete.

If reliable identification is impossible, do not show alternatives.

---

## 9. DATA SOURCES AND LIMITATIONS

Search only among channels actually available through the user's configured IPTV playlists.

Do not fabricate channels.

Do not claim an external channel is available if it is not present in the user's playlist.

Do not automatically open external streaming applications.

Do not introduce new paid services or external account requirements.

If EPG information is unavailable or insufficient, silently omit the panel.

---

## 10. PERFORMANCE

Alternative matching must not slow down live TV playback.

Requirements:

- Perform matching asynchronously.
- Reuse existing EPG data and indexes.
- Avoid expensive repeated full-playlist scans.
- Cache valid event matches where appropriate.
- Invalidate cached matches when the current event changes.
- Do not block the UI thread.
- Do not interrupt the video decoder.
- Do not restart playback just to inspect alternatives.

The panel should appear promptly when matching data is already available.

If matching finishes after the Dock opens, the panel may appear when valid results are ready, without stealing focus or interrupting the user.

---

## 11. REMOTE CONTROL BEHAVIOR

Preserve all existing fullscreen remote mappings.

### UP / DOWN

Open the existing Options Dock.

When valid alternatives exist, the Alternative Channels panel appears automatically alongside the Dock.

### DPAD navigation

Allow logical focus movement between the Options Dock and the Alternatives panel.

The user must be able to reach the panel without opening another menu.

Do not steal initial focus from the existing Options Dock.

### OK on an alternative channel

Immediately switch to the selected channel.

No confirmation dialog.

No additional details screen.

No second OK required.

### BACK

BACK must follow the existing one-level navigation philosophy.

If the Alternatives panel has focus, BACK closes that panel or returns focus to the Dock according to the active navigation state.

If the Dock is active, BACK closes the Dock.

Do not trap the user inside the Alternatives panel.

---

## 12. CHANNEL SWITCHING

When the user selects an alternative channel:

1. Preserve the identity of the previously playing channel.
2. Close the Alternatives panel and Options Dock.
3. Switch to the selected IPTV stream.
4. Enter normal fullscreen playback.
5. Update channel information and EPG.
6. Reevaluate alternative channels for the newly selected event.

Do not open the TV Groups screen or Channel Browser unnecessarily.

Do not show a confirmation dialog.

### Playback failure

If the alternative stream fails to start, handle the failure gracefully.

Where technically feasible, offer or perform recovery to the previous working stream without leaving the player in a broken state.

Do not create an endless retry loop.

---

## 13. PANEL SIZE AND POSITION

The panel must remain compact.

Do not cover the entire right half of the television.

Do not overlap the bottom Options Dock.

Do not obscure important controls.

For longer lists:

- Use vertical scrolling.
- Keep a reasonable number of rows visible.
- Preserve focus visibility.
- Avoid growing the panel beyond the safe screen area.

The panel should adapt to Full HD and 4K displays.

Do not hardcode positions that work only at one resolution.

---

## 14. AUTOMATIC VISIBILITY AND LIFECYCLE

The panel is visible only when:

- Fullscreen live TV is active.
- The Options Dock is open.
- Valid alternatives exist.
- No higher-priority modal or submenu requires the same UI space.

The panel must disappear when:

- The Options Dock is dismissed.
- The user switches channels.
- Fullscreen playback ends.
- The player navigates to another screen.
- The current event changes and no valid alternatives remain.

Do not leave stale alternative channels visible.

---

## 15. INTERACTION WITH OTHER FLOATING MENUS

PRETORIAN already has floating menus for:

- EPG
- Picture
- Catch Up
- History
- More

The Alternatives panel must not overlap or interfere with these menus.

When the user opens a Dock submenu, dismiss or temporarily hide the Alternatives panel as needed.

When returning to the main Dock, it may reappear automatically if valid alternatives still exist.

Preserve the existing BACK behavior and focus restoration.

---

## 16. EXISTING FULLSCREEN PLAYER RULES

Do not modify the previously approved behavior:

- UP/DOWN opens the Options Dock.
- LEFT opens the current group's channel list.
- RIGHT opens the floating EPG.
- Short OK pauses/resumes only when supported.
- Long OK has no special fullscreen action.
- BACK returns one logical level.
- Fullscreen video continues playing behind overlays.
- No accidental Favorites toggling.
- No unnecessary playback interruption.

This feature must integrate into the existing architecture.

---

## 17. TEST SCENARIOS

### Valid alternatives

- [ ] User watches a live sports event.
- [ ] Multiple channels have reliable matching EPG data.
- [ ] UP/DOWN opens the existing Dock.
- [ ] Alternatives panel appears automatically.
- [ ] Panel contains only valid matching channels.
- [ ] Channel logos and names are correct.
- [ ] Quality labels are grounded in available metadata.
- [ ] Current channel is identified.
- [ ] DPAD can focus the panel.
- [ ] OK switches channels without confirmation.

### No alternatives

- [ ] No matching event data exists.
- [ ] Dock opens normally.
- [ ] No Alternatives panel appears.
- [ ] No empty placeholder appears.
- [ ] No additional Dock icon appears.

### Navigation

- [ ] BACK exits the panel correctly.
- [ ] BACK exits the Dock correctly.
- [ ] EPG still opens correctly.
- [ ] Catch Up still works.
- [ ] History still works.
- [ ] Picture settings still work.
- [ ] No focus traps.
- [ ] No overlap between floating menus.

### Performance

- [ ] No playback stutter caused by matching.
- [ ] No unnecessary stream restart.
- [ ] No excessive EPG scanning.
- [ ] No stale results after changing channels.
- [ ] No UI freezing on large playlists.

### Visual design

- [ ] Same PRETORIAN smoky-glass design.
- [ ] Compact right-side panel.
- [ ] Correct alignment above Dock.
- [ ] Thin silver-white focus indicator.
- [ ] No neon effects.
- [ ] No oversized popup.
- [ ] No modification to the six existing Dock options.

---

## 18. IMPLEMENTATION REPORT

After implementation, explain:

1. Which files were modified.
2. How same-event matching works.
3. Which EPG fields are used.
4. How false matches are avoided.
5. How alternative channels are cached.
6. How focus moves between Dock and panel.
7. How BACK behaves.
8. How playback failure is handled.
9. Which tests were completed.
10. Any limitations caused by missing EPG information.

Do not claim physical Android TV testing unless it was actually performed.

---

## 19. IMPLEMENTATION AUTHORIZATION

Save this specification with the other PRETORIAN requirements.

**Do not start implementing it solely because this instruction was received.**

The user will explicitly authorize implementation when the current development work is ready.

---

# FINAL NON-NEGOTIABLE RULES

**NO NEW SCREEN.**

**NO NEW OPTIONS DOCK ICON.**

**KEEP EXACTLY SIX EXISTING OPTIONS: FAVORITES, EPG, PICTURE, CATCH UP, HISTORY, MORE.**

**UP/DOWN OPENS THE DOCK AND AUTOMATICALLY SHOWS THE ALTERNATIVES PANEL ONLY WHEN VALID MATCHES EXIST.**

**NO MATCHES = NO PANEL.**

**OK SWITCHES DIRECTLY.**

**BACK EXITS NORMALLY.**

**USE THE EXISTING PRETORIAN DESIGN SYSTEM.**

**DO NOT BREAK EXISTING FULLSCREEN PLAYER BEHAVIOR.**

The goal is a seamless, elegant way to discover and switch between channels broadcasting the same event, without adding unnecessary complexity to PRETORIAN.