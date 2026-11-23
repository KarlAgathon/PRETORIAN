# PRETORIAN — UNIVERSAL SEARCH
## FINAL UI/UX & FUNCTIONAL IMPLEMENTATION SPECIFICATION

**Project:** PRETORIAN Android TV Media Center  
**Module:** Universal Search  
**Design system:** PRETORIAN Pure Graphite  
**Status:** Approved design specification  
**References:** Attached Pure Graphite background and approved SEARCH screenshot

---

## 1. OBJECTIVE

Claude, we are finalizing the PRETORIAN Universal Search module.

The existing search engine is already working very well. It is fast, responsive and produces results efficiently.

**Do not rewrite or replace the existing search engine unless a specific technical change is necessary to support the requirements below.**

Our goal is to improve the visual design, navigation, search history and result interactions while preserving existing search performance.

SEARCH must look and behave like a natural extension of HOME, TV, MOVIES and SERIES.

It must not feel like a separate application.

---

## 2. MANDATORY GLOBAL DESIGN SYSTEM

Use the existing approved PRETORIAN Pure Graphite design system.

The supplied `PRETORIAN_Pure_Graphite_4K.png` is the single official background for the entire PRETORIAN application.

SEARCH must use this exact background.

### Mandatory visual rules

- Use the same graphite background as HOME and TV.
- Use the same typography and monochrome icon system.
- Use the same restrained silver-white focus indicator.
- Use subtle charcoal translucent surfaces only where needed.
- Preserve consistent spacing, margins and header alignment.
- Maintain correct Android TV safe areas.
- Avoid unnecessary containers and decorative elements.

### Absolutely avoid

- Alternative SEARCH backgrounds.
- Metallic waves or curved decorative lines.
- Blue or purple neon effects.
- Glowing panels.
- Large opaque background cards.
- Different navigation styling.
- Oversized branding.
- Additional decorative textures.

The approved SEARCH screenshot is a reference for the layout and content organization.

**If the screenshot contains any visual decoration that conflicts with Pure Graphite, the established global design system takes priority.**

Do not reproduce the screenshot's decorative effects.

---

## 3. SCREEN STRUCTURE

The SEARCH screen contains five primary areas:

1. Header
2. Search input
3. Category filters
4. Recent searches and results
5. Remote-control hints

Keep the interface clean and easy to navigate with a TV remote.

### Header

Display the SEARCH title and a minimal search icon.

Preserve the application's existing global status header where applicable.

Do not introduce a large PRETORIAN wordmark or MEDIA CENTER heading.

Use the existing small, discreet engraved Spartan helmet branding if that branding is present in the shared application shell.

---

## 4. SEARCH INPUT

Position one prominent search input near the top of the screen.

The field must have:

- Search icon on the left.
- Search text or placeholder.
- Clear Search (X) action on the right.
- Clear focus indication.
- Appropriate keyboard activation.

Placeholder:

**Search TV, Movies, Series, Sport, Settings...**

### Fresh search on every entry

Every time the user enters SEARCH from the main navigation, the input must start empty.

This is mandatory.

Do not restore the previously typed search query when opening a new SEARCH session.

The user should never need to manually erase the previous query before starting another search.

However, when temporarily navigating from SEARCH to a result and returning with BACK, preserve the current SEARCH session, including the query, selected category, result position and focus where practical.

Distinguish between:

- **New SEARCH entry:** fresh, empty input.
- **Return from an opened result:** restore the existing search session.

---

## 5. CLEAR SEARCH

Provide a visible X action inside or immediately beside the search field.

Pressing it must:

1. Clear the current search query.
2. Clear active search results.
3. Return to the empty SEARCH state.
4. Show recent searches.
5. Preserve saved search history.
6. Keep the user inside SEARCH.

Do not open a confirmation dialog.

Do not confuse Clear Search with Clear History.

---

## 6. SEARCH CATEGORIES

SEARCH must support exactly these five top-level categories:

**TV | MOVIES | SERIES | SPORT | SETTINGS**

Use a compact horizontal category row below the search input.

Each category must have a clear, minimal focus and selected state.

### Category behavior

Selecting a category filters search results to that category.

Do not mix results from unrelated categories when a specific filter is selected.

The search query should remain unchanged when switching categories.

Switching categories must not create a new search-history entry by itself.

### Initial category

For consistency and simplicity, use TV as the default category on a fresh SEARCH entry.

