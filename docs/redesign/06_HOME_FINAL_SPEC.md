# PRETORIAN — HOME SCREEN
## FINAL MASTER DESIGN, UX & FUNCTIONAL SPECIFICATION
### Instructions for Claude — Android TV Media Center

**Project:** PRETORIAN  
**Module:** HOME SCREEN  
**Status:** FINAL APPROVED DESIGN — Documentation and preparation  
**Target:** Android TV / Google TV  
**Reference resolution:** 1920 × 1080, 16:9  
**Priority:** Pixel-accurate visual reproduction, consistent navigation and reliable dynamic content

---

# 0. IMPORTANT — READ FIRST

This document defines the final approved PRETORIAN HOME screen.

Two reference images are attached:

**REFERENCE A — APPROVED HOME SCREEN**

This image defines the final layout, navigation, typography, alignment, card geometry, status bar, channel tiles and visual hierarchy.

**REFERENCE B — CLEAN PRETORIAN ROOM BACKGROUND**

This image contains the approved dark concrete environment, atmospheric lighting, stone floor and Spartan helmet without any UI elements.

Use Reference B as the actual background asset.

Use Reference A to reconstruct the UI over that background.

Do not redraw or approximate the background programmatically if the approved image asset is available.

Do not introduce a different visual theme.

Do not change the layout without explicit approval.

The HOME screen must belong to the same design system as the approved MOVIES and SERIES interfaces.

**Do not begin implementing this specification until explicitly authorized.**

---

# 1. CORE DESIGN PHILOSOPHY

PRETORIAN follows these principles:

1. ONE VISUAL DESIGN SYSTEM.
2. ONE MENU — ONE LEVEL.
3. COMPLETE REPLACEMENT OF PREVIOUS NAVIGATION.
4. STRICT TWO-COLUMN HOME LAYOUT.
5. REMOTE-FIRST INTERACTION.
6. DYNAMIC CONTENT WITHOUT EMPTY PLACEHOLDERS.
7. PRECISE ALIGNMENT AND ZERO CROPPING.
8. NO VIDEO AUTOPLAY ON FOCUS.

HOME is the primary navigation hub.

It must feel like a finished premium television media center rather than a conventional Android application.

The UI must be cinematic, minimalist, readable and predictable.

---

# 2. APPROVED BACKGROUND — PRETORIAN ROOM

Use the attached clean background image as the canonical HOME background.

It contains:

- Dark architectural concrete walls.
- Matte charcoal and graphite surfaces.
- Subtle concrete texture.
- Dark stone floor.
- Restrained floor reflections.
- Soft overhead volumetric light beams.
- A large but visually integrated Spartan helmet on the right.
- A stone pedestal beneath the helmet.
- Deep cinematic shadows.

The image must fill the entire television screen.

Use center-crop or equivalent scaling only when necessary to accommodate a different aspect ratio.

Do not stretch or distort the background.

Do not introduce a second helmet graphic on top of the existing helmet.

Do not add another background illustration.

Avoid excessive global dimming.

Maintain enough contrast for text and cards.

## Color language

Primary colors:

- Charcoal.
- Graphite.
- Dark concrete gray.
- Muted silver.
- Soft white.

No neon.

No purple highlights.

No bright blue focus outlines.

No oversized black panels.

The background remains static while HOME navigation and content update.

---

# 3. STRICT TWO-COLUMN HOME LAYOUT

The screen is divided into two non-overlapping regions.

## LEFT COLUMN — NAVIGATION ONLY

Contains:

- PRETORIAN wordmark.
- Navigation icons.
- Navigation labels.
- Active/focused navigation indicator.

Nothing else.

## RIGHT COLUMN — MAIN CONTENT ONLY

Contains:

- Top status bar.
- Main movie/series content section.
- TV-channel section.

No card, channel tile, artwork or text may extend beneath the left navigation.

This is a hard layout constraint.

## Responsive geometry

Use the 1920×1080 reference image as the visual baseline.

Scale and adapt the layout to supported television resolutions while preserving:

- Column separation.
- Safe margins.
- Card proportions.
- Text readability.
- Alignment relationships.
- Full visibility of content.

Do not rely on arbitrary fixed pixel offsets that break at different TV resolutions.

Use shared layout constraints and measured container dimensions.

---

# 4. PRETORIAN WORDMARK

