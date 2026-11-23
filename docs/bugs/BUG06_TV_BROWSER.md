# PRETORIAN 2.4.2 — BUG FIX 06
## TV Channel Browser — Larger Preview, Perfect Alignment & Header Corrections

**Priority:** Medium–High  
**Affected module:** TV Channel Browser  
**Reference:** Attached screenshot showing the Bosnia channel group and BA| N1 BH preview  
**Scope:** Three visual/layout corrections only

Claude, this is BUG 06 from our physical Android TV testing.

The Channel Browser is already functional and much of its design is correct. However, the current layout does not use the available screen space efficiently.

We need to refine the existing design, not replace it.

---

## BUG 06A — Enlarge the Live TV Preview and Align All Controls

### Current problem

In the attached screenshot, the live TV preview on the right is too small relative to the available space.

There is unnecessary empty space between the channel list and preview, and between the preview and the right screen edge.

The preview and the controls below it do not form a sufficiently unified layout.

### Required correction

**Make the preview larger and wider, using nearly the entire available right-hand content area.**

The preview should extend:

- Toward the channel list on the left, leaving a narrow but comfortable gap.
- Toward the right safe margin, without touching or crossing the screen edge.
- Within the available vertical area, without overlapping the header or footer.

Maintain the video preview's true **16:9 aspect ratio**.

Do not stretch or distort the video.

### Critical alignment rule

The preview, EPG information and bottom action controls must share the same horizontal boundaries.

Conceptually:

```text
CHANNEL LIST        RIGHT CONTENT AREA

01  Channel         ┌─────────────────────────────┐
02  Channel         │                             │
03  Channel         │       LIVE TV PREVIEW       │
04  Channel         │            16:9             │
05  Channel         │                             │
                    └─────────────────────────────┘

                    CHANNEL NAME
                    NOW — Current program
                    ───────────────────────────────
                    NEXT — Upcoming program

                    ┌─────────────────────────────┐
                    │  ⏯  ☆  EPG  Picture  ...    │
                    └─────────────────────────────┘
```

The diagram is conceptual, not a request to add new visible boxes.

### Alignment requirements

1. The left edge of the preview establishes the left edge of the right content column.
2. The EPG information begins at exactly the same horizontal position.
3. The EPG progress bar follows the same alignment.
4. The bottom control container begins at the same horizontal position.
5. The right edge of the control container must not extend beyond the preview's right edge.
6. Prefer the same width for the preview and the lower control region, creating a unified composition.
7. The right edge of the preview should approach the screen's right safe margin.
8. Maintain consistent vertical spacing between preview, EPG and controls.
9. Do not overlap the bottom navigation hints.
10. Preserve the left channel list's readability and scrolling area.

**The preview must never be wider than the aligned control region below it.**

Treat the preview, EPG and control region as one responsive right-side layout group.

### Responsive sizing

Do not use arbitrary hardcoded pixel offsets that work only at 1920 × 1080.

Calculate the available width from:

- Screen width.
- Safe-area margins.
- Left channel-list width.
- Gap between columns.

Then calculate the preview height from its 16:9 aspect ratio.

Also consider the available vertical space for:

- Header.
- Preview.
- EPG.
- Bottom controls.
- Footer hints.

If the available vertical space is insufficient, reduce the preview size proportionally rather than clipping the EPG or controls.

### Important

Do not enlarge the preview so much that it causes:

- Cropped video.
- Overlapping EPG.
- Cropped bottom controls.
- Channel-list obstruction.
- Footer overlap.
- Horizontal overflow.

The objective is to fill the available space elegantly while preserving correct proportions and alignment.

---

## BUG 06B — Remove the Unnecessary TREX Information Border

### Current problem

At the top-left of the Channel Browser, the playlist information is enclosed in a rounded rectangular border.

Example:

TREX · 11.01.2027 · 48 days left

This extra container is visually unnecessary.

It adds clutter to an interface that should remain minimal.

### Required correction

Remove the surrounding rounded box completely.

Keep the information itself.

Display it as clean inline header text:

**TREX · 11.01.2027 · 48 days left**

Requirements:

