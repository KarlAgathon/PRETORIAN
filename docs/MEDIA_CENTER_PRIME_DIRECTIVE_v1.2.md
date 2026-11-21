# MEDIA CENTER — PRIME DIRECTIVE

**Version:** 1.2  
**Status:** Owner-approved design principles; implementation requires review and the permissions defined below  
**Audience:** Claude Code, Codex, and every developer or AI agent working on the project  
**Public application name:** Media Center  
**Internal project codename:** PRETORIAN (development-only; not a public-facing identity)

> **Governing philosophy: COMPLEX INSIDE. SIMPLE OUTSIDE.**
>
> The user should experience a calm, fast, reliable media center—not the complexity required to build it.

---

## 0. Authority, scope, and conflict handling

1. Read this document before planning or modifying any feature. It governs UI, player, navigation, architecture, security, build, testing, and releases.
2. These are **product and engineering constraints**, not permission to implement every possible change. Inspect the actual repository and explain relevant tradeoffs before significant modifications.
3. If an owner request, older specification, screenshot, agent instruction, or proposed feature conflicts with this document, **identify the conflict** and request an explicit owner decision. Do not silently override either requirement.
4. Security, applicable law, user consent, third-party licenses, accessibility, and platform requirements remain mandatory. Never use this document to justify concealing legally required notices or weakening protections.
5. **No silent destructive changes:** do not delete user data, reset databases, invalidate backups, change package identity, replace signing keys, break upgrade compatibility, or force-push without explicit approval and a verified migration/recovery plan.
6. Owner shorthand: **“Update Prime Directive”** means propose and prepare an updated, versioned consolidated document. Preserve all previously approved rules except those explicitly revised; provide a change log and flag conflicts. It is **not** permission to publish an app release.
7. The term **“save,” “commit,” “finish,” “ready,” or “ship”** is never authorization for public release. Only an explicit, unambiguous **“Publish Stable”** instruction authorizes a public release, and only after the prerequisites below are met.

---

## 1. Complex Inside. Simple Outside.

- Advanced technology belongs behind the interface. Present only what helps the user accomplish a task.
- Favor useful defaults, clear labels, predictable behavior, and minimal interactions.
- Do not add menus, buttons, settings, prompts, screens, or visual effects merely because they are technically possible.
- Every new feature must have a clear purpose and fit the existing interaction model.
- If two solutions work equally well, choose the simpler user experience and the more maintainable implementation.
- Preserve power-user capabilities when genuinely useful, but keep them contextual rather than permanently visible.

**Acceptance question:** Can a person use the feature on a TV remote without instructions or unnecessary steps?

## 2. One Menu at a Time

- Each **navigation level replaces the previous level** in the same navigation region; never stack unrelated menus or leave earlier navigation columns partially exposed.
- Typical flow: Main categories → TV groups → channel list and associated preview/EPG → fullscreen playback.
- A content list and its associated preview, programme details, or EPG may share a screen: this is **one coherent level**, not a violation.
- BACK reverses **exactly one logical level**. Preserve the prior focused item, selection, and scroll position.
- Temporary dialogs, tooltips, and overlays are allowed only when necessary; they must not create a confusing second navigation system.
- Avoid opening a new full screen when the task can be completed cleanly within the current context.

## 3. TV Remote First

- The application must be fully usable using D-pad, OK, long OK, and BACK; no touchscreen or mouse dependency.
- Focus is visible, stable, and predictable. **Focus movement does not activate content or autoplay video.**
- OK confirms the currently focused action; long OK may open a concise context menu where appropriate.
- BACK closes the current dialog/overlay or returns one logical level. Closing a dialog restores focus to its origin.
- LEFT/RIGHT move focus in horizontal rows and controls; use backward/forward navigation only where consistent with the local context. Do not globally overload them.
- When a context menu opens, move focus inside it immediately. Dismissing it must not unexpectedly trigger playback or lose the previous selection.
- Maintain consistent remote hints, accessible contrast, and large enough TV focus targets.

## 4. Unified Visual Identity — MONOCHROME SILVER (DEFAULT)

**This rule replaces the previous dark-navy/New Dawn base palette.**