Position the PRETORIAN wordmark at the top of the left navigation column.

Use the exact visual treatment shown in Reference A:

- Uppercase lettering.
- Elegant thin typography.
- Generous letter spacing.
- Muted-white color.
- Subtle horizontal light accent underneath.

The wordmark must be centered within the navigation column.

Do not substitute a large Spartan logo for the wordmark.

The helmet already exists in the background.

The wordmark and top status bar must share the same visual horizontal alignment.

---

# 5. LEFT NAVIGATION — EXACT ORDER

The navigation order is:

**SEARCH**

**HOME**

**TV**

**MOVIES**

**SERIES**

**SPORT**

**SETTINGS**

Use these labels in the approved English reference layout, with localization handled through the application's existing language system.

Each entry has a minimalist monochrome icon.

Use the same icon family, stroke weight and visual density across all entries.

## Default state

HOME is the selected destination when the application opens on HOME.

Use the approved focus appearance:

- Transparent smoky-glass rectangle.
- Thin silver-white border.
- Restrained white highlight.
- Small vertical white indicator near the right inner edge.
- White icon and label.

The focus must not look neon or heavily illuminated.

## Inactive items

Inactive icons and labels use muted gray-white tones.

Maintain good readability.

## Empty space below navigation

**The entire area beneath SETTINGS must remain empty.**

Never display:

- Channel tiles.
- Media cards.
- Logos.
- Metadata.
- Background UI panels.

Only the approved background image should be visible there.

---

# 6. EXACT ALIGNMENT CONTRACT

This is one of the most important requirements.

Do not approximate these alignments.

Use shared constraints.

## Horizontal alignment

The PRETORIAN wordmark and status bar occupy the same top horizontal band.

The SEARCH navigation row aligns with the main content heading.

The top edge of the focused HOME navigation capsule aligns with the top edge of the main media-card container.

## Vertical boundaries

The upper media section and lower channel section share:

**THE SAME LEFT X-COORDINATE.**

**THE SAME RIGHT X-COORDINATE.**

The right edge of the notification bell aligns with the right boundary of both content sections.

The final channel tile must end at the same right boundary as the media cards.

## Safe margins

Maintain appropriate margins at:

- Top.
- Bottom.
- Left.
- Right.

No clipping.

No partially visible card.

No partially visible channel.

No content extending outside the television viewport.

## Dynamic alignment

The alignment rules must remain valid when:

- One media card is shown.
- Two media cards are shown.
- Three media cards are shown.
- Media content is unavailable.
- Channel count changes.
- Text is localized.
- Screen resolution changes.

---

# 7. TOP STATUS BAR

The top status bar occupies the upper-right portion of HOME.

Display, where available:

- Weather icon.
- Current temperature.
- Weather condition.
- Current local time.
- Playlist connection/status information.
- Playlist expiration date when provided.
- Notification bell.

Example:

14°C Partly Cloudy • 11:57 AM • Playlist: Active (Exp. March 2027)

The example is illustrative only.

Never hardcode these values.

## Weather

Use the weather provider approved for PRETORIAN, with Open-Meteo as the proposed source.

The weather location comes from SETTINGS.

If weather information is unavailable, hide or gracefully simplify the weather portion.

Do not invent weather conditions.

## Time

Use the actual device-local time.

Respect the application's selected time format.

## Playlist status

Display the actual playlist state.

Examples:

Active.

Expired.

Disconnected.

Loading.

Show expiration only when the provider supplies reliable information.

Do not invent expiration dates.

## Notification bell

Position the bell at the far right.

Use the same muted-white line-icon style.

A small notification indicator may appear when unread notifications exist.

Do not display a fabricated unread notification.

The bell must remain within the screen's right safe margin.

---

# 8. HOME CONTENT — TWO INDEPENDENT DYNAMIC SECTIONS

HOME contains two primary content sections.

**UPPER SECTION: MOVIES AND SERIES**

**LOWER SECTION: LIVE TV CHANNELS**

Each section maintains its own history and fallback logic.

Do not treat movie playback history and live-TV viewing history as the same data source.

Both sections may independently be in first-installation mode or personalized mode.

For example:

The upper section may show RECENTLY ADDED MOVIES while the lower section already shows RECENTLY WATCHED TV.

This is intentional.

---

# 9. FIRST INSTALLATION — UPPER MEDIA SECTION

