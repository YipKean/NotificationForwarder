# Architecture

Status: current Android/receiver boundaries plus the proposed Next.js architecture, updated 2026-09-27. The dashboard and Docker deployment are not implemented. This document owns structural decisions; `DASHBOARD_PLAN.md` owns the first dashboard milestone and `Design.md` owns visual/interaction rules.

## Boundaries

The Android application captures allowlisted notifications, stores queued payloads in its encrypted Room database, and delivers them through the configured HTTPS client. Hermes owns later validation, processing, deduplication and finance storage.

The `webhook/` directory is a separate Node.js synthetic receiver. `server.js` owns configuration, HTTP authentication, request validation and the HTTP lifecycle. `storage.js` owns the local SQLite connection, schema initialization and parameterized event inserts. The receiver stores raw validated payloads and has no processing worker, public read route, application encryption or deduplication.

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

## Proposed receiver dashboard

`DASHBOARD_PLAN.md` proposes a read-only Next.js App Router dashboard under `dashboard/`, deployed with the existing Express receiver as two Docker Compose services. Next.js Route Handlers proxy bounded authenticated reads to the receiver over the Compose network. Only the receiver mounts the persistent SQLite volume and validates the separate dashboard credential; the browser keeps that credential in memory. The dashboard uses standalone output and a multi-stage non-root image. Host ports bind to loopback behind the eventual HTTPS proxy. These services, read routes and deployment files are planned, not implemented. The Android transport and Hermes processing boundary stay as described above. `Design.md` records the proposed browser interface.

The active planning branch is `codex/notifications-dashboard`, created from `notification-receiver` in the existing checkout. No additional worktree is used.

## Repository ownership

Keep three clear responsibilities in one repository. Use independent package manifests and lockfiles for `webhook/` and `dashboard/`; a workspace manager or shared package is unnecessary at this size.

| Path | Owner and responsibility | Status |
| --- | --- | --- |
| `app/` | Android capture, encrypted queue, settings and delivery | Existing |
| `gradle/`, root Gradle files and wrappers | Android build tooling | Existing |
| `webhook/` | Ingestion, authentication, notification storage and notification read API | Existing; read API planned |
| `dashboard/` | Next.js presentation and same-origin HTTP adapters | Planned |
| `compose.yaml` | Service wiring, runtime configuration, volumes and local port bindings | Planned |
| `.github/workflows/` | Existing Android build; add separate web validation workflow | Existing; web checks planned |
| `screenshots/` | Product reference images | Existing |
| `graphify-out/` | Derived architecture navigation; source takes precedence | Existing, stale |
| Root Markdown records | Product scope, architecture, design, setup, security evidence and work history | Existing |

Generated `.gradle/`, build outputs, `node_modules/`, `.next/`, coverage, test reports and local caches are not source. Keep environment secrets and SQLite files/sidecars ignored and excluded from Docker build contexts. Add dashboard-specific ignores when scaffolding it. Hermes processing is a future external integration, not an empty folder to scaffold now.

## Next.js directory plan

Create files as their functionality is implemented; the tree is an ownership map, not a requirement for empty scaffolding. Framework filenames retain Next.js conventions. Components use headed camel case; functions and ordinary modules use headless camel case.

```text
dashboard/
  package.json                 # scripts and dashboard dependencies only
  package-lock.json
  next.config.ts               # standalone build and framework configuration
  tsconfig.json                # strict mode; @/* maps to src/*
  eslint.config.mjs            # quality and import-boundary checks
  Dockerfile
  .dockerignore
  .env.example                 # server configuration names; no secrets
  public/                      # public assets only
  src/
    app/
      layout.tsx               # document and shared shell; Server Component
      page.tsx                 # compose the notification feature at /
      loading.tsx              # route loading fallback
      error.tsx                # route error boundary; no raw error disclosure
      not-found.tsx
      globals.css              # reset, tokens and base element rules only
      api/
        notifications/
          route.ts             # list request adapter
          [receiptId]/route.ts # detail request adapter
        notification-apps/
          route.ts             # app-source request adapter
        health/route.ts         # dashboard process liveness only
    features/
      notifications/
        NotificationInbox.tsx  # client entry and feature composition
        NotificationRow.tsx
        NotificationDetails.tsx
        notifications.module.css
        useNotifications.ts    # request lifecycle and feature state
        notificationsClient.ts # calls same-origin read endpoints
        contracts.ts           # transport types and runtime response checks
        formatNotification.ts  # pure feature formatting
        server/
          notificationsReader.ts # server-only receiver read operations
        *.test.ts              # colocated behavior/contract tests
    components/
      ui/                      # small, domain-independent reused controls
    server/
      env.ts                   # validated server-only runtime configuration
      receiverClient.ts        # fixed origin, timeout, headers, safe errors
    lib/                       # small, pure helpers used by multiple features
  tests/
    e2e/                       # cross-boundary browser flows
```

