# Dairy

## 2026-09-28 — Merge notification receiver into master

Resolved the in-progress merge by combining the dashboard read API with schema-v2 authenticated ingestion, sensitive-content filtering, durable source/event deduplication and the Hermes worker. The SQLite migration now creates both the read arrival index and source/event columns and index in one transaction while preserving historical receipts and processing state. Added the filter module to the receiver Docker image and a cross-feature test that checks a schema-v2 retry appears once through the read API. Reconciled project, setup, architecture and ignore records; retained both branches' historical entries.

Validation: `npm test --prefix webhook` passed 43/43 tests. Android `:app:testDebugUnitTest :app:assembleDebug` passed with Android Studio Java 21; the existing AGP 8.5.2 / compileSdk 36 compatibility warning remains. The receiver Docker image built. `git diff --check` passed. The first Android attempt used Java 11 and failed before compilation; selecting Java 21 resolved it. No live receiver, phone installation or production database was changed.

Graphify's code-only incremental update produced 803 nodes and 1,308 edges. Diagnostics found zero missing/dangling endpoints or collapsed duplicate edges and 19 self-loops. The graph reports 27 unclassified files, 197 weakly connected symbol nodes and 14 thin communities; changed document semantics were not re-extracted by the code-only CLI, and community labels were carried over from 44 to 45 communities. Previously tracked caches and local Graphify metadata were removed from the Git index and ignored; portable graph outputs remain tracked. Source and current documents remain authoritative for the extraction gaps.

## 2026-09-28 — Separate reusable dashboard

Reviewed latest commit 693d817 and the partial sibling-project move. Per user clarification, kept receiver source, SQLite/read authorization, tests and Docker image in NotificationForwarder. Moved/adapted the Compose smoke check and Next.js architecture guidance to general-data-dashboard; replaced the broken dashboard CI here with receiver-only CI. The dashboard now connects over HTTP with configurable RECEIVER_URL, runs alone in Compose and uses an independent synthetic browser fixture. Removed identical misplaced read-route copies there. No generic data schema or frontend redesign was introduced.

Updated current ownership records, README and setup guidance. Preserved local credentials, databases, Windows tooling and existing tracked graph caches. Added ignores for new local caches/tooling. No rules.md exists in either project. RTK searches were checked against source; graph inspection showed obsolete appendLog symbols and no current read API. Graphify CLI/module are absent, so incremental refresh remains pending before any requested commit.

Validation: existing receiver suite passed all 20 tests before separation; receiver implementation is unchanged. Validation: receiver suite passed 20/20 tests; receiver Docker image built successfully. Dashboard npm ci, lint/import boundaries, 4 unit tests, production build, typecheck, all 4 Playwright browser tests, and standalone Docker Compose smoke passed. Both repositories passed git diff --check. The initial browser attempt lacked Chromium; installing the pinned browser resolved it. Next.js emitted existing standalone-start and external parent-lockfile warnings; tests and the production container passed. Android source was unchanged and Android checks were not rerun. Graphify CLI/module are unavailable; the existing graph remains stale and refresh is pending before a requested commit. No commit or deployment performed.

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
## 2026-09-18 — Reboot acceptance clarification

The user confirmed the reboot test was already completed in the previous session. This supersedes earlier notes marking reboot testing outstanding. Sleep/resume remains separately unconfirmed.

## Follow-up — User confirms webhook receipt test

The user reported "Tested. Looks good" after the webhook verification instructions: compare saved receipt counts with `npm run process:status`, generate a harmless notification, and inspect recent classifications with `npm run process:results`. Recorded this as user-confirmed successful webhook receipt testing, not a new agent-observed test run. No exact counts or processing output were supplied. Reboot/sign-in and sleep/resume were not explicitly confirmed, and resolution of the three previously recorded `hermes_failed` receipts remains unverified. This update changes documentation only.

## 2026-09-18 — Windows supervision and next-work handoff

