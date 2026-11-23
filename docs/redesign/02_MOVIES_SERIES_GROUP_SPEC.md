# PRETORIAN — MOVIES & SERIES GROUP VIEW
## MASTER DESIGN, NAVIGATION & BEHAVIOR RULES
**Status:** Approved design specification  
**Applies to:** Movies and Series, all categories and provider groups  
**Implementation:** Do not begin until explicitly authorized

---

## 1. CORE PHILOSOPHY — ONE MENU, ONE LEVEL

PRETORIAN must follow the same navigation philosophy throughout the application:

**ONE MENU — ONE LEVEL — FULL CONTENT AREA**

When a user enters MOVIES or SERIES, they initially see the available categories or provider groups.

Examples:

- Recently Added
- Continue Watching
- Favorites
- My List
- Genres
- Provider Groups
- Any other categories supplied by the provider

When the user selects a category and confirms it with OK:

1. The category navigation menu disappears completely.
2. The selected category opens as a full-width content view.
3. No previous menu remains visible.
4. No secondary navigation panel occupies screen space.
5. The entire available content area becomes dedicated to the selected category.
6. A small breadcrumb identifies the current location, e.g. `Movies › Recently Added`.
7. Pressing BACK returns to the previous category menu with the previous selection and scroll position restored.

Never display two navigation levels side by side.

Do not create overlapping menus or unnecessarily separate detail pages.

## 2. UNIVERSAL GROUP SCREEN

Every Movies and Series category must use the same layout structure.

The approved screen consists of:

**TOP HEADER**

- Breadcrumb on the left.
- Weather, time, playlist status and notification bell on the right.

**UPPER CONTENT AREA — APPROXIMATELY 40%**

- Selected movie or series information.
- Wide cinematic backdrop on the right.
- Text and actions on the left.

**LOWER CONTENT AREA — APPROXIMATELY 60%**

- Large movie or series posters.
- One primary row visible at a time.
- Vertical navigation between rows.
- Smooth transitions.

This layout must remain consistent regardless of the category selected.

## 3. VISUAL DESIGN

Use the approved PRETORIAN design language:

- Minimalist dark-charcoal architectural concrete gallery.
- Matte concrete walls and floor.
- Soft warm-silver lighting from above.
- Subtle Spartan helmet watermark.
- White primary text (#FFFFFF).
- Muted silver secondary text (#B0B8C4).
- Clean, modern sans-serif typography.
- Subtle translucent dark-glass containers.
- Thin 1px borders with low opacity.

Avoid:

- Neon effects.
- Blue or purple glow.
- Heavy black containers.
- Thick decorative frames.
- Excessive visual effects.
- Unnecessary branding.
- Cluttered interfaces.

The visual appearance must remain consistent across Movies and Series.

## 4. MAXIMUM SCREEN UTILIZATION

The design is optimized for 16:9 Android TV screens.

Reference resolution: 1920 × 1080.

Rules:

- Use approximately 4.5% horizontal safe margins.
- Use approximately 3.7% vertical safe margins.
- Maximize the size of the movie artwork.
- Avoid wasted space.
- Align all content to shared guides.
- Keep every visible poster fully inside the screen.
- Do not stretch or distort artwork.
- Maintain consistent spacing and readable typography.
- Preserve TV overscan safety.

The gallery must feel spacious, cinematic and readable from approximately three metres away.

## 5. MOVIE INFORMATION PANEL

The upper information panel displays the **last movie explicitly confirmed with OK**, not merely the poster currently receiving navigation focus.

Display the following information in this order:

**MOVIE TITLE**

Large, prominent, readable title.

**YEAR — DURATION — GENRE**

Example:

2024 · 166 min · Science Fiction, Adventure

**DESCRIPTION**

A concise synopsis, preferably two readable lines.

**ACTORS**

Display the principal cast members, ideally three to five names.

**DIRECTOR**

Display the director's name.

**ACTION BUTTONS**

- Watch
- Trailer
- My List

All information must come from the available provider metadata and/or integrated metadata services such as TMDB.

Never invent missing metadata.

If a field is unavailable, omit it gracefully rather than displaying incorrect information.

## 6. SERIES INFORMATION PANEL

Series use the same visual layout but display series-appropriate metadata.

**SERIES TITLE**

**YEAR — SEASONS — GENRE**

**DESCRIPTION**

**ACTORS**

**CREATOR / DIRECTOR**

Use the creator or director information actually available in metadata.

**ACTION BUTTONS**

- Watch
- Trailer
- My List

For series, Watch opens the appropriate series playback or episode-selection flow.

If the user has existing progress, Watch may offer Resume or Continue Watching according to the established playback rules.

Do not automatically start a random episode.

The series detail flow must follow ONE MENU — ONE LEVEL.

## 7. ACTION BUTTONS

The order must always be:

**WATCH → TRAILER → MY LIST**

### WATCH

- Primary action.
- Receives focus automatically when a poster is confirmed with OK.
- Activates playback or the appropriate episode-selection flow only when the user presses OK on Watch.
- Never starts playback merely because the button receives focus.

### TRAILER

- Secondary action.
- Uses a small red YouTube-style play icon.
- Opens the available trailer.
- Prefer the correct trailer associated with the selected movie or series.
- If no trailer exists, disable the action or show a clear unavailable message.
- Returning from the trailer restores the same group screen, selected title and focus.

### MY LIST

My List is a **one-click toggle**.

When the title is not saved:

- Display `+ My List`.
- Pressing OK immediately adds it.
- The button updates to `✓ In My List` or `Remove from My List`.

When the title is already saved:

- Pressing OK immediately removes it.
- The button returns to `+ My List`.

No confirmation dialog is required for normal add/remove actions.

The operation must update persistent application state and all relevant Favorites/My List views.

Do not duplicate entries.

If persistence fails, restore the previous state and report the failure.

My List and provider Favorites may be distinct collections; do not silently merge them unless the application data model explicitly defines them as the same collection.

## 8. CRITICAL DIFFERENCE — FOCUS VS CONFIRMATION

**MOVING BETWEEN POSTERS MUST NOT CHANGE THE UPPER INFORMATION PANEL.**

There are two distinct states:

**FOCUSED POSTER**

The poster currently highlighted by remote navigation.

**CONFIRMED TITLE**

The movie or series explicitly selected by pressing OK.

These states must not be confused.

Example:

1. The user confirms Dune with OK.
2. Dune's details appear above.
3. Focus automatically moves to Watch.
4. The user returns to the poster gallery.
5. The user moves across Gladiator, Civil War and Oppenheimer.
6. Dune's details remain visible throughout.
7. Only after pressing OK on Oppenheimer do the details change to Oppenheimer.

Never update the information panel simply because the user moves focus or hovers over a poster.

## 9. AUTOMATIC FOCUS TRANSFER

When the user presses OK on a poster:

1. Store the confirmed movie or series ID.
2. Update the upper information panel.
3. Update the cinematic backdrop.
4. Display the confirmed title's metadata.
5. Automatically transfer remote focus to the WATCH button.
6. Wait for further user input.

The user can then:

- Press OK to activate Watch.
- Press RIGHT to focus Trailer.
- Press RIGHT again to focus My List.
- Press LEFT to move back through the actions.
- Press DOWN or BACK to return to the poster gallery.

Returning to the gallery must restore focus to the confirmed poster.

Do not change the confirmed title unless another poster is explicitly confirmed.

## 10. POSTER GALLERY — ONE PRIMARY ROW

The lower content area displays **one complete primary row of posters at a time**.

At 1920 × 1080, the approved reference layout contains six posters per row.

Every poster must:

- Use a 2:3 aspect ratio.
- Preserve its original image proportions.
- Be fully visible.
- Have consistent dimensions.
- Display the title below.
- Display a small TMDB rating badge in the top-left corner when a rating is available.
- Maintain consistent spacing.
- Remain readable from sofa distance.

The gallery should maximize poster size without cutting off the leftmost or rightmost poster.

### ADJACENT ROW PREVIEW

A subtle preview of an adjacent row may be visible near the lower gallery boundary.

This preview must not compete with the active row.

During vertical transitions, approximately one-third of the outgoing row may remain visible briefly.

The active row must remain dominant.

Do not permanently display multiple full rows in the same viewport.

## 11. VERTICAL ROW NAVIGATION

There are no Previous Row or Next Row buttons.

All navigation uses the Android TV remote.

**LEFT**

Move to the previous poster in the active row.

**RIGHT**

Move to the next poster in the active row.

**DOWN**

Move to the next row, preferably maintaining the same poster column.

**UP**

Move to the previous row, preferably maintaining the same poster column.

If UP is pressed from the first row, focus may return to the action buttons according to the established focus map.

If the next row contains fewer posters, select the nearest available poster.

Do not wrap unexpectedly between rows.

Do not allow focus to disappear or land on invisible content.

## 12. SMOOTH ROW TRANSITIONS

Vertical navigation must use smooth, cinematic animation.

When the user presses DOWN:

1. The current row moves upward.
2. The next row enters smoothly from below.
3. The outgoing row may remain partially visible during the transition.
4. The incoming row settles into the exact primary-row position.
5. Focus moves to the appropriate poster.

When the user presses UP, the animation reverses naturally.

Recommended animation:

- Duration: approximately 300–450 ms.
- Easing: smooth ease-out.
- Use hardware-accelerated transforms where possible.
- No abrupt snapping.
- No unnecessary bounce.
- No excessive zooming.
- No flickering.
- No poster reloading during every focus movement.

Rapid remote input must be handled without overlapping or accumulating animations.

The interface must remain responsive on Android TV hardware.

## 13. POSTER FOCUS ANIMATION

The focused poster should step forward subtly.

Recommended appearance:

- Approximately 5–10% scale increase, provided it remains inside safe boundaries.
- Slight upward lift.
- Thin white border.
- Soft white shadow or restrained halo.
- Smooth 180–250 ms animation.

Do not use strong neon glow.

The focus indicator must remain visible on dark backgrounds.

Poster scaling must not overlap important neighboring content or clip the poster.

## 14. BACK NAVIGATION

BACK always returns one logical level.

Examples:

**Inside action buttons**

BACK returns focus to the confirmed poster in the current group.

**Inside a Movies group**

BACK returns to the Movies category list.

**Inside a Series group**

BACK returns to the Series category list.

**Inside the category list**

BACK returns to the previous main navigation level.

When returning to a group or category, restore:

- Previous scroll position.
- Previous focused item.
- Previously confirmed title, where appropriate.
- Relevant playback or My List state.

Never reset the user to the first movie unnecessarily.

## 15. DATA AND CONTENT HANDLING

All groups use the same reusable screen component.

The selected category supplies the content data.

Do not create separate UI implementations for:

- Recently Added.
- Favorites.
- My List.
- Genres.
- Provider groups.
- Other movie or series categories.

Use a shared gallery component with configurable content sources.

The component must handle:

- Empty categories.
- One title.
- Fewer than six titles.
- Multiple full rows.
- Incomplete final rows.
- Missing posters.
- Missing ratings.
- Missing trailers.
- Missing descriptions.
- Network loading and metadata errors.

Do not create fake content to fill missing slots.

Do not stretch a partial row to produce distorted posters.

Show an appropriate empty or loading state when necessary.

## 16. PERFORMANCE

PRETORIAN must remain responsive on Android TV devices.

Requirements:

- Cache poster images appropriately.
- Avoid repeated metadata requests while merely moving focus.
- Load neighboring rows efficiently.
- Preload nearby poster images when useful.
- Use stable item identifiers.
- Avoid rebuilding the entire screen on every directional keypress.
- Avoid unnecessary full-screen redraws.
- Preserve smooth transitions under rapid remote input.
- Release off-screen resources when appropriate.

The information panel should update only after confirmation, not during every navigation movement.

## 17. NON-NEGOTIABLE ACCEPTANCE TESTS

Claude must verify the following behaviors:

1. Selecting a category completely hides the category menu.
2. The group content fills the available screen width.
3. Six complete posters fit in the reference 1920 × 1080 layout.
4. Only one complete primary row is visible at a time.
5. DOWN moves to the next row with a smooth transition.
6. UP returns to the previous row.
7. LEFT/RIGHT changes poster focus without changing confirmed movie details.
8. OK confirms a poster and updates the upper information panel.
9. Focus automatically moves to Watch after confirmation.
10. Watch does not autoplay merely because it receives focus.
11. Trailer opens the correct available trailer.
12. My List adds/removes the selected title with one OK press.
13. BACK restores the correct previous navigation level.
14. Returning to a category restores the previous selection and scroll position.
15. The same behaviors work for both Movies and Series.
16. Posters, text, buttons and ratings remain inside safe screen boundaries.
17. Rapid remote navigation does not cause flickering, focus loss or broken animations.

## 18. IMPLEMENTATION INSTRUCTION FOR CLAUDE

Before modifying the PRETORIAN codebase:

1. Inspect the existing Movies and Series architecture.
2. Identify current navigation, focus handling, content grouping and metadata sources.
3. Determine which existing components can be reused.
4. Prepare a minimal-change implementation plan.
5. Preserve existing functionality that does not conflict with these approved rules.
6. Wait for explicit implementation authorization.

Do not begin coding merely because this specification has been provided.

---

## FINAL DESIGN PRINCIPLE

**ONE MENU — ONE LEVEL.**

**ONE COMPLETE PRIMARY POSTER ROW AT A TIME.**

**FOCUS IS NOT CONFIRMATION.**

**OK CONFIRMS THE TITLE AND MOVES FOCUS TO WATCH.**

**WATCH — TRAILER — MY LIST.**

**SMOOTH VERTICAL NAVIGATION.**

**MAXIMUM SCREEN UTILIZATION WITH PERFECT ALIGNMENT.**

These rules are mandatory for every Movies and Series category and provider group.