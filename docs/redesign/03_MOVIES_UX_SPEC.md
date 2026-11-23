# PRETORIAN — COMPLETE MOVIES UX IMPLEMENTATION SPECIFICATION

## MASTER INSTRUCTION FOR CLAUDE

We have completed the visual design and interaction planning for the PRETORIAN MOVIES section.

Your task is to implement the approved design and navigation behavior consistently across **EVERY category within MOVIES**.

The attached reference screenshots are the authoritative visual references.

**Do not independently redesign the interface. Do not introduce additional navigation levels, alternative layouts or different menu styles.**

Before making code changes, inspect the existing project architecture and identify the components, navigation system, player implementation and provider data interfaces that can be reused.

Do not assume that an unavailable API or feature already exists.

---

# 1. UNIVERSAL RULE — EVERY MOVIES CATEGORY

All MOVIES categories must use exactly the same user experience.

This includes:

- Recently Added
- Continue Watching
- Favorites
- My List
- Action, Comedy, Drama, Science Fiction and other genres
- Provider-defined movie groups
- Any dynamically created or imported movie category
- Search results that open movies, where applicable

No category may implement its own alternative movie gallery, details page, playback controls or subtitle system.

Build reusable components and shared navigation logic.

Category differences are limited to their title, movie collection, ordering and relevant data.

# 2. CORE NAVIGATION — ONE MENU, ONE LEVEL

This is a fundamental PRETORIAN design principle.

When the user enters a new navigation level, the previous level must disappear completely.

Never stack category menus, sidebars or partially visible previous screens.

Navigation sequence:

**MOVIES → CATEGORY → MOVIE GALLERY → SINGLE MOVIE VIEW → FULLSCREEN PLAYBACK**

The gallery may show the selected movie's information within its own screen. This does not create an additional navigation level.

Remote BACK must always return exactly one logical step.

Examples:

- From category gallery → Movies categories.
- From single movie view → Previous gallery, at the original poster and scroll position.
- From fullscreen player → Single movie view, preserving playback progress.
- From an open floating menu → Close only that menu.
- From the visible player controls → Hide only those controls, when no menu is open.

Never return directly to the main menu from an inner MOVIES level unless the user explicitly navigates back through the hierarchy.

Restore previous focus, selected item and scroll position.

# 3. MOVIES CATEGORY SCREEN

The MOVIES entry screen displays the available movie categories and provider groups.

When a category is selected with OK:

1. The category navigation disappears.
2. The selected category's movie gallery occupies the available screen.
3. The breadcrumb updates to identify the current location.
4. Focus moves to the appropriate movie poster.
5. No other category remains visible behind the gallery.

Categories must be populated from real application and provider data.

Do not create fake movie collections in production.

# 4. MOVIE GALLERY — APPROVED LAYOUT

Target reference resolution: 1920 × 1080, 16:9 Android TV.

Use the approved PRETORIAN cinematic gallery design.

## Poster layout

- Display ONE complete row of movie posters at a time.
- The approved reference layout uses six posters per row at 1920 × 1080.
- Posters use the original 2:3 aspect ratio.
- Never stretch, crop or distort poster artwork.
- Use consistent card widths, spacing and alignment.
- Maximize poster size while respecting television safe margins.
- No partially clipped horizontal poster cards.
- A subtle glimpse of the previous row may remain visible during vertical browsing, approximately one-third where layout permits.
- Never display two competing full rows simultaneously.
- Show movie titles below posters.
- Display compact TMDB rating badges when ratings are available.

## Poster focus

The currently focused poster receives:

- A subtle thin white outline.
- A slight scale increase, approximately 10%.
- A small upward lift.
- A restrained soft shadow.
- Smooth ease-out animation.

No neon or colorful focus glow.

The focused poster must never overlap or obscure neighboring titles.

## Remote navigation

LEFT / RIGHT moves between posters in the current row.

UP / DOWN moves between poster rows.

Vertical movement must be smooth and predictable.

Maintain the user's horizontal position when moving between rows where possible.

Do not add Previous Row or Next Row buttons.

Do not reset focus unexpectedly.

## Browsing versus confirming

**Moving focus over a poster must not automatically change the confirmed movie details.**

The gallery's hero/details area represents the last movie confirmed with OK.

When the user presses OK on a poster:

1. Confirm that movie.
2. Update its title, backdrop and metadata.
3. Update description, cast and director.
4. Update the available actions.
5. Move focus directly to the Watch action.

The user can return to poster browsing without losing the confirmed movie.

# 5. GALLERY MOVIE INFORMATION

Use the approved hierarchy:

1. Movie title.
2. Year, duration, genres and rating.
3. Short description, ideally two lines.
4. Actors.
5. Director.
6. Watch, Trailer and My List.