Installed `NotificationForwarder-Laptop` on the actual hosting laptop, using the existing receiver environment, database, ngrok hostname and Hermes parser profile. Added root-level double-click Start, Stop, Status and Install Startup commands. Start enables automatic startup; Stop disables it and stops the owned process tree. The scheduled task runs under the signed-in user, with a supervisor that restarts exited services and checks receiver health. A Windows Job Object cleans up children on supervisor termination.

Windows failure-retry settings alone did not recover the forced supervisor termination during testing, so a one-minute repeating trigger was added and verified. The Store-installed ngrok executable rejected Job Object assignment (error 5); a copy of the existing binary in ignored `webhook/windows/bin/` works. That copy needs separate refresh after Store updates. Machine paths and the existing hostname are kept in ignored `settings.json`.

Validation: 27 receiver/worker tests passed; all three services recovered from forced termination; supervisor termination cleaned up children and the repeating trigger restored service; Stop/Start and duplicate-start checks passed; local and public HTTPS health passed with the existing hostname. Exactly one supervisor and three service processes were left running. Reboot/sign-in and sleep/resume have not been tested. The final processing snapshot was 15 completed and 3 `hermes_failed`; no failed records were reset. See [operating guide](webhook/windows/README.md).

The user subsequently asked about querying expenses through Telegram, explicitly as a question only. Recorded a proposed private, read-only Telegram query bot using a separate Hermes profile and database-calculated totals. No Telegram setup or messages were authorized or performed. Priorities are now actual reboot/resume acceptance, investigation of failed processing, sensitive filtering, persistent event IDs and deduplication, protected/retained storage and a reviewed expense ledger, then optional Telegram queries. These priorities and the proposal are in `PROJECT.md`. No commit or push was performed in this work.

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

## 2026-09-18 — Old-laptop transport and Hermes sync

The user completed the old Windows laptop handoff. Node 24 and the reviewed `webhook/` source are installed there; the receiver health endpoint responds, and the phone reaches it through an ephemeral ngrok HTTPS tunnel. The phone uses the tunnel URL with `/webhook`, POST, Bearer authentication, empty query parameters and an empty payload template. An initial `/` route produced 404; correcting the route fixed transport. A malformed payload produced 400; leaving the app payload template blank restored the receiver's default schema.

Hermes Agent `v0.21.0` is installed on Windows. The user created the isolated `finance-notifications` profile and confirmed it responds with the Luna configuration. `webhook/hermes-worker.js` was added with SQLite claim leases, bounded retries, fixed error codes, quiet stdin invocation, complete-response JSON validation, tool disabling for the dedicated profile and durable `hermes_processing` results. Hermes 0.21 does not support `--format stream-json`; the worker uses `chat --query-file - --quiet` instead.

The worker tests and receiver tests pass together with 27 tests and no failures. On the laptop, `process:once` first exposed the unsupported stream-json flag, then succeeded after the compatibility fix. `process:status` reported two completed receipts, and `process:results` showed both classified as `non_transaction` with no remaining errors. The user then started `npm run process:watch` and confirmed a new synthetic notification was processed successfully.

This establishes the synthetic path: Android capture → ngrok HTTPS → authenticated Node receiver → durable SQLite receipt → Hermes Luna draft classification. It does not establish sensitive-content filtering, expanded-text forwarding, persistent Android event IDs, event-level deduplication, application-encrypted receiver storage, unattended Windows startup, a finance database, or verified banking accuracy. The active ngrok URL and all tokens remain intentionally out of this record.
## 2026-09-19 — Ingestion hardening implementation and review checkpoint

GPT-5.6 Luna implemented the Android and receiver changes in the active checkout; the parent reviewed and corrected the implementation and tests. The user subsequently requested a Markdown summary. This is a progress checkpoint, not final acceptance of every requirement.

Implemented versioned English/Malay sensitive-content rules before listener callback caching, at the Android repository boundary, on legacy queue access, and after authenticated receiver validation. Android and Node exercise the same 81 synthetic fixtures. Accepted notifications preserve short and expanded text separately, carry a persisted UUID-v4 inside the encrypted queue, and serialize schema v2 with one-pass Gson-escaped template substitutions. Legacy decoding explicitly handles absent new fields; migration persists ciphertext and IV before returning an eligible item, and sensitive legacy content is deliberately discarded without counting it as sent.

