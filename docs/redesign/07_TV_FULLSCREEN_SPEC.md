# PRETORIAN — TV FULLSCREEN PLAYER
## FINAL MASTER DESIGN, UX, NAVIGATION & FUNCTIONAL SPECIFICATION
### Implementation Instructions for Claude — Android TV / Google TV

**Project:** PRETORIAN Media Center  
**Module:** Live TV — Fullscreen Player and Floating Controls  
**Target:** Android TV / Google TV  
**Reference resolution:** 1920 × 1080, 16:9  
**Input:** Three attached approved visual references  
**Design philosophy:** Minimalist, cinematic, remote-first, content-focused

---

# 0. READ FIRST — THREE VISUAL REFERENCES

I am attaching three images:

**IMAGE 1 — FULLSCREEN TV OPTIONS**

Shows a live television broadcast with a bottom information overlay and minimalist action icons.

**IMAGE 2 — FLOATING EPG**

Shows a compact, translucent EPG panel positioned above the bottom controls, horizontally centered on the television screen.

**IMAGE 3 — FLOATING HISTORY**

Shows a horizontal carousel of recently watched television channels, with a Clear History action.

Use these images as the visual foundation.

However, apply the following refinements to all three:

- The bottom action icons must be smaller than in the initial mockups.
- The icons must be grouped and centered, not stretched across the entire television width.
- Floating panels should be compact and minimally intrusive.
- No large opaque background panels.
- No unnecessary shadows or neon effects.
- Maintain the existing PRETORIAN silver-white focus treatment.
- Preserve as much visible television content as possible.

The written behavior and layout requirements below take precedence wherever a reference image shows an earlier visual variation.

Do not introduce a new design system.

Use the same design language as the approved PRETORIAN HOME, MOVIES, SERIES and TV Channel Browser.

---

# 1. CORE PHILOSOPHY

The PRETORIAN Live TV player must prioritize watching television.

**The video is the primary content.**

All interface elements are secondary and appear only when requested.

The player must feel like a modern premium television interface, not a traditional IPTV application overloaded with controls.

Core principles:

1. Fullscreen means fullscreen.
2. No permanent controls over video.
3. Small, minimalist icons.
4. All action icons grouped in the bottom center.
5. Floating menus always open upward.
6. Only one floating panel may be active at a time.
7. Channel information must never overlap an active options panel.
8. Remote navigation must be predictable.
9. Focus must remain stable.
10. Video and audio continue playing while menus are open, except when the user explicitly requests a supported playback operation.
11. No unnecessary navigation screens.
12. No fabricated provider capabilities.

---

# 2. PLAYER STATES

The fullscreen player has several clearly defined states.

## STATE A — PURE FULLSCREEN

Normal television viewing.

Display only the video.

No:

- Bottom navigation.
- EPG information.
- Channel description.
- Progress bar.
- Weather.
- Application logo.
- Additional PRETORIAN overlay.

Any channel logo already embedded in the broadcast remains part of the video.

The player must not add another duplicate channel watermark.

## STATE B — CHANNEL INFO OVERLAY

Triggered by a short OK press while watching normally.

Show a compact, translucent information overlay near the bottom.

Information may include:

- Channel number.
- Channel logo or name.
- Actual stream quality when known.
- Current time.
- NOW program.
- NOW start and end times.
- Thin EPG progress bar.
- NEXT program.
- NEXT start time.

If EPG data is missing, show a simple fallback without an empty progress bar.

The information overlay automatically disappears after approximately five seconds of inactivity.

## STATE C — FULLSCREEN OPTIONS

Triggered by a long OK press.

Show six small action icons in the lower center of the screen.

The action icons are:

**Favorites | EPG | Picture | Catch Up | History | More**

These are the only six primary fullscreen actions.

Do not add Audio, Subtitles or Stream Information as additional primary icons.

Those functions belong inside More.

## STATE D — FLOATING SUBMENU

Triggered by selecting an action that requires additional options.

Examples:

- EPG.
- Picture.
- Catch Up.
- History.
- More.

The corresponding submenu opens upward, above the bottom action row.

The bottom action row remains visible.

All unrelated overlays disappear.

---

# 3. CRITICAL OVERLAY EXCLUSIVITY RULE

This is a non-negotiable design requirement.

**Never allow multiple overlays to overlap.**

When a floating submenu opens:

- Hide the standard Channel Info Overlay.
- Hide NOW/NEXT information outside the submenu.
- Hide the standard EPG progress bar outside the submenu.
- Hide unrelated popups.
- Do not leave text underneath another panel.
- Do not allow two active submenus simultaneously.

