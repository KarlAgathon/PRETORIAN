

> **Governing documents (since 2026-11-22):** `docs/MEDIA_CENTER_PRIME_DIRECTIVE_v1.2.md` (highest authority) and
> `docs/CLAUDE_MEDIA_CENTER_UI_UX_APPROVED_DECISIONS_2026-11-21.md`. Public name: **Media Center**; PRETORIAN is the internal
> codename only. Design: Monochrome Silver, one menu at a time. Release: every build is Private Beta; only the owner's explicit
> "Publish Stable" for a named build may run `.github/workflows/stable.yml`. Pushing to main publishes nothing.
> Where this file conflicts with the Prime Directive, the Prime Directive wins.

# CODEX – PRETORIAN DEVELOPMENT INSTRUCTIONS

Before starting any task, read AI_RULES.md.

## Your role
You are the primary implementation agent for Pretorian.

## Responsibilities
- Analyze existing code before making changes.
- Implement requested features.
- Fix bugs and improve performance.
- Maintain Android TV compatibility.
- Preserve existing architecture and functionality.
- Optimize resource consumption.
- Follow established UI/UX conventions.

## Development workflow
1. Understand the requested task.
2. Inspect relevant source files.
3. Identify dependencies and potential side effects.
4. Implement focused changes.
5. Run relevant tests.
6. Verify the Android build when possible.
7. Summarize all modifications.

## Restrictions
- Never modify main directly during feature development.
- Never remove existing functionality without approval.
- Never rewrite entire files unnecessarily.
- Never change Gradle or SDK versions without approval.
- Never introduce unnecessary dependencies.
- Never overwrite unfinished Claude changes.

## Reporting
Always report:
- Files modified.
- Changes implemented.
- Tests performed.
- Build status.
- Known issues.
- Recommended next steps.

Follow AI_RULES.md as the primary project policy.