Room v2 replaces the unique notification-key index with a nonunique index. Repository capture comparison retains changed content and posting times as new immutable events. Parent review removed listener burst suppression that could lose different posting times and framed the content hash to avoid ambiguous field boundaries. Parent also tightened legacy decoding, invalid-ID handling, key-loss handling during rewriting, and explicit null expanded-text serialization.

Receiver migration transactionally adds nullable source/event/hash columns and a unique source/event index. Stable server-configured `WEBHOOK_SOURCE_ID` survives bearer-token rotation. Atomic insert-or-resolve returns one receipt for equal retries, rejects changed payloads with 409, and sanitizes failures. Hermes processing remains keyed by the original receipt. Receiver tests cover real concurrent SQLite connections, response loss, process restart, migration rollback, log privacy and mocked inference with expanded text.

Validation: the final receiver rerun passed all 38 tests. The final combined Gradle unit/debug-build/lint/instrumentation-build command passed in 1m 9s after all source corrections, with 12 JVM tests passing. Instrumentation APK compilation is not device execution. The current instrumentation source is incomplete: its legacy setup omits database version 1 and cannot establish the claimed migration; repository failed-write, concurrent migration, recovery and changed-SENDING integration scenarios still need implementation. Device acceptance and final parent sign-off remain pending. `git diff --check` passed with only Windows line-ending notices.

README, SETUP, PROJECT and Architecture now describe v2 payloads, identity and migration behavior. The coordinated upgrade runbook uses offline/force-stop to preserve the queue: saving forwarding/template settings can clear pending items. Room v2 downgrade is unsupported; prefer a compatible forward fix. No phone installation, deployment, production database changes, live Hermes invocation or commit occurred.

`rules.md` is still missing; existing project guidance was followed. RTK was used from its installed executable because it is absent from PATH. Graphify was installed in an ignored workspace runtime after verifying the skill's package name; static-document semantic extraction and an incremental runner are prepared, but graph refresh and health review are unfinished. Existing graph source paths remain stale. See `tasks/INGESTION_HARDENING_IMPLEMENTATION_REPORT.md` and `tasks/INGESTION_HARDENING_STATUS.md` for the handoff.

## 2026-09-21 — Migration and repository test completion

At the user's request, completed steps 1 and 2 and prepared phone test instructions. Luna drafted the test expansion; parent review corrected fixture timestamps, digest lookup, assertions and missing integration paths. The suite now contains seven tests for actual v1 migration with encrypted preexisting data and metadata, concurrent display/pending migration, UUID persistence across SENDING and database reopen, failed ciphertext rewrites and retry, sensitive legacy deletion without sent increments, immutable changed captures, sensitive capture rejection and invalid existing IDs. Luna's final read-only review found no actionable issue.

Added an opt-in isolated debug application ID using `-PisolatedIngestionTests=true`. Verified generated APK metadata and instrumentation manifest target `com.notificationforwarder.app.ingestiontest`; the runner uses `.ingestiontest.test`. Normal debug/release IDs are unchanged. Temporary Room databases and per-test preferences provide additional isolation. Tests never reset the Keystore key or invoke network delivery. The USB instructions are in `tasks/INGESTION_TEST_STEPS.md`.

