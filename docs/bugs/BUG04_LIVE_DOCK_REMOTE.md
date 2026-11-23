# PRETORIAN 2.4.2 — BUG FIX 04
## LIVE TV FULLSCREEN — CONTROL DOCK, REMOTE NAVIGATION & BACK STACK

**Priority:** Critical — Navigation and usability  
**Affected module:** Live TV Fullscreen Player  
**Reference image:** Attached N1 HD screenshot with the approved dark floating control dock  
**Scope:** Fix the existing fullscreen controls, remote mapping, overlay visibility, channel-list navigation and BACK behavior.

Claude, this is BUG 04 from our real Android TV testing.

The previous implementation has several problems. Some are visual, but the most important ones concern remote-control behavior.

We are changing the Live TV fullscreen control mapping. Please treat the following requirements as authoritative and replace the previous mappings wherever they conflict.

Do not change the approved MOVIES/SERIES remote behavior. These instructions apply specifically to **Live TV**.

---

# 1. CRITICAL — NEW REMOTE CONTROL MAPPING

The previous implementation used a long OK press to open the fullscreen options. This is inconvenient and has caused accidental actions.

We are removing that interaction.

## New fullscreen remote mapping

| Remote input | Required action |
|---|---|
| UP | Open the bottom fullscreen options dock |
| DOWN | Open the bottom fullscreen options dock |
| Short OK | Pause / resume Live TV when supported |
| Long OK | No special action |
| LEFT | Open the channel list for the currently active TV group |
| RIGHT | Open the Floating EPG |
| BACK | Close the current overlay or navigate exactly one level backward |

**UP and DOWN must NEVER switch channels while the player is in normal fullscreen viewing mode.**

This is a permanent change.

Remove the previous channel-switching behavior from these two buttons.

Do not keep the old behavior as a fallback.

Do not require a long press to access the options.

## Long OK safety

A long OK press must not:

- Open the options dock.
- Add the current channel to Favorites.
- Remove the current channel from Favorites.
- Trigger any other hidden action.
- Accidentally repeat the short OK action.

Handle key-down, key-up and long-press events correctly.

Do not allow one physical press to trigger multiple commands.

Favorites must only change when the user explicitly focuses and activates the Favorites control.

---

# 2. APPROVED VISUAL DESIGN — DARK FLOATING CONTROL DOCK

Use the attached N1 HD screenshot as the primary visual reference.

The old white transparent controls were almost invisible on bright TV broadcasts.

The new design must remain minimalist while maintaining reliable contrast on both bright and dark video scenes.

## Exact primary actions

The bottom dock contains exactly six actions in this order:

1. Favorites
2. EPG
3. Picture
4. Catch Up
5. History
6. More

Do not add extra primary icons.

## Position and dimensions

- Place the dock near the bottom of the fullscreen video.
- Center it horizontally relative to the entire screen.
- Keep all six actions grouped together.
- Do not stretch them across the entire television width.
- Use small, consistent monochrome icons.
- Display compact labels beneath the icons.
- Maintain adequate spacing for comfortable D-Pad navigation.
- Respect Android TV safe areas.
- Adapt to different screen resolutions and densities.

The dock should feel compact even on a 75-inch or larger television.

## Background

Use one shared dark smoky-glass container behind the six actions.

Recommended appearance:

- Dark charcoal/graphite.
- Approximately 80–88% opacity as an initial visual target.
- Subtle transparency.
- Optional lightweight background blur if supported efficiently.
- Soft rounded corners.
- Thin restrained border.
- No bright glow.
- No neon.
- No purple.
- No oversized individual buttons.

The attached screenshot shows the desired direction.

Do not introduce additional decorative elements.

## Focus state

Only the focused action receives:

- A thin silver-white outline.
- A slightly lighter smoky background.
- Clear white icon and label.

Avoid bright cyan or blue focus glows.

Use the approved PRETORIAN monochrome focus language.

The focused icon must be clearly distinguishable without becoming visually dominant.

---

