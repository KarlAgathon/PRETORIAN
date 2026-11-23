# PRETORIAN — TV GROUPS MASTER IMPLEMENTATION INSTRUCTION

**For Claude — specification only until explicit implementation authorization**

## 1. Scope and status

This document defines the TV Groups module: Favorites, Recently Watched, and provider-defined channel groups. Store it with the approved PRETORIAN specifications and visual references. **Do not start implementing this module until the user explicitly instructs you to do so.** The ongoing build containing previously requested fixes and the Pure Graphite global background remains separate work.

## 2. Non-negotiable design and navigation

- Use the approved **Pure Graphite** global background and restrained monochrome visual identity on all TV browsing screens. Do not revive blue concrete-room imagery, large decorative panels, or alternative backgrounds.
- Follow **ONE MENU — ONE LEVEL — COMPLETE REPLACEMENT**. Main menu -> TV Groups -> Channel Browser -> Fullscreen, with BACK returning exactly one logical level. Preserve focus and scroll position where practical.
- The TV Groups landing screen is a simple left-aligned, vertically scrollable list. **There is no video preview on the group-selection screen.**
- The first two groups are **Favorites** and **Recently Watched**, followed by the actual provider-defined groups in playlist order. Do not hardcode example countries or group names.
- Selecting *any* group replaces the group list with the **same already-approved TV Channel Browser**: channels on the left, large 16:9 preview on the right, NOW/NEXT EPG below, and existing actions. No separate Favorites or History player UI.
- Do not autoplay channels merely because focus moves. Preserve the existing approved first-OK-preview, second-OK-fullscreen interaction. Do not regress remote mappings, floating menus, or BACK handling.

## 3. TV Groups landing screen

Display:
1. Favorites — favorite channel collection.
2. Recently Watched — channel viewing history.
3. Provider groups — names and membership exactly as supplied by the playlist/provider, including their existing order unless a separate provider sorting rule already exists.

Use compact, legible rows with a restrained focus indicator. Do not show flags, poster grids, full-page cards, or video preview at this level. A group selection enters the Channel Browser and removes the group menu from view. BACK restores the group list and its prior focus/scroll position.

## 4. Favorites — normal browsing

- Display the user's actual favorited channels in the shared Channel Browser, with number/order, logo, name, and quality badge where available.
- The right-hand 16:9 preview, NOW/NEXT EPG, and lower controls behave exactly as in ordinary provider groups.
- Favorites are a view of the same channel entities, not duplicated streams. Removing a favorite must not delete the provider channel.
- Favorites may originate from any provider group. Maintain a persistent **user-defined Favorites ordering**, independent of provider order.
- When a favorite is removed, it disappears from the Favorites list and the remaining rows close the gap. Keep focus on the nearest sensible row; if empty, display a concise empty state and a way to return to groups.

## 5. Favorites — remote-controlled reorder mode (critical)

The desired interaction is an intuitive **pick up -> move -> drop** using the TV remote. There must be **no Save button, no confirmation dialog, no extra page, and no separate modal workflow**.

### Enter edit mode

- While focus is on a channel row inside **TV -> Favorites**, a **long press of OK/DPAD_CENTER** enters inline Favorites Edit Mode.
- Edit Mode is visually subtle: preserve the Channel Browser layout, indicate edit state with a small label or grip/move affordance, and keep focus on the current channel.
- Long OK must be handled in this specific context only. **Never allow it to trigger fullscreen Favorites toggling or conflict with existing fullscreen player remote mappings.**

### Pick up and move

- In Edit Mode, short **OK** on a focused row picks up/activates that channel for moving.
- **DPAD UP/DOWN** moves the picked-up channel one position per navigation step, reflowing the other rows immediately. If the key is held and repeat events occur, move continuously at a controlled, predictable rate.
- The selected channel stays visibly marked as the active/moving row. Keep it in view and scroll automatically at list edges.
- Moving must not switch the live stream or start playback; the preview must not follow incidental row focus during editing.

### Drop and persist

- Short **OK** again drops the channel at its current position.
- The channel **stays exactly where it was dropped**. Immediately persist the new order in the background without asking the user to confirm.
- The user can then select another channel and repeat the operation within the same Edit Mode.
- **BACK exits Edit Mode**, returning to normal Favorites browsing. The new order is already saved. If a channel is still picked up when BACK is pressed, drop it in its current visible position, persist, and exit Edit Mode—no confirmation prompt.
- The persisted order must survive navigation away, app restart, and playlist refresh when the same channel identities can still be matched. Handle removed/unavailable channels gracefully.

### Safety and boundaries

