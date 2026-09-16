# Hermes Finance Notification Bridge

Status: updated 2026-09-16. Discord notification delivery is user-confirmed; the local durable receiver is implemented and reviewed. Windows laptop deployment and Hermes processing remain pending.

## Goal and scope

Use this NotificationForwarder fork as the Android transport for a personal expense-tracking system. The initial device is a Samsung Galaxy S23 Ultra, with MAE / Maybank and Touch 'n Go eWallet as notification sources.

Reuse the existing listener, settings UI, encrypted queue and HTTPS client. Keep transaction detection, parsing, categorisation and finance storage on Hermes. Samsung Routines, Accessibility Services and Telegram are unnecessary for the core path.

The immediate milestone is a notification reaching Hermes over authenticated HTTPS. Reliable financial ingestion follows once Hermes can durably save and deduplicate events. A successful webhook test alone does not establish that a transaction was recorded.

```text
MAE / TNG notification
    → Android package allowlist and sensitive-content filtering
    → encrypted local queue
    → authenticated HTTPS POST
    → Hermes validation and durable event storage
    → asynchronous transaction detection and model parsing
    → finance database and review workflow
```

The model named in the original plan is GPT-5.6 Luna. Treat this as a backend configuration preference; confirm the actual callable model identifier and provider when implementing Hermes. No model credentials or finance logic belong in the Android app.

## What this project already provides

Paths below are relative to `app/src/main/java/com/notificationforwarder/app/` unless stated otherwise.

| Area | Existing implementation | How to use it |
| --- | --- | --- |
| Capture | `service/AppNotificationListenerService.kt` | Receives notification callbacks, checks package allowlist before reading text, skips group summaries and suppresses some repeated callbacks in memory. |
| Settings | `settings/SettingsStore.kt`, `MainActivity.kt` | Configure forwarding, allowed packages, HTTPS destination, bearer token, payload, retention and retries through the existing UI. |
| Local persistence | `data/NotificationRepository.kt`, `QueueItem.kt`, `QueueDao.kt`, `AppDatabase.kt`, `QueueCrypto.kt` | Reuse Room and Keystore-backed encrypted payload storage. Keep policy checks, expiry and terminal deletion intact. |
| Delivery | `network/WebhookClient.kt` | Reuse JSON POST, bearer authentication, HTTPS enforcement, redirect rejection and sanitized errors. |
| Retry and recovery | `worker/QueueWorker.kt`, `WorkerScheduler.kt`, `DeliveryCoordinator.kt`, `receiver/BootCompletedReceiver.kt` | Reuse network-constrained work, backoff, periodic recovery, interrupted-send recovery and delivery cancellation. |
| Receiver | Repository root `webhook/server.js`, `webhook/storage.js` | Authenticates and validates requests, commits accepted payloads to local SQLite before acknowledging them, and emits receipt-only logs. It remains synthetic-test storage without processing or deduplication. |
| Security evidence | Repository root `SECURITY_AUDIT.md`, `HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md` | Preserve implemented hardening and complete the documented runtime checks before relying on the app for banking notifications. |

The existing graph is useful for navigation but predates security changes. Current source takes precedence over graph descriptions and older screenshots.

## Important gaps

1. **Content filtering is not implemented.** Package allowlisting also admits login, promotion, OTP/TAC and approval notifications from those apps. Add source-specific sensitive-content rejection before persistence or transmission. Build rules from observed, redacted formats; an app allowlist alone is insufficient.
2. **Expanded text is not forwarded.** The listener reads `EXTRA_BIG_TEXT` for duplicate detection but only queues `EXTRA_TEXT`. Preserve expanded text when it contains the payment details.
3. **There is no persistent event ID.** `notificationKey` identifies an Android notification, which can be updated. It is not a unique financial transaction identifier. In-memory callback suppression is not durable ingestion deduplication.
4. **The receiver does not process or deduplicate events.** It now retains accepted events locally, but it has no Hermes processing state, application encryption or persistent Android event ID. Use it only for synthetic transport checks until the Hermes ingestion contract exists.
5. **Delivery has bounded retention and retries.** Default retention is 24 hours; expiry, permanent failure and exhausted retries delete content. This is not a lossless financial ledger.
6. **Android runtime validation remains outstanding in the implementation report.** The audit and source fixes are not proof of a working release on the S23 Ultra.

