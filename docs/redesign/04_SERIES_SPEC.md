# PRETORIAN — SERIES REDESIGN
## FINAL MASTER UX, NAVIGATION & PLAYER SPECIFICATION
### Instructions for Claude — Android TV / Remote-First Experience

**Project:** PRETORIAN Media Center  
**Module:** SERIES / TV Shows  
**Status:** Approved design specification — documentation and preparation only  
**Visual references:** All attached approved PRETORIAN screenshots

---

# 0. IMPORTANT — READ BEFORE DOING ANYTHING

This document consolidates the complete approved SERIES design and interaction requirements.

The attached screenshots are the **canonical visual references**. They define the actual appearance of the application, including layout, colors, transparency, typography, icons, focus states, shadows, spacing and cinematic backgrounds.

**Do not redesign the screenshots. Reproduce their design language consistently.**

The existing MOVIES design system is the foundation for SERIES. Shared elements must use shared components rather than separate implementations.

Your immediate task is to:

1. Read this complete specification.
2. Inspect the attached screenshots.
3. Save the specification alongside the existing redesign documentation.
4. Identify reusable MOVIES components and any technical conflicts.
5. Prepare an implementation plan.

**Do not modify application code until explicitly authorized.**

---

# 1. THE MOST IMPORTANT PRETORIAN PRINCIPLE

## ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT

This is a non-negotiable navigation rule throughout PRETORIAN.

**Whenever the user enters a new navigation level, the previous menu disappears completely.**

Never show multiple navigation levels simultaneously.

Never leave a previous category list partially visible beside a new list.

Never stack category sidebars.

Never create unnecessary intermediate screens.

The entire application must feel like one continuous, predictable navigation system.

## Example

The user starts at HOME and selects SERIES.

The HOME navigation is replaced by the SERIES category list.

The SERIES category list may contain:

- Recently Added
- Continue Watching
- Favorites
- My List
- Genres
- Provider Groups
- Provider-defined categories
- Other dynamically imported collections

The user selects Recently Added.

**The entire category list disappears.**

Only the Recently Added series poster gallery is displayed.

The user selects a series.

The gallery disappears and the dedicated single-series screen appears.

The user selects an episode.

The single-series screen disappears and fullscreen playback begins.

No previous navigation level remains visible.

---

# 2. UNIVERSAL NAVIGATION CONTRACT

The exact same behavior must apply to **every SERIES category**, without exceptions.

Examples:

- Recently Added
- Continue Watching
- My List
- Favorites
- Drama
- Comedy
- Science Fiction
- Documentary
- Netflix provider groups
- Apple TV provider groups
- Other IPTV provider groups
- Dynamically imported provider categories
- Search results containing series

No category may implement a different navigation structure, gallery or series-details screen.

## Navigation hierarchy

The normal flow is:

**HOME → SERIES → CATEGORY → SERIES POSTER GALLERY → SINGLE SERIES VIEW → FULLSCREEN EPISODE PLAYER**

However, do not create artificial extra levels.

If a category such as Recently Added or a provider group is already directly selectable in the SERIES category list, selecting it opens its poster gallery immediately.

If the provider data genuinely contains nested groups, navigate those groups using the same complete-replacement principle.

Only actual navigation levels should exist.

## BACK behavior

BACK always reverses **exactly one logical navigation step**.

Examples:

- Fullscreen player → Single Series View
- Single Series View → Series Poster Gallery
- Series Poster Gallery → Previous Category List
- Category List → SERIES entry context
- SERIES → HOME

If an overlay or floating menu is open, BACK closes that overlay first.

Do not jump multiple levels.

Do not return directly to HOME from an inner screen.

Do not reset the user's position.

## State restoration

When returning with BACK, restore:

- Previous category.
- Previous focused category.
- Previous poster.
- Previous gallery row.
- Previous scroll position.
- Previously selected season.
- Previously focused episode.
- Previous episode-row position.
- Appropriate playback progress.

The user must feel that they are returning to exactly where they left off.

---

# 3. UNIVERSAL COMPONENT ARCHITECTURE

Use shared reusable components for all SERIES categories.

The preferred conceptual architecture is:

- SeriesCategoryList
- SeriesPosterGallery
- SeriesDetailsScreen
- SeasonSelector
- EpisodeCarousel
- EpisodeContextOverlay
- WatchedProgressIndicator
- SharedFullscreenPlayer
- SharedPlayerControls
- SharedFloatingMenu
- SharedSubtitleMenu
- SharedAudioMenu
- SharedPlaybackMenu
- SharedPictureMenu
- UpNextOverlay

These are conceptual component responsibilities, not mandatory class names.

Adapt to the existing project architecture.

Do not create duplicate implementations where MOVIES components already provide the required behavior.

---

# 4. SERIES CATEGORY LIST

When SERIES opens, show its available categories.

Use the existing PRETORIAN navigation design.

When the user presses OK on a category:

1. Confirm the selected category.
2. Remove the category navigation completely.
3. Open that category's poster gallery.
4. Update the breadcrumb.
5. Move focus into the gallery.
6. Preserve the previous category focus for BACK restoration.

Use real provider and application data.

Do not hardcode fictional categories into production.

Provider-defined categories must be supported dynamically.

---

# 5. SERIES POSTER GALLERY

Reuse the approved MOVIES poster-gallery design.

## Layout

At 1920×1080:

- One complete primary poster row at a time.
- Approximately six large 2:3 posters per row.
- Consistent poster spacing.
- Full poster artwork without distortion.
- Series title beneath each poster.
- Small rating badge when available.
- Cinematic backdrop and subtle charcoal gradients.
- Appropriate TV safe margins.

Do not introduce a different SERIES gallery layout.

## Focus appearance

The focused poster receives:

- Thin silver-white border.
- Slight scale increase.
- Subtle upward lift.
- Soft restrained shadow.
- Smooth animation.

No neon, purple or blue glow.

## Remote navigation

LEFT/RIGHT navigates posters.

UP/DOWN navigates poster rows.

Vertical transitions should be smooth.

Do not add unnecessary row-navigation buttons.

Preserve the user's horizontal focus position when possible.

## Focus versus confirmation

**Browsing focus and confirmed selection are different states.**

Moving between posters must not continuously replace the hero information.

Pressing OK confirms the series.

The gallery information updates to the confirmed series.

The appropriate action then opens the dedicated single-series view.

Selecting a poster must not automatically start an episode.

---

# 6. SINGLE SERIES SCREEN — VISUAL DESIGN

Use the approved THE LAST HORIZON screenshot as the canonical layout reference.

The screen must feel like the MOVIES single-title screen.

## Background

Use the selected series' cinematic backdrop across the entire screen.

Apply a subtle charcoal gradient on the left for readability.

Preserve the artwork's cinematic composition.

No large opaque information panels.

No separate poster-detail sidebar.

## Header

Top-left:

SERIES / CATEGORY / SERIES TITLE

Top-right:

Existing PRETORIAN weather, time and notification indicators where supported.

Keep their alignment and design consistent with MOVIES.

## Information

Display available metadata:

- Series title.
- Release year or year range.
- Number of seasons.
- Genres.
- Rating.
- Short synopsis.
- Cast.
- Creator or director.

Do not fabricate missing metadata.

Keep the synopsis concise and readable from a television viewing distance.

## Main actions

**WATCH / RESUME | TRAILER | MY LIST**

### Watch / Resume

If the series has never been watched, offer the first available episode.

Example:

WATCH S01E01

If an episode has valid saved progress, show:

RESUME S02E04

Use the real episode identifier and saved playback position.

### Trailer — universal MOVIES and SERIES rule

The Trailer button must use the recognizable **red YouTube Play logo**.

Requirements:

- Red rounded rectangle.
- White triangular Play symbol.
- Positioned to the left of TRAILER.
- Same size and alignment across MOVIES and SERIES.
- Button background remains transparent charcoal.
- The red YouTube logo is the intentional color exception.

Never replace the YouTube logo with a generic monochrome play icon.

If no trailer is available, use the application's consistent unavailable-action behavior.

### My List

One-click toggle.

OK adds or removes the series.

Show a checkmark when added.

No confirmation dialog.

Persist the state.

---

# 7. WATCH PROGRESS ON THE SERIES SCREEN

Show a thin silver-white progress bar for the current partially watched episode.

Use the same styling as MOVIES.

