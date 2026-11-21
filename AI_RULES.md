# PRETORIAN – SHARED AI DEVELOPMENT RULES

> **Governing documents (since 2026-11-22):** `docs/MEDIA_CENTER_PRIME_DIRECTIVE_v1.2.md` (highest authority) and
> `docs/CLAUDE_MEDIA_CENTER_UI_UX_APPROVED_DECISIONS_2026-11-21.md`. Public name: **Media Center**; PRETORIAN is the internal
> codename only. Design: Monochrome Silver, one menu at a time. Release: every build is Private Beta; only the owner's explicit
> "Publish Stable" for a named build may run `.github/workflows/stable.yml`. Pushing to main publishes nothing.
> Where this file conflicts with the Prime Directive, the Prime Directive wins.


**Project:** PRETORIAN  
**Repository:** KarlAgathon/PRETORIAN  
**Project type:** Free and open-source Android TV IPTV application.

These rules apply equally to ChatGPT Codex, Claude Code and any other AI development agent working on Pretorian.

Both agents must read and follow this document before starting any development task.

---

## 1. PROJECT IDENTITY

Pretorian is a personal and community-oriented open-source project.

- Pretorian is permanently free.
- It is not a commercial product.
- The project is intended to help users enjoy a better television experience.
- Development priorities are quality, simplicity, reliability and accessibility.

The goal is to deliver a professional application experience comparable to modern streaming platforms such as Netflix, Apple TV and Disney+.

---

## 2. CORE PROJECT PHILOSOPHY

The two most important project priorities are:

### Modern design

- Professional and contemporary interface.
- Consistent visual identity.
- Carefully selected colors and typography.
- Clean layouts and balanced spacing.
- Responsive layouts for different TV resolutions.
- No unnecessary visual clutter.
- Consistent design across all application sections.

### Excellent user experience (UX)

- Simple and intuitive navigation.
- Logical Back button behavior.
- Optimized Android TV remote-control navigation.
- Minimal unnecessary navigation steps.
- Predictable focus behavior.
- Clear visual feedback for user actions.
- Fast access to frequently used functions.
- Intelligent presentation of relevant information.
- The application should anticipate user needs whenever possible.

The user should never have to fight the interface to find something.

---

## 3. PRESERVE EXISTING FUNCTIONALITY

- Always inspect existing code before making changes.
- Preserve the existing application architecture.
- Never remove working functionality without explicit approval.
- Never rewrite entire files unnecessarily.
- Avoid duplicating existing functionality.
- Respect existing naming conventions and coding patterns.
- Maintain compatibility with existing features.
- Avoid unnecessary dependencies and complexity.

Existing working functionality takes priority over unnecessary refactoring.

---

## 4. ANDROID TV COMPATIBILITY

Android TV is the primary target platform.

Every implementation must consider:

- D-pad navigation.
- Focus management.
- Remote-control interaction.
- Back button behavior.
- Different screen resolutions.
- Different aspect ratios.
- TV overscan and safe areas where applicable.
- Performance on lower-powered TV devices.
- Memory and CPU consumption.

The interface must remain usable without a touchscreen or mouse.

Never introduce navigation that requires touch interaction.

---

## 5. UI/UX DESIGN RULES

- Maintain consistent visual styling.
- Preserve the established Pretorian design language.
- Use existing components whenever possible.
- Avoid inconsistent margins, padding and element sizes.
- Ensure text remains readable from normal TV viewing distances.
- Prevent clipping, overlapping and truncated content where avoidable.
- Ensure focus indicators are clearly visible.
- Keep animations smooth and purposeful.
- Avoid excessive animations and visual effects.

Any significant design change must be evaluated across the relevant application screens.

---

## 6. PERFORMANCE AND RELIABILITY

- Optimize resource consumption.
- Avoid unnecessary background operations.
- Prevent memory leaks.
- Avoid unnecessary network requests.
- Use efficient data processing.
- Keep navigation and playback responsive.
- Handle network errors gracefully.
- Avoid blocking the main UI thread.
- Preserve playback stability.

Performance improvements must not compromise existing functionality.

---

## 7. CODE QUALITY

- Write clean, maintainable and readable code.
- Follow the existing project architecture.
- Use meaningful names.
- Avoid unnecessary abstractions.
- Handle exceptions properly.
- Avoid duplicated logic.
- Keep implementations focused.
- Document complex logic when necessary.

Do not introduce technical complexity without a clear benefit.

---

## 8. DEVELOPMENT AND GIT RULES

- Never develop directly on the main branch.
- Use separate feature or bug-fix branches.
- Never overwrite unfinished changes from another agent.
- Never force-push without explicit approval.
- Never delete branches containing unfinished work without approval.
- Keep commits focused and descriptive.
- Review changes before committing.
- Do not commit secrets, API keys, passwords or private user data.

Before editing files, check the current Git state and existing modifications.

If another agent has uncommitted changes, do not overwrite them.

---

## 9. BUILD AND TESTING

After implementation:

1. Run relevant tests.
2. Check for compilation errors.
3. Verify affected functionality.
4. Check Android TV navigation where applicable.
5. Verify that existing features still work.
6. Run the Android build when possible.
7. Report the actual build and test results.

Never claim that a build or test succeeded unless it was actually executed and verified.

---

## 10. AI COLLABORATION RULES

Pretorian uses a combined AI development workflow.

### ChatGPT Codex

Primary implementation agent.

Responsible for:
- Writing and modifying code.
- Implementing features.
- Fixing bugs.
- Optimizing performance.
- Running tests and builds.

### Claude Code

Architecture, research and review agent.

Responsible for:
- Analyzing application architecture.
- Researching