After the user imports a valid movie catalog, but before watching any movie or series, display:

**RECENTLY ADDED MOVIES**

Show the three most recently added movies from the imported catalog.

Use actual movie artwork and metadata.

Do not generate fictional movies in production.

Do not display fake progress bars.

Do not display fabricated percentages.

Do not display fake remaining playback time.

## Ordering

Use the provider's reliable added timestamp when available.

If timestamps are missing, use a deterministic documented fallback based on the imported catalog's ordering.

Do not misrepresent an unknown addition date as a verified timestamp.

## Item count

If three movies exist, show three.

If two exist, show two.

If one exists, show one.

If none exist, hide the section.

Do not create placeholder movies.

---

# 10. CONTINUE WATCHING — AUTOMATIC PERSONALIZATION

Once the user starts watching movies or series, the upper section changes to:

**CONTINUE WATCHING**

The content is populated from actual playback history.

Movies and series share this section.

## Eligible items

Include:

- Partially watched movies.
- Partially watched series episodes.
- Series with a meaningful next available episode, according to the shared series-continuation rules.

Do not include arbitrary unplayed titles.

## Sorting

Show the most recently relevant items first.

Use actual playback activity.

The most recent item occupies the primary large card.

## Maximum number

HOME displays a maximum of three media items.

If more than three are eligible, show only the top three.

Do not show a partially cropped fourth item.

The full Continue Watching collection remains accessible in the appropriate application section.

## Completion behavior

When a movie is fully watched, remove it from the unfinished Continue Watching list.

When a series episode finishes, advance the series continuation target to the next available episode.

Do not duplicate the same series across multiple HOME cards merely because multiple episodes have playback history.

When the final episode of a series is completed and no continuation remains, remove the series from Continue Watching unless the user has another unfinished episode.

## Fallback behavior

If no eligible Continue Watching items remain, return to RECENTLY ADDED MOVIES when catalog content exists.

Do not display an empty Continue Watching heading.

---

# 11. UPPER CONTENT — FIXED RESERVED CONTAINER

The upper media section has a consistent reserved rectangular area.

Its outer boundaries must remain stable across the one-, two- and three-item layouts.

The top, bottom, left and right edges must not shift depending on the number of items.

The section must use the full available reserved area.

No unused blank card slots.

No fake placeholder content.

---

# 12. DYNAMIC LAYOUT — ZERO ITEMS

If no Continue Watching items and no recently added movies exist:

- Hide the upper media section.
- Hide its heading.
- Do not show empty frames.
- Do not show fake artwork.
- Do not show loading placeholders indefinitely.

The background remains visible.

The lower TV section may remain independently visible.

---

# 13. DYNAMIC LAYOUT — ONE ITEM

One card occupies:

**100% OF THE RESERVED WIDTH.**

**100% OF THE RESERVED HEIGHT.**

The card must fill the entire media-content container.

Use the actual movie or series backdrop.

Display relevant metadata and progress if the item has been watched.

Do not leave empty card slots beside it.

---

# 14. DYNAMIC LAYOUT — TWO ITEMS

Display two cards side by side.

Approximate proportions:

**LEFT CARD: 60% WIDTH.**

**RIGHT CARD: 40% WIDTH.**

Both cards occupy the full reserved container height.

The spacing between them must be accounted for inside the container width.

No overflow.

No half-height second card.

The most recently relevant item is displayed on the left.

---

# 15. DYNAMIC LAYOUT — THREE ITEMS

This is the approved layout shown in Reference A.

Use:

**ONE LARGE LEFT CARD.**

**TWO SMALLER RIGHT CARDS STACKED VERTICALLY.**

The large left card occupies approximately 60% of the content width and 100% of the reserved height.

The right column occupies approximately 40% of the width.

The right column contains:

- Second item at the top.
- Third item at the bottom.

Both right-side cards have equal heights.

The sum of their heights plus the vertical gap must equal the height of the large left card.

All three cards share the exact same outer bounding rectangle.

## Hard requirements

No overlapping cards.

No cropping.

No missing lower-right card.

No oversized gaps.

No fourth card peeking from the edge.

No shifting of the entire media section when the number of cards changes.

---

# 16. MEDIA CARD DESIGN

All HOME media cards use the approved cinematic PRETORIAN styling.

## Appearance