## First milestone: configure and prove transport

### 1. Build and install the fork

Use the repository Gradle wrapper with a compatible JDK and Android SDK. The project compiles against SDK 36 and targets JVM 17. The earlier missing-Java blocker was superseded by the environment switch: Android Studio's Java 21.0.6 and SDK 36 were found. The user reported completing setup and installing the app; build/lint output was not captured for independent review.

```powershell
.\gradlew.bat assembleDebug
```

The debug APK should be produced at `app/build/outputs/apk/debug/app-debug.apk`. Use synthetic notifications for initial testing. For ongoing use, create a personally signed release and retain its signing key for future updates.

### 2. Prepare a synthetic receiver on Hermes

From `webhook/`, install dependencies using `npm ci`, copy `.env.example` to `.env`, replace the example bearer token with a generated secret and configure:

```dotenv
HOST=127.0.0.1
PORT=3000
WEBHOOK_PATH=/api/v1/finance/notification
WEBHOOK_BEARER_TOKEN=<generated-secret>
JSON_LIMIT=1mb
DATABASE_PATH=./data/notifications.sqlite
```

Run `npm run start` from that directory. Put the receiver behind an HTTPS reverse proxy with a certificate trusted by the phone; keep the Node port private and disable request-content logging. Use the exact final endpoint because the app rejects redirects.

The hostname and private-network access mechanism remain deployment decisions. If a private network such as Tailscale is chosen, verify connectivity from the phone on mobile data as well as home Wi-Fi. Preserve HTTPS and bearer authentication. Stop the receiver before copying its SQLite database between machines.

### 3. Configure the existing Android UI

| Setting | Value |
| --- | --- |
| Webhook URL | `https://<hermes-host>/api/v1/finance/notification` |
| HTTP method | `POST` |
| Authentication | Bearer, with the receiver's token |
| Query parameters | Empty |
| Custom headers | Empty; the app already supplies JSON content type and bearer auth |
| Payload template | Use the template below |
| Allowlist | A synthetic test source first; later the verified installed MAE and TNG package IDs |
| Retry retention | Start with the existing 24-hour default |

Grant Notification Access through Home and use Open Battery Settings to allow background operation where available. Verify the actual Samsung settings on the device. Record the exact installed package IDs rather than guessing them from app names.

Save the destination and allowlist before enabling forwarding. Changing destination, authentication, template or allowlist disables forwarding and clears pending content; finish configuration with an empty queue, then re-enable deliberately.

This template works with the current placeholders and omits Android device ID and raw notification key:

```json
{
	"schemaVersion": 1,
	"packageName": "{packageName}",
	"appName": "{appName}",
	"title": "{title}",
	"text": "{text}",
	"postedAt": {postedAt}
}
```

`postedAt` is a JSON number containing Unix epoch milliseconds, not an ISO timestamp. Keep the field unquoted. The current default payload also sends `deviceId` and `notificationKey`; the custom template deliberately selects the required fields.

### 4. Verify the entire capture path

Use the webhook test action for connectivity, then generate a synthetic notification from the allowlisted test app to exercise capture, filtering, queueing and delivery. Verify a receiver receipt and the phone's sent counter. Confirm an unlisted app produces no queued or received event.

The receiver stores synthetic payloads locally, so correlate the response receipt ID with the SQLite row during testing. Do not turn raw banking-payload logging on to debug this step.

## Second milestone: make real ingestion dependable

### Minimal Android changes

| Change | Files to start with | Intended behavior |
| --- | --- | --- |
| Preserve expanded text | `service/AppNotificationListenerService.kt`, `data/NotificationPayload.kt`, `data/NotificationRepository.kt` | Retain `text` and an optional `bigText` in the encrypted payload. Apply sensitive-content checks to both. |
| Reject sensitive notification types | Listener plus a small, dedicated filter helper | Apply package-specific rules before queue insertion; cover OTP/TAC, login and Secure2u approval prompts with redacted fixtures. Keep semantic transaction classification on Hermes. |
| Persist event identity | Payload, repository and `network/WebhookClient.kt` | Generate a UUID once per accepted capture, store it inside the encrypted payload and expose `{eventId}`. Reuse it for every delivery retry. |
| Extend serialization | `network/WebhookClient.kt` | Expose optional expanded text and event ID. Ensure quotes, newlines, Unicode and placeholder-like input survive unchanged. Prefer structured JSON serialization if tests expose template substitution problems. |

