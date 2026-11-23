# PRETORIAN — HOME SCREEN MASTER DESIGN & IMPLEMENTATION SPECIFICATION (v2.0)

**Target Implementation Model:** Claude Opus 5.5

**Platform:** Android TV (16:9 Landscape, 10-foot UI, D-Pad Directional Remote)

**Section:** Home Screen (`Home`)

---

## 1. DESIGN PHILOSOPHY & VISUAL IDENTITY

PRETORIAN is a modern, cinematic Android TV IPTV and media center built for effortless living-room navigation. The interface must remain open, spacious, and strictly aligned across all TV screen resolutions without relying on heavy opaque boxes or outdated 2000s IPTV grids.

* **Approved Background Theme:** Minimalist dark-charcoal architectural concrete gallery interior featuring smooth matte poured-concrete walls, a seamless dark-gray concrete floor at the bottom, and soft diffused warm-silver overhead light shafts with subtle shadows. *(Optional alternate theme: atmospheric misty mountain valley in desaturated dark slate/graphite).*
* **Watermark:** A barely visible Spartan helmet silhouette watermark positioned in the upper-right area behind the content grid, subtle enough never to compete with artwork or text.
* **Typography & Palette:** Clean sans-serif typography in crisp white (`#FFFFFF`) and muted silver (`#B0B8C4`), paired with subtle translucent dark-glass containers (`rgba(20, 24, 28, 0.55)`) and hairline borders (`1px solid rgba(255, 255, 255, 0.12)`).
* **Focus Indicator:** Restrained white/silver translucent pill highlight (`rgba(255, 255, 255, 0.12)`) with a subtle `1px` bright white/silver border and soft outer light diffusion—never neon blue, purple, or oversized solid white blocks.
* **Forbidden Elements:** No neon glows, no heavy black dashboard boxes, no thick borders, no extraneous brand logos (never insert unrequested logos like Cinemax), and no overlapping pop-up sidebars.

---

## 2. STRICT GLOBAL LAYOUT & ALIGNMENT RULES

### Rule 1 — Safe Margins & Zero Cropping (Mandatory)

* Maintain consistent outer safe margins around the entire 16:9 viewport (`~48dp` / `4.5%` horizontal and `~40dp` / `3.7%` vertical safe padding).
* **Zero Bottom/Edge Cropping:** Every element—especially the bottom **Recently Watched TV** channel row—must sit cleanly above the bottom safe margin with visible breathing room between the bottom of the tiles and the screen edge. No tile, logo, or text may ever be cut off at the bottom or right edge.

### Rule 2 — Two-Zone Column Separation (Nothing Under Navigation)

* The screen is divided vertically into two strict non-overlapping columns:
1. **Left Navigation Column** (occupies ~16%–18% width on the left).
2. **Right Main Content Column** (occupies the remaining ~78%–80% width on the right).


* **Strict Negative Space Rule:** Nothing is allowed beneath the left navigation menu. The **Continue Watching** section and the **Recently Watched TV** section must share the exact same left vertical boundary (`X_start`) and the exact same right vertical boundary (`X_end`). Channel tiles must never extend leftward underneath the navigation column.

### Rule 3 — Horizontal Baseline & Top-Edge Alignment

* **Top Header Row:** The `PRETORIAN` wordmark (top-left, centered horizontally over the left navigation column) and the **Top Status Bar** (top-right) sit on the exact same horizontal center line.
* **Navigation-to-Content Vertical Alignment:**
* The top item of the navigation list (`Search` or `Home`, per chosen ordering—see Section 3) must align cleanly with the right-hand content zone: specifically, the `Search` baseline aligns horizontally with the `Continue Watching` section heading, and `Home` (the active focused state) aligns its top edge flush with the top border of the `Continue Watching` media cards.
* Every navigation entry uses consistent left-aligned monochrome icons (`24dp`) and full text labels (`Home`, `TV`, `Movies`, `Series`, `Sports`, `Search`, `Settings`) spaced evenly down the column.



### Rule 4 — Right-Edge & Bottom-Edge Locking

* The right edge of the notification bell in the Top Status Bar aligns vertically with the right edge of the `Continue Watching` container and the right edge of the final visible tile in `Recently Watched TV`.
* All cards inside `Continue Watching` are locked to the exact same top Y-coordinate and bottom Y-coordinate so the entire right-hand media block forms one unified, flush rectangle.

---

## 3. NAVIGATION ARCHITECTURE (`ONE MENU — ONE LEVEL`)

* **Menu Entries (Icon + Full Label):**
1. `Home`
2. `TV`
3. `Movies`
4. `Series`
5. `Sports`
6. `Search`
7. `Settings`
*(Note: Keep `Search` aligned with the `Continue Watching` header and `Home` flush with the top card border if placing `Search` above `Home`, or keep `Home` first and align its top edge flush with the top border of the Continue Watching cards while maintaining clean vertical rhythm).*


* **Navigation Behavior:**
* **One Menu — One Level:** Selecting `TV`, `Movies`, `Series`, `Sports`, `Search`, or `Settings` replaces the current navigation level cleanly; never stack or overlap multiple sidebars.
* **D-Pad Controls:** `UP` / `DOWN` moves vertically within lists; `LEFT` / `RIGHT` moves between the navigation column and content rows or across horizontal items; `OK` selects; `BACK` always returns exactly one level.
* **No Autoplay on Focus:** Focusing an item highlights it without triggering video playback.



