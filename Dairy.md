# Dairy

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