- Actual cinematic backdrop.
- Subtle dark gradient behind text.
- Thin restrained border.
- Soft shadow.
- Consistent corner radius.
- Minimal typography.
- No heavy opaque text box.

## Metadata

For movies, show available information such as:

- Title.
- Release year.
- Runtime.
- Genre.
- Rating.

For series, show:

- Series title.
- Season and episode.
- Episode title when space permits.
- Relevant playback information.

Keep metadata concise.

Do not overcrowd the smaller cards.

## Progress

For Continue Watching items, display:

- Thin 3–4px silver-white progress bar.
- Actual playback progress.
- Remaining time or percentage when known.

Do not use bright blue progress bars.

Do not display fabricated progress.

For Recently Added Movies, do not show progress bars.

## Image scaling

Use appropriate cover scaling to fill each card.

Preserve aspect ratio.

Cropping within the image itself is allowed to fit the card.

Cropping the card outside the reserved UI boundaries is not allowed.

Keep important text readable.

---

# 17. LOWER SECTION — FIRST INSTALLATION

Before the user watches any live-TV channel, display:

**RECENTLY ADDED TV CHANNELS**

Populate the row from the imported playlist.

Use the most recently added channels according to reliable provider metadata or a documented import-order fallback.

Do not invent channels.

Do not show a fake viewing history.

---

# 18. LOWER SECTION — RECENTLY WATCHED TV

Once the user starts watching live TV, change the heading to:

**RECENTLY WATCHED TV**

Display actual recently watched channels.

Order by most recent viewing activity.

Do not show the same channel multiple times.

If a channel is watched again, move it to the beginning of the recent list.

The row must update when the user returns to HOME.

If there is no valid watch history, use the recently added channel fallback.

---

# 19. TV CHANNEL TILE GEOMETRY

Display a single horizontal row of complete channel tiles.

Use the same left and right boundaries as the upper media section.

The number of visible tiles is determined by the available width.

At the reference resolution, approximately nine tiles may fit.

## Hard requirements

Every visible tile must be complete.

Never show half of the next channel.

Never allow the last tile to extend beyond the right safe margin.

Never allow channel tiles to overlap the navigation column.

Never crop tiles at the bottom.

Keep equal gaps between tiles.

Distribute tile widths consistently.

Use a deterministic layout calculation based on container width, minimum tile width and gap size.

When more channels exist than fit, use remote-controlled horizontal browsing or an appropriate full channel-list destination.

The default visible row must still show only complete tiles.

---

# 20. TV CHANNEL TILE VISUAL DESIGN

Each channel tile uses:

- Dark translucent background.
- Very subtle border.
- Consistent corner radius.
- Centered channel logo.
- Original logo aspect ratio.
- Minimal text where appropriate.

Do not distort logos.

Do not enlarge small logos until they become blurry.

Do not add bright colored tile backgrounds.

Preserve original logo colors when available.

## Missing-logo fallback

If the provider does not supply a channel logo, or the image fails to load:

Display the channel name centered in clean white text.

Do not show:

- Broken-image icons.
- Generic TV icons.
- Empty transparent placeholders.

---

# 21. TV CHANNEL INTERACTION

The channel tiles are navigable with the remote.

LEFT/RIGHT moves between channel tiles.

Focus uses the same restrained silver-white treatment as the rest of PRETORIAN.

Do not automatically start a channel when focus moves onto it.

Pressing OK on a channel opens the existing TV playback flow.

Use the shared PRETORIAN TV player and navigation architecture.

Do not create a separate HOME-only TV player.

BACK returns to HOME and restores the previously focused channel tile.

---

# 22. MEDIA CARD INTERACTION

HOME media cards are interactive.

LEFT/RIGHT/UP/DOWN move focus according to the visible card geometry.

Do not start playback merely because a card receives focus.

## Recently Added Movies

Pressing OK opens the selected movie's Single Movie View.

It must use the approved MOVIES design.

Do not automatically start the movie.

## Continue Watching

Pressing OK on an unfinished movie or series opens the appropriate title view with the relevant Resume action.

Use the existing MOVIES/SERIES navigation and playback logic.

Do not create a separate HOME-only movie or series details screen.

## BACK

BACK returns one logical level and restores the HOME focus.

The selected card and section position must be preserved.

---

# 23. D-PAD FOCUS SYSTEM

HOME must be fully navigable without a mouse.

## Left navigation

