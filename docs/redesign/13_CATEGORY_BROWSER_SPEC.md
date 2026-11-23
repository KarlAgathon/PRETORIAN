# PRETORIAN — MOVIES & SERIES CATEGORY BROWSER
## FINAL APPROVED DESIGN & INTERACTION SPECIFICATION

**Project:** PRETORIAN Android TV Media Center  
**Modules:** MOVIES and SERIES  
**Design:** Pure Graphite — Side List + Category Preview  
**References:** Two attached approved screenshots (MOVIES and SERIES)  
**Status:** Approved specification — implementation only when explicitly authorized

---

## 1. OBJECTIVE

Claude, we have approved a new category browsing design for both MOVIES and SERIES.

The existing category screens look too much like a traditional IPTV file manager. We want a more refined, premium Android TV experience without making navigation complicated.

The new design combines:

- An elegant vertical category list on the left.
- A dynamic category information area on the right.
- A permanent cinema film-reel illustration for MOVIES.
- A permanent professional television camera illustration for SERIES.
- Subtle graphite category outlines.
- A restrained silver-white focus indicator.
- A Browse Category action.
- Single-OK and double-OK navigation.

**This replaces the current category-list presentation only.**

It must not redesign the movie/series poster galleries, detail screens, video player, or other application modules.

---

## 2. PRETORIAN DESIGN PHILOSOPHY

Preserve our fundamental navigation rule:

**ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT.**

The right-side information area is part of the same category screen.

It is NOT:

- A separate screen.
- A modal dialog.
- A second navigation level.
- A floating overlay.
- A separate content browser.

When a category is opened, the entire category browser is replaced by the existing poster gallery.

When BACK is pressed from the gallery, return to the category browser.

Do not stack multiple category screens.

---

## 3. GLOBAL BACKGROUND

Use the existing official PRETORIAN Pure Graphite background image.

This is the only approved global background for browsing screens.

Do not recreate the background using a different gradient.

Do not introduce:

- Old concrete-room imagery.
- Blue spotlights.
- Neon effects.
- Purple accents.
- New decorative backgrounds.
- Bright colored panels.

The MOVIES and SERIES category screens must visually belong to the same application as HOME, TV and SEARCH.

---

## 4. OVERALL LAYOUT

Use a clean 16:9 two-column composition.

### Left column — Category navigation

Approximately 45–48% of the available content width.

Contains all available category entries in a vertically scrollable list.

### Right column — Category information

Approximately 52–55% of the available content width.

Contains:

1. CATEGORY label.
2. Selected category name.
3. Category content count.
4. Newly added count, when reliable data exists.
5. Short description.
6. Browse Category button.
7. Permanent module-specific illustration.

These proportions are initial design guidance, not rigid pixel requirements.

Ensure long category names remain readable and do not overlap counts or arrows.

Use the supplied screenshots to establish the final visual balance.

---

## 5. LEFT CATEGORY LIST — VISUAL DESIGN

Remove the existing large rectangular container surrounding the entire category list.

Instead, render each category as an individual elegant row.

### Unselected category

- Dark transparent graphite surface.
- Thin, barely visible graphite-gray border.
- Soft rounded corners.
- White or light-gray category text.
- Monochrome icon on the left.
- Item count and subtle chevron on the right.
- No glow.
- No bright colored fill.

### Selected category

When a category receives focus:

- Slightly brighten its graphite background.
- Display a thin but clearly visible silver-white outline.
- Keep the category name highly readable.
- Keep the item count and chevron visible.
- Use a subtle, quick focus transition.
- Do not enlarge the row dramatically.
- Do not use a strong white fill.

The focus appearance must match the restrained PRETORIAN design language.

**Selected means focused, not opened.**

Moving focus must not automatically open a category.

---

## 6. CATEGORY ORGANIZATION

Preserve the actual category organization and order from the existing implementation.

### MOVIES

The existing top-level special categories include:

- Recently Added
- In Cinemas
- My List

Below these, show the provider-defined movie categories in their original order.

### SERIES

The existing top-level special categories include:

- Recently Added
- Streaming
- My List