- No surrounding pill.
- No border.
- No opaque background.
- No unnecessary shadow.
- Preserve the existing header typography.
- Use restrained white/gray text.
- The remaining-days indicator may retain its subtle status color.
- Maintain consistent spacing between values.
- Preserve the real playlist expiration data.
- Do not hardcode the example date or remaining days.

The playlist information should feel integrated into the header rather than displayed as a separate button.

### Header alignment

Keep the playlist information, breadcrumb and right-side status elements on a consistent horizontal baseline.

Ensure sufficient spacing between:

- Playlist information.
- TV / BOSNIA breadcrumb.
- Channel count, if displayed.
- Notification bell.
- Time and date.
- Existing account/settings indicator.

Do not allow these elements to collide at smaller resolutions.

---

## BUG 06C — Notification Bell Is Partially Cropped

### Current problem

The notification bell in the top header appears partially clipped or incorrectly positioned.

The icon and its notification badge do not have enough safe space around them.

### Required correction

Ensure the complete bell icon is visible.

Also ensure any notification-count badge is fully visible.

Requirements:

1. Do not clip the bell icon.
2. Do not clip the notification badge.
3. Provide sufficient internal padding.
4. Respect the top safe margin.
5. Avoid parent containers with inappropriate clipping.
6. Align the bell vertically with the other header elements.
7. Maintain a consistent icon size.
8. Keep its focus and click behavior unchanged.
9. Do not move the icon so far inward that it disrupts the header layout.

Inspect the icon's actual bounds, parent padding and clipping behavior.

Do not hide the problem by simply making the bell extremely small.

---

## PRESERVE THE EXISTING CHANNEL BROWSER

Do not redesign the channel list.

Keep:

- One channel per row.
- Channel number.
- Channel logo.
- Channel name.
- Actual quality badge where available.
- Existing focus highlight.
- Smooth vertical scrolling.
- Current preview behavior.
- NOW/NEXT EPG information.
- Existing playback logic.
- Existing navigation hierarchy.
- PRETORIAN dark concrete design language.

The channel list must remain completely readable.

Do not introduce large channel cards or unnecessary new panels.

---

## IMPORTANT — PREVIEW AND FULLSCREEN ARE DIFFERENT

These layout corrections apply to the Channel Browser preview screen.

They must not alter the separate fullscreen player design.

The fullscreen player continues to use the approved:

- Dark floating options dock.
- UP/DOWN to open options.
- LEFT for current-group channel list.
- RIGHT for Floating EPG.
- BACK for one-level navigation.
- Existing fullscreen overlay behavior.

Do not accidentally change those mappings while adjusting the Channel Browser.

---

## ACCEPTANCE TESTS

- [ ] Preview is noticeably larger than before.
- [ ] Preview approaches the left channel list without overlapping it.
- [ ] Preview approaches the right safe margin.
- [ ] Preview maintains a correct 16:9 aspect ratio.
- [ ] Preview video is not distorted.
- [ ] EPG is aligned with the preview.
- [ ] EPG progress bar is correctly aligned.
- [ ] Bottom controls align with the preview's horizontal boundaries.
- [ ] Preview and controls form one unified right-side composition.
- [ ] No controls are cropped.
- [ ] Footer navigation hints remain visible.
- [ ] TREX information has no surrounding border.
- [ ] Playlist information remains readable.
- [ ] Breadcrumb and status header remain aligned.
- [ ] Notification bell is fully visible.
- [ ] Notification badge is not clipped.
- [ ] Channel-list scrolling and focus remain unchanged.
- [ ] Preview playback still works correctly.
- [ ] Fullscreen playback is unaffected.

---

## IMPLEMENTATION INSTRUCTIONS

Fix **BUG 06A, BUG 06B and BUG 06C only**.

Do not introduce unrelated changes.

After implementation, report:

1. The cause of the undersized preview.
2. How the right-side layout dimensions are calculated.
3. How preview, EPG and controls are aligned.
4. How the TREX border was removed.
5. Why the notification bell was clipped.
6. Which files were modified.
7. Which checks were completed.
8. What still requires testing on the physical Android TV device.

Do not claim device-level verification until the corrected build has been tested on the television.

**After BUG 06, stop and wait for BUG 07.**