- Reordering is enabled **only in Favorites**. Never reorder provider groups or Recently Watched with this feature.
- Do not delete, unfavorite, or launch a channel while moving it.
- If channel options such as **Remove from Favorites** are needed, expose them through an existing nonconflicting channel-options affordance in normal browsing, not by making long OK ambiguous with entry to Edit Mode. Preserve any existing approved option UI where compatible.
- On an empty or single-item Favorites list, disable moving meaningfully rather than showing a broken editor.

## 6. Recently Watched

- Present actual previously watched TV channels, newest first, in the **same Channel Browser** with 16:9 preview and NOW/NEXT EPG.
- A channel should enter viewing history on a genuine viewing action, **not merely on focus or incidental scrolling**.
- Rewatching a channel moves it to the front; avoid duplicate entries for the same stable channel identity.
- Keep this history consistent with HOME's **Recently Watched TV** section and the fullscreen player's **History** feature. If the fullscreen dock uses a limited recent-channel subset (e.g. 10), derive it from the same underlying history source rather than maintaining conflicting histories.
- Support **Remove from History** for an individual channel and **Clear History** for the entire TV history, using an existing compact options interface or unobtrusive control. Clearing history must not remove Favorites, provider groups, or unrelated movie/series progress.
- Normal preview/fullscreen navigation remains unchanged. No Favorites-style reorder mode here.
- Provide a clear, minimal empty state when no channels have been watched.

## 7. Provider-defined groups

- Use groups and channels from the active IPTV playlist/provider, not invented or hardcoded categories.
- Preserve each group's channel membership and provider order. No user reorder mode in these groups.
- Selecting a provider group opens the shared Channel Browser with that group's channel list, large preview, NOW/NEXT EPG, and existing actions.
- The channel-list overlay opened during fullscreen playback must use the **currently active group**, whether Favorites, Recently Watched, or a provider group.
- When the playlist refreshes, reconcile changes safely without losing user Favorites order or corrupting viewing history.
- Handle empty groups, missing logos, missing quality metadata, missing EPG, and temporarily unavailable channels gracefully.

## 8. Existing Channel Browser and fullscreen behavior — preserve

- Large correctly proportioned 16:9 preview on the right; compact channel rows on the left; EPG NOW/NEXT below; approved bottom actions.
- First OK starts preview, second OK opens fullscreen as previously approved. Moving focus alone never starts playback.
- Fullscreen TV remote mappings remain as approved: UP/DOWN opens dock, LEFT opens active-group channel overlay, RIGHT opens floating EPG, BACK closes one logical layer, and long OK has no special fullscreen action. Do not accidentally toggle Favorites on long OK.
- Preserve EPG, Catch Up, History, Picture, More, focus restoration, and correct BACK handling. Do not rebuild unrelated playback code merely to add TV Groups.

## 9. Data integrity and implementation guidance

- Use stable channel identities where possible; account for playlist updates, duplicate display names, and provider changes.
- Persist favorite membership, Favorites ordering, and TV viewing history as distinct concerns. No profile picker or profile migration is required for the current clean-slate PRETORIAN direction.
- Reuse the shared Channel Browser and centralized global background/theme rather than duplicating UI for each group.
- Keep state transitions deterministic and remote-first. Avoid confirmation dialogs, unnecessary animations, or new screens.

## 10. Acceptance tests

1. TV shows Favorites, Recently Watched, then actual provider groups; no preview at group-selection level.
2. Selecting each group opens the same approved Channel Browser with preview and EPG.
3. BACK returns browser -> groups -> main menu, preserving sensible focus.
4. Favorite a channel from a provider group; it appears in Favorites without being removed from the provider group.
5. Long OK on a Favorites channel row enters inline Edit Mode; OK picks it up; UP/DOWN reorders; OK drops; no confirmation dialog.
6. Reorder several Favorites in one edit session; BACK exits; the order persists after app restart.
7. Holding UP/DOWN during move scrolls safely; no unintended playback, unfavorite action, or channel switching.
8. Long OK in fullscreen never accidentally changes Favorites.
9. Recently Watched records genuine viewing, deduplicates entries, and synchronizes with HOME and fullscreen History.
10. Remove one history entry and clear history without affecting Favorites or provider groups.
11. Fullscreen LEFT opens the currently active group's channel list.
12. Playlist refresh and missing metadata do not crash the UI or destroy saved Favorites order.
13. All browsing screens retain Pure Graphite and legible monochrome focus states; no old background remnants.

## 11. Required handoff behavior

**For now: save and acknowledge this specification only. Do not start coding TV Groups until the user gives a clear start instruction.** When authorized, implement the feature without altering unrelated approved layouts or behaviors. After implementation, report changed files, persistence strategy, actual tests performed, and anything still needing physical Android TV validation.
