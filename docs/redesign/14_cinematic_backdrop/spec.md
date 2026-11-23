# PRETORIAN — GLOBAL CINEMATIC BACKDROP RULE
## ALL MOVIES & SERIES DETAIL SCREENS

**Project:** PRETORIAN Android TV Media Center  
**Modules:** MOVIES and SERIES  
**Scope:** Every individual movie and series detail screen  
**Reference:** Attached physical TV screenshot — Project Hail Mary  
**Priority:** Mandatory global visual rule

---

## 1. OBJECTIVE

Claude, the attached Project Hail Mary screenshot demonstrates the cinematic detail-screen design we want to standardize throughout PRETORIAN.

The combination of full-screen artwork, intelligently darkened left-side content and clearly visible right-side imagery is approved.

**Apply this same visual composition to EVERY movie and EVERY series detail screen, regardless of which category the title belongs to.**

This is a shared design rule, not a special layout for Recently Added or New Released titles.

Do not create different detail-screen designs for different provider categories.

---

## 2. GLOBAL COVERAGE

Apply the same design when opening titles from:

- Recently Added.
- In Cinemas.
- Streaming.
- My List.
- All provider-defined MOVIES categories.
- All provider-defined SERIES categories.
- Search results that open a movie or series detail screen.
- Continue Watching when it navigates to a detail screen.
- Any other existing navigation path leading to a movie or series detail screen.

The originating category must not determine the backdrop style.

All detail screens must share the same cinematic rendering system.

---

## 3. FULL-SCREEN CINEMATIC BACKDROP

The selected movie or series artwork should become the main visual background of its detail screen.

### Requirements

1. Display the title-specific cinematic backdrop across the entire available detail-screen area.
2. Preserve the original artwork aspect ratio.
3. Scale proportionally to fill the screen.
4. Do not stretch or distort.
5. Avoid visible empty bars.
6. Keep the main visual subject visible, preferably toward the right side.
7. Allow the image to extend behind the content area while maintaining readability.
8. Keep the backdrop stable during focus navigation.

The result should look like a premium streaming-service detail screen, not a poster inside a rectangular container.

### Important

Use the actual title-specific backdrop artwork where available.

Prefer a landscape backdrop over a portrait poster.

Do not simply stretch a portrait movie poster to fill a 16:9 screen.

---

## 4. INTELLIGENT LEFT-SIDE DARKENING

The left side contains the title's information and controls.

It must remain readable regardless of the brightness or colors in the source artwork.

### Required gradient

Apply a smooth horizontal darkening gradient:

- Strongest behind the left-side text.
- Gradually decreasing toward the center.
- Mostly transparent toward the right side.

Use neutral charcoal and graphite tones.

Do not use a hard vertical division.

Do not create a visible rectangular panel behind the description.

The gradient must feel naturally integrated into the cinematic artwork.

### Additional vertical shading

Apply restrained shading near the bottom where action buttons and option icons are displayed.

This helps preserve readability without hiding the artwork.

### Bright artwork

For unusually bright backdrops, increase the darkening behind the text as needed.

The aim is readable information without unnecessarily darkening the entire image.

The main character or subject on the right should remain clearly visible.

---

## 5. ARTWORK COMPOSITION AND FOCAL POINT

The attached Project Hail Mary screenshot shows the desired composition:

- Information on the left.
- Main actor and artwork predominantly on the right.
- Smooth visual transition between the two areas.

Prefer a right-biased composition when selecting or positioning landscape artwork.

Do not center-crop important faces unnecessarily.

However, avoid a rigid crop rule that makes every image look correct only in one specific aspect ratio.

### Recommended approach

1. Prefer a proper landscape backdrop supplied by the media provider or existing metadata source.
2. Use a right-biased crop or alignment when appropriate.
3. Preserve the important subject as much as possible.
4. Use the left-side gradient to create readable space.
5. Never distort the image.

If a suitable backdrop is unavailable, use the existing approved fallback rather than displaying a badly stretched poster.

Do not introduce new external artwork services without approval.

---

## 6. LEFT-SIDE INFORMATION HIERARCHY

Preserve the existing title-information structure.

### Movie detail screen

Show the available information in the established order:

1. Movie title.
2. Rating.
3. Release year.
4. Runtime.
5. Genre.
6. Synopsis.
7. Cast.
8. Director.
9. Primary actions.
10. Playback-related options.

### Series detail screen

Preserve the equivalent series information:

1. Series title.
2. Rating.
3. Release year.
4. Genres.
5. Synopsis.
6. Cast and other available metadata.
7. Primary actions.
8. Playback-related options.
9. Season selector and episode list.

Do not invent missing metadata.

Do not add unnecessary information panels.

---

## 7. TEXT READABILITY

Text must remain readable over the backdrop.

Use:

- Soft white primary text.
- Muted silver-gray secondary text.
- Clear hierarchy between title and metadata.
- Comfortable spacing.
- Restrained line lengths.
- Smooth gradient shading behind text.

Avoid:

- Text over bright faces without shading.
- Heavy opaque text boxes.
- Strong text glow.
- Excessive drop shadows.
- Long paragraphs extending across the right-side artwork.

### Synopsis

Keep the synopsis within the approved left content area.

If it exceeds the available space, use the existing truncation or expansion behavior.

Do not let text overlap the right-side subject.

---

## 8. PRESERVE EXISTING PRIMARY ACTIONS

Keep the existing three main actions:

**Watch · Trailer · My List**

Do not rename them.

Do not introduce new buttons.

Preserve their existing behavior.

### Appearance

- Compact rounded buttons.
- Restrained graphite surfaces.
- Thin silver outlines.
- Clear focused state.
- Soft white text.
- No neon or strong glow.