Do not restore a previous category selection as a requirement.

Keep category selection independent from saved search history.

### TV

Search available IPTV channels by their names and supported searchable metadata.

### MOVIES

Search imported movies using the existing movie catalog and search logic.

### SERIES

Search imported series using the existing series catalog and search logic.

### SPORT

Search available SPORT module destinations and supported sports content once the SPORT module is implemented.

Do not invent unavailable sports events, results or external data.

### SETTINGS

Search actual application settings and navigable configuration destinations once their structure is finalized.

Do not display fictional settings.

If SPORT or SETTINGS search integration is not yet available, preserve their category UI and implement a clear, honest unavailable or empty state until the corresponding modules exist.

---

## 7. SEARCH PERFORMANCE

Preserve the existing fast search behavior.

Results should update as the user types.

Requirements:

- No mandatory Search button.
- No unnecessary loading screens.
- No visible lag caused by UI decoration.
- No duplicate search requests when unnecessary.
- No repeated full-catalog rebuilding on every keystroke if the existing architecture already avoids it.
- Proper cancellation or invalidation of outdated asynchronous results.
- Stable scrolling and focus.
- No unnecessary player or screen recreation.

Use the existing indexing, caching and search infrastructure wherever possible.

Do not degrade performance to implement the visual redesign.

---

## 8. RECENT SEARCHES — LAST 10

Add persistent search history.

The application must remember the last **10 distinct completed search queries**.

Display them under:

**RECENT SEARCHES**

Use a clean vertical list.

Each entry contains:

- Small history icon.
- Search query text.
- Small X action to remove that individual entry.

### History rules

1. Maximum 10 entries.
2. Newest entry first.
3. Oldest entry removed when an eleventh distinct query is saved.
4. No duplicate queries.
5. Repeating an existing query moves it to the top.
6. Match duplicates case-insensitively after trimming unnecessary whitespace.
7. Do not save empty queries.
8. Do not save each intermediate keystroke.
9. Persist history across app restarts.
10. Keep one shared history across all five categories.

For example, if the user searches:

- Sport Klub
- Breaking Bad
- HBO
- Formula 1
- Dark

all these queries belong to the same recent-search history.

### When to save a query

Save a query when the user meaningfully completes a search, such as opening a result or explicitly selecting a recent query.

Do not save partial typing sequences.

If the existing search flow has a clearly defined completed-search event, it may also be used.

---

## 9. REUSING RECENT SEARCHES

When SEARCH opens with an empty input, show the recent-search list.

Selecting a recent search using OK must:

1. Populate the search field with that query.
2. Trigger the existing search engine immediately.
3. Display matching results for the currently selected category.
4. Move that query to the top of the recent-search history.
5. Avoid creating duplicate entries.

No extra confirmation is required.

---

## 10. REMOVE INDIVIDUAL SEARCHES

Every history entry should have a small X action.

Selecting X removes only that entry.

Do not delete the current search input.

Do not delete unrelated history entries.

Do not display a confirmation dialog.

The list should update immediately.

---

## 11. CLEAR HISTORY

Provide a separate action:

**Clear History**

This must remove all saved recent-search entries.

It must not:

- Delete IPTV channels.
- Delete movie or series content.
- Delete Favorites.
- Delete Recently Watched.
- Delete playback history.
- Change playlists.
- Delete the currently typed query.

Clear History affects only search history.

Because the history contains a maximum of 10 lightweight entries, no confirmation dialog is necessary.

After clearing, display a minimal empty state:

**No recent searches**

---

## 12. RESULTS LAYOUT

Use the approved SEARCH screenshot as the structural reference.

For a widescreen television, organize the lower content area into two logical regions where appropriate:

**Left:** Recent Searches  
**Right:** Search Results

Avoid excessive separation panels.

A thin, subtle divider may be used if needed.

### Empty search state

When the query is empty:

- Display recent searches.
- Do not show stale results from a previous search.
- Keep the screen visually balanced.
- Do not invent placeholder search results.

### Active search state

When a query exists:

- Display matching results.
- Keep category filters visible.
- Maintain clear keyboard and D-pad navigation.
- Do not allow the on-screen keyboard to permanently obstruct results.

The recent-search list may remain visible alongside results if space allows, following the approved reference.

---

## 13. TV SEARCH RESULTS

TV results must be compact horizontal rows.

Each row should display:

