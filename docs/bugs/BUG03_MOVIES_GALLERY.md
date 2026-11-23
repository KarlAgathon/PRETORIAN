# PRETORIAN 2.4.2 — BUG FIX 03
## MOVIES — Poster Cropping, Artwork Scaling & Gallery Scrolling

**Priority:** High  
**Affected module:** MOVIES — Poster Gallery  
**Reference:** Attached screenshot showing *The Chestnut Man* and the movie/series poster gallery  
**Scope:** Three visual and layout defects

Claude, this is BUG 03 from our Android TV device testing.

We have already reported similar problems in BUG 02 for the SERIES gallery.

The same underlying issues are now confirmed in the MOVIES section.

Please investigate the shared MOVIES/SERIES gallery architecture and implement a consistent correction.

Do not create separate visual behavior for MOVIES and SERIES.

---

## BUG 03A — Poster Cards Are Cropped During Vertical Scrolling

### Current problem

When browsing downward through the MOVIES gallery, the poster cards are partially cut off at the bottom of the television screen.

In the attached screenshot, the lower parts of several posters are missing.

This happens during navigation between poster rows.

The gallery does not correctly reposition itself to keep the selected row fully visible.

### Required correction

Every visible poster card must be completely displayed within the usable television viewport.

Requirements:

1. Move the gallery slightly upward if necessary.
2. Ensure the bottom of each visible poster remains inside the screen's safe area.
3. Preserve the complete poster focus border.
4. Prevent the footer navigation hints from overlapping poster cards.
5. When moving DOWN to another row, automatically scroll enough to reveal the complete newly selected row.
6. When moving UP, restore the previous row without clipping.
7. Never leave the focused poster partially outside the viewport.
8. Avoid displaying a partially visible poster row at the bottom when the layout should instead scroll to reveal it.
9. Maintain smooth scrolling without sudden jumps or flickering.
10. Preserve the focused poster's logical column when navigating between rows.

### Important

Do not fix this by arbitrarily shrinking every poster.

The preferred solution is to correct the gallery's vertical layout, viewport constraints, scrolling offsets and safe-area padding.

The poster cards should remain comfortably sized for television viewing.

---

## BUG 03B — Poster Images Are Excessively Cropped Inside Their Cards

### Current problem

Several poster images appear zoomed in or incorrectly positioned.

Only parts of faces or artwork are visible.

Some posters are difficult to recognize because the original image composition is lost.

This is a separate problem from the gallery being cropped by the screen.

### Required correction

Poster artwork must be properly fitted inside its portrait card.

Use the following rules:

- Preserve the source image's original aspect ratio.
- Center the artwork horizontally and vertically.
- Prefer showing the complete poster image.
- Avoid excessive zooming.
- Avoid unnecessary cropping of faces, titles or important artwork.
- Do not stretch or distort the image.
- Keep consistent poster-card dimensions.
- Preserve the existing rating badge.
- Preserve the existing silver-white focus border.

### Image fitting

Use a contain-style fitting strategy when necessary to preserve the entire poster.

If the poster's source aspect ratio differs from the card:

- Fit the complete image inside the card.
- Center it.
- Use a subtle neutral background for any unused space.
- Do not stretch the image to fill the card.
- Do not aggressively crop the image to eliminate empty space.

A complete, recognizable poster is more important than filling every pixel of its container.

### Image selection

Verify that the gallery loads the correct portrait poster asset.

Do not substitute a landscape backdrop and aggressively crop it into a portrait card when a proper poster is available.

If the provider supplies no portrait poster, use the established PRETORIAN fallback rather than producing a severely cropped image.

---

## BUG 03C — Background Artwork Is Stretched or Incorrectly Positioned

### Current problem

The large background image behind the selected title appears unnaturally scaled and cropped.

The composition does not look cinematic.

Important visual subjects are partially missing or awkwardly positioned.

The image also competes with the title and action buttons.

### Required correction

Restore the approved PRETORIAN cinematic background treatment.

**LEFT SIDE**

