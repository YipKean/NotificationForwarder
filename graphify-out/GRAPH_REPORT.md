# Graph Report - NotificationForwarder  (2026-09-28)

## Corpus Check
- 65 files · ~52,968 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 27 file(s) not represented in the graph (top: .xml 10, (none) 6, .cmd 4)

## Summary
- 803 nodes · 1308 edges · 45 communities (31 shown, 14 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 55 edges (avg confidence: 0.9)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `affaf8fc`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity.kt
- QueueCrypto.kt
- NotificationRepository
- Hermes finance bridge project scope
- QueueDao
- QueueMigrationInstrumentedTest.kt
- WorkerScheduler.kt
- SettingsStore
- WebhookClient
- hermes-worker.js
- server.js
- Luna ingestion hardening review
- ingestion.test.js
- AppNotificationListenerService.kt
- outage-receiver.test.js
- package.json
- server.test.js
- Ingestion hardening implementation report
- DeliveryCoordinator
- Run ingestion integration tests on an Android phone
- MainScreen
- Ingestion hardening — progress and continuation
- HomeScreen
- readRoutes.test.js
- .onCreate
- Build & Release APK workflow
- AppTab
- Namespace rename plan
- Independent ingestion implementation sign-off
- Q: Audit sensitive banking notification data flows, persistence, logging, exported components and dependencies
- gradlew
- Design.md
- appsettings
- c_users_kean5_onedrive_desktop_project_notificationforwarder_webhook_server_createapp
- c_users_kean5_onedrive_desktop_project_notificationforwarder_webhook_server_loadconfig
- c_users_kean5_onedrive_desktop_project_notificationforwarder_webhook_storage_opennotificationstore
- filtermode
- preparedwebhookrequest
- sendresult

## God Nodes (most connected - your core abstractions)
1. `NotificationRepository` - 51 edges
2. `QueueDao` - 31 edges
3. `SettingsStore` - 29 edges
4. `WebhookClient` - 21 edges
5. `QueueMigrationInstrumentedTest` - 19 edges
6. `NotificationPayload` - 17 edges
7. `createApp()` - 17 edges
8. `Call` - 15 edges
9. `MainScreen()` - 13 edges
10. `QueueWorker` - 13 edges

## Surprising Connections (you probably didn't know these)
- `1. Requirement completion and deviations` --references--> `NotificationRepository`  [INFERRED]
  tasks/INGESTION_HARDENING_IMPLEMENTATION_REPORT.md → app/src/main/java/com/notificationforwarder/app/data/NotificationRepository.kt
- `5. Stable UUID, replay and update evidence` --references--> `NotificationRepository`  [INFERRED]
  tasks/INGESTION_HARDENING_IMPLEMENTATION_REPORT.md → app/src/main/java/com/notificationforwarder/app/data/NotificationRepository.kt
- `Evidence limitations retained as unresolved review items` --references--> `QueueWorker`  [INFERRED]
  tasks/LUNA_INGESTION_REVIEW_REPORT.md → app/src/main/java/com/notificationforwarder/app/worker/QueueWorker.kt
- `3. Retention and cleanup (H5)` --references--> `MainActivity`  [INFERRED]
  tasks/HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md → app/src/main/java/com/notificationforwarder/app/MainActivity.kt
- `2. Secure initialization and encrypted queue (H5-H6)` --references--> `AppDatabase`  [INFERRED]
  tasks/HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md → app/src/main/java/com/notificationforwarder/app/data/AppDatabase.kt

## Import Cycles
- None detected.

## Communities (45 total, 14 thin omitted)

### Community 0 - "MainActivity.kt"
Cohesion: 0.03
Nodes (62): arrangement, button, card, carddefaults, channel, collectasstate, collectlatest, column (+54 more)

### Community 1 - "QueueCrypto.kt"
Cohesion: 0.13
Nodes (17): aeadbadtagexception, EncryptedQueuePayload, QueueCrypto, QueueKeyMissingException, QueuePayloadCorruptException, base64, cipher, gcmparameterspec (+9 more)

### Community 2 - "NotificationRepository"
Cohesion: 0.08
Nodes (10): QueueMigrationInstrumentedTest, Corrupt, DecryptionResult, KeyMissing, Flow, NotificationRepository, Success, PendingQueueItem (+2 more)

### Community 3 - "Hermes finance bridge project scope"
Cohesion: 0.05
Nodes (47): Current ingestion architecture, Android receiver worker boundaries, Current verification records, Project diary current independent sign-off, Documentation validation, Graph refresh outcome and limits, Independent ingestion sign-off complete, Synthetic rollout and live acceptance pending (+39 more)

### Community 4 - "QueueDao"
Cohesion: 0.06
Nodes (21): Flow, QueueDao, QueueConverters, QueueItem, QueueMetrics, QueueStats, QueueStatus, FAILED (+13 more)

### Community 5 - "QueueMigrationInstrumentedTest.kt"
Cohesion: 0.08
Nodes (23): after, androidjunit4, Context, SharedPreferences, ContextWrapper, AppDatabase, Callback, androidx (+15 more)

### Community 6 - "WorkerScheduler.kt"
Cohesion: 0.08
Nodes (21): Context, T, SecureInitialization, NotificationForwarderApp, BootCompletedReceiver, Context, Context, WorkerScheduler (+13 more)

### Community 7 - "SettingsStore"
Cohesion: 0.07
Nodes (30): buildHeadersPreview(), AppSettings, AuthMode, BEARER, CUSTOM, NONE, FilterMode, ALL_APPS (+22 more)

### Community 8 - "WebhookClient"
Cohesion: 0.06
Nodes (31): NotificationPayload, EndpointValidator, Callback, PreparedWebhookRequest, Ready, Rejected, SendResult, WebhookClient (+23 more)

### Community 9 - "hermes-worker.js"
Cohesion: 0.10
Nodes (29): ref_node_assert_strict, ref_node_child_process, ref_node_crypto, crypto, { DatabaseSync }, fs, inferNotification(), webhook_hermes_worker_lease_ms (+21 more)

### Community 10 - "server.js"
Cohesion: 0.09
Nodes (34): 3. Changes, withReceiver(), createOutageReceiver(), installReadRoutes(), parseListQuery(), serve(), closeStorage(), createApp() (+26 more)

### Community 11 - "Luna ingestion hardening review"
Cohesion: 0.06
Nodes (21): RetryPolicy, QueuePayloadTest, RetryPolicyTest, Fixture, SensitiveNotificationFilterTest, TypeToken, assertequals, gson (+13 more)

### Community 12 - "ingestion.test.js"
Cohesion: 0.09
Nodes (23): ref_node_http, ref_node_worker_threads, test_fixtures_sensitive_notifications, assert, { CATALOGUE_VERSION, evaluate, normalize }, { createApp, loadConfig, validateConfig }, crypto, { DatabaseSync } (+15 more)

### Community 13 - "AppNotificationListenerService.kt"
Cohesion: 0.12
Nodes (15): FilterRejection, Rule, SensitiveNotificationFilter, AppNotificationListenerService, RecentEvent, build, coroutinescope, dispatchers (+7 more)

### Community 14 - "outage-receiver.test.js"
Cohesion: 0.10
Nodes (20): ref_node_events, ref_node_fs, ref_node_os, ref_node_readline, ref_node_test, payload(), { createApp, loadConfig }, fs (+12 more)

### Community 15 - "package.json"
Cohesion: 0.08
Nodes (24): dotenv, express, author, dependencies, dotenv, express, description, engines (+16 more)

### Community 16 - "server.test.js"
Cohesion: 0.08
Nodes (12): ref_node_net, { after, test }, assert, { createApp, loadConfig, start, validateConfig }, { DatabaseSync }, fs, net, { openNotificationStore, resolveDatabasePath } (+4 more)

### Community 17 - "Ingestion hardening implementation report"
Cohesion: 0.08
Nodes (23): 10. Follow-up status — 2026-09-18, 1. Outcome, 2. Baseline, 4. Contract, 5. Verification table, 6. Durability evidence, 7. Limitations and deviations, 8. Review pointers (+15 more)

### Community 18 - "DeliveryCoordinator"
Cohesion: 0.18
Nodes (6): DeliveryCoordinator, T, mutex, noncancellable, withcontext, withlock

### Community 19 - "Run ingestion integration tests on an Android phone"
Cohesion: 0.20
Nodes (9): 1. Prepare the phone, 2. Prepare PowerShell, 3. Run the tests, 4. Optional cleanup, Controlled device outage acceptance, Outage and recovery, Run ingestion integration tests on an Android phone, Scope (+1 more)

### Community 20 - "MainScreen"
Cohesion: 0.23
Nodes (14): QueueEntry, DropdownSelector(), FilterScreen(), MainScreen(), parseKeyValuePairs(), QueueScreen(), QueueStatusBadge(), saveSettings() (+6 more)

### Community 21 - "Ingestion hardening — progress and continuation"
Cohesion: 0.22
Nodes (8): Current checkpoint — 2026-09-26, Implemented in the checkout, Ingestion hardening — progress and continuation, Main records, Next-session handoff: outage retry loss, Operational constraints, Unfinished work — do not treat as accepted, Verified

### Community 22 - "HomeScreen"
Cohesion: 0.32
Nodes (8): HomeScreen(), isBatteryUnrestricted(), isNotificationListenerEnabled(), androidx, Context, openBatterySettings(), QueueStatCard(), StatusBadge()

### Community 23 - "readRoutes.test.js"
Cohesion: 0.12
Nodes (16): ref_node_path, ref_node_sqlite, assert, config, { createApp, validateConfig }, { DatabaseSync }, { join }, { mkdtempSync, copyFileSync } (+8 more)

### Community 24 - ".onCreate"
Cohesion: 0.20
Nodes (8): MainActivity, AppTheme(), Bundle, color, ComponentActivity, composable, darkcolorscheme, materialtheme

### Community 25 - "Build & Release APK workflow"
Cohesion: 0.40
Nodes (5): Build & Release APK workflow, Temurin JDK 17, Signed release APK, Tagged GitHub releases, Versioned APK artifacts

### Community 26 - "AppTab"
Cohesion: 0.40
Nodes (5): AppTab, FILTER, HOME, QUEUE, WEBHOOK

### Community 27 - "Namespace rename plan"
Cohesion: 0.29
Nodes (6): Decision, Final review (parent agent), Final review results, Graphify assessment, Implementation (Luna, xhigh), Namespace rename plan

### Community 28 - "Independent ingestion implementation sign-off"
Cohesion: 0.33
Nodes (5): Evidence and limitations, Independent ingestion implementation sign-off, Remaining acceptance gates, Source review, Verdict

### Community 29 - "Q: Audit sensitive banking notification data flows, persistence, logging, exported components and dependencies"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Audit sensitive banking notification data flows, persistence, logging, exported components and dependencies, Source Nodes

### Community 30 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **197 isolated node(s):** `HOME`, `WEBHOOK`, `FILTER`, `QUEUE`, `KeyMissing` (+192 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 413 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `QueueWorker` connect `SettingsStore` to `WebhookClient`, `Ingestion hardening implementation report`, `NotificationRepository`, `Luna ingestion hardening review`?**
  _High betweenness centrality (0.355) - this node is a cross-community bridge._
- **Why does `Durable receiver implementation report` connect `Ingestion hardening implementation report` to `server.js`?**
  _High betweenness centrality (0.332) - this node is a cross-community bridge._
- **Why does `3. Changes` connect `server.js` to `Ingestion hardening implementation report`?**
  _High betweenness centrality (0.317) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `NotificationRepository` (e.g. with `1. Requirement completion and deviations` and `5. Stable UUID, replay and update evidence`) actually correct?**
  _`NotificationRepository` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `HOME`, `WEBHOOK`, `FILTER` to the rest of the system?**
  _197 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.031746031746031744 - nodes in this community are weakly interconnected._
- **Should `QueueCrypto.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.13 - nodes in this community are weakly interconnected._