Examples:

Opening EPG must hide the standard channel description.

Opening History must hide the standard channel description.

Opening Catch Up must hide the standard channel description.

Opening Picture must hide the standard channel description.

Opening More must hide the standard channel description.

Only the selected submenu and the six bottom action icons remain visible.

The television broadcast continues behind them.

## State management

Implement a single authoritative overlay state.

Conceptually:

NONE

CHANNEL_INFO

OPTIONS

FLOATING_EPG

FLOATING_PICTURE

FLOATING_CATCHUP

FLOATING_HISTORY

FLOATING_MORE

Avoid multiple independent visibility flags that can accidentally activate overlapping panels.

Switching between options must close the previous submenu before displaying the new one.

Do not allow duplicated dialogs or stale overlay elements.

---

# 4. BOTTOM ACTION BAR — EXACT DESIGN

The six primary actions are:

1. Favorites
2. EPG
3. Picture
4. Catch Up
5. History
6. More

## Placement

Position the action group near the bottom center of the television screen.

The group must be horizontally centered relative to the entire screen.

Not relative to the selected icon.

Not stretched across the entire screen.

Do not use six equally distributed full-screen-width columns.

The group should occupy only the width necessary to display six comfortable icon-and-label controls.

## Icons

Use small, modern, monochrome line icons.

Recommended symbols:

- Favorites — star.
- EPG — calendar.
- Picture — screen.
- Catch Up — replay/rewind clock.
- History — recent-history clock.
- More — three horizontal dots.

Catch Up and History must have visually distinguishable icons.

Use the same icon family and stroke weight.

## Labels

Display a short label beneath each icon.

Use restrained white typography.

Labels must remain legible at typical TV viewing distances.

Do not use oversized text.

## Focus

Only the focused icon receives:

- Subtle smoky-glass background.
- Thin silver-white border.
- Restrained highlight.
- Clear white icon and label.

All other icons remain transparent.

No neon glow.

No bright blue or purple.

No oversized focus scaling.

## Navigation

LEFT/RIGHT moves between the six actions.

OK activates the selected action.

BACK closes the action bar and returns to fullscreen playback.

When a submenu closes, restore focus to the icon that opened it.

---

# 5. FLOATING MENU GEOMETRY

All floating submenus follow the same visual and positioning rules.

## Upward opening

Every submenu opens upward from the bottom controls.

Never downward.

Never outside the screen.

Never behind the bottom icons.

## Centering

The floating panel must be horizontally centered relative to the full television viewport.

The panel is not required to align its center with the selected icon.

This is important because different icons occupy different positions in the bottom action row.

Use a shared screen-centered floating-menu container.

## Vertical positioning

Position the floating panel above the action row, leaving a consistent visual gap.

The panel must remain fully inside the safe viewing area.

The panel may extend toward the middle of the screen but must not cover more video than necessary.

## Adaptive sizing

Calculate width and height from available screen dimensions.

Do not hardcode geometry for one physical television size.

Use the same relative layout at 43", 55", 65", 75", 85" and larger screens.

Remember that physical screen size does not directly determine UI layout; resolution, density and safe insets do.

## Appearance

Use:

- Smoky charcoal transparency.
- Very subtle background blur if supported efficiently.
- Thin muted-gray borders.
- Small corner radius.
- Restrained shadows.
- Silver-white text.
- Consistent focus treatment.

Avoid:

- Large opaque black rectangles.
- Thick outlines.
- Bright accent colors.
- Oversized headers.
- Excessive animations.

## Scrolling

When content exceeds the visible panel height:

- Keep the panel itself stationary.
- Scroll only its internal content.
- Preserve stable focus.
- Do not resize the panel while scrolling.
- Do not jump unexpectedly between entries.

---

# 6. FAVORITES

Favorites is a direct action.

Pressing OK on Favorites immediately toggles the current channel's favorite status.

If not already a favorite:

**Add to Favorites.**

If already a favorite:

**Remove from Favorites.**

Do not open a separate submenu.

Do not require confirmation.

Show a brief, subtle confirmation message.

Examples:

Added to Favorites.

Removed from Favorites.

The star icon should reflect the current favorite state.

Favorite changes must persist.

They must immediately update:

- TV Favorites group.
- Relevant channel lists.
- Fullscreen favorite state.

Removing a channel from Favorites must not remove it from provider groups or viewing history.

---

# 7. FLOATING EPG