- Dark charcoal/black gradient.
- Clearly readable title and metadata.
- Clearly readable synopsis.
- Clearly readable Watch, Trailer and My List buttons.
- No distracting bright artwork behind the text.

**RIGHT SIDE**

- Properly scaled landscape backdrop.
- Recognizable main subjects.
- Natural image proportions.
- No excessive zooming.
- No stretching.
- Artwork positioned toward the right when appropriate.

**GRADIENT**

Use a smooth left-to-right transition.

The left side should be darker, gradually revealing more artwork toward the right.

Do not create a visible dividing line.

Do not add an opaque rectangular background panel.

### Image handling

Use the actual landscape backdrop where available.

Preserve its aspect ratio.

Avoid using a portrait poster as a stretched fullscreen background.

For different backdrop dimensions, adapt the rendering automatically.

If filling the entire background requires cropping, use a controlled crop that preserves the important composition as much as possible.

Do not apply title-specific offsets that only fix *The Chestnut Man*.

---

## SHARED MOVIES / SERIES COMPONENT REQUIREMENT

This is critical.

BUG 02 demonstrated the same issues in SERIES.

BUG 03 confirms them in MOVIES.

Please determine whether both screens use shared components for:

- Poster rendering.
- Gallery layout.
- Vertical scrolling.
- Focus management.
- Background artwork.
- Image scaling and positioning.

Where shared components exist, correct the underlying problem once.

Do not maintain separate inconsistent poster sizing or cropping rules between MOVIES and SERIES.

The two sections must remain visually consistent.

---

## FOCUS AND REMOTE NAVIGATION

While correcting the gallery:

- Preserve stable D-Pad focus.
- Keep the selected poster completely visible.
- Avoid sudden focus jumps.
- Preserve focus when returning from a title detail view.
- Restore the correct scroll position.
- Ensure fast UP/DOWN navigation does not cause flickering.
- Ensure LEFT/RIGHT navigation remains smooth.
- Do not reload the entire gallery unnecessarily after every focus change.

If a new row is not fully visible, scroll the gallery to reveal it before finalizing the focus transition.

---

## DO NOT CHANGE

Preserve all working features:

- MOVIES and SERIES navigation hierarchy.
- Poster selection.
- Watch and Continue.
- Trailer.
- My List.
- Rating badges.
- Title metadata.
- Search.
- Existing focus styling.
- PRETORIAN colors and typography.
- Playback.
- Previously approved design language.

Do not add new menus, buttons or navigation levels.

---

## ACCEPTANCE TESTS

- [ ] Poster cards are completely visible when opening MOVIES.
- [ ] No poster is cut off at the bottom.
- [ ] Focus borders remain fully visible.
- [ ] Moving DOWN reveals the complete next poster row.
- [ ] Moving UP correctly restores the previous row.
- [ ] Fast navigation does not cause jumping or flickering.
- [ ] Poster images are properly centered.
- [ ] Poster artwork maintains its aspect ratio.
- [ ] Faces, titles and important artwork are not unnecessarily cropped.
- [ ] Different poster dimensions are handled correctly.
- [ ] The correct portrait artwork is used.
- [ ] Background images are not stretched.
- [ ] Background compositions remain recognizable.
- [ ] The left side remains dark enough for text readability.
- [ ] MOVIES and SERIES use consistent poster behavior.
- [ ] Returning from a title detail view preserves correct focus and scrolling.

---

## IMPLEMENTATION INSTRUCTIONS

Fix **BUG 03A, BUG 03B and BUG 03C**.

First inspect whether BUG 02 and BUG 03 share the same root causes.

Avoid duplicating fixes already implemented for BUG 02.

Make the smallest reliable corrections necessary.

After implementation, report:

1. The root cause of each issue.
2. Which shared components were affected.
3. Which files were modified.
4. How poster scaling and gallery scrolling were corrected.
5. Which tests were performed.
6. What still requires testing on the actual Android TV device.

Do not claim the issues are fully resolved until verified on the device.

**After BUG 03, stop and wait for BUG 04.**