- Channel logo, when available.
- Channel name.
- Quality badge, when available.
- Favorite status indicator.

Use real playlist metadata.

Do not invent logos, channels or quality information.

Avoid large posters or oversized cards for TV channels.

### Short OK on a TV result

Open the selected channel in the existing TV Channel Browser context.

Use the established preview and EPG interface.

Preserve the established playback behavior:

- First OK starts preview when appropriate.
- Second OK opens fullscreen.

Do not create a separate SEARCH-specific TV player.

### Long OK on a TV result

Open a small contextual menu with the appropriate action:

**Add to Favorites**

or

**Remove from Favorites**

The displayed action must reflect the channel's actual current Favorite state.

After selection:

- Apply the change immediately.
- Persist it automatically.
- Update the favorite indicator.
- Synchronize with TV Favorites.
- Do not ask for additional confirmation.

Long OK here is specific to a focused TV search-result row.

Do not alter the previously approved fullscreen player remote mappings.

---

## 14. MOVIES SEARCH RESULTS

Use the existing movie metadata and artwork.

Movie results should be visually consistent with the established MOVIES design.

Prefer correctly proportioned poster thumbnails and readable titles.

### Short OK

Open the existing Movie Detail screen.

Do not open an alternative detail layout.

Preserve:

- Movie artwork.
- Description.
- Metadata.
- Playback controls.
- Continue Watching behavior.
- Existing movie actions.

### BACK

Return to the same SEARCH query, category and result position.

---

## 15. SERIES SEARCH RESULTS

Use the existing series metadata and artwork.

Maintain consistency with the approved SERIES gallery.

### Short OK

Open the existing Series Detail screen.

Preserve:

- Series artwork.
- Description.
- Seasons.
- Episodes.
- Watched status.
- Continue Watching.
- Existing playback options.

Do not create a separate SEARCH-specific series screen.

### BACK

Restore the previous SEARCH context.

---

## 16. SPORT SEARCH RESULTS

The SPORT module will be finalized separately.

Prepare SEARCH integration without assuming the final SPORT architecture.

Once SPORT is implemented, search results should navigate to the corresponding real destination.

Possible destinations may include:

- Sport category.
- Competition.
- Team.
- Event.

Only expose destinations actually supported by the implemented SPORT module.

Do not implement speculative external sports APIs as part of this SEARCH redesign.

---

## 17. SETTINGS SEARCH RESULTS

The SETTINGS module will also be finalized separately.

SEARCH should eventually support finding real application settings.

For example, a matching setting should open its actual location inside SETTINGS.

### Mandatory behavior

- Navigate to the exact matching settings destination when supported.
- Do not create a duplicate settings screen.
- Do not display settings that do not exist.
- Preserve the normal BACK navigation path.

The final SETTINGS search index can be completed when the SETTINGS module specification is approved.

---

## 18. ANDROID TV REMOTE NAVIGATION

SEARCH must be fully usable without a mouse or touchscreen.

### DPAD UP / DOWN

Move through available rows and controls.

### DPAD LEFT / RIGHT

Navigate logically between:

- Search field
- Clear Search
- Category filters
- Recent searches
- Search results
- Context actions

Do not trap focus inside one section.

### Short OK

Activate the focused control or open the selected result.

### Long OK

On TV search results, open Favorite management actions.

Do not use Long OK globally for unrelated behavior.

### BACK

Use predictable one-level navigation.

Priority:

1. Close an open context menu.
2. Dismiss the on-screen keyboard if open.
3. Return from an opened result to the previous SEARCH session.
4. Exit SEARCH to the previous navigation level.

BACK must never trap the user.

---

## 19. KEYBOARD BEHAVIOR

Use the existing Android TV-compatible keyboard implementation.

Requirements:

- Open the keyboard when the search field is activated.
- Preserve typed text while navigating to results.
- Allow the user to leave the keyboard using the remote.
- Do not automatically reopen the keyboard after returning from a result.
- Do not allow the keyboard to obscure the currently focused result.
- Keep the SEARCH interface usable after dismissing the keyboard.

If voice input is already supported, preserve it.

Do not introduce a new voice-search dependency as part of this task.

---

## 20. FOCUS AND VISUAL FEEDBACK

Use the global PRETORIAN focus language.

Focused elements should have:

- Subtle graphite highlight.
- Thin silver-white outline.
- Clear text contrast.
- No aggressive glow.

