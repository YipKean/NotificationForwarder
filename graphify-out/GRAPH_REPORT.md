# Graph Report - NotificationForwarder  (2026-09-26)

## Corpus Check
- Corpus is ~48,755 words - fits in a single context window. You may not need a graph.

## Summary
- 735 nodes · 1138 edges · 44 communities (33 shown, 11 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 23 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Community 0
- Community 1
- NotificationRepository
- ASTRA_INGESTION_REVIEW_REPORT
- QueueDao
- Community 5
- Community 6
- SettingsStore
- WebhookClient
- hermes-worker
- server
- Community 11
- ingestion.test
- Community 13
- outage-receiver.test
- package
- server.test
- QueueItem
- DURABLE_RECEIVER_IMPLEMENTATION_REPORT
- AppDatabase
- MainActivity
- HERMES_SETUP
- MainActivity
- storage
- Community 24
- build
- MainActivity
- NAMESPACE_RENAME_PLAN
- Community 28
- MainActivity
- gradlew
- Architecture
- INGESTION_TEST_STEPS
- README
- Community 36
- Community 37
- Community 38
- Community 39

## God Nodes (most connected - your core abstractions)
1. `NotificationRepository` - 49 edges
2. `QueueDao` - 31 edges
3. `SettingsStore` - 20 edges
4. `QueueMigrationInstrumentedTest` - 19 edges
5. `WebhookClient` - 13 edges
6. `createApp()` - 12 edges
7. `Independent ingestion implementation sign-off` - 12 edges
8. `NotificationPayload` - 11 edges
9. `MainScreen()` - 11 edges
10. `AppNotificationListenerService` - 9 edges

## Surprising Connections (you probably didn't know these)
- `Insert before acknowledgment` --conceptually_related_to--> `Policy revision and cancellation`  [INFERRED]
  DURABLE_RECEIVER_IMPLEMENTATION_REPORT.md → HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md
- `High-severity fix implementation report` --implements--> `Historical high-severity findings H1-H7`  [EXTRACTED]
  HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md → SECURITY_AUDIT.md
- `High-severity fix implementation report` --references--> `Sensitive notification security audit`  [EXTRACTED]
  HIGH_SEVERITY_FIX_IMPLEMENTATION_REPORT.md → SECURITY_AUDIT.md
- `Recovery boundary and sleep limitation` --semantically_similar_to--> `At-least-once inference limitation`  [INFERRED] [semantically similar]
  webhook/windows/README.md → webhook/HERMES_SETUP.md
- `Independent ingestion implementation sign-off` --references--> `Luna ingestion review handoff`  [EXTRACTED]
  ASTRA_INGESTION_REVIEW_REPORT.md → LUNA_INGESTION_REVIEW_REPORT.md

## Import Cycles
- None detected.

## Communities (44 total, 11 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.04
Nodes (53): appsettings, arrangement, button, card, carddefaults, collectasstate, column, componentname (+45 more)

### Community 1 - "Community 1"
Cohesion: 0.06
Nodes (27): aeadbadtagexception, NotificationPayload, EncryptedQueuePayload, QueueCrypto, QueueKeyMissingException, QueuePayloadCorruptException, QueuePayloadTest, RetryPolicyTest (+19 more)

### Community 2 - "NotificationRepository"
Cohesion: 0.09
Nodes (12): Corrupt, DecryptionResult, KeyMissing, Flow, NotificationPayload, QueueEntry, QueueItem, QueueStats (+4 more)

### Community 3 - "ASTRA_INGESTION_REVIEW_REPORT"
Cohesion: 0.05
Nodes (41): Independent ingestion implementation sign-off, User-reported seven device passes, Persistence-failure evidence boundary, Graph baseline and extraction limitations, Historical validation reused, Operational and financial acceptance follow-up, Coordinated rollout and live acceptance pending, Independent repository sign-off complete (+33 more)

### Community 4 - "QueueDao"
Cohesion: 0.08
Nodes (10): Flow, QueueItem, QueueStats, QueueStatus, QueueDao, dao, insert, onconflictstrategy (+2 more)

### Community 5 - "Community 5"
Cohesion: 0.10
Nodes (21): after, androidjunit4, Context, NotificationPayload, QueueItem, QueueStatus, SettingsStore, QueueMigrationInstrumentedTest (+13 more)

### Community 6 - "Community 6"
Cohesion: 0.07
Nodes (24): NotificationForwarderApp, DeliveryCoordinator, T, Context, WorkerScheduler, Application, backoffpolicy, Call (+16 more)

### Community 7 - "SettingsStore"
Cohesion: 0.08
Nodes (21): Context, T, SecureInitialization, BootCompletedReceiver, Context, AppSettings, AuthMode, BEARER (+13 more)

### Community 8 - "WebhookClient"
Cohesion: 0.09
Nodes (20): EndpointValidator, Callback, PreparedWebhookRequest, Ready, Rejected, SendResult, WebhookClient, Callback (+12 more)

### Community 9 - "hermes-worker"
Cohesion: 0.10
Nodes (29): ref_node_assert_strict, ref_node_child_process, crypto, { DatabaseSync }, fs, inferNotification(), webhook_hermes_worker_lease_ms, { loadConfig } (+21 more)

### Community 10 - "server"
Cohesion: 0.11
Nodes (27): withReceiver(), closeStorage(), createApp(), crypto, { DEFAULT_DATABASE_PATH, openNotificationStore }, dotenv, { evaluate: filterSensitiveNotification }, express (+19 more)

### Community 11 - "Community 11"
Cohesion: 0.11
Nodes (16): RetryPolicy, CoroutineWorker, Result, QueueCleanupWorker, DeliveryPlan, AuthMode, QueueWorker, Ready (+8 more)

### Community 12 - "ingestion.test"
Cohesion: 0.09
Nodes (24): ref_node_http, ref_node_worker_threads, test_fixtures_sensitive_notifications, assert, { CATALOGUE_VERSION, evaluate, normalize }, { createApp, loadConfig, validateConfig }, crypto, { DatabaseSync } (+16 more)

### Community 13 - "Community 13"
Cohesion: 0.12
Nodes (15): FilterRejection, Rule, SensitiveNotificationFilter, AppNotificationListenerService, RecentEvent, build, coroutinescope, dispatchers (+7 more)

### Community 14 - "outage-receiver.test"
Cohesion: 0.09
Nodes (23): c_users_kean5_onedrive_desktop_project_notificationforwarder_webhook_server_createapp, c_users_kean5_onedrive_desktop_project_notificationforwarder_webhook_server_loadconfig, c_users_kean5_onedrive_desktop_project_notificationforwarder_webhook_storage_opennotificationstore, ref_node_crypto, ref_node_events, ref_node_os, ref_node_readline, ref_node_test (+15 more)

### Community 15 - "package"
Cohesion: 0.08
Nodes (24): dotenv, express, author, dependencies, dotenv, express, description, engines (+16 more)

### Community 16 - "server.test"
Cohesion: 0.08
Nodes (12): ref_node_net, { after, test }, assert, { createApp, loadConfig, start, validateConfig }, { DatabaseSync }, fs, net, { openNotificationStore, resolveDatabasePath } (+4 more)

### Community 17 - "QueueItem"
Cohesion: 0.12
Nodes (15): PendingQueueItem, QueueConverters, QueueEntry, QueueItem, QueueMetrics, QueueStats, QueueStatus, FAILED (+7 more)

### Community 18 - "DURABLE_RECEIVER_IMPLEMENTATION_REPORT"
Cohesion: 0.12
Nodes (18): Insert before acknowledgment, Historical receiver limitations, Durable receiver implementation report, Historical schema-v1 validation, SQLite notification_events, Durable receiver validation evidence, HTTPS-only redirect rejection, Policy revision and cancellation (+10 more)

### Community 19 - "AppDatabase"
Cohesion: 0.18
Nodes (9): AppDatabase, Callback, androidx, Callback, Context, database, room, RoomDatabase (+1 more)

### Community 20 - "MainActivity"
Cohesion: 0.26
Nodes (13): buildHeadersPreview(), DropdownSelector(), FilterScreen(), AuthMode, SettingsStore, MainScreen(), parseKeyValuePairs(), saveSettings() (+5 more)

### Community 21 - "HERMES_SETUP"
Cohesion: 0.17
Nodes (12): At-least-once inference limitation, Quiet stdin classification boundary, Hermes synthetic processing setup, Durable claim leases and bounded retries, finance-notifications Hermes profile, Dedicated parser profile tool restrictions, Windows laptop startup and recovery, Machine-specific settings and ngrok binary (+4 more)

### Community 22 - "MainActivity"
Cohesion: 0.27
Nodes (10): androidx, HomeScreen(), isBatteryUnrestricted(), isNotificationListenerEnabled(), Context, QueueStats, openBatterySettings(), QueueStatCard() (+2 more)

### Community 23 - "storage"
Cohesion: 0.29
Nodes (6): ref_node_fs, ref_node_path, ref_node_sqlite, { DatabaseSync }, fs, path

### Community 24 - "Community 24"
Cohesion: 0.33
Nodes (4): color, composable, darkcolorscheme, materialtheme

### Community 25 - "build"
Cohesion: 0.40
Nodes (5): Build & Release APK workflow, Temurin JDK 17, Signed release APK, Tagged GitHub releases, Versioned APK artifacts

### Community 26 - "MainActivity"
Cohesion: 0.40
Nodes (5): AppTab, FILTER, HOME, QUEUE, WEBHOOK

### Community 27 - "NAMESPACE_RENAME_PLAN"
Cohesion: 0.40
Nodes (5): com.notificationforwarder.app, New application identity requires fresh setup, Historical missing-Java build blocker, Namespace rename plan and final review, Current-source rename verification

### Community 28 - "Community 28"
Cohesion: 0.50
Nodes (4): QueueEntry, QueueStatus, QueueScreen(), QueueStatusBadge()

### Community 29 - "MainActivity"
Cohesion: 0.50
Nodes (3): MainActivity, Bundle, ComponentActivity

### Community 30 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 31 - "Architecture"
Cohesion: 0.67
Nodes (3): Current ingestion architecture, Android receiver worker boundaries, Current verification records

### Community 32 - "INGESTION_TEST_STEPS"
Cohesion: 0.67
Nodes (3): Isolated ingestion test steps, Connected device execution, Isolated Android test package

## Knowledge Gaps
- **156 isolated node(s):** `QueueMetrics`, `QueueStats`, `QueueEntry`, `PendingQueueItem`, `Fixture` (+151 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 363 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NotificationRepository` connect `NotificationRepository` to `Community 0`, `Community 5`, `Community 6`, `Community 11`, `Community 13`, `MainActivity`?**
  _High betweenness centrality (0.136) - this node is a cross-community bridge._
- **Why does `SettingsStore` connect `SettingsStore` to `Community 11`, `AppDatabase`, `Community 13`?**
  _High betweenness centrality (0.073) - this node is a cross-community bridge._
- **What connects `QueueMetrics`, `QueueStats`, `QueueEntry` to the rest of the system?**
  _156 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.037037037037037035 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.06207482993197279 - nodes in this community are weakly interconnected._
- **Should `NotificationRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.08943089430894309 - nodes in this community are weakly interconnected._
- **Should `ASTRA_INGESTION_REVIEW_REPORT` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
## Refresh limitations

Semantic extraction used local task agents; their token usage was not exposed by the collaboration API. The zero token counters above are placeholders, not a measured zero cost. Historical screenshots and generated graph files are excluded. AST edges remain subject to the graphify diagnostic limitations recorded in the implementation report.

## Reproducible refresh commands

```powershell
$py = '.gradle/graphify-runtime/Scripts/python.exe'
& $py .gradle/refresh_ingestion_graph.py
& $py -c "import json; g=json.load(open('graphify-out/graph.json', encoding='utf-8')); print(len(g['nodes']), len(g['links']))"
```