Keep the initial token field and lock interaction inside the notifications feature. If another feature needs authenticated access, extract a small access provider at the nearest common layout then; do not introduce a global auth framework in anticipation. The initial `components/ui/` and `lib/` folders may stay absent until code is genuinely reused.

## Dependency rules

| Importing area | Allowed dependencies | Forbidden dependencies |
| --- | --- | --- |
| `app/` pages/layouts | Feature entry components and shared presentation | SQL, receiver transport internals, business processing |
| `app/api/` Route Handlers | Feature `server/` operations and transport contracts | UI components, browser hooks, direct SQLite |
| Feature client components/hooks | Own feature client modules/contracts; shared UI/pure helpers | Any `server/` module, environment secrets, another feature's internals |
| Feature `server/` modules | Own contracts, shared server transport and pure helpers | Browser modules or UI |
| `components/ui/`, `lib/` | Other appropriate shared/pure modules | Feature modules, `app/`, server transport |
| `server/` | Server configuration and focused transport helpers | Feature components, browser code, route files |

`app/` composes features; features never import `app/`. Features do not directly import each other. If a page combines notifications and a future transactions feature, compose their entry components at the route level and pass explicit data/callbacks. Move a shared concept out only after its ownership and multiple real consumers are clear.

Use direct module imports. Avoid catch-all `index.ts` barrels, generic repositories, dependency-injection containers and a universal service layer. Separate a helper when it has a distinct responsibility, not to achieve a target file count. Do not import implementation files across `dashboard/` and `webhook/`; HTTP is their integration boundary.

## Server and browser boundaries

Pages/layouts stay Server Components by default. Mark the interactive inbox entry with `"use client"`; do not turn the root layout into a Client Component just to support the inbox. Mark `src/server/*` and feature `server/*` entry modules with `import "server-only"` so accidental client imports fail during build.

The initial credential is entered in the browser and kept only in feature memory. Therefore notification data is fetched after unlock through the same-origin Route Handlers, not during Server Component rendering. The server-rendered page contains only the shell and locked state. Never pass the token in URLs, serialized page props, HTML, persistent browser storage or global server variables.

Route Handlers translate HTTP inputs and outputs and call named feature server operations. `receiverClient.ts` owns the fixed runtime `RECEIVER_URL`, timeout, no-store fetching and sanitized upstream failures. It never accepts a browser-selected destination or acts as an arbitrary proxy. It forwards only the read token and expected request fields; it does not substitute a privileged server credential. Disable upstream redirects so credentials cannot follow a redirect to another host.

Express authenticates every read, validates inputs authoritatively and owns SQL. Next.js performs inexpensive input checks before forwarding and validates successful upstream response shapes. A hidden UI control or a Next.js layout is never an authorization boundary.

## Data and contract ownership

```text
Phone -> authenticated Express ingestion -> SQLite commit -> receipt response
Browser -> Next.js read Route Handler -> Express authenticated read -> SQLite
                                      <- bounded response projection <-
Future Hermes processor -> explicitly designed durable processing contract
```

Notification contracts expose only list/detail/source fields required by the UI. The receiver owns the wire contract; dashboard `contracts.ts` mirrors it with runtime checks. TypeScript declarations alone do not validate network data. Keep focused contract fixtures/tests on both sides until a real third consumer justifies shared schema tooling. Do not create a shared package merely to remove a few duplicated field names.

List responses contain summaries plus an opaque `nextCursor`; details contain full text. Validate lengths and types, cap page size and reject malformed cursors. Unknown upstream errors become a safe response; map connection failure to 503, timeout to 504 and malformed upstream responses to 502. Keep expected 400/401/403/404 statuses intact with safe messages. Expose a stable machine-readable error code; do not make clients parse message text.

Keep current ingestion payload schema compatibility. Additive read fields may evolve without breaking the client; renamed/removed fields or changed meanings require a documented version transition and coordinated contract tests. Receipt IDs identify stored receipts, not financial transactions. Android retry deduplication and transaction matching remain separate planned concerns.