Only one control should appear focused at a time.

Maintain focus after:

- Adding or removing a Favorite.
- Removing a history entry.
- Switching categories.
- Returning from a detail screen.
- Closing a contextual menu.

If the focused item is deleted, move focus to the nearest valid remaining item.

---

## 21. EMPTY STATES AND ERROR HANDLING

Handle these cases gracefully:

### No recent searches

Display:

**No recent searches**

### No matching results

Display:

**No results found**

Optionally include the query.

### Missing channel logo

Use the existing neutral fallback icon.

### Missing movie or series artwork

Use the application's existing artwork fallback.

### Unavailable SPORT or SETTINGS integration

Show a minimal informative state rather than fake content.

### Search errors

Do not crash or leave the interface unusable.

Preserve the query and allow retrying.

---

## 22. DATA CONSISTENCY

Favorites and search history are separate datasets.

Search history must not affect:

- Channel Favorites ordering.
- Recently Watched TV.
- Movie and series playback progress.
- Provider group order.
- Playlist data.

Adding a TV channel to Favorites from SEARCH must update the same Favorite collection used by TV Groups.

There must not be two independent Favorite systems.

---

## 23. ACCEPTANCE TESTS

Verify the following:

### Search entry

- [ ] SEARCH opens with an empty query.
- [ ] TV is the initial category.
- [ ] Recent searches are displayed.
- [ ] Previous query is not automatically restored on a fresh entry.

### Search input

- [ ] Search results update quickly while typing.
- [ ] Clear Search removes the query immediately.
- [ ] Clear Search does not remove history.
- [ ] Keyboard can be dismissed.
- [ ] Keyboard does not trap focus.

### Categories

- [ ] TV filter works.
- [ ] MOVIES filter works.
- [ ] SERIES filter works.
- [ ] SPORT integration does not invent unsupported content.
- [ ] SETTINGS integration does not invent unsupported settings.
- [ ] Query remains intact when changing filters.

### Recent searches

- [ ] Maximum 10 entries.
- [ ] Newest entry first.
- [ ] Oldest entry removed when capacity is exceeded.
- [ ] No duplicates.
- [ ] Repeated searches move to the top.
- [ ] Individual removal works.
- [ ] Clear History works.
- [ ] History survives app restart.
- [ ] Partial typing does not pollute history.

### TV results

- [ ] Correct channel logo and name.
- [ ] Correct Favorite state.
- [ ] Short OK opens the existing TV experience.
- [ ] Long OK opens Favorite actions.
- [ ] Favorite changes persist.
- [ ] TV Favorites updates immediately.

### Navigation

- [ ] BACK closes context menus.
- [ ] BACK dismisses keyboard.
- [ ] BACK restores SEARCH from detail screens.
- [ ] Focus is restored correctly.
- [ ] No navigation dead ends.
- [ ] No accidental fullscreen player behavior changes.

### Visual design

- [ ] Exact Pure Graphite background.
- [ ] No metallic waves.
- [ ] No blue or purple neon.
- [ ] Same focus style as the rest of PRETORIAN.
- [ ] Correct 16:9 layout.
- [ ] No cropped controls.
- [ ] Readable on Full HD and 4K televisions.
- [ ] No unnecessary decorative panels.

---

## 24. IMPLEMENTATION REPORT

After implementation, report:

1. Files modified.
2. Components reused.
3. Changes to search history persistence.
4. How the existing search engine was preserved.
5. How Favorites synchronization works.
6. How SEARCH navigation and BACK were implemented.
7. Which acceptance tests were completed.
8. Which SPORT and SETTINGS integrations remain pending.

Do not claim physical Android TV testing unless it was actually performed.

---

## 25. IMPLEMENTATION AUTHORIZATION

This document is an approved design and functional specification.

**Save it alongside the other PRETORIAN specifications. Do not begin implementation solely because this document was received.**

The user will explicitly authorize implementation and determine when it should be integrated with the current development work.

---

# FINAL DESIGN PRINCIPLE

**ONE APPLICATION. ONE DESIGN SYSTEM. ONE GLOBAL BACKGROUND.**

SEARCH must be fast, minimal, intuitive and consistent.

Preserve what already works.

Improve only what has been specified.

Do not introduce unrelated features or redesign existing screens.

**PRETORIAN PURE GRAPHITE — UNIVERSAL SEARCH.**