Define compatibility for existing encrypted payloads before changing their schema: tolerate missing new fields and assign any legacy event ID once with persistence, or perform an explicitly documented queue reset. Never generate an event ID on every send attempt.

Proposed production payload after these changes:

```json
{
	"schemaVersion": 2,
	"eventId": "4a4a2f3c-929b-4988-896c-790733d68237",
	"packageName": "com.example.bank",
	"appName": "MAE",
	"title": "Payment Successful",
	"text": "RM18.90 paid to ABC Kopitiam",
	"bigText": null,
	"postedAt": 1789441200000
}
```

The package and content above are illustrative. This schema and its new placeholders are proposed, not supported by the current app. Associate the phone with its bearer credential on Hermes instead of requiring Android ID in the body.

### Hermes ingestion contract

Extend the existing receiver or implement the same contract in Hermes's backend. The backend framework and finance database remain undecided; there is no need to build the finance schema to persist an incoming event durably.

1. Authenticate before parsing; validate schema, allowed source package, field types and lengths.
2. Atomically insert the raw event into durable storage with a unique constraint on authenticated source plus `eventId`.
3. Return `200` or `202` only after the event is durable. Return a successful response for an already stored event as well.
4. Process saved events asynchronously. Model latency or failure must not hold up the phone's acknowledgement.
5. Retain a processing state so a backend restart can resume unprocessed events.

| Response or failure | Current phone behavior | Receiver requirement |
| --- | --- | --- |
| Any `2xx` | Marks sent and deletes local content | Acknowledge only durable acceptance, including duplicate event IDs. |
| Network error, `5xx`, `429` | Retries within configured limits | Use for temporary unavailability; do not acknowledge failed persistence. |
| Other `4xx`, including `408` and `409` | Permanent failure; deletes local content | Do not return `409` for an ordinary duplicate. Treat validation/authentication failures as potentially lost events requiring attention. |
| `3xx` | Permanent redirect rejection | Configure the phone with the final URL. |

A UUID prevents duplicate inserts when the same queued item is retried. A repeated Android callback may still create a different event ID. Hermes must separately detect likely notification updates and transaction duplicates using source, timing, available provider references and content. Do not collapse events solely because merchant and amount match: two legitimate payments can be identical.

## Reliability and device acceptance

WorkManager schedules immediate network-constrained work and a periodic fallback requested every 15 minutes. These are scheduling requests, not delivery deadlines. Verify background behavior on the actual phone.

Before regular banking use, demonstrate:

- Synthetic content survives capture and JSON serialization, including expanded text, quotes and line breaks.
- Unlisted apps and sensitive test fixtures never enter the queue or receiver.
- Offline events remain encrypted and deliver after reconnection within retention and retry limits.
- A Hermes outage followed by recovery drains the queue, including more than one configured batch, without requiring a new notification.
- A lost response after durable acceptance produces one stored event when the phone retries.
- A process restart and phone reboot recover pending work; separately test force-stop behavior and reopening the app without assuming missed notifications will replay.
- Expiry, exhausted retries, invalid authentication and endpoint changes produce the documented deletion behavior and visible safe counters.
- Screen-off, overnight idle and Wi-Fi/mobile-data transitions work on the S23 Ultra.
- The security report's HTTPS, redirect, Keystore, backup, upgrade and cancellation checks pass on a built APK.

Default retention intentionally trades recovery time for shorter local storage. Do not switch it off merely to claim reliability: Off removes time-based expiry but does not remove retry exhaustion or other deletion paths. Record failures and reconcile against account records; notifications are inputs to an expense tracker, not an authoritative bank statement.

## Later finance processing

Once transport and durable ingestion pass acceptance:

1. Separate payment events from promotions and other non-transactions on Hermes.
2. Parse amount, currency, merchant, direction and category into a validated structured result. Treat notification text as untrusted data, never model instructions.
3. Preserve the original event reference, parser/model version and processing outcome.
4. Send ambiguous results to review; do not silently invent amounts or transaction types.
5. Add transaction storage, corrections, categorisation and analytics.
6. Optionally add Telegram confirmations or correction prompts outside the ingestion path.