Validation: isolated `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (52s; unchanged JVM tests were up-to-date), then final `:app:assembleDebugAndroidTest :app:lintDebug` passed (16s) after all test corrections. The existing AGP/SDK compatibility warning remains. Instrumentation was compiled, not executed; the phone should report seven passing tests before runtime acceptance. `git diff --check` passed. No installation, deployment, production DB changes or commit was performed. Graph refresh and end-to-end phone acceptance remain separate pending work, as requested.

## 2026-09-22 — User-confirmed instrumentation pass

The user reported "all passed" after receiving the seven-test USB instructions. Recorded all seven Android migration/repository instrumentation tests as user-confirmed runtime passes. Raw logs and device details were not provided, so this is not an independently observed run. Steps 1–3 are complete. Updated PROJECT, ingestion status, implementation report and test instructions accordingly.

Remaining work: incremental graph refresh and health review, final overall implementation sign-off, coordinated rollout, and separate end-to-end phone acceptance (notification capture, HTTPS delivery, offline retry, response-loss replay, restart and reboot). Passing the isolated suite does not confirm deployment of the upgraded production app or receiver. This turn changed documentation only; no tests were rerun, no deployment occurred and no commit was made. `git diff --check` passed.

## 2026-09-22 — Luna review, graph refresh and Astra handoff

Completed the source review of Android capture, encrypted queue and Room migration, schema-v2 serialization, receiver authentication/validation/filtering/durable SQLite acceptance, transport deduplication, and Hermes worker leases/retries. No confirmed implementation blocker was found. `npm test` passed 38/38, and the independent synthetic receiver review passed filtering, Unicode/joined-field handling, restart durability, bearer-token rotation, concurrent retries, source isolation, conflict handling, serialization and log privacy. The receiver test named “failed commit” was recorded accurately as a pre-insert SQLite abort, not proof of OS-level commit-fault injection. Device instrumentation remains user-reported because raw output was not supplied.

Refreshed the graph using the prepared ignored `.gradle/graphify-runtime` and current checkout. Regenerated `graphify-out/graph.json`, `GRAPH_REPORT.md`, `graph.html`, `manifest.json`, community analysis and labels. Removed stale screenshot, old-machine absolute-path, generated-memory and pre-rename namespace nodes. The first refresh exposed missing semantic document coverage; subsequent extraction added current-document anchors. Final counts, coverage and diagnostic limitations are recorded in the graph report. Added `tasks/LUNA_INGESTION_REVIEW_REPORT.md` with the requirement matrix, evidence/dispositions, rollout limits and explicit Astra review request. Parent review corrected evidence wording and current/historical status distinctions; no production source fix was needed. Astra owns the next independent repository review; rollout and live phone acceptance remain separate. No production deployment, live Hermes invocation, commit or push occurred.


## 2026-09-24 — Independent ingestion repository sign-off

Completed the requested independent source/test review of Luna’s handoff. Checked capture/filter ordering, encrypted legacy migration and ID persistence, immutable changed captures, Room migration and isolated tests, schema-v2 serialization, receiver transactions/deduplication, and worker leases/retries. No confirmed actionable implementation blocker was found. Added `tasks/ASTRA_INGESTION_REVIEW_REPORT.md` and synchronized current project/status/report records.

Production source, JVM tests and shared fixtures have no diff from `c76efd1`; the existing instrumentation/build changes were reviewed in full and preserved. Reused recorded 38/38 receiver and 12 JVM passes plus prior build/lint results; no unchanged suite was rerun. The user-reported seven device passes remain distinct from independently observed evidence. Baseline graph integrity check found 647 nodes, 1,049 edges, zero missing sources/dangling endpoints and 17 self-loops; extraction limitations remain.

Repository sign-off is complete. Next: coordinated synthetic rollout and live phone acceptance, including offline/replay/restart/reboot and multi-batch recovery; laptop sleep/resume and historical failed Hermes receipts remain operational follow-up. No deployment, live Hermes operation, production data change, commit or push occurred. `rules.md` remains absent. `git diff --check` passed after the review-record edits; graph documentation refresh results and limitations are recorded in `graphify-out/GRAPH_REPORT.md`.

## 2026-09-25 — Synthetic deployment and outage retry-loss handoff

Confirmed this checkout is on the old laptop hosting the receiver; its scheduled task points here. With services stopped, verified matching SQLite, WAL and SHM backup bytes. Rebuilt the main debug APK with `-PisolatedIngestionTests=false`. Replacement failed because the signing key differed; the user authorized discarding old app data and uninstalled the original. Installed `com.notificationforwarder.app` on the S23 Ultra and restored configuration and notification access. This was a fresh install, not live proof of in-place migration.

Started receiver services. Corrected the phone URL from the tunnel root to `/webhook` after observing 404; subsequently observed 200 responses. Enabled forwarding after finding it disabled. User confirmed normal Discord forwarding and OTP rejection. Database: `webhook/data/notifications.sqlite`; tunnel inspector: `http://127.0.0.1:4040/inspect/http`. Credentials and the active tunnel hostname are omitted. Keep local backups out of commits.