Display elapsed and remaining time when reliable duration information is available.

Never invent progress.

Do not show a misleading progress bar for a series that has never been played.

Keep the indicator approximately 3–4 pixels high.

The progress bar must remain subtle and must not dominate the screen.

---

# 8. FOUR SETTINGS ICONS — APPROVED SERIES POSITION

Display exactly four settings icons:

**SUBTITLES | AUDIO | PLAYBACK | PICTURE**

On the SERIES details screen, position these icons in the available right-side area, above the episode carousel and near the progress-bar region, as shown in the approved updated mockup.

This position is intentional.

It gives the episode row more usable horizontal and vertical space.

## Visual requirements

The icons must be identical to MOVIES:

- Same monochrome shapes.
- Same line thickness.
- Same muted-silver colors.
- Same transparency.
- Same focus background.
- Same border treatment.
- Same labels.
- Same spacing and alignment philosophy.
- Same animation.

Do not place the icons inside oversized cards.

Do not introduce a new toolbar style.

The four icons should appear lightweight and nearly transparent over the cinematic background.

---

# 9. EPISODE CAROUSEL — ONE HORIZONTAL ROW ONLY

This is the approved final episode layout.

**Every season displays its episodes in ONE horizontally scrollable row.**

Never use a two-row episode grid.

At the reference resolution, approximately five episode thumbnails should be visible.

## Episode card

Each episode displays:

- 16:9 thumbnail.
- Episode number.
- Episode title.
- Watched indicator when applicable.
- Watch-progress indicator when applicable.

Use actual provider episode artwork when available.

If artwork is missing, use a consistent neutral fallback.

## Focus

The focused episode receives:

- Thin silver-white outline.
- Slight enlargement.
- Subtle lift.
- Restrained shadow.

The focus must remain clear without obscuring neighboring episode titles.

## Horizontal scrolling

LEFT moves to the previous episode.

RIGHT moves to the next episode.

Scrolling must be smooth, controlled and visually predictable.

The row should move naturally while maintaining a stable focus target.

## Circular navigation — mandatory

When the user is on the final episode and presses RIGHT:

**Wrap to the first episode.**

When the user is on the first episode and presses LEFT:

**Wrap to the final episode.**

This must work in both directions.

Use a smooth, predictable transition.

Do not duplicate episode data or change the underlying episode order.

Do not create artificial episodes to fill the carousel.

## State preservation

When returning from an episode overlay or fullscreen playback, restore the same episode focus and horizontal scroll position.

---

# 10. SEASON SELECTOR

Position the compact season selector above the episode row, aligned to the right.

Example:

**SEASON 2 ▾**

Pressing OK opens a small transparent dropdown.

Do not open a new full-screen season-selection page.

## Available seasons

Show only seasons actually available from the provider.

UP/DOWN moves between seasons.

OK selects a season.

BACK closes the dropdown.

When a new season is selected:

1. Update the episode row.
2. Keep the user on the same series screen.
3. Close the dropdown.
4. Restore focus to the season selector.
5. Do not open another navigation level.

## Season actions

The season selector also provides:

- Mark Season as Watched
- Mark Season as Unwatched

These actions apply only to the currently selected season.

### Mark Season as Watched

Mark every episode in the selected season as watched.

Display ✓ on every episode.

Fill the white progress bar for every episode.

Persist the change.

### Mark Season as Unwatched

Remove watched checkmarks for that season.

Reset its episode watch progress.

Persist the change.

Do not modify other seasons.

Do not mark the entire series watched unless a separate series-level action is explicitly approved.

---

# 11. EPISODE WATCHED AND PROGRESS STATES

There are three visual states.

## Unwatched

No completed checkmark.

No fabricated watch progress.

## Partially watched

Show a thin white progress bar representing actual playback progress.

No completed checkmark.

## Watched

Show a small silver-white ✓ on the episode thumbnail.

Show a fully completed white progress bar.

Use exactly the same progress and focus design throughout SERIES.

Watched indicators must be visible but discreet.

Do not cover important thumbnail artwork unnecessarily.

---

# 12. SHORT OK — EPISODE PLAYBACK

A normal short OK press on a focused episode starts or resumes that episode.

If valid progress exists, resume from that position.

Otherwise start from the beginning.