- Primary interface colors: **black, charcoal, graphite, neutral grey, silver-grey, and white**.
- Backgrounds: predominantly black/charcoal; optional subtle neutral-grey fog or atmospheric texture. **No dark navy/blue foundation.**
- Panels: restrained dark-charcoal transparency, subtle neutral-grey borders, and minimal layering.
- Typography: clear white primary text, silver-grey secondary text; strong contrast and consistent sizing/spacing.
- Selection/focus: soft silver-grey emphasis with readable dark foreground when appropriate; no distracting glow.
- Icons: simple, modern, monochrome, consistently sized and aligned.
- Progress bars and player chrome: silver/white and grey, with muted tracks.
- Avoid purple, neon accents, shiny gradients, decorative glows, visual noise, and inconsistent themes.
- **Status-color exception:** meaningful semantic color is permitted when necessary (for example LIVE red, warning/error red or amber, success green). It must remain restrained and understandable without color alone.
- **Media exception:** preserve original colors in actual video, film posters, cover artwork, and photographs. Do not recolor content to monochrome.
- Apply consistently to Home, TV, Movies, Series, Sports, Search, Settings, launcher, playback, dialogs, and screensaver.
- Exact hexadecimal tokens, fonts, dimensions, and motion timings belong in a separate **Design System**. They may evolve while this visual philosophy remains fixed.

## 5. Playback Comes First

- Video stability, correct audio, responsive controls, and recovery from stream failure outrank decorative features and metadata enrichment.
- When playback controls are hidden, fullscreen viewing shows the content without persistent branding or unnecessary UI.
- Do not restart a stream simply because the user opens an EPG, audio/subtitle settings, or a non-destructive overlay.
- No focus-triggered autoplay. Enter preview or fullscreen only through the established explicit user action.
- Optional overlays and network lookups must not block playback, steal focus, or interrupt the viewer.
- Unsupported playback features must be disabled or explained gracefully, not presented as broken controls.
- Favor a unified playback behavior and reusable player components, while retaining necessary distinctions for live, catch-up, movies, and episodes.
- Specific toolbar icons, ordering, and player gestures belong in the **Player Specification**, not in this constitutional document.

## 6. Performance Without UX Sacrifice

- Keep the interface responsive on low-powered Android TV / Google TV devices.
- Never perform heavy networking, parsing, image decoding, or database work on the UI thread.
- Load large channel lists, posters, catalogues, and EPG progressively; cache responsibly; cancel obsolete requests.
- Bound memory, threads, queues, network retries, and background work; avoid unnecessary polling.
- Support sensible low-resource behavior without surprising users or hiding essential features.
- Optimize using measurements, not guesses. An optimization that breaks playback, focus, or usability is not an improvement.
- Avoid overengineering and adding new infrastructure when the existing architecture can solve the problem reliably.

## 7. No Redundancy — One Function, One Logical Place

- Do not duplicate the same action across multiple menus without a strong contextual justification.
- Prefer contextual actions and a concise primary toolbar over crowded permanent controls.
- Hide empty, unsupported, irrelevant, or redundant actions and panels.
- Avoid adding settings users rarely need; choose intelligent defaults.
- Review every proposed feature for overlap with existing EPG, favorites, aspect ratio, time-shift, settings, and player actions.
- An action can be reachable from more than one context only if those contexts genuinely serve distinct user workflows and remain predictable.

## 8. Intelligent Context — Relevant When Verified, Silent Otherwise

- Show metadata and recommendations only when reasonably reliable and relevant.
- Do not fabricate channel/programme matches, broadcaster rights, episode identities, live status, or availability.
- Separate content actually playable from the user's configured sources from informational content outside those sources.
- Hide panels with no useful data; do not repeat the current selection as a fake recommendation.
- Perform discovery asynchronously; cache and cancel work appropriately; never delay playback or steal focus when results arrive.
- Specific **Dostupno / Prenos** matching, time windows, provider integrations, and UI placement belong in the **Contextual Information Specification**.

## 9. Public Identity, Privacy, and Neutral Branding

**Public-facing application identity: MEDIA CENTER.**

- Do not display **IPTV**, **PRETORIAN**, **Praetorian**, personal names, developer email addresses, personal GitHub usernames, or repository links in user-facing app screens, notifications, onboarding, launcher labels/icons, banners, help, release notes, or promotional assets.
- Use neutral, accurate labels such as TV, Live TV, Channels, Movies, Series, Sports, Search, and Settings.
- Replace project-specific logos with neutral Media Center artwork. Do not place an app logo or app name over active video.
- Keep any internal codename or legacy technical identifiers only when required for compatibility or maintainability, and document why. Do not blindly rename packages, classes, databases, preferences, provider authorities, backup keys, or signing identities.
- Minimize personal identifying metadata in distributable artifacts, logs, update feeds, and URLs. Do not embed private access tokens, signing secrets, or personal credentials in an APK.
- Preserve legally required third-party notices, licensing, and truthful permission/privacy disclosures. Do not claim an APK is uninspectable or impossible to reverse-engineer.
- A private source repository and public downloadable signed app updates are compatible, but require a distribution architecture separate from private source access.