---

## 4. TOP STATUS BAR

Positioned at the top right, aligned horizontally with the `PRETORIAN` wordmark, displaying from left to right:

1. Weather icon + temperature + condition (e.g., `14°C Partly Cloudy`)
2. Bullet separator (`•`)
3. Current time (`11:57 AM`)
4. Bullet separator (`•`)
5. Playlist status & expiration (`Playlist: Active (Exp. March 2027)`)
6. Subtle vertical divider (`1px solid rgba(255,255,255,0.2)`)
7. Notification bell icon (rightmost element, aligned with the right edge of the content grid below; displays a small unread dot only when unread notifications exist).

---

## 5. CONTINUE WATCHING — DYNAMIC RESPONSIVE CONTAINER (MAX 3 ITEMS)

The **Continue Watching** section reserves one fixed, responsive rectangular area (`100%` width of the right content column, fixed height `H`) that adapts dynamically without leaving empty slots or overflowing borders:

* **0 Items (Fresh Install):** Hide the entire `Continue Watching` section cleanly so the architectural concrete / mountain background remains open. Never render fake placeholder posters.
* **1 Item:** Render **one large cinematic card** occupying `100%` of the reserved width and `100%` of the reserved height `H`.
* **2 Items:** Render **two cards side-by-side** separated by a consistent gap (`16dp`–`20dp`):
* **Left Card (Most Recent):** `60%` width, `100%` height `H`.
* **Right Card (2nd Most Recent):** `40%` width, `100%` height `H` (must extend completely from the exact top border to the exact bottom border of the left card—never half-height).


* **3 Items (Maximum on Home):** Render **three cards** filling `100%` of the reserved rectangle:
* **Left Card (Most Recent):** `60%` width, `100%` height `H`.
* **Right Column (2nd & 3rd Most Recent):** `40%` width, split into two vertically stacked cards (`Card 2` on top, `Card 3` on bottom) separated by a `16dp`–`20dp` gap. The top edge of `Card 2` aligns flush with the top edge of the Left Card, and the bottom edge of `Card 3` aligns flush with the bottom edge of the Left Card (`Height(Card 2) + Gap + Height(Card 3) == H`).


* **> 3 Items:** Show only the 3 most recently watched items on Home; never show a partially visible 4th card.
* **Card Content Overlay:** Full-bleed high-resolution 16:9 artwork, subtle bottom dark scrim gradient for legibility, clean title, episode/season (`S4 • E3`) on the bottom-left, remaining time or percentage (`24 minutes remaining` / `72% watched`) on the bottom-right, and a thin (`3dp`–`4dp`) rounded progress bar spanning the bottom edge inside the card padding.

---

## 6. RECENTLY WATCHED TV — CHANNEL ROW & MISSING LOGO FALLBACK

Positioned below `Continue Watching` within the exact same left and right column boundaries:

* **Tile Sizing & Pagination:** Calculate tile width dynamically so an exact integer number of complete channel tiles (e.g., 5 to 9 tiles depending on aspect/width) fits cleanly between `X_start` and `X_end` with consistent spacing (`14dp`–`16dp`) and generous bottom screen clearance.
* **No Partial Tiles:** Never render a cropped or half-visible tile at the right edge. Horizontal D-Pad scrolling shifts smoothly to reveal complete tiles.
* **Subtle Translucent Presentation:** Each channel sits in a low-opacity translucent container (`rgba(255, 255, 255, 0.06)` with subtle soft shadow and no heavy border) or directly over the floor gradient so the background remains visible, keeping the row visually secondary to `Continue Watching`.
* **Logo Scaling & Missing Logo Fallback Rule:**
* Preserve original channel logo aspect ratios, centered inside each tile with generous internal padding.
* **Fallback Rule:** If a channel in the user's M3U/Xtream playlist has no logo URL or fails to load, never display a broken-image icon or generic TV placeholder graphic. Automatically render the channel's short name in clean, centered, medium-weight uppercase white typography (`#FFFFFF`) inside the same subtle translucent tile so missing logos blend seamlessly with branded logos.


* **Fresh Install:** If no channels have been watched yet, hide the row cleanly instead of showing fake sample channels.

---

## 7. SUMMARY CHECKLIST FOR CLAUDE OPUS 5.5 IMPLEMENTATION

1. **Left column is pure navigation** (`PRETORIAN` wordmark centered at top + vertical icon-and-label list); **zero content is permitted below the navigation list**.
2. **`Continue Watching` and `Recently Watched TV` share identical `left` and `right` X-coordinates**, aligned with the notification bell on the far right.
3. **Top-edge / baseline alignment** is enforced between the navigation column and the right-hand content block (`Search` aligned with `Continue Watching` header; `Home` top aligned with `Continue Watching` card top border).
4. **Bottom safe margin is strictly enforced** so the `Recently Watched TV` row sits comfortably above the bottom edge of the 16:9 screen with zero clipping.
5. **Dynamic 1 / 2 / 3 card layouts** always fill 100% of the reserved `Continue Watching` bounding box with matching top and bottom edges.