Do not open unnecessary confirmation dialogs.

Do not require a separate episode information screen before playback.

---

# 13. LONG OK — EPISODE DESCRIPTION AND ACTIONS

A long press on an episode opens a compact transparent episode-information overlay.

This was explicitly approved.

The purpose is to let the user quickly read the episode synopsis and remember whether they have watched it.

## Overlay content

Display:

- Episode number.
- Episode title.
- Short episode synopsis.
- Runtime when available.
- Watched or progress state.

## Actions

Display:

**WATCH / RESUME**

And one relevant watched-state action:

- Mark as Watched, if the episode is not completed.
- Mark as Unwatched, if the episode is completed.

Never display both watched-state actions simultaneously.

## Navigation

UP/DOWN navigates actions.

OK activates the selected action.

BACK closes the overlay.

Focus returns to the exact episode that opened it.

The episode carousel must not jump, reset or scroll unexpectedly.

## Visual style

Use the same smoky-glass transparency as the MOVIES floating menus.

No large opaque information screen.

No new full-screen navigation level.

Keep the overlay compact, elegant and readable.

---

# 14. UNIVERSAL FLOATING-MENU SYSTEM

All settings menus must reuse the MOVIES floating-menu component.

This applies to both:

- Single Series View.
- Fullscreen Episode Player.

The same visual design must be used everywhere.

## Mandatory styling

- Nearly transparent smoky-charcoal background.
- Subtle blur.
- Thin low-opacity border.
- Soft shadow.
- White primary text.
- Muted-silver secondary text.
- Subtle translucent focus strip.
- Small active-state checkmark.
- Identical corner radii.
- Identical spacing and typography.

No large opaque menus.

No heavy borders.

No neon.

No unnecessary decorative panels.

## Mandatory opening direction

**ALL FOUR SETTINGS MENUS MUST OPEN UPWARD. NEVER DOWNWARD.**

The bottom edge stays anchored just above the corresponding icon.

Additional items extend the menu upward.

If there is insufficient space, constrain menu height and scroll the content internally.

The menu may shift horizontally to remain within the TV safe area.

Opening a menu must not move the underlying series artwork, video, progress bar, episode row or other controls.

## Focus versus active state

Focused item: translucent horizontal highlight.

Active item: small ✓.

Moving focus does not activate a setting.

OK activates the focused item.

BACK closes the floating menu and restores focus to its original icon.

---

# 15. UNIFIED SUBTITLES MENU

Reuse the exact MOVIES subtitle menu.

**Provider and online subtitles must appear inside ONE unified floating menu.**

Do not create a separate Online Subtitles screen.

## Layout

Title:

Subtitles

Top-right language selector:

English ▾

Section:

PROVIDER

- Off.
- Actual provider subtitle tracks.

Section:

ONLINE · ENGLISH

- Eng ver.1
- Eng ver.2
- Eng ver.3
- Additional results when available.

## Series-specific subtitle matching

Use:

- Series title.
- Season number.
- Episode number.
- Available external IDs.
- Release metadata where available.

Never mix subtitle results from different episodes.

Use only supported and legally accessible subtitle services.

## Language selection

English is the default online subtitle search language.

Remember the user's chosen language.

Keep language selection inside the same floating menu.

Do not open an additional full-screen language page.

## Navigation and scrolling

UP/DOWN moves one item at a time.

Long lists scroll smoothly inside the floating menu.

Do not reset focus.

Do not reorder visible results while the user is navigating.

The active subtitle has ✓.

The focused subtitle has the translucent selection highlight.

## Applying subtitles

OK selects the subtitle.

Provider subtitles activate directly.

Online subtitles load through supported methods.

Only update the active checkmark after successful activation.

If loading fails, preserve the previous active subtitle and display a small error.

Never restart the episode to change subtitles.

---

# 16. AUDIO MENU

Reuse the MOVIES audio menu.

Display only actual audio tracks supplied by the current stream.

Language, codec and channel information may be shown when known.

Never fabricate tracks.

Use ✓ for the active track.

Use the approved focus highlight for navigation.

Switch tracks directly without leaving playback.

---

# 17. PICTURE MENU

Reuse the MOVIES picture menu.

Exactly five modes:

- Default
- Fit
- Fill
- Stretch
- 4:3

Do not add a separate 16:9 option.

Apply supported changes directly.

Show ✓ on the active picture mode.

---

# 18. SERIES PLAYBACK MENU

Reuse the MOVIES floating-menu styling.

The SERIES Playback menu contains:

## Restart Episode

Restart the current episode from the beginning.

## Playback Speed

Default: Normal · 1.0×

Supported choices may include:

0.75× / 1.0× / 1.25× / 1.5× / 2.0×

Only expose options actually supported by the player.

## Auto Next Episode

**DEFAULT: ON**

Automatically start the next episode after a 10-second countdown.

The user may switch Auto Next ON or OFF.

Persist the preference.

## Skip Intro

Allow skipping the introduction when reliable timing metadata exists.

Do not guess intro timestamps.

Hide or disable this option when unsupported.

## Sleep Timer

Off / 15 / 30 / 60 / 90 minutes.

Use a compact inline selector.

No oversized secondary dialogs.

Keep the Playback menu visually identical to the other floating menus.

---

# 19. FULLSCREEN EPISODE PLAYER

When the user starts an episode:

1. Open fullscreen playback.
2. Start the correct episode.
3. Resume from valid saved progress when appropriate.
4. Remove the Single Series View.
5. Hide all unnecessary interface elements.

During active playback, the screen must display only the video.

No persistent title.

No permanent progress bar.

No permanent icons.

No navigation sidebars.

No screensaver during active playback.

---

# 20. FULLSCREEN PLAYER — REMOTE CONTROL

## During active playback

PLAY resumes playback.

PAUSE pauses playback and reveals the controls.

OK reveals playback controls without automatically pausing.

DOWN reveals the four compact settings icons.

LEFT/RIGHT performs seeking when no menu has focus.

## Settings row

Exactly four icons:

**SUBTITLES | AUDIO | PLAYBACK | PICTURE**

The icons appear directly over the video.

They must be lightweight, modern and nearly transparent.

No large surrounding panel.

LEFT/RIGHT moves focus between icons.

OK opens the associated floating menu.

UP/DOWN navigates inside the open menu.

BACK closes the menu and returns focus to its icon.

When reopening the settings row, restore the last-used icon focus where appropriate.

---

# 21. FULLSCREEN PAUSED OVERLAY

When the user presses PAUSE, show the same player overlay used in MOVIES.

Display:

- Series title.
- Season and episode identifier.
- Episode title.
- Paused status.
- Thin silver-white progress bar.
- Elapsed and total time.
- Skip Backward.
- Play.
- Skip Forward.
- Subtitles.
- Audio.
- Playback.
- Picture.

Use the approved MOVIES fullscreen screenshot as the canonical reference.

The controls must appear directly over the paused video.

Do not create a large opaque panel.

The only difference from MOVIES is the episode-specific information.

---

# 22. FULLSCREEN SETTINGS MENUS

The fullscreen player must reuse the same four settings menus.

No separate fullscreen redesign.

No new icon set.

No extra settings page.

Every floating menu:

- Opens upward.
- Uses nearly transparent smoky glass.
- Keeps its bottom edge anchored above its icon.
- Scrolls internally when necessary.
- Preserves the video and controls beneath.
- Uses the same active and focused states.
- Closes with BACK.

Do not darken the entire video unnecessarily.

---

# 23. AUTO NEXT EPISODE — FINAL APPROVED BEHAVIOR

**Auto Next Episode is ON by default.**

**Countdown duration: 10 seconds.**

When the current episode finishes:

1. Mark the completed episode as watched.
2. Display ✓ on its thumbnail.
3. Fill its white progress bar.
4. Identify the next available episode.
5. Show a compact transparent Up Next overlay.
6. Begin the 10-second countdown.
7. Automatically play the next episode if the user does not intervene.

## Up Next overlay

Display:

UP NEXT

Series title.

Next episode identifier and title.

Playing automatically in 10 seconds.

Actions:

**PLAY NEXT**

**CANCEL AUTOPLAY**

No large central box.

No opaque fullscreen dialog.

Use the same PRETORIAN transparency and focus styling.

## Play Next

Immediately starts the next episode.

## Cancel Autoplay

Stops the countdown and prevents automatic transition.