UP/DOWN moves between:

SEARCH → HOME → TV → MOVIES → SERIES → SPORT → SETTINGS.

## Moving to content

RIGHT from the navigation moves into the corresponding available content area using a predictable focus target.

LEFT from the leftmost content item returns to navigation where appropriate.

## Content navigation

Use spatially predictable directional navigation.

For the three-item layout:

- Large left card.
- Upper-right card.
- Lower-right card.

RIGHT from the large card should enter the right-side column.

UP/DOWN navigates between the two right-side cards.

LEFT returns to the large card.

DOWN from the upper media section can enter the TV-channel row.

UP from the TV-channel row returns to the upper media section when present.

## Focus restoration

When returning from MOVIES, SERIES or TV, restore the last focused HOME element.

Do not reset focus to HOME navigation unnecessarily.

## No autoplay on focus

Moving focus must never automatically start:

- A movie.
- An episode.
- A live-TV channel.
- A trailer.

Playback requires an explicit user action.

---

# 24. ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT

This navigation rule applies throughout PRETORIAN.

When the user selects MOVIES:

The HOME navigation and HOME content are replaced by the MOVIES interface.

When the user selects SERIES:

The HOME navigation and HOME content are replaced by the SERIES interface.

When the user selects TV:

The HOME interface is replaced by the TV category/group navigation.

When the user selects SPORT:

The HOME interface is replaced by the SPORT interface.

When the user selects SEARCH:

The HOME interface is replaced by the SEARCH interface.

When the user selects SETTINGS:

The HOME interface is replaced by SETTINGS.

Do not leave the HOME navigation visible beside deeper category screens unless an already approved screen explicitly requires that layout.

## BACK

BACK reverses one navigation step.

Example:

HOME → SERIES → RECENTLY ADDED → SERIES GALLERY → SINGLE SERIES VIEW → FULLSCREEN PLAYER.

BACK reverses that exact path.

Do not skip levels.

Restore focus and scroll positions at every level.

---

# 25. NOTIFICATION BELL

The notification bell is a functional control, not decoration.

If notification functionality exists, integrate with it.

Display a small indicator only when unread notifications exist.

If notifications are unavailable, do not fabricate unread items.

The notification interface must use the same PRETORIAN visual language.

Avoid large intrusive popups.

BACK closes notifications and restores the previous HOME focus.

---

# 26. PLAYLIST STATUS

The HOME status bar should reflect the currently configured IPTV playlist.

Show:

- Actual connection status.
- Actual expiration when available.
- Relevant loading/error state.

If multiple playlists exist, use the application's existing active-playlist rules.

Do not invent a subscription expiration date.

If the provider does not expose an expiration date, omit it.

The status bar must not break the alignment when some fields are unavailable.

---

# 27. DYNAMIC DATA UPDATES

HOME must respond to changes in the media catalog and watch history.

Examples:

- New movies imported.
- Playlist refreshed.
- A movie started.
- A movie completed.
- A series episode started.
- A series episode completed.
- A TV channel watched.
- A TV channel removed.
- A media item deleted.
- Watch history reset.

Update the corresponding HOME section without unnecessarily resetting focus.

Avoid full-screen flickering or rebuilding the entire HOME layout for minor data changes.

Do not create duplicate media entries.

---

# 28. EMPTY AND PARTIAL DATA STATES

Handle:

- No playlist configured.
- Playlist configured but still loading.
- No movies available.
- No series available.
- No TV channels available.
- Only one movie.
- Only two movies.
- Only one watched channel.
- Missing artwork.
- Missing metadata.
- Missing channel logo.
- Missing playlist expiration.
- Weather service unavailable.
- Network temporarily unavailable.

Do not fabricate content.

Do not display broken images.

Do not leave permanent loading placeholders.

For an unconfigured playlist, use the application's existing setup flow rather than pretending content is available.

---

# 29. PERFORMANCE REQUIREMENTS

HOME should load quickly on Android TV hardware.

Use:

- Cached background assets.
- Efficient image loading.
- Appropriate thumbnail resolution.
- Asynchronous metadata loading.
- Stable focus management.
- Minimal unnecessary recomposition.
- Shared design tokens.
- Shared navigation state.

Avoid expensive real-time background rendering.

The approved PRETORIAN ROOM background should be a static optimized image asset.

