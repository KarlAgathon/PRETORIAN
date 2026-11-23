# PRETORIAN 2.4.2 — BUG FIX 01
## SERIES — Episode Carousel Cropping & Background Artwork Positioning

**Priority:** High  
**Affected module:** SERIES — Single Series View  
**Reference:** Attached screenshot of *Wilderness*  
**Scope:** Fix only the two issues described below.

Claude, we are now testing PRETORIAN version 2.4.2 on an actual Android TV device.

We will report and resolve bugs individually, with a screenshot for each report. Please avoid unrelated modifications, redesigns or new features.

This screenshot demonstrates two problems in the SERIES detail screen.

---

## BUG 01A — Episode Carousel Is Cropped on Initial Load

### Current problem

Look at the first episode thumbnail, **E01**, in the attached screenshot.

Its thumbnail/focus border is not rendered correctly on initial screen load.

The important observation is:

**As soon as I navigate LEFT or RIGHT using the remote, the layout corrects itself.**

This strongly suggests a possible initial layout measurement, horizontal scroll offset, focus positioning or rendering synchronization issue.

Please investigate the actual cause rather than applying a cosmetic workaround.

### Required behavior

1. When opening a series, the episode carousel must render correctly immediately.
2. E01 must be fully visible, including its complete focus outline.
3. No episode thumbnail or border may be clipped by the carousel container.
4. The initial horizontal scroll offset must be correct.
5. The initial focused episode must be positioned correctly.
6. No remote-control movement should be required to repair the layout.
7. The carousel must maintain correct positioning when:
   - Opening a series for the first time.
   - Returning from fullscreen playback.
   - Changing seasons.
   - Returning to the series from another screen.
   - Restoring previously focused episodes.
8. LEFT/RIGHT navigation and circular episode navigation must continue working as previously approved.

### Technical investigation

Please inspect:

- Initial carousel measurements.
- Layout timing.
- Scroll state initialization.
- Focus restoration.
- Content padding.
- Item spacing.
- Focus-border clipping.
- Parent container clipping.
- Any focus-triggered scrolling or layout recalculation.

Do not solve this by adding arbitrary margins that create new alignment problems.

The carousel should calculate its correct position before it becomes visible.

**Acceptance test:** Open *Wilderness* directly and verify that E01 and every other visible episode are completely rendered before pressing any remote button.

---

## BUG 01B — Incorrect Series Background Artwork Positioning

### Current problem

The background artwork is not positioned according to our approved PRETORIAN design philosophy.

In the screenshot:

- The central character dominates the middle of the interface.
- The artwork competes with the series title, description and buttons.
- The left side is insufficiently dark.
- The background composition feels crowded.
- The artwork does not transition naturally into the dark text area.

### Required visual result

We previously approved a cinematic layout with:

**LEFT SIDE — DARK TEXT ZONE**

The left side must have a stronger charcoal-to-black gradient.

This area contains:

- Series title.
- Rating.
- Year.
- Season count.
- Genres.
- Synopsis.
- Continue / Watch button.
- Trailer button.
- My List button.

All text must remain clearly readable regardless of the artwork colors.

**RIGHT SIDE — ARTWORK ZONE**

The main artwork should visually occupy the right side of the screen.

Adjust its positioning so the important subject is shifted toward the right and, where composition permits, slightly downward.

The artwork must not dominate the center behind the synopsis and action buttons.

**GRADIENT TRANSITION**

Use a smooth cinematic gradient:

- Darkest on the left.
- Gradually more transparent toward the right.
- No visible dividing line.
- No heavy opaque rectangle behind the description.
- Preserve the overall PRETORIAN charcoal/graphite atmosphere.

### Important artwork handling

Distinguish between:

- Landscape backdrop artwork.
- Portrait poster artwork.

Prefer the appropriate landscape backdrop for a fullscreen 16:9 background.

Do not stretch a portrait poster across the screen.

Preserve the original aspect ratio.

Avoid unnecessary cropping of important subjects, but do not distort the image simply to display every pixel.

If the available backdrop composition cannot be shifted without losing important content, use appropriate scaling and gradient treatment instead.

The result should feel cinematic and balanced, not artificially stretched.

### Consistency

This is a shared layout issue.

Apply the corrected SERIES background treatment consistently to all series, not only *Wilderness*.

Review the corresponding MOVIES background implementation for the same underlying problem, but do not redesign MOVIES or change unrelated behavior.

---

## DO NOT CHANGE

Please preserve all currently working functionality:

- Series title and metadata.
- Continue / Watch behavior.
- Trailer button and original red YouTube icon.
- My List.
- Season selector.
- Episode order and watched progress.
- Episode selection and playback.
- Subtitles, Audio, Playback and Picture controls.
- Existing PRETORIAN typography and color palette.
- Remote-control navigation.
- Previously approved focus behavior.

Do not introduce new panels, buttons, navigation levels or visual effects.

---

## REQUIRED VERIFICATION

Before declaring BUG 01 fixed, verify:

- [ ] E01 is fully visible immediately on first render.
- [ ] Its complete focus border is visible.
- [ ] No other visible episode is cropped.
- [ ] LEFT/RIGHT scrolling remains smooth.
- [ ] Focus restoration works after returning from playback.
- [ ] Season switching does not recreate the cropping bug.
- [ ] The left text area is visibly darker.
- [ ] The main artwork is visually positioned toward the right.
- [ ] The artwork no longer competes with the title or synopsis.
- [ ] Image aspect ratio is preserved.
- [ ] The fix works with different series artwork.
- [ ] No previously working features are broken.

## IMPLEMENTATION INSTRUCTION

Fix **BUG 01A and BUG 01B only**.

Use the attached screenshot to understand the current defects.

Investigate the root causes, make the smallest reliable corrections, and report:

1. What caused each problem.
2. Which files were modified.
3. What was changed.
4. What was verified in code or tests.
5. What still requires testing on the actual Android TV device.

Do not claim the bugs are fully resolved until the corrected build has been tested on the device.

**After completing BUG 01, stop and wait for BUG 02.**