This is one of the signature PRETORIAN interface elements.

Use IMAGE 2 as the primary visual reference.

## Appearance

When the user activates EPG:

A compact translucent floating panel opens upward above the bottom icon row.

The panel is horizontally centered on the television screen.

Do not open a new fullscreen EPG page.

Do not display a traditional large grid covering the entire television.

Do not darken the whole video excessively.

## Header

Display a small heading:

**EPG — CHANNEL NAME**

For example:

EPG — NOVA BH

Use compact white typography.

## Visible programs

Display approximately three EPG entries at a time.

Example:

18:00–19:00 — Dnevnik

19:00–19:30 — Večernji program

19:30–20:15 — TV Magazin

Each entry may include:

- Start time.
- End time.
- Program title.
- Short description when available.
- NOW indicator for the current program.

Avoid large individual cards.

Use compact rows separated by subtle lines or spacing.

## Focus

The focused EPG row receives a thin silver-white outline or subtle smoky highlight.

The currently airing program may display a small NOW indicator.

**NOW status and remote focus must be independent.**

Do not confuse the currently airing program with the selected program.

## Scrolling

UP/DOWN moves through the available EPG entries.

The panel remains fixed.

Only program rows scroll.

Scrolling must be smooth and stable.

Do not jump focus to the bottom icons while the user is scrolling through programs.

## Selection

Pressing OK on an EPG program opens only actions that are actually supported.

For the current program:

- Show program information.
- Offer Watch from Start only when supported by reliable Catch Up or timeshift capability.

For past programs:

- Show program information.
- Offer Watch from Start when the provider offers that program in the archive.

For future programs:

- Show available program information.
- Do not invent recording, reminder or scheduling capabilities.

If only informational content is available, show a compact details view rather than an empty action menu.

## Missing EPG

If no EPG data exists:

Show:

**No EPG data available.**

Do not generate fictional schedules.

Do not show an empty timeline.

## BACK

BACK closes the EPG panel.

Focus returns to the EPG icon.

Another BACK closes the bottom controls.

---

# 8. FLOATING CATCH UP

Catch Up uses the same visual philosophy as Floating EPG.

It should look like a sibling component, not a different application.

## Availability

Catch Up must be enabled only when the current channel and provider actually support archive playback.

Do not infer availability solely from a channel name.

If Catch Up is unavailable:

- Keep the icon visibly disabled or show a brief unavailable message.
- Do not open an empty archive interface.
- Do not invent archive programs.

## Appearance

Use a compact translucent floating panel.

Open upward above the bottom icons.

Center the panel horizontally on the full screen.

Show approximately three archived program entries at once.

## Content

Display available historical programs with:

- Date.
- Start time.
- End time.
- Program title.
- Available archive status.

Example:

Yesterday · 19:00 — Dnevnik

Yesterday · 20:00 — TV Magazin

Sunday · 18:30 — Dokumentarni program

Use actual provider and EPG data.

Do not invent archive availability.

## Navigation

UP/DOWN scrolls through archive entries.

The panel stays stationary.

OK selects an available archived program.

If the provider supports playback, begin the archived program from the requested start position.

If multiple days are available, provide a compact date/day selector within the same floating interface.

Avoid opening a large calendar page unnecessarily.

## Playback transition

When archived playback starts:

Close the Catch Up panel.

Transition into the appropriate archive playback mode.

Do not continue presenting the content as live if the user is watching an archived program.

Use accurate seek and playback controls only where supported.

## BACK

BACK closes the Catch Up panel and returns focus to the Catch Up icon.

---

# 9. PICTURE

Picture opens a small vertical floating menu upward.

The exact options are:

1. Default
2. Fit
3. Fill
4. Stretch
5. 4:3

Do not add a redundant 16:9 option.

## Default

Use the player's normal source aspect-ratio behavior.

## Fit

Display the entire video within the available viewport.

Preserve aspect ratio.

Black bars may appear.

## Fill

Fill the available display while preserving aspect ratio.

Cropping is permitted when required.

## Stretch

Stretch the video to fill the entire viewport.

Do not preserve the original aspect ratio.

This option may distort the image.

It is intentional and useful for incorrectly formatted broadcasts.

## 4:3

Present the video using a 4:3 display aspect ratio, fitted within the television viewport.

Avoid unintended cropping.

## Selection

The currently active mode displays a checkmark.

The currently focused option has a separate silver-white focus indicator.

Active selection and focus must never be visually confused.

Apply the selected picture mode immediately.