## BACK

Closes the Up Next overlay and cancels the countdown.

Does not immediately exit the player.

## End of season

After the final episode of a season, continue to the first available episode of the next season.

## End of series

After the final available episode of the entire series, do not restart the series automatically.

## Previously started next episode

If the next episode already has valid saved progress, respect that progress.

Do not silently overwrite existing watch history.

---

# 24. SKIP INTRO

Show Skip Intro only when reliable intro timing information exists.

The button should be small, transparent and consistent with the player.

OK skips to the verified end of the introduction.

Hide the button when the intro has passed.

Do not invent timestamps.

Do not display a broken Skip Intro action for unsupported episodes.

---

# 25. PLAYER AUTO-HIDE

During active playback:

- Hide controls after approximately five seconds of inactivity.
- Do not hide controls while the user is navigating.
- Do not hide controls while a floating menu is open.
- Keep controls visible while paused.
- Return to clean fullscreen playback after resuming.

Do not interrupt playback with a screensaver.

---

# 26. BACK — COMPLETE STATE MACHINE

BACK must always undo the most recent logical UI level.

Priority:

1. Close the currently open floating menu or episode overlay.
2. Return focus to the control that opened it.
3. Dismiss visible playback controls when appropriate.
4. Exit fullscreen playback only when no higher-priority overlay remains.
5. Restore the Single Series View.
6. Restore the previous Series Gallery.
7. Restore the previous Category List.
8. Continue back through actual navigation levels until HOME.

For paused playback, BACK must not unexpectedly discard the session.

When a fullscreen overlay is dismissed, playback state must remain unchanged unless the user explicitly resumes or exits.

Do not introduce an unnecessary pause/resume side effect.

---

# 27. WATCHED STATUS — DATA CONSISTENCY

A completed episode displays:

- ✓ Watched.
- Full white progress bar.

A partially watched episode displays:

- Actual progress.
- No completed checkmark.

An unwatched episode displays:

- No completed checkmark.
- No fabricated progress.

Manual watched/unwatched actions must update the same persistent state used by the player.

## Manual Mark as Watched

Set the episode's watched state to completed.

Fill the progress indicator.

## Manual Mark as Unwatched

Remove completed status.

Clear its saved playback progress.

## Season-level actions

Apply the same operations to every episode in the selected season only.

## Playback completion

Mark an episode watched when playback genuinely finishes or reaches a carefully defined near-end completion threshold.

Do not mark it watched simply because playback started.

---

# 28. STATE PERSISTENCE

Preserve:

- Current category.
- Gallery focus and scroll.
- Selected series.
- Selected season.
- Focused episode.
- Episode carousel position.
- Episode watched state.
- Episode playback progress.
- My List state.
- Active audio track.
- Active subtitle track.
- Preferred subtitle language.
- Picture mode.
- Playback speed.
- Auto Next preference.
- Sleep Timer state, if supported.

Use stable identifiers.

Keep UI focus separate from active settings.

Avoid resetting state during screen transitions or recomposition.

---

# 29. PERFORMANCE AND TV REMOTE UX

This is an Android TV application, not a mobile interface.

All interaction must be optimized for a television remote.

Requirements:

- Predictable directional focus.
- Smooth episode-carousel scrolling.
- Smooth internal menu scrolling.
- Stable focus restoration.
- No unnecessary page reloads.
- No focus jumps during asynchronous loading.
- No layout movement when floating menus open.
- Graceful handling of missing artwork.
- Graceful handling of unavailable metadata.
- Proper TV safe margins.
- Readable text from a normal viewing distance.
- Efficient rendering of large provider collections.

Avoid unnecessary component duplication and expensive UI operations.

---

# 30. REQUIRED ERROR AND EDGE-CASE HANDLING

Test at least:

- Empty SERIES categories.
- Empty seasons.
- Series with only one season.
- Seasons with only one episode.
- Missing episode thumbnails.
- Missing synopsis.
- Missing duration.
- Missing trailers.
- Series with very long titles.
- Hundreds of episodes.
- Missing provider subtitle tracks.
- No online subtitle results.
- Subtitle-loading errors.
- Audio streams with only one track.
- Unsupported playback speeds.
- Unavailable intro timing.
- Playback interruption.
- App restart during a partially watched episode.
- Last episode of a season.
- Last episode of an entire series.
- Returning from fullscreen playback.
- Circular episode navigation in both directions.
- BACK navigation through every level.