# 3. OPENING AND CLOSING THE DOCK

## From pure fullscreen playback

Pressing either UP or DOWN must open the options dock.

Both buttons perform the same action.

The dock appears with a short, restrained transition.

The current television video continues behind it.

## Initial focus

When the dock opens, restore focus to the last-used primary action if that action is still available.

If there is no valid previous selection, focus Favorites by default.

Do not automatically activate the focused action.

## While the dock is open

LEFT and RIGHT navigate between the six primary actions.

OK activates the currently focused action.

BACK closes the dock and returns to pure fullscreen viewing.

UP/DOWN must not change channels while the dock is open.

They may be consumed by the dock or used only for explicitly defined navigation within the active overlay.

## Important

Opening the dock must not trigger a favorite change, playback restart or channel switch.

---

# 4. CRITICAL — NO OVERLAY COLLISIONS

The current implementation allows the standard channel description and control icons to overlap.

This makes text difficult to read.

Correct this using a single authoritative overlay-state system.

When the options dock opens:

- Hide the normal channel information overlay.
- Hide NOW/NEXT text outside the active panel.
- Hide the standard channel progress bar.
- Hide unrelated overlays.
- Keep the video playing behind the dock.

When an EPG, Catch Up, Picture, History or More submenu opens:

- Keep the bottom dock visible.
- Show only the selected floating panel above it.
- Hide all unrelated information.
- Never stack several panels over each other.

Only one submenu may be active at a time.

Use one coordinated overlay state rather than independent visibility flags that can accidentally overlap.

---

# 5. FLOATING SUBMENUS

The previously approved floating-menu design remains valid.

All submenus must:

- Open upward.
- Appear above the bottom dock.
- Be horizontally centered relative to the full screen.
- Remain within safe margins.
- Use dark translucent backgrounds.
- Use compact readable typography.
- Scroll internally when needed.
- Preserve stable remote focus.
- Never obscure one another.

## Favorites

One explicit OK press on the focused Favorites icon toggles the current channel's favorite state.

No confirmation dialog is needed.

Display a brief success message.

Favorites must not change because of long OK presses elsewhere in the player.

## EPG

Open the approved Floating EPG.

Show approximately three visible program entries.

UP/DOWN scrolls the EPG entries.

The panel stays stationary.

The current program is identified independently from the focused program.

Use real EPG data.

## Picture

Open the compact Picture menu.

Options:

- Default
- Fit
- Fill
- Stretch
- 4:3

Apply changes immediately.

Preserve the existing distinction between active selection and focused selection.

## Catch Up

Open the approved Floating Catch Up interface when supported.

Show actual available archived programs.

Do not invent archive availability.

If unsupported, keep the action disabled.

## History

Open the approved Floating History panel.

Show up to ten distinct recently watched channels.

LEFT/RIGHT navigates the history carousel with circular wrapping.

OK tunes the selected channel.

Clear History requires confirmation and updates shared history data.

## More

Open the compact More menu.

Available functions:

- Audio Tracks, when selectable tracks exist.
- Subtitles, when stream-provided subtitles exist.
- Stream Information.

No online subtitle search for Live TV.

---

# 6. LEFT — OPEN CHANNEL LIST OVER FULLSCREEN VIDEO

This is a fundamental navigation requirement.

While watching Live TV in fullscreen, pressing LEFT must open a translucent channel list.

**The list must contain channels from the currently active TV group.**

Examples:

- If playback was started from Bosnia, show Bosnia channels.
- If playback was started from Germany, show Germany channels.
- If playback was started from Favorites, show Favorites.
- If playback was started from Recently Watched, show that group's channels.
- If playback was started from a provider-defined sports group, show that group's channels.

Do not automatically switch to All Channels.

Do not mix unrelated groups.

## Visual appearance

Use the approved PRETORIAN Channel Browser design language.

The list should be a compact translucent overlay on the left side of the playing video.

Do not create a second fullscreen page.

Do not display huge cards.

Each channel row should contain:

**Channel Number | Logo | Channel Name | Quality**

