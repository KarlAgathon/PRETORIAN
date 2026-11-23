# PRETORIAN 2.4.2 — BUG FIX 05
## CRITICAL: BACK Button Does Not Work in Floating Menus

**Severity:** CRITICAL — Navigation lock / User trapped in overlay  
**Affected module:** Live TV Fullscreen Player  
**Reference:** Attached screenshot showing Floating EPG on N1 BH  
**Affected controls:** EPG, Catch Up, Picture, History, More and any nested floating submenu

Claude, we have discovered a serious navigation bug during testing on a physical Android TV device.

**When I open the EPG, Catch Up or another floating options panel, the BACK button stops working.**

I cannot close the panel or return to fullscreen playback.

The application becomes effectively trapped in the active overlay, and I have to force-stop the app to recover.

This must be fixed before proceeding with other development.

---

## 1. CURRENT BROKEN BEHAVIOR

Reproduction:

1. Open PRETORIAN.
2. Navigate to TV.
3. Select a provider group.
4. Select a channel.
5. Enter fullscreen playback.
6. Open the bottom options dock.
7. Select EPG, Catch Up or another floating menu.
8. Press BACK on the Android TV remote.

**Actual result:**

The floating panel remains open.

BACK does not close it.

Repeated BACK presses do not restore normal navigation.

The user may have to force-stop the application.

**Expected result:**

The first BACK press must immediately close the active floating panel and restore the appropriate previous UI state.

No menu may trap the user.

---

## 2. NON-NEGOTIABLE RULE — BACK MUST ALWAYS WORK

Every interactive overlay, popup, menu, submenu and navigation level must support BACK.

There must never be a state in which BACK is ignored indefinitely.

BACK must always perform one logical backward navigation step.

This applies to:

- Floating EPG.
- Floating Catch Up.
- Floating Picture.
- Floating History.
- Floating More.
- Audio Tracks.
- Subtitles.
- Stream Information.
- Any confirmation dialog.
- Bottom Options Dock.
- Fullscreen Channel List.
- Channel Browser.
- TV Groups.
- Main navigation.

---

## 3. EXACT BACK BEHAVIOR

### A. EPG opened from the bottom dock

Navigation:

Fullscreen → Options Dock → Floating EPG

BACK sequence:

**First BACK:** Close Floating EPG and return focus to the EPG icon in the Options Dock.

**Second BACK:** Close the Options Dock and return to pure fullscreen video.

**Third BACK:** Exit fullscreen and return to the originating Channel Browser.

**Fourth BACK:** Return to TV Groups.

**Fifth BACK:** Return to the previous main navigation level.

Do not skip any level.

### B. Catch Up opened from the bottom dock

Fullscreen → Options Dock → Catch Up

First BACK closes Catch Up.

Second BACK closes the dock.

Third BACK returns to Channel Browser.

The same rule applies to Picture and History.

### C. More and nested submenus

Example:

Fullscreen → Options Dock → More → Audio Tracks

BACK sequence:

1. Audio Tracks → More.
2. More → Options Dock.
3. Options Dock → Fullscreen.
4. Fullscreen → Channel Browser.

The same applies to Subtitles and Stream Information.

### D. EPG opened directly using RIGHT

Fullscreen → Floating EPG

In this case:

**One BACK closes EPG and returns directly to fullscreen.**

Do not create an invisible Options Dock layer.

The return destination must depend on how EPG was opened.

### E. Channel list opened using LEFT

Fullscreen → Channel List Overlay

One BACK closes the channel list and returns to the same playing channel in fullscreen.

Another BACK returns to Channel Browser.

Do not jump directly to TV Groups.

### F. Confirmation dialogs

Example:

History → Clear History → Confirmation Dialog

First BACK dismisses the confirmation dialog without deleting anything.

Second BACK closes History.

Third BACK closes the dock.

No destructive action should occur when BACK is pressed.

---

## 4. INVESTIGATE THE ACTUAL ROOT CAUSE

Please do not simply add an additional BACK listener as a superficial workaround.

Inspect the existing Android TV input and navigation implementation.

Potential causes to investigate include:

- BACK key events being consumed by a child component.
- A focused popup intercepting BACK without handling it.
- Incorrect KeyEvent dispatch.
- Missing KEYCODE_BACK handling.
- Incorrect event propagation.
- A dialog/window intercepting system BACK.
- Conflicting BackHandler or OnBackPressedDispatcher callbacks.
- Android predictive-back / OnBackInvokedDispatcher integration, where applicable.
- Overlay state not being updated.
- Focus being trapped inside a modal component.
- Multiple independent overlay visibility flags.
- A stale navigation callback.
- A popup remaining attached after its state is cleared.
- Key-down and key-up handling conflicts.
- An underlying player intercepting the event before the active overlay.

These are investigation candidates, not confirmed diagnoses.

Identify the real cause in the current codebase.

---

## 5. CENTRALIZED BACK DISPATCHING

Implement or correct a single authoritative navigation/back-dispatch mechanism.

BACK must always be handled by the topmost active UI layer.

Conceptual priority:

1. Confirmation dialog.
2. Nested floating submenu.
3. Active floating panel.
4. Fullscreen channel-list overlay.
5. Bottom options dock.
6. Standard channel information overlay, if visible.
7. Fullscreen playback.
8. Channel Browser.
9. TV Groups.
10. Main navigation / application exit.

Only the highest-priority active layer should consume a BACK event.

Do not let one BACK event close multiple layers.

Do not let a child component consume BACK without performing its expected navigation.

The overlay manager must know:

- Which panel is currently active.
- Whether a nested submenu exists.
- Which control opened the panel.
- Which state should be restored when the panel closes.

---

## 6. FOCUS RESTORATION

Closing a popup must also restore correct remote focus.

Examples:

- Closing EPG returns focus to the EPG icon.
- Closing Picture returns focus to Picture.
- Closing Catch Up returns focus to Catch Up.
- Closing History returns focus to History.
- Closing More returns focus to More.
- Closing Audio Tracks returns to the appropriate item in More.

When returning to fullscreen, remove focus from hidden popup controls.

When returning to Channel Browser, restore the previous channel focus and scroll position.

Do not leave focus attached to an invisible component.

Do not allow focus restoration to reopen a popup automatically.

---

## 7. BACK MUST WORK REGARDLESS OF FOCUS POSITION

This is especially important.

BACK must work when:

- The first EPG entry is focused.
- The last EPG entry is focused.
- The user is scrolling the EPG.
- An EPG description is expanded.
- A Catch Up program is selected.
- A Picture mode is focused.
- A History channel is focused.
- Clear History is focused.
- A More submenu is open.
- An audio or subtitle track is focused.
- A popup contains no selectable items.
- EPG data is missing.
- Catch Up data is empty.
- The player is buffering.
- A network request is pending.

BACK must not depend on a specific focused button.

---

## 8. PREVENT OVERLAY COLLISIONS

Preserve the approved exclusive-overlay system.

When a floating menu is open:

- Standard channel information must be hidden.
- Only the relevant floating panel is visible.
- The bottom dock remains visible when it is the parent.
- No unrelated popup may overlap.
- Video continues playing behind the UI.

When BACK closes the panel:

- Remove the floating panel.
- Restore the correct parent layer.
- Restore focus.
- Do not leave invisible modal surfaces intercepting input.
- Do not accidentally restart playback.

---

## 9. PRESERVE THE NEW REMOTE MAPPING

The following mapping from BUG 04 remains mandatory:

| Remote | Function |
|---|---|
| UP | Open Options Dock from pure fullscreen |
| DOWN | Open Options Dock from pure fullscreen |
| Short OK | Pause/resume when genuinely supported |
| Long OK | No special action |
| LEFT | Open channels from the current TV group |
| RIGHT | Open Floating EPG |
| BACK | Close one active layer or navigate one level backward |

UP/DOWN must not change channels during pure fullscreen viewing.

Do not reintroduce the old long-OK options behavior.

Do not allow long OK to modify Favorites.

Within an open floating menu, directional keys navigate that menu rather than triggering fullscreen shortcuts.

---

## 10. IMPORTANT — NEVER FORCE THE USER TO RESTART

A broken overlay must not make the entire application unusable.

Even if:

- EPG data fails to load.
- Catch Up returns an error.
- A popup has no content.
- A stream disconnects.
- A background operation is still running.

BACK must remain responsive.

Ensure asynchronous results cannot reopen a panel after the user has already closed it.

Ensure dismissing a panel cancels or invalidates pending UI updates where appropriate.

Do not block the main UI thread while processing remote input.

---

## 11. REQUIRED TEST MATRIX

Test every case below.

| Active UI | First BACK should |
|---|---|
| Floating EPG from dock | Return to dock |
| Floating EPG from RIGHT | Return to fullscreen |
| Floating Catch Up | Return to dock |
| Floating Picture | Return to dock |
| Floating History | Return to dock |
| Floating More | Return to dock |
| Audio Tracks | Return to More |
| Subtitles | Return to More |
| Stream Information | Return to More |
| Clear History confirmation | Dismiss confirmation |
| Fullscreen Channel List | Return to fullscreen |
| Options Dock | Return to fullscreen |
| Channel Info Overlay | Dismiss info overlay |
| Pure Fullscreen | Return to Channel Browser |
| Channel Browser | Return to TV Groups |
| TV Groups | Return to main navigation |

Also verify:

- BACK works after rapid UP/DOWN scrolling.
- BACK works after changing focus.
- BACK works with missing EPG.
- BACK works during buffering.
- BACK works after opening and closing the same menu repeatedly.
- BACK never closes multiple levels at once.
- BACK never unexpectedly exits the application.
- No overlay remains invisible but intercepts remote input.
- Video playback continues when dismissing overlays.
- Focus is restored correctly.

---

## 12. IMPLEMENTATION PRIORITY

This is a **critical usability defect**, not a cosmetic issue.

Please prioritize it above visual refinements.

Make the smallest reliable architectural correction that fixes BACK handling across all fullscreen overlays.

Do not redesign the floating menus.

Do not change the approved PRETORIAN visual style.

Do not modify unrelated MOVIES/SERIES behavior.

If the root cause exists in a shared navigation component, fix it safely and verify that other sections are not affected.

---

## 13. REQUIRED REPORT

After fixing BUG 05, provide:

1. The confirmed root cause.
2. Why BACK was being ignored.
3. Which files were changed.
4. How BACK events are now routed.
5. How overlay dismissal and focus restoration work.
6. Which automated or emulator tests were performed.
7. What still needs verification on the physical Android TV device.

Do not claim physical-device verification unless it was actually performed.

**After BUG 05, stop and wait for the next bug report.**

---

# FINAL RULE

**NO PRETORIAN MENU MAY EVER TRAP THE USER.**

**ONE BACK PRESS = ONE LOGICAL STEP BACK.**

**BACK MUST WORK FROM EVERY FLOATING PANEL, EVERY FOCUSED ITEM AND EVERY NESTED SUBMENU.**

**NO FORCE STOP SHOULD EVER BE NECESSARY TO EXIT A MENU.**