**Known current compatibility constraint (verify against current code before acting):** The application ID has been `com.pretorian.tv`, and the updater has used public GitHub release URLs. Retain existing update compatibility unless a safe, explicitly approved migration is tested.

## 10. BETA BY DEFAULT → OWNER ADB TEST → EXPLICIT PUBLIC STABLE

**Every new development build is PRIVATE BETA by default. There is no automatic public release. The owner is the sole beta tester for now.**

### 10.1 Development and beta creation
- All feature changes, fixes and refactors go to an appropriate feature branch; run required checks before producing an APK.
- Every newly built candidate is **Beta**, even if it builds successfully, is committed, merged, or has a new versionName.
- Deliver the **signed, installable Beta APK** to the owner through a private, access-controlled CI artifact or other approved private channel, with a download link, artifact SHA-256, package ID, versionCode/versionName, signing identity and commit SHA.
- Do **not** publish Beta through GitHub Releases, public update manifests, public `latest` endpoints, public artifacts, public websites or app auto-update channels. If the CI artifact is not private or requires setup, say so and configure the workflow safely before claiming privacy.
- The owner downloads the APK to a laptop and installs it on **their own Android TV box via ADB**. Claude may provide exact ADB instructions, but must not assume device IP, debugging configuration or network access.
- Example, with placeholders only: `adb connect <TV_IP>:<ADB_PORT>` (where network debugging is supported and authorized), then `adb install -r <BETA_APK_PATH>`. ADB over Wi-Fi may require pairing. Keep ADB disabled when not needed.
- There is **no beta QR pairing, user invitation system, private update service, or second tester population** unless explicitly requested later.

### 10.2 Iteration and owner testing
- The owner tests Beta manually. Fixes generate a **new private Beta candidate**; previous Beta is not implicitly approved.
- Keep Beta and Stable identifiable in build metadata and Sentry environment/release tags, without violating neutral public branding.
- Record the precise tested artifact hash, source commit, versionCode, signing certificate and test observations.
- A successful build, automated tests, merge, `save`, `commit`, `done`, `ready`, `looks good`, or `finish` **never** means publish.

### 10.3 Explicit promotion to public Stable
- Only an explicit instruction such as **“Publish Stable”** or **“Proglasi Stable i pošalji svima”**, clearly referring to a specific tested candidate, authorizes public distribution.
- Before publication, Claude must identify the exact candidate and destination, confirm all mandatory release gates, and surface unresolved blockers. Do not silently substitute a newer commit.
- **Prefer publishing the exact byte-for-byte signed APK that the owner tested.** Promotion should change release-channel metadata, not the APK. Verify the SHA-256 matches the tested artifact. If an unavoidable rebuild is required, treat it as a *new candidate requiring owner testing/approval*, not the previously approved artifact.
- Stable publication updates the public signed-APK distribution and public update manifest **only after authorization**. Existing users then receive updates through the supported app update flow; do not claim all users have received or installed them immediately.
- Preserve signing certificate and application ID for in-place updates. Version codes must be monotonically increasing for users receiving updates; plan beta→stable and recovery accordingly. Never assume Android permits a normal downgrade or that an older stable APK can overwrite a newer beta.
- A stable release requires a valid tested update route, checks of signing identity and artifact integrity, data-migration safety, and an owner-visible release summary.

### 10.4 CI/CD safety and repository visibility
- **Audit and disable any existing automatic GitHub Release on main/master/tag/versionName change before the first new Beta.** CI may automatically build/test Beta, but must not auto-publish public releases.
- Separate private Beta artifact jobs from manually approved Stable release jobs. Restrict write permissions and signing secrets to the narrowest required jobs; protect release environments and credentials.
- Private source repository visibility is a separate future owner decision. Do not change visibility until a neutral public HTTPS update route has been implemented, tested with existing installations, and explicitly approved.
- Do not embed private GitHub credentials in the APK. A public APK/update endpoint may remain reachable even when source code is private; its hostname is discoverable and should be neutral, not presented as secret.

### 10.5 Example command semantics
- “Build a beta” → test, package, privately deliver the APK and ADB instructions; **no public release**.
- “Fix this and send another beta” → new candidate, new hash and versionCode; **no public release**.
- “Save / commit / merge” → perform only authorized source-control action; **no public release**.
- “Publish Stable [specific tested candidate]” → validate gates and promote exactly that approved signed artifact to the public channel.