Use real quality metadata where available.

Example:

01 — BHT 1 — HD

02 — Federalna TV — FHD

03 — Nova BH — HD

04 — Hayat — HD

The list should be vertically scrollable.

The focused row receives a subtle silver-white highlight.

The currently playing channel must be identifiable independently from the focused row.

## Opening behavior

When LEFT is pressed:

1. Hide the standard channel information overlay.
2. Hide the bottom options dock if open.
3. Open the channel list.
4. Focus the currently playing channel if it exists in the active group.
5. Keep the current video playing.
6. Do not automatically tune another channel.

## Channel navigation

UP/DOWN moves through the channels inside the open list.

These buttons navigate the list only.

They must not change the playing channel simply because focus moved.

The list must scroll smoothly.

Avoid:

- Focus jumping.
- Flickering.
- Sudden scroll resets.
- Unnecessary player reloads.
- Accidental channel tuning.

## Channel selection

When OK is pressed on a channel:

1. Tune the selected channel.
2. Close the channel list.
3. Remain in fullscreen playback.
4. Update the shared Recently Watched history after successful playback.
5. Preserve the active TV group context.

## Channel-list BACK

When BACK is pressed while the channel list is open:

Close the channel list.

Return to the same fullscreen channel that was playing before the list opened.

Do not jump directly to TV Groups.

Do not exit the player.

Do not change channels.

---

# 7. CRITICAL — PRESERVE THE ACTIVE TV GROUP

The fullscreen player must know which group the user entered from.

The active group is part of the navigation context.

For example:

TV → Provider Groups → Bosnia → Channel Browser → Nova BH → Fullscreen

The player must retain:

- The originating group identity.
- The originating group type.
- The relevant channel collection.
- The selected channel.
- The Channel Browser scroll position.
- The Channel Browser focus position.
- The previous navigation hierarchy.

This context must survive:

- Opening and closing the bottom dock.
- Opening and closing EPG.
- Opening and closing History.
- Opening and closing the channel list.
- Switching channels inside the current group.
- Returning from fullscreen to the Channel Browser.

Do not rebuild navigation from the HOME screen after every BACK press.

Do not discard the originating group.

If the underlying playlist changes and the original group no longer exists, handle that explicitly without crashing or inventing an unrelated group.

---

# 8. BACK — ONE LOGICAL STEP AT A TIME

This is one of PRETORIAN's most important global navigation rules.

**BACK always closes the current layer or returns exactly one navigation level.**

Never skip several levels.

Never jump directly to HOME unless HOME is actually the previous navigation destination.

Never exit the application while an internal navigation level is still open.

## Example A — Fullscreen with an EPG panel

Navigation:

TV Groups → Bosnia → Channel Browser → Fullscreen → Options Dock → Floating EPG

BACK sequence:

1. First BACK closes Floating EPG and returns focus to EPG in the dock.
2. Second BACK closes the Options Dock and returns to fullscreen video.
3. Third BACK leaves fullscreen and returns to the Bosnia Channel Browser.
4. Fourth BACK returns to TV Groups.
5. Fifth BACK returns to the previous main navigation level, normally the main menu with TV selected.

Each BACK performs exactly one transition.

## Example B — Fullscreen with channel list

Navigation:

TV Groups → Bosnia → Channel Browser → Fullscreen → Channel List Overlay

BACK sequence:

1. First BACK closes Channel List Overlay.
2. Second BACK exits fullscreen to Bosnia Channel Browser.
3. Third BACK returns to TV Groups.
4. Fourth BACK returns to the main navigation menu.

The current channel must continue playing when only the overlay is closed.

## Example C — Nested More menu

Navigation:

Fullscreen → Options Dock → More → Audio Tracks

BACK sequence:

1. Audio Tracks → More.
2. More → Options Dock.
3. Options Dock → Fullscreen.
4. Fullscreen → Channel Browser.

Do not close all four layers with one BACK press.

## Example D — Main category hierarchy

The previously approved replacement-navigation philosophy still applies.