The hero area should have a wide cinematic backdrop fading smoothly into the background.

Do not create an oversized information card.

Do not add unnecessary additional pages.

# 6. SINGLE MOVIE VIEW

When the user selects Watch from the gallery, open the dedicated single-movie screen shown in the approved reference screenshots.

This is a separate cinematic information and playback-preparation screen.

The previous gallery must disappear completely.

The movie does not automatically start merely because the user entered this screen.

## Wallpaper

- Fill the entire screen with the selected movie's widescreen backdrop.
- Edge-to-edge, without frames.
- Preserve the artwork's proportions.
- Use a smooth dark-charcoal gradient on the left for readability.
- Preserve the cinematic artwork on the right.
- Add a subtle bottom gradient where necessary.
- No large opaque information panels.

## Movie information

Display:

- Title.
- Year, duration, genres and rating.
- Description.
- Actors.
- Director.

Use the approved white and silver typography.

Do not invent missing metadata.

## Main actions

Display:

**WATCH / RESUME | TRAILER | MY LIST**

Watch appears for movies that have not been started.

Resume appears when valid saved progress exists.

Trailer uses the approved small red play symbol.

My List is a one-click toggle:

- Press OK once → add movie.
- Press OK again → remove movie.
- Update the icon and state immediately.
- No confirmation dialog.

If a trailer is unavailable, handle it gracefully without presenting a broken action.

## Watch progress

For a partially watched movie, show a thin silver-white progress bar beneath the main actions.

Show elapsed and remaining time when available.

Do not display fake progress for a movie that has never been played.

The progress bar must remain discreet and should not dominate the

# PRETORIAN — MOVIES REDESIGN
## PART 3 — CONTINUATION FROM SECTION 6
### Instructions for Claude

This document continues the previously received MOVIES specification stored under `docs/redesign/03_…`.

**Do not replace the existing document. Append this continuation after the truncated section 6.**

All previously supplied screenshots remain the authoritative visual references.

No implementation should begin until explicit authorization.

---

## 6. WATCH PROGRESS — CONTINUATION

The watch-progress indicator must remain subtle and must not dominate the screen.

Rules:

- Display progress only when the movie has actually been started.
- Use a thin silver-white progress line, approximately 3–4 pixels high.
- Display elapsed and remaining time when reliable duration information is available.
- Do not invent playback progress.
- Resume must use the actual saved playback position.
- Preserve progress when the user leaves the player.
- Do not reset progress when browsing categories or opening settings.
- Movies that have never been watched display WATCH instead of RESUME.

Progress must not be covered unnecessarily by floating menus.

---

## 7. FOUR UNIVERSAL CONTROL ICONS

Every single-movie screen must display exactly four controls:

**SUBTITLES | AUDIO | PLAYBACK | PICTURE**

These icons appear beneath the primary actions and progress indicator.

All four must share identical:

- Icon design and stroke thickness.
- Monochrome white/silver colors.
- Icon dimensions.
- Typography and label positioning.
- Spacing and alignment.
- Focus highlight.
- Transparency.
- Border and shadow styling.
- Animation behavior.

Use the supplied screenshots as the exact visual references.

Do not introduce additional settings icons.

---

## 8. ONE UNIVERSAL FLOATING-MENU COMPONENT

All four controls must use the same reusable floating-menu component.

The visual design is locked.

Use the same smoky charcoal transparency, subtle backdrop blur, thin border, restrained shadows, white text, muted-silver descriptions and translucent focus highlight shown in the screenshots.

### Mandatory positioning

**EVERY FLOATING MENU OPENS UPWARD FROM ITS ASSOCIATED ICON.**

The menu's bottom edge remains anchored above the icon row.

Additional options extend the menu upward.

Never expand the menu downward.

Never let it leave the television safe area.

When a menu contains too many options, constrain its height and enable internal scrolling.

Opening a menu must never reposition the movie wallpaper, progress bar, primary actions or icons.

### Focus versus active selection

These are different states:

- Focused option: subtle translucent horizontal highlight.
- Active option: small checkmark.

Moving focus must not change the active setting.

OK activates the focused option.

BACK closes the menu and restores focus to the icon that opened it.

No Save, Apply, Cancel or Close buttons.

---

## 9. SUBTITLES — ONE UNIFIED MENU

**CRITICAL: NEVER CREATE A SECOND ONLINE SUBTITLES MENU.**

Provider and online subtitles must be integrated into one single scrollable Subtitles menu.

### Layout

Title: Subtitles

Top-right language selector: English ▾

Section 1:

**PROVIDER**

Off — Disable subtitles

English ✓ — Provider subtitle

Deutsch — Provider subtitle

These languages are examples only.