Avoid creating a complex 3D scene when the approved background image already provides the intended appearance.

---

# 30. RESPONSIVE TV LAYOUT

The reference image is 1920×1080.

Support other television resolutions while maintaining the same overall composition.

Preserve:

- Two-column separation.
- Navigation readability.
- Status-bar alignment.
- Media-card aspect behavior.
- Complete channel tiles.
- Safe margins.
- Focus visibility.

Do not simply scale all elements blindly.

Use appropriate TV density and layout constraints.

At smaller resolutions, reduce the number of visible channel tiles rather than cropping the final tile.

Never sacrifice the strict two-column layout.

---

# 31. ACCEPTANCE TESTS

The HOME implementation is accepted only when:

1. The clean approved PRETORIAN ROOM image is used as the background.
2. The UI visually matches the approved HOME reference.
3. The left column contains navigation only.
4. The right column contains content only.
5. The navigation order is exact.
6. HOME uses the approved silver-white focus capsule.
7. SEARCH aligns with the upper content heading.
8. HOME aligns with the top edge of the media cards.
9. The status bar aligns with the PRETORIAN wordmark.
10. The notification bell and content right edges align.
11. Both content sections share identical horizontal boundaries.
12. No content appears beneath SETTINGS.
13. No card or channel tile is cropped.
14. First installation shows RECENTLY ADDED MOVIES.
15. First installation shows RECENTLY ADDED TV CHANNELS.
16. No fake watch progress is displayed.
17. One media item fills the entire reserved area.
18. Two items use the 60/40 full-height layout.
19. Three items use the large-left/two-stacked-right layout.
20. More than three items never produce a cropped fourth card.
21. Continue Watching updates from real playback history.
22. Completed movies leave unfinished Continue Watching.
23. Series continuation advances appropriately.
24. Recently Watched TV updates from real viewing activity.
25. Channel logos preserve their original aspect ratio.
26. Missing logos fall back to channel names.
27. No autoplay occurs on focus.
28. D-Pad navigation is predictable.
29. BACK restores the previous focus and navigation level.
30. Weather and playlist status use actual available data.
31. The screen remains readable and correctly aligned at supported TV resolutions.

---

# 32. FINAL NON-NEGOTIABLE RULES

**USE THE APPROVED PRETORIAN ROOM BACKGROUND.**

**MATCH THE APPROVED HOME SCREEN REFERENCE.**

**STRICT TWO-COLUMN LAYOUT.**

**NOTHING BELOW THE LEFT NAVIGATION.**

**PRECISE SHARED CONTENT BOUNDARIES.**

**ZERO CROPPING.**

**SEARCH ALIGNS WITH THE MAIN SECTION HEADING.**

**HOME ALIGNS WITH THE TOP OF THE MEDIA CARDS.**

**FIRST INSTALLATION = RECENTLY ADDED MOVIES + RECENTLY ADDED TV CHANNELS.**

**ACTIVE USAGE = CONTINUE WATCHING + RECENTLY WATCHED TV.**

**UPPER CONTENT = MAXIMUM THREE ITEMS.**

**ONE ITEM = FULL WIDTH.**

**TWO ITEMS = 60/40 FULL HEIGHT.**

**THREE ITEMS = ONE LARGE LEFT + TWO STACKED RIGHT.**

**ALL TV CHANNEL TILES MUST BE FULLY VISIBLE.**

**NO FAKE DATA OR PLAYBACK HISTORY.**

**NO AUTOPLAY ON FOCUS.**

**ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT.**

**BACK RETURNS EXACTLY ONE STEP.**

**SHARED DESIGN SYSTEM WITH MOVIES AND SERIES.**

Do not redesign approved elements.

Do not add extra navigation layers.

Do not replace the approved background with an approximation.

Do not begin implementation until explicitly authorized.

---

# 33. EXPECTED RESPONSE FROM CLAUDE

After receiving this specification and the two reference images:

1. Confirm that both images were received.
2. Confirm that the complete specification was received without truncation.
3. Save the HOME specification alongside the MOVIES and SERIES redesign documentation.
4. Identify the correct project asset location for the clean background image.
5. Identify existing navigation and HOME components that can be reused.
6. Identify any technical conflicts.
7. Present a concise HOME implementation plan.
8. Wait until the current implementation phase is completed and HOME development is explicitly authorized.

**Do not start HOME implementation automatically.**