## 11. Security by Design

- Protect playlist credentials, authentication tokens, device identifiers, backups, and any sensitive user configuration.
- Secure local pairing, including authentication, expiration, replay prevention, bounded requests, and appropriate transport protections.
- Treat playlists, URLs, EPG, subtitles, metadata, and external inputs as untrusted. Validate and bound parsing and network behavior.
- Prefer secure transport for trusted infrastructure; accommodate legacy HTTP media sources only when required and with explicit risk boundaries.
- Encrypt sensitive exports; support safe restore/migration and backward compatibility. Avoid plaintext credential backups.
- Minimize permissions and attack surface; audit exported Android components, file access, redirects, and external intents.
- Redact secrets and personal data from logs, crash reports, and diagnostics; apply sensible retention.
- Authenticate and verify software updates, signatures, artifact integrity, redirects, and version policy. Fail safely when identity verification cannot be completed.
- Protect CI secrets, signing material, and release permissions; use least privilege and review dependencies.
- Security measures must be proportionate, tested, and compatible with real user media workflows. Technical crypto choices and implementation specifics belong in the **Security Specification**.

## 12. Safe Development and AI Collaboration

- Inspect the current code and relevant existing rules before proposing changes.
- Work on an appropriately named feature/hardening branch; do not develop directly on the protected main branch.
- Preserve working code, user data, tests, translations, and platform compatibility.
- Prefer targeted changes over large rewrites. Do not replace another agent's ongoing work or force-push without permission.
- Run relevant unit tests, build checks, lint/static analysis where available, and manual Android TV regression tests for affected UX.
- Report actual evidence: what changed, what passed, what failed, what was not tested, and any remaining risk. Never invent test results.
- Seek approval before major architectural shifts, new paid services, data-destructive migrations, package/signing changes, repository visibility changes, or public release.
- If the owner asks for **review only**, do not implement, commit, deploy, or publish.

## 13. No Silent Destructive Changes

- Treat deletion, irreversible migration, signing-key replacement, package ID changes, profile/playlist resets, backup-format breaks, and loss of user settings as high-risk operations.
- Require a written migration plan, backups, rollback/recovery method, tests, and explicit owner approval.
- If a safe migration is not possible, explain that limitation before acting.

## 14. Graceful Degradation

- Core browsing, navigation, and playback should remain functional when optional EPG, metadata, sports, AI, artwork, discovery, or analytics services fail.
- Use bounded retries, clear but unobtrusive error messages, and cached/local information when valid.
- Do not let a third-party API outage block content the user already has access to.
- Do not mask failures as fabricated successful results.

## 15. Prime Directive Precedence and Change Control

- This directive takes priority over older design concepts and conflicting feature-level specifications, subject to Section 0's legal, safety, and compatibility constraints.
- Agents must surface conflicts rather than silently choose a side.
- Update it as a **complete, versioned master document** whenever the owner says **“Update Prime Directive.”** Include a short change log and keep the previous version for reference.
- A new owner-approved revision supersedes older revisions; unapproved proposals are not binding.

---

## 16. Automatic Privacy-First Crash Reporting

- Integrate **Sentry Cloud** into Android APK builds as a lightweight optional crash-reporting mechanism; no owner-hosted diagnostics server is required.
- Offer one understandable **Send automatic crash reports** setting, **OFF by default until valid user consent**. Once enabled, reporting runs automatically without QR codes, email, device registration or repeated prompts. Opt-out must prevent further collection/transmission and address pending queued reports.
- Generate a random app-installation ID locally. Preserve it through normal updates, regenerate it after app data is cleared or the app is reinstalled, and exclude it from transferable backups. Send it only with consent. It is pseudonymous, **not fully anonymous**.
- Allow only minimal technical data: installation ID, application version/build/channel, Android version, useful coarse device model, event time, safe crash classification and sanitized technical location/code.
- **Never transmit** usernames, passwords, tokens, playlist contents, stream URLs, viewing history, private device nicknames, screenshots, raw Logcat or uncontrolled exception text. Treat stack traces and breadcrumbs as potentially sensitive; verify actual transmitted payloads, not just configuration intentions.
- Disable unnecessary telemetry and capture integrations. Keep processing lightweight, queues bounded, and failures non-blocking. Playback, UI responsiveness and offline operation have priority.
- Reports go to Sentry; the owner **manually** exports or copies sanitized reports to Claude/ChatGPT for code analysis. No automatic AI integration, private device registry, spreadsheet sync or custom dashboard is required.
- Beta and Stable events must be distinguishable. Test consent, secret redaction, crash delivery, offline behavior, device ID lifecycle and low-resource impact in Private Beta before any Stable release.
- Sentry's receipt of connection metadata, including source IP, and applicable data-protection obligations must be assessed honestly; do not promise absolute anonymity.
- The companion implementation specification is **CLAUDE_IMPLEMENTATION_DIRECTIVE_SENTRY_CRASH_REPORTING.md**. Technical SDK details may evolve without changing this governing principle.