Do not restart the stream unnecessarily.

## Persistence

Retain the selected picture mode according to the existing player settings architecture.

Do not introduce inconsistent picture behavior between TV channels.

If no prior persistence policy exists, use a consistent global Live TV picture setting and document that decision.

## BACK

Close Picture and return focus to the Picture icon.

---

# 10. HISTORY — LAST 10 CHANNELS

Use IMAGE 3 as the primary visual reference.

History is a new primary fullscreen action.

Its purpose is to provide immediate access to recently watched channels without leaving fullscreen playback.

## Maximum number

Show the last ten distinct watched channels.

Not ten duplicate entries.

If a previously watched channel is watched again, move it to the beginning of the list.

The list is ordered:

Most recently watched → oldest retained.

## Visual design

History opens a compact translucent floating panel above the bottom action row.

Center it horizontally on the screen.

Use a single horizontal row of small channel tiles.

Do not create a large poster gallery.

Each tile displays:

- Channel logo when available.
- Channel name.
- Optional actual quality label.

If a logo is unavailable, use the channel name as a clean fallback.

Keep tiles compact.

Avoid oversized square cards.

## Horizontal navigation

LEFT/RIGHT moves through the History channels.

The selected channel receives a subtle silver-white outline.

When the user reaches the final channel and presses RIGHT:

Move to the first channel.

When the user is on the first channel and presses LEFT:

Move to the last channel.

This is circular navigation.

## Scrolling

The carousel must support up to ten channels.

If not all channels fit at once:

Scroll horizontally inside the fixed panel.

Keep the panel itself stationary.

Do not clip partial tiles at the visible boundaries.

Keep focus stable.

## Channel selection

Pressing OK on a History channel:

1. Closes the History panel.
2. Tunes the selected channel.
3. Keeps playback in fullscreen.
4. Updates the recently watched order after successful playback.
5. Optionally shows the brief channel information overlay.

Do not return to the Channel Browser.

## Clear History

Provide a compact Clear History action inside the History panel.

The action must be accessible by remote control.

Pressing Clear History opens a small confirmation dialog:

**Clear recently watched channels?**

Cancel | Clear All

Cancel preserves history.

Clear All removes the stored recent-channel history.

Do not delete:

- Favorites.
- Provider channel lists.
- Playlist data.
- Channel configuration.

After clearing, the History panel should show a minimal empty-state message or close gracefully.

## Shared history

History must use the same underlying Live TV watch-history data as:

- TV Recently Watched group.
- HOME Recently Watched TV section.

Avoid maintaining contradictory independent histories.

The fullscreen History panel displays only the latest ten distinct channels.

The broader Recently Watched section may use the application's established history retention policy.

Clearing history must update every related view consistently.

---

# 11. MORE — ADDITIONAL STREAM OPTIONS

More opens a compact vertical floating menu upward.

The menu contains only relevant functions.

Potential entries:

**Audio Tracks**

**Subtitles**

**Stream Information**

## Audio Tracks

Show Audio Tracks only when the stream exposes selectable audio tracks.

Display actual available tracks.

Examples:

English.

Bosnian.

German.

Original.

Do not fabricate languages.

The selected audio track displays a checkmark.

Selecting another track applies it without restarting the stream when technically possible.

If there is only one fixed audio track and no meaningful choice, the Audio Tracks submenu may be omitted.

## Subtitles

Show Subtitles only when the provider or stream actually supplies supported subtitle tracks.

Include Off when applicable.

Show available subtitle languages.

Do not add online subtitle search for Live TV.

Do not reuse the MOVIES/SERIES online subtitle-search workflow.

If the stream has no subtitles, omit the Subtitles option.

## Stream Information

Stream Information must always be available as a diagnostic option.

Display actual known values, such as:

- Channel name.
- Stream resolution.
- Video codec.
- Frame rate.
- Video bitrate.
- Audio codec.
- Audio channels.
- Playback protocol.
- Buffer state.
- Stream status.

Do not fabricate technical values.

Unknown values may be omitted or displayed as unavailable.

Use a compact scrollable information panel if necessary.

Do not create an oversized diagnostics dashboard.

## More menu behavior

More opens upward.

Selecting Audio or Subtitles opens its corresponding compact submenu in the same floating-menu region.

Do not stack a second menu on top of the first.

Replace the previous popup content.

BACK from a nested submenu returns to More.

BACK again closes More and returns to the More icon.

---

# 12. REMOTE CONTROL — FULLSCREEN

