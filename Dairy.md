# Dairy

## 2026-09-27 — Next.js growth architecture

Expanded Architecture.md at the user's request with repository ownership, a concrete feature-based Next.js tree, allowed imports, thin routes, server-only transport, client state ownership, contract evolution, receiver-owned migrations, feature-addition steps and enforceable CI requirements. Chose independent packages and explicit HTTP boundaries over a workspace/shared package or generic service framework. Future finance processing remains with Hermes; Next.js owns presentation and request adaptation.

Aligned DASHBOARD_PLAN.md and Design.md with these decisions and corrected the plan's reference to an existing HTTPS proxy: deployment is still planned. Reviewed official Next.js structure and server/client documentation. Existing changes were the prior planning documents; no unrelated modifications appeared. Validation: documentation consistency inspection and `git diff --check`; no code, build, browser or Docker tests were run. Import enforcement, migrations and CI are proposed, not implemented. The known stale Graphify map remains insufficient for this new structure; refresh is still required before a requested commit.

## 2026-09-27 — Next.js and Docker planning revision

The user selected Next.js and Docker for the dashboard. Revised the plan from Express-served static files to a separate Next.js App Router app, with same-origin read proxies and a standalone production image. Planned Docker Compose services for dashboard and receiver; SQLite stays exclusively with the receiver in a persistent named volume. Defined loopback host ports, runtime credentials, non-root execution, build exclusions, existing-database migration precautions and container acceptance checks. Updated Architecture.md and Design.md to match.

Checked official Next.js deployment/self-hosting and Docker Next.js guidance. This remains a planning-only revision: no application or Docker files were created, no dependencies installed, and no container/build/runtime validation claimed. Reviewed the documentation diff and ran `git diff --check`. Existing working changes were the previous dashboard planning files.

## 2026-09-27 — Notifications dashboard plan

Created `codex/notifications-dashboard` from the clean `notification-receiver` checkout at the user's request. Reviewed project guidance, architecture, recent records and the current receiver source. `rules.md` was absent; used the supplied conventions. Used design-quiet-interfaces to propose a simple receiver-side notification inbox, with a split inspector as an alternative. Layout preference is pending user feedback.

Added `DASHBOARD_PLAN.md` with scope, wireframe, data/read API behavior, separate read credential, reachable states, implementation steps and acceptance checks. Created `Design.md` as a proposed browser design specification and documented the proposed boundary in `Architecture.md`. No application code, dependency, Android file, secret or database changed.

Verified the current store contains receipt ID, receive time and payload JSON and exposes insertion only. The graph inspection found obsolete append-log symbols and old Android paths; the 2026-09-14 graph cannot establish the current SQLite boundary. Graphify CLI was unavailable. Incremental refresh remains required before a requested commit. No runtime or visual tests were run for this documentation-only plan; validation is branch/status inspection and `git diff --check`.

## 2026-09-16 — Durable receiver correction pass

Fixed the review findings in the durable receiver implementation. Environment loading now occurs only when the standalone server needs it, so tests can inject configuration without reading a real `webhook/.env`. Express parses the same `application/json` and `application/*+json` media types that the content-type guard accepts. SQLite cleanup is tied to server close and listen-error paths, with idempotent cleanup, sanitized synchronous and asynchronous startup errors, and signal-listener removal.

Broadened `webhook/.gitignore` to keep `.env` variants and SQLite files plus sidecars out of Git while preserving `.env.example`. Strengthened tests with the full default Android payload, wrong-token rejection, `+json` parsing, direct-close and signal cleanup tracking, controlled application shutdown during process-restart durability testing, and sanitized synchronous and asynchronous port-bind failure evidence. Corrected the implementation report baseline and recorded the actual test count.

Validation evidence: `npm ci` passed; `npm test` passed with 17 tests and 0 failures; `node --check server.js`, `node --check storage.js` and `node --check server.test.js` passed; `git diff --check` passed. Node `v24.13.0` printed its expected experimental `node:sqlite` warning. No commit, push, Android change, HTTPS deployment or laptop migration was performed.

Remaining receiver limitations are unchanged: the database is local and not application-encrypted, events have no processing state or public read API, and the Android transport has no persistent event ID for retry deduplication. The `engines` declaration documents Node `>=24.13.0 <25`; npm does not enforce that range by default.

### Parent review

The parent reviewed the correction diff and independently passed all 17 tests and `git diff --check`. A separate read trap confirmed import and explicit configuration never read `.env`; custom SQLite and secret-file ignore checks also passed. No blocking findings remain for the synthetic receiver milestone. Deployment and device validation remain pending.

## 2026-09-16 — Phone confirmation and laptop handoff

The user reported completing the build/setup commands and phone installation, configured Instagram (`com.instagram.android`) as a test source, and explicitly confirmed that the Discord webhook worked and the notification was forwarded successfully. This is user-confirmed basic phone-to-Discord delivery. The agent did not collect Android build/lint logs or independently observe device behavior; the full acceptance checklist remains pending.

Earlier local inspection found Android Studio's Java 21.0.6 and Android SDK 36 after the environment switch, superseding the old missing-Java status. Updated SETUP.md and PROJECT.md to distinguish this from the unresolved AGP/SDK compatibility verification.

The target receiver machine is the user's old Windows laptop with Hermes installed. Rechecked the sibling poly-agent project records: they describe native Windows Hermes Desktop, Hermes 0.21.0, and `%LOCALAPPDATA%\hermes\bin\hermes.exe`. The laptop itself has not been inspected. Added the ordered handoff in PROJECT.md: transfer the uncommitted receiver changes, verify local SQLite receipt on the laptop, configure HTTPS, switch the phone from Discord, then add isolated Hermes processing. Hostname/access method, finance processing and banking-readiness work remain pending.

This update changes documentation only. No commit, push, laptop deployment or credential transfer was performed.