Below these, show the provider-defined series categories in their original order.

Do not invent additional categories.

Do not rename provider categories.

Do not hardcode category counts from the screenshots.

### Visual separation

Use a small, elegant spacing break between the three special categories and the provider-defined groups.

Avoid thick dividers and oversized section headers.

The separation should be visible but understated.

---

## 7. CATEGORY LIST SCROLLING

The category list must support any number of provider groups.

Requirements:

- Smooth vertical scrolling.
- Predictable UP/DOWN focus movement.
- Focused row always fully visible.
- No clipped first or last row.
- No excessive animation delays.
- No unexpected focus jumps.
- No automatic opening when focus changes.
- No unnecessary loading or rebuilding of the right-side illustration.

Preserve the focused category and scroll position when returning from its poster gallery.

The user should not have to navigate from the beginning every time.

---

## 8. RIGHT SIDE — CATEGORY PREVIEW

The right area displays information about whichever category is currently selected in the left list.

This area updates dynamically when focus changes.

### Information hierarchy

**CATEGORY**

Small, muted uppercase label.

**Recently Added**

Large, elegant title showing the selected category's actual name.

**Content count**

Example for MOVIES:

`976 Movies`

Example for SERIES:

`549 Series`

Use the real number of titles available in that category.

**Newly added count**

If reliable information exists, show the number of titles added during the most recent successful catalog update.

For example:

`+24 New`

This must be based on actual catalog changes, not an estimate.

If the application does not track this reliably, omit the new-items indicator.

Do not confuse the total category count with the number of newly added items.

**Description**

A short, useful description of the selected category.

Examples:

Recently Added:
"Discover the latest movies added to your library."

My List:
"Your saved titles, all in one place."

Provider categories:
"Browse the movies available in this category."

Use localization and the correct media type.

Avoid invented promotional descriptions for unknown provider groups.

---

## 9. MOVIES — PERMANENT FILM-REEL ILLUSTRATION

The MOVIES screenshot establishes the visual direction.

Use a premium cinema film reel with a flowing film strip.

### Appearance

- Metallic silver and dark graphite.
- Restrained cinematic lighting.
- Dark monochrome treatment.
- Positioned toward the right side.
- Integrated naturally into the information composition.
- No neon.
- No bright colorful accents.
- No excessive glow.

### Critical behavior

**The illustration remains the same across all MOVIES categories.**

For example:

Recently Added → film reel.

In Cinemas → same film reel.

My List → same film reel.

Netflix Movies → same film reel.

Only the category information changes.

Do not load separate artwork for every category.

Do not replace the illustration with random movie posters.

---

## 10. SERIES — PERMANENT TELEVISION CAMERA ILLUSTRATION

The SERIES screenshot establishes the visual direction.

Use a professional broadcast television camera.

### Appearance

- Premium studio camera.
- Dark graphite body.
- Subtle silver metallic details.
- Large professional camera lens.
- Soft, restrained lighting.
- Positioned toward the right side.
- No bright colors or neon.

### Critical behavior

**The camera illustration remains the same across all SERIES categories.**

For example:

Recently Added → TV camera.

Streaming → same TV camera.

My List → same TV camera.

Provider series groups → same TV camera.

Only the category information changes.

### Shared design

MOVIES and SERIES must use the same:

- Layout proportions.
- Typography.
- Category row styling.
- Focus behavior.
- Information hierarchy.
- Browse Category button.
- Remote-control navigation.

Only the illustration and module-specific content differ.

---

## 11. ILLUSTRATION ASSETS

The attached screenshots are design references.

Do not use an entire screenshot as a background.

The film reel and television camera should be separate image assets with transparent or appropriately blended backgrounds.

Preserve the official Pure Graphite image underneath.

Do not introduce a new full-screen background for MOVIES or SERIES.

If the separate illustration assets are not available, report that they are missing and request them.

Do not substitute unrelated images or recreate a different design without approval.

---

## 12. BROWSE CATEGORY BUTTON

The right-side information area contains one primary action:

**Browse Category**

The button should appear below the category description.

### Appearance

