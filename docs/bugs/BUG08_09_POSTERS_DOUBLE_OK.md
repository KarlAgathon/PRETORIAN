# PRETORIAN — BUG 08 & BUG 09
## MOVIES / SERIES POSTER ARTWORK AND DOUBLE-OK PLAYBACK

Claude, the latest physical TV screenshots show two additional UX issues affecting MOVIES and SERIES.

These are targeted corrections. Do not redesign the approved galleries or change the Pure Graphite design system.

### BUG 08 — POSTER IMAGES DO NOT FILL THEIR CARDS

Several posters appear extremely small and centered inside large dark cards. Other posters fill their cards correctly.

This inconsistent rendering looks unfinished and makes browsing difficult on a television.

**Required behavior:**

1. Movie and series poster artwork must fill the available poster card area.
2. Preserve the original image aspect ratio.
3. Use proportional crop-to-fill behavior, not fit-inside behavior that creates large empty margins.
4. Clip artwork to the existing rounded card shape.
5. Keep ratings, focus outlines and title labels in their existing positions.
6. Apply the same rendering policy to MOVIES and SERIES.
7. Do not stretch or distort images.

**Investigate the actual cause before changing the implementation.**

Check:

- Image loading transformations.
- `ContentScale.Fit` versus `ContentScale.Crop`.
- Image composable dimensions.
- Parent and child constraints.
- Placeholder-to-final-image transitions.
- Thumbnail versus full-resolution artwork.
- Different artwork paths used by MOVIES and SERIES.
- Whether the source bitmap itself contains large empty margins.

Do not assume the problem is caused by caching without evidence.

**Loading behavior:**

The gallery must remain responsive while artwork loads.

Use the existing caching and asynchronous image-loading infrastructure.

When an image becomes available, it should fill the card correctly without changing the card's dimensions or disrupting focus.

Do not display a permanently tiny image simply to make scrolling faster.

If the source is missing or unusable, use the existing appropriate fallback.

### BUG 09 — DOUBLE-OK QUICK PLAY

We want a faster way to start watching a selected movie or series.

Currently, the user must navigate to the Watch button.

Add the following behavior to poster gallery items:

**Single OK:**
Select the poster and preserve the existing title-information and action behavior.

**Double OK:**
Perform the same action as the existing Watch button for that title.

Requirements:

1. Detect two deliberate OK presses on the same focused poster within a short interval.
2. Use a reasonable double-press window, initially approximately 300–400 ms, subject to device testing.
3. Do not require the user to navigate to the Watch button.
4. Reuse the existing Watch action handler rather than creating a separate playback path.
5. Respect existing resume/playback progress behavior.
6. Do not trigger playback twice.
7. Do not interpret a held OK key as a double press.
8. Do not activate double-OK playback when focus is on Trailer, My List or another action.
9. Preserve BACK and all existing remote-control navigation.

**Important interaction detail:**

If the first OK already performs an action that changes focus or opens another screen, ensure double-OK recognition is compatible with that behavior. Avoid delaying normal single-OK interactions unnecessarily.

For series, double OK must follow the same destination and playback selection logic as the existing Watch button.

### PRESERVE EXISTING DESIGN

Do not modify:

- Gallery card dimensions.
- Poster spacing.
- Focus appearance.
- Ratings.
- Category organization.
- Pure Graphite background.
- Header.
- Navigation.
- Existing Watch, Trailer and My List actions.

### ACCEPTANCE TESTS

- [ ] Posters fill their cards consistently.
- [ ] No unnecessary dark margins inside valid poster cards.
- [ ] Artwork is not stretched.
- [ ] MOVIES and SERIES use consistent rendering.
- [ ] Artwork loads without disturbing scrolling or focus.
- [ ] Single OK retains its existing behavior.
- [ ] Double OK activates the same Watch action.
- [ ] Holding OK does not accidentally trigger playback.
- [ ] Double OK does not activate Trailer or My List.
- [ ] Playback progress and resume behavior remain correct.
- [ ] No duplicate playback launches.
- [ ] BACK remains predictable.

### IMPLEMENTATION STATUS

Add these as BUG 08 and BUG 09 to the approved correction list.

Do not begin implementation until explicitly authorized.

Report the identified causes and proposed changes before implementation if further clarification is required.

**FINAL RULE: FULL-BLEED POSTER ARTWORK + FAST, PREDICTABLE DOUBLE-OK PLAYBACK, WITHOUT REDESIGNING THE GALLERY.**