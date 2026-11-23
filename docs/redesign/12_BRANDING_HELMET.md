# PRETORIAN — FINAL BRANDING IMPLEMENTATION
## TRANSPARENT SPARTAN HELMET · PURE GRAPHITE · HOME SCREEN

**Project:** PRETORIAN Android TV Media Center  
**Priority:** Mandatory branding correction  
**Target:** `Home2.kt` and related branding resources  
**Asset:** `PRETORIAN_Spartan_Helmet_Transparent.png` (attached)  
**Design system:** PRETORIAN Pure Graphite  
**Status:** Final approved branding instruction

---

## 1. OBJECTIVE

Claude, we have now prepared the Spartan helmet PNG asset you requested.

This asset replaces the old MEDIA CENTER heading in the PRETORIAN HOME screen.

The previous branding is permanently retired.

The new design uses only a small, subtle Spartan helmet silhouette that appears lightly engraved into the Pure Graphite background.

**This is a targeted branding correction, not a redesign of HOME.**

Do not change the existing layout, navigation, content cards, playback functionality or background.

---

## 2. MANDATORY PRIORITY — NEW RULE OVERRIDES OLD RULES

This instruction supersedes every previous instruction concerning the branding area above the left navigation.

If older specifications require:

- MEDIA CENTER text
- A decorative underline
- A PRETORIAN wordmark
- Any other text heading
- A large logo
- A visible logo container

those requirements are obsolete.

**The final branding rule is: ONE SPARTAN HELMET ICON ONLY.**

No text.

No underline.

No decorative container.

---

## 3. REMOVE THE OLD BRANDING FROM THE CODE

You confirmed that the MEDIA CENTER text and underline originate in `Home2.kt`.

Please remove the corresponding UI elements from the actual source code.

Do not:

- Hide the text using opacity.
- Move it outside the screen.
- Cover it with the helmet image.
- Leave an invisible duplicate component.
- Preserve the old underline.

Remove the obsolete branding elements cleanly.

Check whether the old text or underline is referenced elsewhere in the HOME layout and eliminate any remaining rendering paths.

Do not remove unrelated application functionality.

---

## 4. USE THE ATTACHED OFFICIAL PNG

The attached file is:

`PRETORIAN_Spartan_Helmet_Transparent.png`

This is the approved asset for the new branding implementation.

### Asset requirements

- Preserve the original image proportions.
- Preserve transparency.
- Do not stretch.
- Do not crop the helmet.
- Do not replace it with an emoji or generic icon.
- Do not draw an alternative helmet.
- Do not add text to the image.
- Do not add a background rectangle.
- Do not add a visible circular badge.

Place the asset in the appropriate Android drawable resource location using the project's existing naming conventions.

If Android resource rules require renaming, use a valid lowercase filename such as:

`pretorian_spartan_helmet.png`

Keep the original asset available as the source reference.

---

## 5. POSITIONING

Place the helmet in the existing upper-left branding area, where MEDIA CENTER was previously displayed.

### Requirements

- Keep it visually small.
- Preserve the current navigation column width.
- Preserve the vertical space allocated to branding.
- Do not move SEARCH downward.
- Do not shift HOME, TV, MOVIES, SERIES, SPORTS or SETTINGS.
- Do not modify content card positions.
- Do not change the header height.
- Do not affect the top-right status bar.

The icon should be aligned naturally within the existing left navigation geometry.

It must not appear as a floating decorative object.

---

## 6. SIZE AND SCALING

The supplied PNG is high-resolution and should be scaled down for the interface.

Use density-independent sizing appropriate for Android TV.

Start with a compact visible helmet size of approximately 40–48 dp in height, adjusting only as necessary to match the approved HOME reference.

Preserve the image's aspect ratio.

Use a fit/contain scaling mode.

Do not use a crop scaling mode.

If the transparent image contains unnecessary internal margins, adjust the rendering bounds or prepare a properly trimmed transparent asset without altering the helmet itself.

The icon must remain crisp on Full HD and 4K displays.

---

## 7. ENGRAVED GRAPHITE APPEARANCE

The final result must not look like a large metallic emblem.

The attached PNG provides the helmet silhouette.

The application must adapt it to the PRETORIAN Pure Graphite design language.

### Desired appearance

The helmet should look as though it is lightly engraved or embossed into the graphite surface.

Use:

- Very restrained contrast.
- Graphite gray tones.
- Low opacity.
- Subtle tonal separation from the background.
- Extremely soft edge definition.

Avoid:

- Bright silver metallic shine.
- White glowing edges.
- Blue highlights.
- Purple highlights.
- Red accents.
- Heavy shadows.
- Strong 3D effects.
- Animated lighting.
- Neon effects.

### Suggested implementation approach

Use a restrained monochrome tint or suitable color-filter treatment if needed.

Adjust opacity until the helmet is recognizable but visually unobtrusive.

If implementing a subtle engraved edge effect, use a minimal light/dark tonal offset rather than an obvious drop shadow.

Do not introduce expensive image effects or runtime blur.

The final logo should feel integrated into the background, not placed on top as a prominent badge.