During outage testing the user reported repeated 503s, with all but the oldest queued item eventually entering Failed. The server was restored only after those failures. A manual Sync Queue attempt succeeded; later the user explicitly confirmed surviving pending content delivered automatically shortly after recovery. This proves automatic retry for surviving content, not full backlog recovery. Code treats 503 as transient but deletes content at retry exhaustion; Failed is a counter rather than a recoverable queue. Exact per-item attempts, the oldest-item difference and 503 origin remain unverified. Inspected ngrok history contained two 200 responses and no retained 503 evidence. Stop Forwarder also stops ngrok, so it does not isolate receiver downtime.

Validation during deployment: main debug build and installation succeeded; webhook `npm test` passed 38/38. Existing AGP/SDK warning remains. Latest Hermes status was 33 completed and 4 failed, without per-event attribution; backend processing failures are distinct from phone queue failures. No retry-policy source change was made.

Next session: reproduce a controlled multi-item outage, inspect sanitized retry metadata and scheduling, investigate oldest-item survival, and address transient-outage loss with explicit retention behavior. Preserve encryption, filtering and persistent event IDs. Verify automatic drain beyond one batch without manual sync. Replay/restart/phone reboot, laptop sleep/resume, expanded-text live evidence and remaining device acceptance are still pending. Detailed handoff is in `tasks/INGESTION_HARDENING_STATUS.md`.

Final handoff updates PROJECT, ingestion status and this log only. Existing unrelated changes are preserved; no commit or push. `rules.md` remains absent. Incremental graph refresh remains due before a future commit; the existing graph does not reflect this documentation update.

## 2026-09-26 — Retain transient failures and continue queued batches

Continued the outage handoff using Channeling, Graphify source relationships and
RTK searches. `rules.md` remains absent. Source confirms retry exhaustion deleted
transient failures and successful batches did not schedule a successor. The
reported oldest-item survival and original 503 source remain unproven.

Transient failures now retain encrypted rows until expiry; permanent failures
still delete and increment Failed. The persisted maxRetries setting now caps
backoff growth, reflected in the UI label and supporting text. Delay remains
exponential with jitter, capped at 32 minutes; attempt counts saturate safely.
The worker persists a successor at the earliest pending due time before returning
success, including future-due rows and remaining batches. Periodic recovery uses
KEEP when outside the one-time chain to avoid redundant continuations. Policy
coordination, filtering, encryption, immutable IDs and intentional deletion remain.
Created Design.md to record the existing Material interface and retry semantics.

Added two JVM retry-policy tests and two isolated repository regressions for
five-event outages beyond the former attempt limit, ciphertext preservation,
database reopen, three-batch repository drain, permanent failure and expiry.
The repository test advances eligibility explicitly; it is not automatic worker
or live HTTP evidence. Added a separate synthetic outage receiver on port 3301
with a temporary database and console-controlled failure switch. Its real HTTP
test exercised twenty 503 responses, recovery to five distinct receipts and
duplicate replays. The first test assertion incorrectly expected 401 for a wrong
bearer token; current authentication returns 403, and the assertion was corrected.

Validation: final combined isolated Gradle unit/build/instrumentation-build/lint
run passed in 25 seconds after the scheduler adjustment; XML reports show 14 JVM
tests with zero failures/errors. Instrumentation compiled (nine tests) but was
not executed because ADB reports no connected device. Targeted Node outage test
passed. Existing receiver/worker production code is unchanged, so prior 38-test
evidence is retained rather than rerunning unchanged tests. The existing AGP 8.5.2
warning for SDK 36 remains. Gradle required approved access to its existing shared
cache after the sandbox denied its lock file; native Windows RTK commands worked.