Choose model provider, credential handling, raw-event retention and database design at that stage. Keep these decisions outside the Android build.

## Repository strategy and current status

The checkout currently has `origin` pointing to `https://github.com/YipKean/NotificationForwarder.git`. No `upstream` remote is configured. When implementing, add `https://github.com/ItsAzni/NotificationForwarder.git` as upstream if needed and use a focused branch such as `codex/hermes-bridge`. Keep changes small enough to review and maintain against upstream.

| Item | Status |
| --- | --- |
| Android source, initial apps and direct HTTPS architecture | Selected in the project brief |
| Personal fork | Present and configured as origin |
| Security audit and high-severity fixes | Documented; runtime verification still outstanding |
| Existing capture, encrypted queue and HTTPS transport | Present in source |
| Phone installation and Discord notification delivery | User-confirmed on 2026-09-16; Instagram configured as test source |
| Local durable receiver | Implemented; 17 automated tests pass and parent review found no blocking issues for synthetic testing |
| Synthetic phone-to-Hermes verification | Next milestone; not established by this document |
| Sensitive-content filters, expanded-text payload and persistent event IDs | Planned changes |
| Durable Hermes ingestion and idempotency | Planned |
| Model parsing, finance database and analytics | Deferred |

The Discord result establishes basic delivery to Discord from the installed app. It does not establish local SQLite receipt, Hermes processing, or the full background/recovery acceptance checklist. No receiver deployment, commit or push has been performed in this work.

## Windows laptop handoff

The user selected their old Windows laptop, where Hermes is already installed, as the eventual receiver host. Inspection of the local sibling `poly-agent` project's `project.md` and `SETUP.md` confirmed its native Windows Hermes Desktop integration, a documented Hermes 0.21.0 runtime, and the CLI path `%LOCALAPPDATA%\hermes\bin\hermes.exe`. This is evidence about that project's configuration; the old laptop's actual installation and version still need verification.

Continue in this order:

1. Transfer the reviewed receiver source and lockfile to the laptop. These changes are currently uncommitted; cloning the existing remote alone will not include them. Keep secrets and databases out of Git. Install Node 24.13.0 or a later Node 24 patch, run `npm ci` and `npm test` from `webhook/`, create `.env` without overwriting existing settings, and configure the local bearer token and database path. See [SETUP.md](SETUP.md#5-receiver-requirements).
2. Start the receiver on localhost and send a synthetic request. Correlate its receipt ID with the SQLite row, restart the receiver and confirm persistence. Stop the receiver before copying an existing database. Laptop execution remains unverified until these checks pass there.
3. Choose and configure phone-reachable HTTPS access with a trusted certificate and no redirects. Hostname and access method are still undecided. Keep the Node listener private, preserve bearer authentication and disable proxy access logging.
4. Change the phone from Discord to the receiver's final HTTPS URL, select POST and Bearer, and clear the Discord query parameters and payload template. Save the settings, test the webhook, then enable forwarding and save again. Keep `com.instagram.android` as the test source. Confirm both the webhook test and a fresh notification are stored in SQLite; test Wi-Fi and mobile-data reachability.
5. After transport passes, prepare a separate proposed `finance-notifications` Hermes profile and processing task. Do not repurpose poly-agent's `lol-control` or `lol-memory` profiles. Hermes processing has not been implemented or configured for this project.

Before banking use, implement sensitive-content rejection, expanded notification text, persistent Android event IDs and receiver retry deduplication. Storage protection and retention also need decisions before retaining real financial notifications. The current receiver is synthetic-test storage, with no application encryption, automatic deletion or processing worker.

## Local references

- [Setup and supported configuration](README.md)
- [Security audit](SECURITY_AUDIT.md)
- [Implemented fixes and remaining validation](HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md)
- [Android build configuration](app/build.gradle.kts)
- [Notification listener](app/src/main/java/com/notificationforwarder/app/service/AppNotificationListenerService.kt)
- [Webhook request implementation](app/src/main/java/com/notificationforwarder/app/network/WebhookClient.kt)
- [Delivery worker](app/src/main/java/com/notificationforwarder/app/worker/QueueWorker.kt)
- [Synthetic receiver](webhook/server.js)