SQLite access and migrations belong only to `webhook/`. Before the first dashboard schema/index change, add a small transactional schema-version migration path with forward-only migrations, test upgrades from the current unversioned database, and verify backup/restore. Do not let the dashboard run migrations. Existing `storage.js` can remain one focused module; split it by storage responsibility only when it grows beyond clear maintenance.

## State, styling and feature growth

Keep row expansion, loading, selected app, cursor and last successful refresh local to the notifications feature. Use React state and one focused request hook initially. Abort superseded fetches and guard against stale completions, including lock/unmount. Clear every retained list/detail value on lock or authentication failure. Do not add a global state store or query-cache library before multiple features demonstrate a need.

Keep notification content and credentials out of URLs. If shareable navigation becomes necessary, put only deliberately non-sensitive navigation parameters in the URL and document the logging implications. Polling, persisted caches and background synchronization are separate requirements, not default behavior.

Use CSS Modules beside feature components. `globals.css` contains design tokens and base rules, not page-specific selectors. Shared UI stays free of notification fetching, credentials and domain-specific status meanings. Extend the existing tokens in `Design.md` rather than inventing a palette per feature.

When adding a feature:

1. State its user task, data owner and whether it reads or mutates data.
2. Add a thin route and one `features/<feature>/` folder with its UI, contracts and tests.
3. Use the existing fixed server transport for receiver reads; introduce another transport only for an actual new integration.
4. Keep durable writes in the owning backend. For future mutations, define authorization, validation, audit/error handling and concurrency behavior before adding a button. Revisit authentication/CSRF if changing to cookie sessions.
5. Add shared code only for demonstrated reuse. Never promote a notification-specific helper to `lib/` simply to shorten its import.
6. Update this document for changed boundaries, `Design.md` for new UI patterns, and `Dairy.md` for decisions and evidence.

For example, a future transactions page would get `features/transactions/` and a thin route, while Hermes continues to own transaction parsing and durable processing. Do not run model calls, durable queues or scheduled ingestion jobs inside Next.js request handlers. Adding user accounts, multiple devices or remote multi-user access requires revisiting the single-owner token model before exposing more data.

## Deployment and checks that preserve these boundaries

Use two Compose services with independent build contexts. Only the receiver gets the database volume and ingestion credential. The dashboard gets server-side receiver location configuration, and forwards the user-supplied read credential per request. Configuration stays runtime-only where appropriate; validate it before use without requiring a running receiver or production secrets during `next build`.

Use standalone Next.js output, pinned compatible Node images, locked dependencies and non-root runtime users. Copy static/public assets into the dashboard image. Keep host ports loopback-bound; HTTPS access and database migration details remain in `DASHBOARD_PLAN.md` and eventual `SETUP.md`. Restarting the dashboard must not affect ingestion. Receiver downtime should produce a recoverable dashboard state. No process-local map becomes durable application storage.

At implementation, add a separate web CI workflow without replacing the Android build:

- Strict TypeScript, explicit ESLint invocation and production Next.js build. Configure restricted import patterns (including relative paths) for the table above; enforce client/server separation through `server-only` and build checks. Add a focused boundary checker only for rules ESLint cannot express clearly.
- Colocated tests for contracts, input validation, request cancellation and error mapping; receiver tests for authorization, storage and upgrade behavior.
- A small browser suite against synthetic data for unlock/list/details/filter/pagination/lock and failure states. Do not duplicate every internal helper test in end-to-end tests.
- Docker builds and a Compose smoke test proving ingestion, authenticated display and persistence after container recreation. Do not call liveness proof of phone connectivity or database readiness.
- Lockfile review, ignored-output/secret checks and `git diff --check`. When files in either service or Compose change, run integration/contract checks for both services.

These checks are planned requirements, not currently configured or passing. Add them alongside the first feature rather than deferring boundary enforcement until the app grows. Architecture decisions that change these rules should be recorded here with rationale; introduce separate decision records only if their history outgrows this document.

## Framework references

The feature-based organization is a project decision. Next.js supports multiple organization styles; its framework boundaries and conventions are documented in [Project structure](https://nextjs.org/docs/app/getting-started/project-structure) and [Server and Client Components](https://nextjs.org/docs/app/getting-started/server-and-client-components). Docker deployment references are in `DASHBOARD_PLAN.md`.

## Verification and records

`webhook/server.test.js` uses Node's built-in test runner with temporary SQLite files and ephemeral ports. `PROJECT.md` remains the product and delivery plan. `Dairy.md` records implementation decisions and validation evidence.