No live receiver/database change, installation, uninstall, commit or push occurred.
Updated architecture, README, project status, continuation and test instructions.
Next: execute the isolated suite, then deploy and observe automatic multi-batch
recovery on the phone using the separate outage receiver. Screen-off/restart/reboot
acceptance remains pending. Existing user changes are preserved. Graph refresh is
still due before a future commit; the current graph has historical extraction
limitations and does not include this checkpoint.

## 2026-09-26 — Move task Markdown into `tasks/`

Moved the task-specific implementation reports, review reports, status handoff,
test instructions and namespace plan into `tasks/`. Kept the project brief,
README, setup guide, security audit and project records at the repository root.
Updated project and architecture references, historical diary references and the
security report's relative link. Refreshed the incremental graph to 735 nodes
and 1,138 edges; extraction has no semantic nodes for the eight moved task files
or the existing `Design.md`, and the report retains the documented graphifier
diagnostic limitations. Validation: checked task-document links and repository
references, and ran `git diff --check`; no source or runtime behavior changed.
`rules.md` remains absent.

## 2026-09-26 — Dismissible feedback without a snackbar backlog

Replaced independently queued snackbar calls with one conflated message channel
and collectLatest presentation. A newer result cancels only the earlier snackbar,
not its save/network operation. All messages use the Material close action,
short accessibility-aware timeout and horizontal swipe dismissal in either
direction. Each message gets fresh swipe state. Kept the existing theme and
message text; updated Design.md and the device checklist. No dependency added.

Validation: main debug build and lint passed (existing AGP/SDK warning remains).
All nine isolated queue instrumentation tests passed on SM-S918B / Android 16,
with XML confirming nine tests, zero failures and zero errors. Gradle removed its
test packages afterward; installed only the isolated debug app for UI checks.
After the user left the phone idle, checked Webhook Save validation feedback:
Dismiss action, left/right swipes, and ten rapid taps with no remaining backlog
after five seconds. The secure-window flag correctly blocks screenshots, so UI
verification used hierarchy snapshots; no protection was disabled. Large-font,
TalkBack and successful-save copy checks remain unobserved. Returned the phone
to its home screen after the check.

Preserved the built main APK at app/build/outputs/apk/main-debug/app-debug.apk
before rebuilding the normal debug output as the isolated test app. The main
forwarder, its settings/data and receiver remain unchanged. No commit or push.
Existing task-document moves and retry changes were preserved. `git diff --check`
passed. Graph refresh remains due before a future commit.

## 2026-09-26 — Offline ngrok response classification and installed follow-up

The user reported immediate failures with laptop network loss/sleep. Confirmed
the previous patch was installed by matching APK hashes. Metadata-only inspection
found no queue rows, 26 Sent, 12 Failed and 0 Expired; retention was 24 hours and
forwarding enabled. Original terminal responses were unavailable. Reproduced
HTTP 404 / ngrok-error-code ERR_NGROK_3200 via a credential-free GET to an unused
ngrok hostname. Official ngrok documentation identifies 3200 as endpoint offline
and reserves the error header. This exposed a confirmed classifier gap matching
the reported outage type, without proving each historical deleted item's cause.

Made only this status/header combination retryable, preserving other permanent
responses. Added three JVM regressions exercising actual async response handling
and failure-only QueueDelivery metadata logging (no content, URL or credentials).
Final 17 JVM tests, main build and lint passed in 21 seconds; the existing SDK/AGP
warning remains. Installed over the main app with connectivity briefly paused;
verified settings and queue DB/WAL bytes unchanged and installed APK hash
1141e6d3b464fa1c1cccfb4b46804cc54ffc001b7e9e5cee5bd216af05dbab35.
Restored network state and opened the updated app. No live receiver changes,
notification sends, uninstall, data clear, commit or push. The user requested
no further agent tests; they will perform live outage/recovery acceptance.
Graph refresh remains due before a future commit.