Never invent content to fill missing data.

---

# 31. IMPLEMENTATION PREPARATION

Before writing code:

1. Inspect the current PRETORIAN project.
2. Read the existing MOVIES redesign documentation.
3. Inspect all attached approved screenshots.
4. Identify reusable MOVIES gallery and player components.
5. Identify the current provider data model.
6. Identify existing watch-history persistence.
7. Identify the current Android TV focus/navigation system.
8. Identify the player engine and supported settings.
9. Identify subtitle-source integration requirements.
10. Identify the shared design tokens needed for screenshot fidelity.

Prepare a concise implementation plan that minimizes duplication.

Report conflicts or unsupported requirements before proceeding.

Do not silently replace an approved design decision with a different approach.

---

# 32. ACCEPTANCE CRITERIA

The SERIES redesign is accepted only when:

1. Every SERIES category follows the same complete-replacement navigation.
2. Opening a category removes the previous category list.
3. Every category opens the same poster-gallery component.
4. Every series uses the same single-series screen.
5. Every season displays one horizontal episode row.
6. Episode navigation wraps from last to first and first to last.
7. Season selection works without a new screen.
8. Seasons can be marked watched/unwatched.
9. Episodes can be marked watched/unwatched.
10. Watched episodes show ✓.
11. Partially watched episodes show accurate white progress.
12. Long OK opens a compact episode-description overlay.
13. Short OK starts or resumes the selected episode.
14. The four settings icons appear in the approved upper-right SERIES position.
15. Fullscreen playback reuses the MOVIES player.
16. DOWN reveals the compact transparent settings icons.
17. All four settings menus open upward.
18. Provider and online subtitles share one unified menu.
19. Audio displays only actual available tracks.
20. Playback includes series-specific controls.
21. Auto Next defaults to ON with a 10-second countdown.
22. Cancel Autoplay and Play Next work correctly.
23. Trailer always uses the red YouTube Play logo.
24. BACK returns exactly one logical step.
25. Previous focus and scroll positions are restored.
26. No unnecessary navigation levels are introduced.
27. The interface matches the supplied approved screenshots.

---

# 33. FINAL NON-NEGOTIABLE CONTRACT

**ONE PRETORIAN DESIGN SYSTEM.**

**ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT.**

**EVERY SERIES CATEGORY USES THE SAME COMPONENTS.**

**ONE SHARED SERIES POSTER GALLERY.**

**ONE CINEMATIC SINGLE-SERIES SCREEN.**

**ONE HORIZONTAL EPISODE CAROUSEL.**

**CIRCULAR LEFT/RIGHT EPISODE NAVIGATION.**

**ONE COMPACT SEASON SELECTOR.**

**SHORT OK = PLAY EPISODE.**

**LONG OK = EPISODE DESCRIPTION AND ACTIONS.**

**WATCHED = ✓ AND FULL WHITE PROGRESS.**

**FOUR IDENTICAL TRANSPARENT SETTINGS ICONS.**

**ALL FOUR SETTINGS MENUS OPEN UPWARD.**

**ONE SHARED FULLSCREEN PLAYER FOR MOVIES AND SERIES.**

**AUTO NEXT = ON, 10-SECOND COUNTDOWN.**

**TRAILER = ORIGINAL RED YOUTUBE PLAY LOGO.**

**BACK = EXACTLY ONE STEP, ALL THE WAY TO HOME.**

Do not redesign approved elements.

Do not create duplicate player implementations.

Do not add extra screens.

Do not begin implementation until explicit authorization.

Save this as the authoritative SERIES redesign specification alongside the existing MOVIES documentation.

---

# 34. EXPECTED RESPONSE FROM CLAUDE

After reading this document and the screenshots:

1. Confirm that the full specification was received without truncation.
2. Confirm where the document was saved.
3. Summarize the navigation hierarchy.
4. Identify which MOVIES components can be reused.
5. Identify technical limitations or conflicting requirements.
6. Present the proposed implementation order.
7. Wait for explicit permission before modifying application code.

**Do not begin implementation automatically.**