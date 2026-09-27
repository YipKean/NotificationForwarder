# Architecture

Updated 2026-09-28. This repository owns Android transport and the notification receiver. The independent general-data-dashboard project owns browser presentation and deployment. No dashboard checkout is required here.

## Boundaries

The Android application captures allowlisted notifications, stores queued payloads in its encrypted Room database, and delivers them through the configured HTTPS client. Hermes owns later validation, processing, deduplication and finance storage.

The `webhook/` directory is a separate Node.js synthetic receiver. `server.js` owns configuration, HTTP authentication, request validation and the HTTP lifecycle. `storage.js` owns the local SQLite connection, schema initialization and parameterized event inserts. The receiver stores validated payloads and exposes authenticated read routes through readRoutes.js. Processing, application encryption and retry deduplication remain unimplemented.

## Data flow

```text
Android notification listener
    -> encrypted Android queue
    -> authenticated HTTP request
    -> webhook/server.js validation
    -> webhook/storage.js SQLite commit
    -> success response with receipt ID
```

The receiver writes the response only after SQLite accepts the event. Each request receives a generated UUID and creates one `notification_events` row. The Android app currently has no persistent event ID, so repeated delivery can create duplicate rows.

## Runtime and configuration

The receiver uses Express, dotenv and Node's built-in `node:sqlite` module. It loads `webhook/.env` only when the standalone `start()` path needs environment configuration; tests pass explicit configuration and temporary databases. Relative `DATABASE_PATH` values resolve from `webhook/`. The default listener is `127.0.0.1:3000`; phone access requires a later HTTPS reverse proxy.

Local `.env` variants and SQLite database files are ignored by `webhook/.gitignore`; `.env.example` remains tracked. Do not commit credentials or synthetic event databases.

## Repository ownership

- `app/`: Android capture, encrypted queue, settings and delivery.
- `gradle/` and root Gradle files: Android builds.
- `webhook/`: notification ingestion, SQLite, schema migrations, read authorization, API tests and standalone receiver image.
- `.github/workflows/`: independent Android and receiver checks.
- `screenshots/`: Android reference images.
- `graphify-out/`: derived navigation, currently stale; source takes precedence. Local caches are ignored.
- Root Markdown: scope, architecture, setup, security evidence and work history.

## External dashboard boundary

The sibling general-data-dashboard project builds, tests and deploys independently. It connects to a configured HTTP(S) API origin; neither project imports the other's source or accesses the other's storage. Its current notification feature is one client of this receiver. Other data sources belong behind their own dashboard feature adapters.

Receiver-owned GET routes are `/api/notifications`, `/api/notifications/:receiptId` and `/api/notification-apps`. Lists accept bounded `limit`, optional `packageName` and opaque `before`, and return `items` plus `nextCursor`. Details return full text. Read responses disable caching. Receipt IDs identify stored arrivals, not financial transactions.

Optional DASHBOARD_BEARER_TOKEN authorizes reads and must differ from WEBHOOK_BEARER_TOKEN. Missing read configuration disables reads. Do not configure the ingestion token in the dashboard. SQL, input validation and authorization stay in this repository.

Storage migrates legacy unversioned databases to user_version 1 with a transactional arrival index; unknown future versions are rejected. The receiver has no automatic retention or processing worker. Continue using synthetic data until banking-readiness requirements are resolved.

## Deployment and verification

The receiver Docker image runs as a non-root user with DATABASE_PATH=/data/notifications.sqlite. Its operator owns the persistent volume, tokens, HTTPS proxy and listener exposure. Deploy it separately from the dashboard; dashboard restarts must not manage receiver lifecycle.

Run `npm ci --prefix webhook`, `npm test --prefix webhook`, and `git diff --check`. Receiver CI also builds its Docker image. Existing Android CI remains unchanged. Coordinate contract checks with clients when changing the HTTP API.

Work only in the active checkout. Preserve local secrets, databases and Windows tools during source cleanup. Existing tracked graph caches remain historical; new caches are ignored. Refresh the stale graph before a requested commit when graphify is available. PROJECT.md owns delivery scope and Dairy.md records validation and decisions.