Main Menu → TV Groups → Channel Browser → Fullscreen

Each selection replaces the previous navigation screen.

The previous screen must not remain partially visible behind the new one.

BACK restores exactly the preceding screen.

The prior focus and scroll position should be preserved.

---

# 9. SHORT OK — LIVE TV PAUSE / RESUME

We are assigning short OK in pure fullscreen to playback pause/resume.

However, Live TV pause requires careful handling.

## Required behavior

If the current playback mode genuinely supports pausing and resuming from the same position:

- First short OK pauses playback.
- Second short OK resumes playback.
- Preserve the playback position.
- Do not skip silently to the current live edge.

If the stream or player does not support reliable pause/resume:

- Do not pretend that timeshift is available.
- Do not display a misleading paused state.
- Show a brief unobtrusive message such as "Pause unavailable for this channel."

Do not treat stopping and restarting a live stream as equivalent to true timeshift.

## Important input priority

Short OK pauses/resumes only when pure fullscreen playback has focus.

When the dock is open, OK activates the focused dock action.

When the channel list is open, OK selects the focused channel.

When EPG is open, OK selects the focused EPG entry.

When History is open, OK selects the focused History channel or action.

The active UI state determines what OK does.

---

# 10. RIGHT — DIRECT FLOATING EPG

While watching pure fullscreen video, pressing RIGHT opens the Floating EPG directly.

The user does not need to open the bottom dock first.

The EPG uses the same shared Floating EPG component as the dock's EPG action.

Avoid implementing two different EPG interfaces.

When opened directly by RIGHT:

- Show the floating EPG above the lower screen area.
- Hide the normal channel information overlay.
- Preserve the video.
- Focus the relevant EPG entry.
- BACK closes the EPG and returns directly to pure fullscreen.

When opened through the bottom dock:

- BACK closes EPG and returns to the dock with EPG focused.

**The return destination depends on how the EPG was opened.**

Do not create an invisible extra dock navigation layer when opening EPG directly with RIGHT.

---

# 11. FOCUS MANAGEMENT

Remote focus must be deterministic.

## Bottom dock

Remember the last focused action.

Opening the dock restores that focus when valid.

## Channel list

Initially focus the currently playing channel when available.

Moving focus must not automatically tune channels.

## EPG

UP/DOWN scrolls EPG entries.

Do not allow those keys to change channels.

## Picture

UP/DOWN selects picture modes.

Do not allow those keys to change channels.

## History

LEFT/RIGHT navigates history channels.

Do not let these keys trigger the fullscreen channel-list or EPG shortcuts.

## BACK

After closing a submenu, restore focus to the control that opened it.

After returning from fullscreen, restore Channel Browser focus and scroll position.

Avoid automatic focus jumps to unrelated controls.

---

# 12. INPUT EVENT SAFETY

The current long-press behavior caused accidental Favorites actions.

Please investigate input handling carefully.

Requirements:

1. Distinguish short press from long press correctly.
2. Do not dispatch a second action on key release after consuming a long press.
3. Avoid repeated key-down events triggering repeated actions.
4. Prevent one remote input from reaching multiple UI layers.
5. Consume directional input when a submenu has focus.
6. Prevent double-tuning caused by repeated OK events.
7. Avoid rapid overlay opening/closing from a single key event.
8. Ensure BACK is processed by the topmost active navigation layer only.

No hidden long-OK favorite shortcut may remain.

---

# 13. PERFORMANCE

All overlays must remain responsive on Android TV hardware.

Requirements:

- Smooth channel-list scrolling.
- Stable focus.
- No video restart when opening menus.
- No unnecessary player recreation.
- No repeated EPG data reload on every focus change.
- No full-screen flicker when toggling the dock.
- No excessive blur or expensive visual effects.
- No memory leaks from repeated overlay creation.
- Proper cleanup of input handlers and UI state.

The playing video should remain the visual background throughout overlay interactions.

---

# 14. ACCEPTANCE TESTS

Please verify the following:

### Remote controls