---

## 8. NO TEXT — ABSOLUTE RULE

Do not display:

- MEDIA CENTER
- PRETORIAN
- SPARTAN
- A slogan
- A subtitle
- Any branding text

in the upper-left branding area.

Do not add a decorative underline.

The helmet icon alone represents PRETORIAN.

---

## 9. PRESERVE PURE GRAPHITE

You confirmed that the application already uses the official Pure Graphite background asset.

Keep that asset unchanged.

Do not modify:

- Background colors.
- Gradient transitions.
- Image brightness.
- Background texture.
- Background scaling.
- Global background architecture.

We are not approving a background color adjustment at this stage.

The current slightly cool graphite appearance is acceptable for now.

Do not reintroduce old concrete-room scenery or blue spotlight effects.

---

## 10. PRESERVE THE APPROVED HOME LAYOUT

The left navigation order must remain:

1. SEARCH
2. HOME
3. TV
4. MOVIES
5. SERIES
6. SPORTS
7. SETTINGS

Preserve:

- Navigation positions.
- Vertical spacing.
- Icon sizes.
- Text styling.
- Focus indicators.
- Selected-item appearance.
- D-pad navigation.
- BACK behavior.
- Content alignment.

Do not use this branding correction as an opportunity to redesign navigation.

---

## 11. PRESERVE HOME CONTENT

Do not modify the existing HOME content sections.

Preserve:

### CONTINUE WATCHING

- Dynamic movie and series entries.
- Existing one-, two- and three-card arrangements.
- Titles.
- Playback progress.
- Remaining time.
- Card focus behavior.

### RECENTLY WATCHED TV

- Existing channel tiles.
- Channel logos.
- History integration.
- Remote navigation.

Do not alter playback data or history.

The previously requested top-aligned Continue Watching artwork correction is a separate approved requirement and must not be reversed by this branding change.

---

## 12. ANDROID TV PERFORMANCE

The helmet is a static UI asset.

Implementation must be lightweight.

Requirements:

- No continuous animation.
- No repeated bitmap decoding.
- No expensive blur.
- No runtime image generation.
- No unnecessary GPU layers.
- No memory leaks.
- No focusable logo unless there is an explicitly approved action.

The helmet is decorative branding, not a navigation button.

It should not receive D-pad focus.

---

## 13. ACCESSIBILITY AND DISPLAY SCALING

Verify the result on:

- 1920 × 1080 layouts.
- 3840 × 2160 layouts.
- Different Android TV display densities.

The icon must:

- Remain within the screen safe area.
- Maintain its proportions.
- Avoid clipping.
- Avoid overlapping SEARCH.
- Remain subtly visible against the actual background.

Do not increase brightness excessively just to make it more prominent.

---

## 14. IMPLEMENTATION CHECKLIST

### Old branding removal

- [ ] MEDIA CENTER text removed from `Home2.kt`.
- [ ] Decorative underline removed.
- [ ] No obsolete branding rendering remains.
- [ ] No replacement text introduced.

### New helmet

- [ ] Official attached PNG used.
- [ ] Transparency preserved.
- [ ] Correct proportions.
- [ ] Small, discreet size.
- [ ] Engraved graphite effect.
- [ ] No bright metallic shine.
- [ ] No background container.
- [ ] No decorative underline.
- [ ] No text.

### Layout preservation

- [ ] Navigation order unchanged.
- [ ] SEARCH position unchanged.
- [ ] Existing spacing unchanged.
- [ ] Focus behavior unchanged.
- [ ] HOME content cards unchanged.
- [ ] Status header unchanged.

### Global consistency

- [ ] Official Pure Graphite background unchanged.
- [ ] No old branding remains.
- [ ] No new visual theme introduced.
- [ ] No functionality regressions.

---

## 15. IMPLEMENTATION REPORT

After implementation, provide:

1. The exact source files modified.
2. The Android drawable resource name used.
3. The final rendered icon dimensions.
4. The opacity and tint treatment applied.
5. Confirmation that MEDIA CENTER and its underline were removed from the source code.
6. Confirmation that the navigation layout was preserved.
7. Tests performed.
8. Any remaining issues requiring verification on the physical TV.

Do not claim physical-device verification unless actually performed.

---

# FINAL NON-NEGOTIABLE RULES

**REMOVE MEDIA CENTER COMPLETELY.**

**REMOVE ITS UNDERLINE.**

**USE THE ATTACHED OFFICIAL SPARTAN HELMET PNG.**

**ONLY THE HELMET — NO TEXT.**

**SMALL, TRANSPARENT, MONOCHROME AND SUBTLY ENGRAVED.**

**NO BRIGHT METALLIC LOGO.**

**NO NEW BACKGROUND.**

**NO CHANGES TO NAVIGATION OR HOME FUNCTIONALITY.**

**THIS INSTRUCTION OVERRIDES ALL PREVIOUS BRANDING RULES.**

The final result should be a minimal Spartan helmet engraving that blends naturally into the Pure Graphite surface, establishing a consistent and understated PRETORIAN identity.