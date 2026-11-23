# PRETORIAN 2.4.2 — BUG FIX 02
## SERIES Gallery — Background Artwork, Poster Cropping & Image Alignment

**Priority:** High  
**Affected module:** SERIES — Poster Gallery / Series Browsing  
**Reference:** Attached screenshot showing the series *Proud* and the poster gallery  
**Scope:** Three specific visual/layout defects

Claude, this is the second bug report from actual Android TV testing.

Please fix the following three issues without redesigning the approved PRETORIAN interface or changing unrelated functionality.

## BUG 02A — Background Artwork Is Incorrectly Cropped

### Current problem

Look at the large background image behind the details of the selected series, *Proud*.

The artwork is positioned and cropped incorrectly. Only an awkward portion of the original image is visible, making it difficult to understand what the image represents.

The main subject is not properly framed.

### Required correction

- Correct the background image positioning so that its main subject is visible.
- Use the appropriate landscape backdrop rather than stretching a portrait poster into a widescreen background.
- Center the backdrop composition by default.
- Preserve the original image aspect ratio.
- Avoid excessive zooming and unnecessary cropping.
- If the image must be cropped to fill the available background area, crop symmetrically around the appropriate focal point rather than using an arbitrary offset.
- Maintain the approved darker gradient on the left for title, metadata, description and action buttons.
- Keep the artwork visually dominant toward the right where possible, without cutting off the main subject.

**Important:** Centering the artwork does not mean removing our approved cinematic composition. The goal is to keep the main subject visible while preserving a darker, readable text area on the left.

If artwork dimensions or metadata differ between series, the layout must adapt automatically.

Do not apply a hardcoded image offset that only fixes *Proud*.

### Acceptance test

Open multiple series with different backdrop compositions and confirm that their main subjects remain recognizable and the artwork is not unnecessarily zoomed or cropped.

---

## BUG 02B — Poster Cards Are Cropped at the Bottom of the Screen

### Current problem

In the screenshot, the poster gallery is positioned too low.

The bottom portions of the portrait posters extend beyond the visible gallery area.

As a result, users cannot see the complete poster cards.

This is especially noticeable along the bottom edge of the television screen.

### Required correction

**Move the entire poster gallery slightly upward.**

The gallery must have enough vertical space to display complete poster cards.

Requirements:

1. Every visible poster card must fit completely inside the usable screen area.
2. The bottom edge of every poster must be visible.
3. Focus borders must never be clipped.
4. Poster titles, ratings and other existing metadata must remain readable.
5. Maintain consistent spacing between the gallery and the bottom safe margin.
6. Do not place posters underneath the footer navigation hints.
7. Preserve the existing PRETORIAN visual style and poster proportions.

### Moving to another poster row

This is equally important.

When navigating DOWN to another poster row:

- The gallery must scroll vertically by the correct amount.
- The newly focused row must become fully visible.
- Every poster in that visible row must fit inside the available viewport.
- The previous row must not partially cover the focused row.
- The focused poster must never be cut off at the top or bottom.
- The screen must not stop scrolling while the focused poster remains partially outside the visible area.

Use stable focus-aware scrolling.

Avoid sudden jumps, repeated layout recalculations or flickering.

If there is insufficient space for a complete additional row, scroll to reveal that row instead of displaying an unusable cropped row.

### Technical investigation

Please inspect:

- Gallery vertical positioning.
- Poster-card height.
- Available viewport height.
- Bottom safe-area padding.
- Footer overlay placement.
- Vertical scroll-container constraints.
- Focus-aware scrolling.
- Row spacing and scroll offsets.
- Parent clipping and overflow behavior.

Do not simply reduce every poster to an unnecessarily small size.

The preferred solution is to reposition the gallery upward and correct its scrolling/layout constraints.

---

## BUG 02C — Poster Artwork Is Incorrectly Positioned Inside Poster Cards

### Current problem

Look closely at the individual series posters.

The artwork inside several poster frames is awkwardly cropped.

Important parts of the images are missing or positioned outside the visible area.

Some posters are difficult to recognize because their contents are not properly framed.

This is separate from BUG 02B.