The player must support reliable Android TV remote interaction.

## Normal fullscreen

**Short OK**

Show Channel Info Overlay.

**Long OK**

Open bottom action controls.

**UP**

Previous channel according to the current channel-navigation context.

**DOWN**

Next channel according to the current channel-navigation context.

**LEFT**

Open the translucent fullscreen channel list.

**RIGHT**

Open Floating EPG directly.

**BACK**

Return to the previous Channel Browser state.

## Channel Info Overlay

Short OK may dismiss or refresh the information overlay according to the established player behavior.

Long OK opens the bottom action controls.

BACK dismisses the overlay.

## Bottom action controls

LEFT/RIGHT changes focused action.

OK activates the action.

BACK closes controls.

## Floating vertical menus

UP/DOWN changes selected item.

OK activates the item.

BACK closes the submenu.

## Floating History

LEFT/RIGHT navigates the horizontal carousel.

UP/DOWN moves to other available History controls, including Clear History.

OK selects a channel or activates the focused control.

BACK closes History.

## Important

Remote input must be routed according to the active UI state.

For example:

When Floating EPG is open, UP/DOWN must scroll EPG entries, not change TV channels.

When Floating History is open, LEFT/RIGHT must navigate History tiles, not open another player overlay.

When Picture is open, UP/DOWN must navigate picture modes.

Prevent input from accidentally reaching the fullscreen player underneath an active submenu.

---

# 13. INFO OVERLAY TIMING

The normal Channel Info Overlay should disappear after approximately five seconds of inactivity.

However:

**Floating submenus must not disappear while the user is actively interacting with them.**

Do not close EPG while the user reads or scrolls.

Do not close Catch Up during archive selection.

Do not close Picture while adjusting the display.

Do not close History during channel selection.

Do not close More while choosing tracks.

The bottom controls should use a reasonable inactivity timeout when no submenu is open.

Reset the timeout on relevant remote interaction.

Cancel or suspend conflicting timers whenever the active overlay state changes.

---

# 14. ANIMATION

Use subtle, short animations.

## Opening a floating panel

A restrained upward reveal.

Small movement.

Gentle opacity transition.

No dramatic sliding across the screen.

No bouncing.

No neon glow.

## Closing

Reverse the same transition.

## Scrolling

Smooth, stable movement.

No focus jumping.

No sudden changes in panel size.

No flickering.

## Performance

Animations must not interrupt or restart video playback.

Prefer lightweight UI transitions.

The player must remain responsive on modest Android TV hardware.

---

# 15. VISUAL RESPONSIVENESS

All fullscreen controls must work across supported TV sizes and resolutions.

Do not position panels using arbitrary hardcoded coordinates.

Use viewport-based layout constraints.

## Bottom controls

Keep the six icons centered as a compact group.

Maintain appropriate spacing.

Do not spread them across the entire screen.

## Floating panels

Center horizontally.

Open upward.

Respect safe margins.

Limit width and height.

## EPG

Display approximately three program rows.

Scroll additional programs internally.

## History

Display only complete visible tiles.

Scroll additional channels horizontally.

## More and Picture

Keep vertical menus compact.

Avoid unnecessary blank space.

## Accessibility

Ensure text and focus remain readable from a normal television viewing distance.

Minimalist does not mean microscopic.

---

# 16. STREAM AND PROVIDER CAPABILITY RULES

PRETORIAN must never invent features that the current stream does not support.

Examples:

- Do not show unavailable audio tracks.
- Do not show nonexistent subtitle languages.
- Do not claim Catch Up is available when it is not.
- Do not fabricate EPG entries.
- Do not invent technical stream information.
- Do not invent quality badges.

Use real metadata and player capabilities.

If a feature is unavailable, hide or disable it according to the rules above.

Avoid dead controls.

---

# 17. DATA AND STATE CONSISTENCY

The fullscreen player must share data with the rest of PRETORIAN.

## Favorites

One consistent favorite state.

Changes in fullscreen immediately update the TV Favorites group.

## History

One consistent recent-channel history.

Fullscreen History, TV Recently Watched and HOME must agree.

## EPG

Use the same channel EPG source as the Channel Browser.

## Catch Up

Use the actual provider archive capabilities.

## Picture

Use the established playback settings architecture.

## Audio and subtitles

Use actual player track information.

Do not create separate conflicting data stores for each screen.

---

# 18. ERROR HANDLING

Handle:

- Stream loading.
- Stream unavailable.
- Network interruption.
- Missing EPG.
- Missing channel logo.
- Missing audio tracks.
- Missing subtitles.
- Unsupported Catch Up.
- Expired archive entries.
- Channel removed from playlist.
- History channel no longer available.
- Missing stream metadata.

Keep error messages brief and consistent with the PRETORIAN design.

Do not display raw technical errors to ordinary users unless they explicitly open Stream Information.

Never freeze remote navigation because a stream fails.

---

# 19. ACCEPTANCE TESTS

The implementation is accepted only when:

1. Normal fullscreen shows video only.
2. Short OK shows a minimal channel information overlay.
3. Long OK opens six centered action icons.
4. The six actions are in the exact approved order.
5. Icons are small and visually consistent.
6. The action group does not span the full television width.
7. Only the focused icon receives a silver-white focus indicator.
8. Every submenu opens upward.
9. Floating panels are horizontally centered on the screen.
10. Panels remain inside safe margins.
11. Opening any submenu hides the standard channel information overlay.
12. No overlay text overlaps another overlay.
13. Only one submenu is active at a time.
14. EPG shows approximately three visible program entries.
15. EPG UP/DOWN scrolling is smooth.
16. EPG selection does not change channels unintentionally.
17. Missing EPG uses a clean fallback.
18. Catch Up uses the same floating design.
19. Catch Up is available only when supported.
20. Picture includes Default, Fit, Fill, Stretch and 4:3.
21. Stretch fills the viewport even if proportions are distorted.
22. Picture mode selection updates immediately.
23. Favorites toggles with one OK press.
24. History shows a maximum of ten distinct channels.
25. History supports LEFT/RIGHT circular navigation.
26. History channel selection tunes directly in fullscreen.
27. Clear History requests confirmation.
28. Clearing history does not delete Favorites or provider channels.
29. More contains Audio, Subtitles and Stream Information as applicable.
30. Audio and subtitle options reflect actual stream capabilities.
31. Live TV has no online subtitle search.
32. BACK closes the current UI layer first.
33. Focus is restored after closing a submenu.
34. The playing stream continues behind menus.
35. Menu timers do not interrupt active interaction.
36. All controls remain usable at supported TV resolutions.
37. No unnecessary large panels or decorative effects are introduced.

---

# 20. FINAL DESIGN CONTRACT

**FULLSCREEN VIDEO FIRST.**

**SIX SMALL CENTERED ICONS.**

**FAVORITES — EPG — PICTURE — CATCH UP — HISTORY — MORE.**

**EVERY FLOATING MENU OPENS UPWARD.**

**FLOATING PANELS ARE CENTERED RELATIVE TO THE SCREEN.**

**ONLY ONE ACTIVE SUBMENU.**

**CHANNEL INFO HIDES WHEN A SUBMENU OPENS.**

**NO OVERLAPPING TEXT OR CONTROLS.**

**EPG SHOWS THREE PROGRAMS WITH INTERNAL VERTICAL SCROLLING.**

**CATCH UP USES THE SAME FLOATING DESIGN.**

**HISTORY SHOWS THE LAST TEN DISTINCT CHANNELS.**

**HISTORY USES HORIZONTAL CIRCULAR NAVIGATION.**

**PICTURE SUPPORTS STRETCH.**

**MORE CONTAINS AUDIO, SUBTITLES AND STREAM INFORMATION.**

**NO ONLINE SUBTITLE SEARCH FOR LIVE TV.**

**NO FAKE PROVIDER FEATURES.**

**NO VIDEO AUTOPLAY CAUSED BY FOCUS MOVEMENT.**

**BACK RETURNS ONE LOGICAL STEP.**

**KEEP THE EXISTING PRETORIAN DESIGN SYSTEM.**

---

# 21. IMPLEMENTATION HANDOFF

Claude, before making code changes:

1. Confirm receipt of all three reference images.
2. Confirm that the written specification was received completely.
3. Review the existing PRETORIAN player and navigation architecture.
4. Identify reusable fullscreen player components.
5. Identify existing EPG, Catch Up, Favorites, History, audio, subtitle and picture-mode capabilities.
6. Identify any technical conflicts or unavailable provider features.
7. Explain how you will prevent overlay collisions.
8. Explain how you will preserve remote focus and stable scrolling.
9. Propose an incremental implementation and testing order.
10. Do not replace or redesign already approved HOME, MOVIES, SERIES or TV Channel Browser components.

Keep implementation incremental and test each major fullscreen feature before proceeding.

**Do not begin this implementation until I explicitly authorize it.**