---

## A. Technical specifications — intentionally separate

Prime Directive describes **what must remain true**. Maintain these as separate, revisable specifications for **how** to implement it:

| Specification | Contents |
|---|---|
| Design System | Exact color tokens, fonts, dimensions, spacing, transitions, assets |
| Navigation & Remote UX | Screen flows, D-pad focus graphs, edge cases, restoration |
| Player Specification | Six-action toolbar, EPG, audio/subtitles, aspect ratio, archive, live behavior |
| Contextual Information | Dostupno/Prenos data sources, matching, verification and cache policies |
| Performance & Architecture | Database, memory budgets, lazy loading, background execution |
| Security Specification | Credential encryption, backup KDF, QR pairing, logging, permissions |
| Release Engineering | CI permissions, versioning, signing, beta access, update endpoints, rollback |
| Integrations | Provider/API contracts, network policies, feature availability |

Do not freeze implementation details in this document unless the owner explicitly elevates one to a permanent rule.

## B. Claude's required workflow for every significant task

1. **Read:** Prime Directive and the relevant technical specification(s).
2. **Inspect:** Actual repository code, tests, assets, configuration, and release pipeline. Do not assume prior findings are still current.
3. **Assess:** State the user goal, proposed change, alternatives, redundancy, risks, and Prime Directive compliance.
4. **Plan:** List affected files, migration requirements, test strategy, and whether owner approval is needed.
5. **Implement:** Only within the scope authorized by the owner; use a separate branch and small, reviewable changes.
6. **Validate:** Run available automated checks and appropriate device-level tests; distinguish performed tests from recommended tests.
7. **Report:** Summarize changes, limitations, compatibility, security, and any unresolved decisions.
8. **Release:** **Never** distribute publicly without the explicit “Publish Stable” authorization and completed release gates.

## C. Mandatory review checklist

Before declaring a change complete, answer:

- Does it make the application simpler for the viewer, or merely add complexity?
- Does it preserve one-menu-at-a-time navigation and predictable remote focus?
- Does it follow Monochrome Silver across all affected UI?
- Does it avoid unwanted branding, IPTV terminology, and personal contacts in public-facing surfaces?
- Does it avoid duplicating actions or showing empty/unsupported panels?
- Does it preserve playback, performance, data, settings, and upgrade compatibility?
- Does it protect secrets, validate untrusted inputs, and keep optional failures non-blocking?
- Are tests and untested risks honestly documented?
- Is the build **private beta** or **public stable**, and is distribution explicitly authorized?

If any answer is uncertain, report it and request a decision rather than assuming success.

---

## D. Version history

**v1.0 — Initial consolidated master directive**
- Consolidated 15 governing principles.
- Replaced New Dawn dark-navy palette with **Monochrome Silver** as the default across the application.
- Established public identity **Media Center**, with no public IPTV/PRETORIAN/developer-contact branding.
- Established beta by default → owner-only ADB testing → explicit public stable approval.
- Added no-destructive-changes, graceful degradation, precedence, and versioned updates.
- Distinguished permanent principles from changeable technical specifications.

**v1.2 — Beta by default, owner-only ADB testing**
- Replaced Rule #10 with the approved owner-only process: every new build is private Beta, delivered as a private APK to the owner for laptop/ADB installation and manual testing.
- Only explicit approval of a specific tested artifact permits Stable publication; the exact APK hash must be preserved when promoted.
- Required disabling automatic public releases and separating private build artifacts from public update feeds.
- Clarified versionCode, signing, source privacy, and update-compatibility safeguards.

**v1.1 — Automatic crash diagnostics**
- Added Rule #16: minimal opt-in Sentry crash reporting, random installation ID, strict privacy controls, manual AI analysis and no separate device registry in the application.
- Added companion Claude implementation directive and Private Beta privacy acceptance criteria.

**End of Prime Directive v1.2.**
