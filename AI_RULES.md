# PRETORIAN – SHARED AI DEVELOPMENT RULES

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
- Researching technical solutions and Android TV compatibility.
- Planning complex features before implementation.
- Reviewing code changes made by Codex.
- Identifying bugs, regressions and performance issues.
- Evaluating UI/UX and remote-control navigation.
- Providing independent technical recommendations.

Claude should not implement the same task simultaneously with Codex.

### Collaboration workflow

1. Claude analyzes and prepares technical recommendations.
2. Codex implements approved changes.
3. Claude reviews the implementation when requested.
4. Codex addresses identified problems.
5. Final changes are tested and verified.

Both agents must follow the same project rules.

---

## 11. SECURITY

- Never expose API keys, passwords or private credentials.
- Never commit secrets or sensitive user information.
- Validate external data.
- Use secure network communication whenever possible.
- Avoid unnecessary permissions.
- Preserve user privacy.

---

## 12. CHANGE MANAGEMENT

- Understand existing functionality before changing it.
- Explain significant architectural changes before implementation.
- Prefer small, focused changes.
- Preserve backward compatibility where possible.
- Obtain approval before removing existing functionality.
- Avoid unnecessary dependencies.

---

## 13. REPORTING

After every implementation task, report:

- Files modified.
- Changes implemented.
- Tests performed.
- Build status.
- Known issues.
- Potential regressions.
- Recommended next steps.

Reports must distinguish verified results from assumptions.

---

## 14. FINAL PROJECT PRINCIPLE

Every development decision must prioritize:

- Excellent user experience.
- Modern and consistent design.
- Reliability and stability.
- Android TV compatibility.
- Performance and resource efficiency.
- Maintainable architecture.
- Accessibility and intuitive remote navigation.

Pretorian must remain permanently free and open source.

The project exists to provide useful technology and help its community.
