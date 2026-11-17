
# PRETORIAN – AI DEVELOPMENT RULES

## 1. PROJECT IDENTITY

Project: PRETORIAN
Type: Free and open-source Android TV IPTV application.
Repository: KarlAgathon/PRETORIAN

Pretorian is a personal and community-oriented project.
It is permanently free and is not intended as a commercial product.

The main objective is to provide an excellent Android TV experience with:
- Modern and professional interface.
- Simple and intuitive navigation.
- Reliable playback.
- Low resource consumption.
- Excellent remote-control experience.
- Compatibility with different Android TV devices.

## 2. CORE DEVELOPMENT PRINCIPLES

- Preserve the existing application architecture.
- Never rewrite working functionality unnecessarily.
- Never remove existing features without approval.
- Avoid unnecessary dependencies.
- Prioritize stability, performance and reliability.
- Keep the application lightweight.
- Avoid unnecessary background processes.
- Optimize memory and CPU usage.
- Maintain backward compatibility whenever possible.

## 3. USER EXPERIENCE (UX)

User experience is one of the highest priorities.

The interface should follow modern design principles inspired by premium streaming platforms.

Requirements:
- Simple and logical navigation.
- Consistent visual language.
- Clear typography and readable text.
- Consistent spacing and component sizing.
- Appropriate contrast and colors.
- No unnecessary menus or navigation steps.
- Predictable Back button behavior.
- No accidental focus loss.
- No overlapping or clipped UI elements.

The application should anticipate user needs and minimize unnecessary interaction.

## 4. ANDROID TV AND REMOTE CONTROL

Android TV compatibility is essential.

Always consider:
- D-pad navigation.
- Focus management.
- OK / Select behavior.
- Back button behavior.
- Long-press interactions.
- Focus indicators.
- Different screen resolutions.
- Different aspect ratios.
- TV overscan and safe areas.

Never introduce changes that make remote navigation less intuitive.

Every interactive element must be accessible using a TV remote.

## 5. PERFORMANCE AND STABILITY

- Avoid memory leaks.
- Avoid unnecessary object allocations.
- Avoid blocking the main thread.
- Optimize image loading and caching.
- Handle network errors gracefully.
- Prevent application crashes.
- Avoid unnecessary recompositions or UI updates.
- Preserve smooth scrolling and navigation.
- Consider low-memory Android TV devices.

Stability is more important than adding unnecessary visual effects.

## 6. IPTV FUNCTIONALITY

Preserve existing functionality related to:
- M3U and Xtream playlists.
- Live TV.
- Movies.
- Series.
- EPG.
- Channel groups.
- Favorites.
- Search.
- Reminders.
- Video playback.
- Audio and subtitle selection.
- External players.

Do not change existing playback behavior without understanding its dependencies.

## 7. VISUAL DESIGN

Maintain Pretorian's established visual identity.

Design priorities:
- Modern streaming-platform appearance.
- Clean and professional layout.
- Consistent component design.
- Appropriate use of colors.
- Smooth and subtle animations.
- Responsive layouts.
- Clear information hierarchy.

Do not redesign existing screens unless specifically requested.

## 8. CODE MODIFICATIONS

Before modifying code:

1. Inspect the relevant files.
2. Understand existing implementation.
3. Identify dependencies.
4. Determine possible side effects.
5. Propose a solution when changes are substantial.

When modifying code:
- Make focused changes.
- Avoid unrelated modifications.
- Preserve existing naming conventions.
- Follow existing coding patterns.
- Document important architectural decisions.

Never replace entire files unnecessarily.

## 9. BUILD AND TESTING

- Inspect existing Gradle configuration before changing it.
- Preserve current SDK and dependency configuration.
- Do not upgrade Gradle or Android dependencies without justification.
- Run appropriate tests after modifications.
- Verify APK build whenever possible.
- Report build errors honestly.
- Never claim a successful build without verification.

## 10. GIT AND COLLABORATION

- Never commit directly to main during feature development.
- Use separate branches for development tasks.
- Keep commits focused and descriptive.
- Never overwrite another agent's unfinished work.
- Review changes before merging.
- Use Pull Requests for significant changes.
- Require human approval before merging into main.

## 11. AI COLLABORATION

Both ChatGPT Codex and Claude must follow these rules.

When working on the same project:

- Inspect existing work before starting.
- Communicate assumptions and findings.
- Avoid duplicate implementation.
- Do not overwrite another agent's changes.
- Review each other's code when requested.
- Identify potential regressions.
- Explain important technical decisions.

One agent's suggestions must not automatically be treated as correct.

## 12. SECURITY

- Never expose passwords, API keys or access tokens.
- Never commit private credentials.
- Do not introduce unnecessary network permissions.
- Preserve existing security mechanisms.
- Handle user data responsibly.

## 13. FINAL DEVELOPMENT RULE

Pretorian is built around three fundamental priorities:

1. USER EXPERIENCE
2. STABILITY
3. PERFORMANCE

Every development decision should respect these priorities.

Do not sacrifice reliability for unnecessary complexity or visual effects.