BUG 02B concerns the position of the entire poster card on the screen.

**BUG 02C concerns the image rendering inside each poster card.**

### Required correction

The portrait poster artwork must be properly centered and proportionally scaled inside its container.

Requirements:

1. Preserve the original image aspect ratio.
2. Use the correct portrait poster artwork when available.
3. Do not stretch images.
4. Do not use an excessive zoom or scale factor.
5. Center the poster image horizontally and vertically.
6. Prefer showing the entire poster artwork whenever possible.
7. Avoid cutting off titles, faces and other important visual content.
8. Use consistent poster-card dimensions throughout the gallery.
9. Maintain the existing focus border and rating badge.
10. Handle different source-image aspect ratios gracefully.

### Recommended rendering behavior

For the poster gallery, use a **contain-style image fit** as the safe default when necessary to preserve the entire poster.

If the image aspect ratio differs from the card:

- Preserve the image proportions.
- Center the image.
- Allow subtle neutral background space inside the card if required.
- Do not stretch the artwork to eliminate that space.
- Do not crop essential poster content simply to fill every pixel.

A properly fitted complete poster is preferable to a visually confusing zoomed poster.

### Important distinction

Use different image-fitting strategies for different content types:

**Series background/backdrop:** Cinematic landscape presentation, with carefully controlled cropping and readable gradient.

**Series gallery poster:** Portrait artwork, properly fitted and centered, preferably fully visible.

Do not reuse the same aggressive center-crop behavior for both image types.

---

## GLOBAL SERIES GALLERY REQUIREMENTS

These corrections must work for all series, not only the examples visible in the screenshot.

The gallery must handle:

- Different poster aspect ratios.
- Different backdrop dimensions.
- Missing artwork.
- Long series titles.
- Different rating values.
- Large provider catalogs.
- Multiple poster rows.
- Fast D-Pad navigation.
- Returning from a series detail page.
- Restoring previous focus and scroll position.

All focused cards must remain fully visible.

Scrolling must remain smooth and predictable.

Do not rebuild or reload the entire gallery unnecessarily when focus changes.

---

## DO NOT CHANGE

Preserve:

- Existing navigation hierarchy.
- Shared MOVIES/SERIES gallery architecture.
- Poster selection behavior.
- Series detail view.
- Continue Watching.
- My List.
- Recently Added.
- Ratings and metadata.
- Existing remote-control focus behavior.
- Approved PRETORIAN colors, typography and transparency.
- Existing playback functionality.

Do not introduce new navigation levels, oversized panels, additional buttons or a different gallery design.

If a shared component is responsible for the same defect in MOVIES, identify it and ensure the correction does not create regressions there.

---

## REQUIRED ACCEPTANCE TESTS

- [ ] The *Proud* backdrop shows a recognizable, properly positioned subject.
- [ ] Backdrop images are not excessively zoomed.
- [ ] The left text area retains its dark readability gradient.
- [ ] Every visible portrait poster fits completely inside its card.
- [ ] Poster artwork is centered and maintains its aspect ratio.
- [ ] Poster titles and faces are not unnecessarily cropped.
- [ ] The first visible poster row is fully inside the screen.
- [ ] Poster bottoms and focus borders remain visible.
- [ ] Moving DOWN reveals the next complete row.
- [ ] Moving UP restores the previous row correctly.
- [ ] Fast scrolling does not cause jumping or flickering.
- [ ] Returning from a series detail screen preserves correct gallery positioning.
- [ ] Different artwork dimensions are handled correctly.
- [ ] MOVIES and other shared components are not negatively affected.

---

## IMPLEMENTATION INSTRUCTIONS

Fix **BUG 02A, BUG 02B and BUG 02C only**.

Investigate the actual causes before changing layout values.

Prefer a reliable shared layout correction over title-specific hardcoded offsets.

After making the corrections, report:

1. The root cause of each bug.
2. The files modified.
3. The changes made.
4. Which checks or tests were performed.
5. Which behaviors still require verification on the physical Android TV device.

Do not claim that the problems are fully fixed until they have been tested on the actual device.

**After completing BUG 02, stop and wait for BUG 03.**