- [ ] UP opens the bottom dock.
- [ ] DOWN opens the bottom dock.
- [ ] UP does not switch channels in pure fullscreen.
- [ ] DOWN does not switch channels in pure fullscreen.
- [ ] Long OK does not open the dock.
- [ ] Long OK does not add a channel to Favorites.
- [ ] Short OK uses the supported pause/resume behavior.
- [ ] LEFT opens the current group's channel list.
- [ ] RIGHT opens Floating EPG.
- [ ] BACK always closes one logical layer.

### Visual design

- [ ] Dock remains readable on a bright white broadcast.
- [ ] Dock remains readable on a dark broadcast.
- [ ] Six icons are centered and compact.
- [ ] Icons and labels are legible.
- [ ] Focus is silver-white rather than bright blue.
- [ ] No channel description overlaps the dock.
- [ ] No EPG text overlaps another panel.
- [ ] Floating menus open upward.
- [ ] Only one submenu is active.

### Channel list

- [ ] LEFT opens the correct originating group.
- [ ] Currently playing channel is identifiable.
- [ ] UP/DOWN navigates without tuning.
- [ ] Channel list scrolling is smooth.
- [ ] OK tunes the selected channel.
- [ ] BACK closes only the channel list.
- [ ] Group context is preserved after switching channels.

### Navigation

- [ ] EPG → Dock → Fullscreen → Channel Browser → TV Groups works with successive BACK presses.
- [ ] Channel List → Fullscreen → Channel Browser → TV Groups works with successive BACK presses.
- [ ] Audio Tracks → More → Dock → Fullscreen works correctly.
- [ ] Returning to Channel Browser restores focus and scroll position.
- [ ] No BACK press unexpectedly jumps to HOME.
- [ ] No BACK press exits the app while internal navigation layers remain open.

### Stability

- [ ] Opening menus does not restart the video.
- [ ] No accidental Favorites changes.
- [ ] No duplicated remote actions.
- [ ] No unexpected focus jumps.
- [ ] No new regressions in MOVIES or SERIES.

---

# 15. IMPLEMENTATION SCOPE

Fix BUG 04 only.

Do not redesign TV Groups.

Do not change the already approved Channel Browser layout.

Do not modify unrelated MOVIES or SERIES functionality.

Reuse existing player, EPG, Favorites, Catch Up, History and channel-list components where possible.

Correct the input routing, navigation state and overlay rendering instead of building duplicate systems.

After implementation, report:

1. The root causes of the previous remote-control bugs.
2. How long-OK accidental Favorites activation was eliminated.
3. How UP/DOWN behavior was remapped.
4. How the active channel group is preserved.
5. How the BACK stack is implemented.
6. Which files were modified.
7. Which tests were completed.
8. What still needs testing on the physical Android TV device.

**Do not claim device-level verification unless the corrected build has actually been tested on the television.**

After BUG 04 is implemented, stop and wait for BUG 05.

---

# FINAL NON-NEGOTIABLE RULES

**UP = OPEN OPTIONS.**

**DOWN = OPEN OPTIONS.**

**UP/DOWN NEVER SWITCH CHANNELS IN PURE FULLSCREEN.**

**SHORT OK = PAUSE / RESUME WHEN SUPPORTED.**

**LONG OK = NO SPECIAL ACTION.**

**LEFT = CHANNEL LIST OF THE CURRENT GROUP.**

**RIGHT = FLOATING EPG.**

**BACK = EXACTLY ONE LOGICAL STEP BACK.**

**NO ACCIDENTAL FAVORITES ACTIONS.**

**SIX COMPACT CENTERED ICONS.**

**DARK TRANSLUCENT CONTROL DOCK.**

**NO OVERLAPPING OVERLAYS.**

**NO AUTOPLAY WHEN MOVING FOCUS THROUGH CHANNEL LISTS.**

**PRESERVE CHANNEL GROUP, FOCUS AND SCROLL POSITION.**

**DO NOT CHANGE THE REST OF PRETORIAN.**