Display the actual subtitle tracks supplied by the current media source.

Section 2:

**ONLINE · ENGLISH**

Eng ver.1 — Online subtitle

Eng ver.2 — Online subtitle

Eng ver.3 — Online subtitle

Eng ver.4 — Online subtitle

Continue as results are available.

### Language preference

English is the default online subtitle search language.

If the user selects another language, remember that preference.

Changing language must happen within the same Subtitles menu.

Do not navigate to a separate language-selection screen.

### Online subtitle search

Use supported and legally accessible subtitle providers and APIs.

Respect service permissions, licensing, authentication and rate limits.

Search using accurate movie metadata:

- Movie title.
- Release year.
- Available external identifiers.
- Release information when available.

For television series, also use the correct season and episode.

Prefer results matching the media release and timing.

### Subtitle naming

Hide long technical filenames from the primary interface.

Use standardized display names:

Eng ver.1

Eng ver.2

Deu ver.1

Bos ver.1

Hrv ver.1

Srp ver.1

Number versions independently within each language.

Keep original filenames, IDs, source information and metadata internally.

### Scrolling

This is a critical Android TV requirement.

- UP/DOWN moves focus exactly one option.
- Scroll smoothly, without sudden jumps.
- Scroll only when required to keep the focused option visible.
- Preserve focus during asynchronous result loading.
- Never reset to the first result unexpectedly.
- Never reorder the visible list while the user is navigating.
- Use a very subtle scroll indicator.
- Keep the menu height constrained.
- Keep provider subtitles above online subtitles.

### Applying subtitles

OK selects the focused subtitle.

Provider subtitles activate directly.

Online subtitles are downloaded or loaded through supported methods.

Only move the active checkmark after successful activation.

On failure, preserve the previously active subtitle and show a small unobtrusive error.

Selecting Off disables subtitles.

Never restart the movie just to change subtitles.

---

## 10. AUDIO MENU

Display only audio tracks actually provided by the current stream.

Example:

English — 5.1 ✓

Deutsch — Stereo

Bosanski — Stereo

English — Stereo

These are illustrative labels only.

Never invent audio languages, codecs or channel configurations.

If information is unknown, omit it.

Display a small checkmark beside the active track.

Use the same translucent highlight for remote focus.

OK switches the audio track directly.

The user must not need to leave the movie.

---

## 11. PLAYBACK MENU

Use the exact same transparent floating-menu component.

Display:

**Start Over**

Play from the beginning.

**Playback Speed**

Normal · 1.0×

**Sleep Timer**

Off

Playback speed options, when supported:

0.75× / 1.0× / 1.25× / 1.5× / 2.0×

Sleep Timer options, when enabled:

Off / 15 / 30 / 60 / 90 minutes.

The Sleep Timer is part of the current visual mockup but remains subject to final product approval.

Use compact inline selections that retain the same visual design.

Do not create large secondary dialogs.

Do not repeat Resume inside Playback because Resume already exists as a primary action.

---

## 12. PICTURE MENU

Display exactly five options:

**Default**

Original picture format.

**Fit**

Show the entire picture.

**Fill**

Fill the screen, cropping edges where necessary.

**Stretch**

Stretch to screen dimensions.

**4:3**

Classic television aspect ratio.

Do not add a separate 16:9 option.

Use the approved transparent menu design.

Apply the selected picture mode directly when supported.

Indicate the active mode with a checkmark.

---

## 13. FULLSCREEN MOVIE PLAYBACK

When the user activates WATCH or RESUME from the single-movie screen:

1. Open fullscreen playback.
2. Start the selected media.
3. Resume from saved progress when applicable.
4. Remove the single-movie information screen.
5. Hide all unnecessary UI.

During normal playback, the screen must display only the movie.

No persistent navigation.

No movie description.

No permanent controls.

No unnecessary overlays.

### Remote behavior

PLAY starts or resumes playback.

PAUSE pauses playback and displays player controls.

OK during playback displays player controls without automatically pausing.

LEFT/RIGHT supports seeking in the appropriate player navigation state.

BACK follows the universal one-level-back rule.

---

## 14. FULLSCREEN PAUSED PLAYER OVERLAY

When playback is paused, show the approved minimalist overlay directly over the paused video.

Do not create a large dark panel.

Display:

- Compact movie title.
- Paused state.
- Thin progress bar.
- Current and total playback time.
- Skip Backward.
- Play / Resume.
- Skip Forward.
- Subtitles.
- Audio.
- Playback.
- Picture.

Use exactly the same PRETORIAN colors, typography, transparency, shadows, icon designs and focus styling as the approved movie screenshots.

The overlay must be lightweight and cinematic.

### Reuse the same four menus

The fullscreen player must reuse the exact same components used on the single-movie screen.