The primary focused button may use a lighter graphite/silver fill as already approved.

---

## 9. PRESERVE THE FOUR EXISTING OPTIONS

Below the primary actions, retain:

**Subtitles · Audio · Playback · Picture**

Keep the current approved icon style and positioning.

Do not add extra options.

Do not change the underlying player behavior.

The four controls must remain clearly visible against the backdrop.

Use subtle bottom shading if necessary.

---

## 10. SERIES — SEASONS AND EPISODES

Series detail screens must use the same cinematic backdrop philosophy as movies.

However, the series-specific content must remain functional and visible.

Preserve:

- Season selector.
- Episode cards.
- Episode artwork.
- Episode names and numbering.
- Viewing progress.
- Existing watched-state indicators.
- Existing episode navigation.

The background must not interfere with episode readability or focus.

Use subtle local shading where needed.

Do not place a large opaque panel over the entire cinematic image.

Do not crop the first episode row on initial rendering.

Do not require the user to move focus before the episode row becomes fully visible.

---

## 11. PURE GRAPHITE DESIGN CONSISTENCY

The official Pure Graphite background remains the global background for browsing menus.

**Individual movie and series detail screens are the intentional exception.**

These screens use title-specific cinematic artwork.

The transition between the Pure Graphite category/gallery screen and the cinematic detail screen should feel natural.

Do not reintroduce the old blue concrete-room background.

Do not use unrelated decorative backgrounds.

---

## 12. PERFORMANCE AND IMAGE LOADING

The backdrop must load efficiently.

Use the existing image-loading and caching architecture.

Requirements:

- Load the appropriate backdrop resolution for the display.
- Avoid unnecessary repeated downloads.
- Avoid expensive real-time blur.
- Avoid full-screen flickering during image loading.
- Do not block remote-control navigation while artwork loads.
- Do not reload the backdrop when focus moves between buttons.
- Avoid excessive memory use on 4K devices.

If the image is still loading, display a stable Pure Graphite fallback until it becomes available.

Transition smoothly without changing the layout geometry.

---

## 13. NAVIGATION AND FOCUS

Preserve all existing Android TV remote-control rules.

- UP/DOWN: Navigate existing controls and content.
- LEFT/RIGHT: Move focus according to the approved detail-screen layout.
- OK: Activate the focused action.
- BACK: Return exactly one logical navigation level.

Do not introduce additional screens or modal layers.

Preserve focus restoration when returning to the poster gallery.

Do not alter the separately approved double-OK quick-play behavior.

---

## 14. TESTING REQUIREMENTS

Test the shared cinematic detail layout with different artwork types:

### Dark backdrop

Ensure text remains readable without excessive darkening.

### Bright backdrop

Ensure the left gradient provides sufficient contrast.

### Backdrop with a face near the center

Avoid unnecessary cropping or text overlap.

### Backdrop with the main subject on the right

Preserve the cinematic composition.

### Backdrop with the main subject on the left

Use the best available framing without forcing a destructive crop.

### Missing backdrop

Use a stable fallback.

### Series with multiple seasons

Ensure all season and episode controls remain visible and usable.

### Different navigation paths

Verify the same detail-screen design is used regardless of the category from which the title was opened.

---

## 15. ACCEPTANCE CRITERIA

- [ ] Every movie detail screen uses the approved cinematic layout.
- [ ] Every series detail screen uses the approved cinematic layout.
- [ ] The originating category does not change the design.
- [ ] Backdrop fills the screen without distortion.
- [ ] Right-side artwork remains clear.
- [ ] Left-side information remains readable.
- [ ] Gradient transitions smoothly without a visible panel edge.
- [ ] Watch, Trailer and My List remain unchanged.
- [ ] Subtitles, Audio, Playback and Picture remain unchanged.
- [ ] Series season and episode controls remain fully usable.
- [ ] No image flickering during focus movement.
- [ ] No regressions in playback or BACK navigation.
- [ ] Missing artwork uses an appropriate fallback.

---

## 16. IMPLEMENTATION REPORT

After implementation, report:

1. Which shared detail-screen components were modified.
2. Whether MOVIES and SERIES use the same backdrop rendering logic.
3. How landscape artwork is selected.
4. How image alignment and cropping are handled.
5. How the left-side gradient is implemented.
6. How missing artwork is handled.
7. Which tests were completed.
8. Any limitations requiring verification on the physical TV.

Do not claim physical-device testing unless it was actually performed.

---

# FINAL NON-NEGOTIABLE RULES

**EVERY MOVIE AND EVERY SERIES DETAIL SCREEN USES THE SAME CINEMATIC DESIGN.**

**FULL-SCREEN TITLE-SPECIFIC BACKDROP.**

**STRONG READABILITY ON THE LEFT.**

**CLEAR, VISIBLE CINEMATIC ARTWORK ON THE RIGHT.**

**SMOOTH GRADIENT — NO OPAQUE INFORMATION PANEL.**

**NO STRETCHED POSTERS.**

**NO DIFFERENT DETAIL DESIGN FOR DIFFERENT CATEGORIES.**

**PRESERVE WATCH, TRAILER, MY LIST AND THE FOUR EXISTING OPTIONS.**

**PRESERVE SERIES EPISODES AND SEASON NAVIGATION.**

**PURE GRAPHITE REMAINS THE GLOBAL BROWSING BACKGROUND.**

**DO NOT CHANGE EXISTING PLAYBACK OR REMOTE-CONTROL BEHAVIOR.**

This is the final approved global cinematic backdrop rule for PRETORIAN MOVIES and SERIES detail screens.