- Compact rectangular button.
- Slightly rounded corners.
- Dark smoky-glass surface.
- Thin silver-white outline.
- White play/forward icon.
- Clearly readable label.
- Subtle focus brightness change.
- No large bright background fill.
- No animated glow.

The button opens whichever category is currently selected in the left list.

It must never open a different category due to stale state.

---

## 13. REMOTE CONTROL — EXACT INTERACTION RULES

This section is mandatory.

### UP / DOWN

When focus is in the left category list:

- Move between categories.
- Update the right-side information.
- Preserve the same illustration.
- Do not open the category.
- Do not trigger unnecessary network requests.

### SINGLE OK ON A CATEGORY

A single OK press on a focused category:

**Moves focus to the Browse Category button on the right.**

It does not open the category immediately.

The category remains selected.

The right-side information remains unchanged.

### DOUBLE OK ON A CATEGORY

Two deliberate, quick OK presses on the same category:

**Open that category directly.**

This is a shortcut for experienced users.

Use a reasonable double-press interval, initially approximately 300–400 ms, subject to testing.

### OK ON BROWSE CATEGORY

Open the selected category using the existing category-opening logic.

### LEFT FROM BROWSE CATEGORY

Return focus to the selected category in the left list.

### RIGHT FROM A CATEGORY

Allow direct focus navigation to Browse Category.

### BACK FROM BROWSE CATEGORY

Return focus to the selected category in the left list without leaving the category screen.

### BACK FROM THE CATEGORY LIST

Return to the previous logical navigation level.

### BACK FROM THE POSTER GALLERY

Return to the category browser, restoring:

- Previously selected category.
- Previous list scroll position.
- Previous focus position.

Do not return to the main application menu prematurely.

---

## 14. DOUBLE-OK IMPLEMENTATION — IMPORTANT

The single-OK and double-OK behavior must be implemented carefully.

The first OK transfers focus to Browse Category.

A second quick OK must still be recognized as the second press of the original category double-click sequence.

Do not accidentally treat the second press as an unrelated Browse Category click.

### Required safeguards

- Track the originating category identity.
- Recognize only two deliberate presses.
- Ignore automatic key-repeat events from a held OK button.
- Do not open a category twice.
- Do not trigger two navigation operations.
- Cancel pending double-click recognition when the user navigates to another category.
- Ensure a slow second press behaves as an ordinary OK on the Browse Category button.
- Keep normal single-OK behavior responsive.

Use the application's existing focus and key-event architecture where possible.

Avoid a screen-wide interception system that interferes with other buttons.

The category screen must remain usable even if the user never uses double-click.

---

## 15. CATEGORY OPENING

Both opening methods must call the same existing navigation function.

**Method A:** Double OK on a category.

**Method B:** OK on Browse Category.

Both must open the exact same poster gallery.

There must be no differences in:

- Category filtering.
- Item ordering.
- Poster loading.
- Focus initialization.
- Playback behavior.
- BACK behavior.

Do not create a separate gallery implementation.

---

## 16. HEADER AND BRANDING

Preserve the existing shared status bar.

Use the newly approved branding rule:

- Small, subtle engraved Spartan helmet.
- No MEDIA CENTER text.
- No PRETORIAN wordmark beside the helmet.
- No decorative underline.

The MOVIES reference screenshot contains a PRETORIAN wordmark. **That text is not approved and must not be implemented.**

Preserve the existing clock, notification indicators and status information.

The active playlist indicator should follow the separately approved format:

`TREX — [48 DAYS]`

The actual name and days must come from the active playlist.

Keep the thin status-colored border around the remaining-days value.

Do not hardcode screenshot values.

---

## 17. PERFORMANCE

The category screen must remain fast and lightweight.

The right-side illustration is static.

Do not reload it whenever the selected category changes.

When moving between categories, update only the necessary text and count information.

Avoid:

- Full-screen recomposition when unnecessary.
- Repeated image decoding.
- Heavy blur.
- Expensive transitions.
- Network requests on every focus change.
- Long delays before the preview updates.

The selected category's information should update promptly.

Use existing cached catalog metadata.

---