Subtitles, Audio, Playback and Picture must have identical design and behavior in both contexts.

All floating menus open upward.

The user must be able to change subtitles, audio tracks, playback speed and picture format without leaving fullscreen playback.

If playback was paused when a menu opened, it remains paused.

If playback was running, opening settings should not automatically pause it unless technically required.

---

## 15. AUTO-HIDE RULES

During active playback:

- Hide controls after approximately five seconds of inactivity.
- Do not hide controls while the user is navigating them.
- Do not hide controls while a floating menu is open.
- Keep controls visible while playback is paused.
- After resuming playback, return to a clean fullscreen view.

Never activate the screensaver during active video playback.

---

## 16. BACK BUTTON — UNIVERSAL RULE

**BACK ALWAYS RETURNS EXACTLY ONE LOGICAL LEVEL.**

Required navigation:

Floating menu → Previous control row.

Player controls → Clean fullscreen playback, when playing.

Fullscreen playback → Single Movie View.

Single Movie View → Previous Movie Gallery.

Movie Gallery → Previous Movies Category List.

Movies Category List → Main PRETORIAN Navigation.

For paused playback, BACK must close an open menu or controls before exiting the player. It must not unexpectedly discard the playback session.

Restore:

- Previous focused item.
- Confirmed movie.
- Gallery row.
- Scroll position.
- Saved playback progress.
- Relevant settings.

Never jump directly to HOME from an inner movie screen.

---

## 17. STRICT VISUAL DESIGN CONSISTENCY

The supplied screenshots establish the approved PRETORIAN visual system.

Use identical:

- Dark charcoal and concrete-gray undertones.
- White and muted-silver typography.
- Transparent smoky-glass menus.
- Backdrop blur.
- Border opacity.
- Shadow softness.
- Focus effects.
- Icon style.
- Corner radii.
- Spacing.
- Animation philosophy.

Do not introduce neon, purple or blue glow, heavy black boxes, oversized dialogs, new icon systems or alternative menu layouts.

The cinematic movie wallpaper remains the dominant visual element.

---

## 18. UNIVERSAL APPLICATION TO EVERY MOVIES CATEGORY

These rules apply without exception to:

- Recently Added.
- Continue Watching.
- Favorites.
- My List.
- Genres.
- Provider-defined movie groups.
- Dynamically imported categories.
- Movie search results where applicable.

Do not create separate implementations for different categories.

Use shared gallery, single-movie, player and floating-menu components.

Only the collection data, category name and relevant content should differ.

---

## 19. STATE MANAGEMENT

Preserve the following state where applicable:

- Current movie category.
- Focused movie poster.
- Confirmed movie.
- Gallery row and scroll position.
- Playback progress.
- My List membership.
- Active audio track.
- Active subtitle track.
- Preferred online subtitle language.
- Picture mode.
- Playback speed.
- Sleep Timer state, if enabled.

Keep temporary navigation focus separate from active settings.

Do not reset state unnecessarily when changing screens.

Use stable media identifiers.

---

## 20. IMPLEMENTATION REQUIREMENTS

Before modifying the project:

1. Inspect the existing architecture.
2. Identify reusable navigation and player components.
3. Check the provider data interfaces.
4. Check actual audio and subtitle track support.
5. Check online subtitle integrations and permitted APIs.
6. Identify shared design tokens.
7. Plan reusable components rather than duplicating code.

Test with an Android TV remote, not only mouse interaction.

Test empty categories, large movie collections, missing artwork, long titles, missing trailers, subtitle search failures, unavailable audio tracks, scrolling, focus restoration and fullscreen playback.

---

## 21. FINAL ACCEPTANCE CRITERIA

Implementation is acceptable only if:

1. Every MOVIES category uses the same gallery UX.
2. Every movie uses the same single-movie screen.
3. Every movie uses the same fullscreen player.
4. All four settings menus share one visual component.
5. Provider and online subtitles exist in one scrollable menu.
6. Audio displays only real available tracks.
7. Floating menus open upward.
8. Scrolling is smooth and remote focus remains stable.
9. BACK returns exactly one level and restores the previous position.
10. The result matches the supplied reference screenshots.
11. No extra navigation layers or alternative designs are introduced.

### FINAL DESIGN PRINCIPLE

**ONE DESIGN SYSTEM.**

**ONE SHARED MOVIE GALLERY.**

**ONE SINGLE-MOVIE VIEW.**

**ONE FULLSCREEN PLAYER.**

**ONE UNIVERSAL FLOATING-MENU SYSTEM.**

**APPLIED TO EVERY MOVIES CATEGORY.**

Save this continuation alongside the previously received MOVIES redesign documentation.

Do not begin implementation until explicitly instructed.