## 18. LOCALIZATION

Respect the currently selected application language.

Localize application-controlled labels such as:

- CATEGORY
- Recently Added
- In Cinemas
- Streaming
- My List
- Browse Category
- Movies
- Series
- New

Provider-defined category names must remain exactly as supplied by the provider unless an existing approved normalization rule applies.

Do not translate or rename provider categories automatically.

---

## 19. DISPLAY AND ACCESSIBILITY

Test the design at both 1080p and 4K display configurations.

Requirements:

- Text remains readable from a typical TV viewing distance.
- Long provider category names do not overlap counts.
- The selected outline is clearly visible.
- Focus does not disappear.
- Category rows are not cropped.
- The illustration does not cover information.
- Browse Category remains visible and accessible.
- No content extends outside safe display margins.

Do not sacrifice readability for decorative effects.

---

## 20. ACCEPTANCE TESTS

### Visual design

- [ ] MOVIES uses the film-reel illustration.
- [ ] SERIES uses the TV-camera illustration.
- [ ] Official Pure Graphite background is preserved.
- [ ] No large outer list container.
- [ ] Individual category rows have subtle graphite borders.
- [ ] Selected category has a thin silver-white border.
- [ ] Selected category is slightly brighter.
- [ ] Both modules share the same design language.

### Category data

- [ ] Real category names are displayed.
- [ ] Provider-defined order is preserved.
- [ ] Real category counts are displayed.
- [ ] No fabricated new-item counts.
- [ ] Preview updates when category focus changes.
- [ ] Illustration remains unchanged within each module.

### Navigation

- [ ] UP/DOWN moves through categories.
- [ ] Single OK moves focus to Browse Category.
- [ ] Double OK directly opens the category.
- [ ] OK on Browse Category opens the category.
- [ ] LEFT returns focus to the category list.
- [ ] BACK from Browse Category returns to the list.
- [ ] BACK from the list returns to the previous level.
- [ ] BACK from a poster gallery restores the selected category.
- [ ] Holding OK does not trigger double-click.
- [ ] No duplicate category-opening events.
- [ ] No focus traps.

### Performance

- [ ] Smooth scrolling with large category lists.
- [ ] No repeated illustration loading.
- [ ] No noticeable delay updating category information.
- [ ] No unnecessary catalog reloads.
- [ ] No regression in poster galleries or playback.

---

## 21. IMPLEMENTATION REPORT

After implementation, provide:

1. Source files modified.
2. New image assets added.
3. Confirmation that MOVIES and SERIES share the same category browser design.
4. Explanation of single-OK and double-OK handling.
5. Explanation of focus restoration.
6. Confirmation that existing category data and navigation are reused.
7. Tests performed.
8. Any limitations requiring physical Android TV verification.

Do not claim physical-device testing unless it was actually performed.

---

# FINAL NON-NEGOTIABLE RULES

**ONE SCREEN, TWO COLUMNS, NO ADDITIONAL MENU LEVEL.**

**MOVIES = FILM REEL.**

**SERIES = PROFESSIONAL TV CAMERA.**

**THE ILLUSTRATION NEVER CHANGES WITHIN ITS MODULE.**

**SUBTLE INDIVIDUAL CATEGORY FRAMES.**

**SELECTED CATEGORY = THIN SILVER-WHITE OUTLINE + SLIGHTLY BRIGHTER GRAPHITE.**

**SINGLE OK = FOCUS BROWSE CATEGORY.**

**DOUBLE OK = OPEN CATEGORY DIRECTLY.**

**OK ON BROWSE CATEGORY = OPEN CATEGORY.**

**BACK = ONE LOGICAL STEP, WITH FOCUS RESTORATION.**

**OFFICIAL PURE GRAPHITE BACKGROUND ONLY.**

**NO PRETORIAN WORDMARK — ENGRAVED HELMET ONLY.**

**PRESERVE ALL EXISTING CATALOG AND PLAYBACK FUNCTIONALITY.**

This specification supersedes the old MOVIES and SERIES category-list visual design. It does not supersede the separately approved poster gallery, playback, or global navigation requirements.