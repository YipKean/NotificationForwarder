# Graph Report - NotificationForwarder  (2026-09-14)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 266 nodes · 401 edges · 19 communities
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 3 edges (avg confidence: 0.92)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b2f7e9e3`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Main UI Screens
- Settings & Delivery Worker
- Webhook Server
- Queue Persistence
- App Lifecycle Scheduling
- Notification Listener
- README Product Overview
- Webhook HTTP Client
- Database Setup
- Navigation Tabs
- Gradle Wrapper Utilities
- Home Screen Capture
- Webhook Screen Capture
- Queue Screen Capture
- Filter Screen Capture
- License Terms

## God Nodes (most connected - your core abstractions)
1. `README: Notification Forwarder` - 34 edges
2. `NotificationRepository` - 19 edges
3. `SettingsStore` - 16 edges
4. `Notification Forwarder Home Screen` - 16 edges
5. `MainScreen()` - 15 edges
6. `Notification Forwarder Webhook Screen` - 14 edges
7. `QueueDao` - 11 edges
8. `QueueItem` - 11 edges
9. `Notification Forwarder Queue Screen` - 11 edges
10. `WebhookClient` - 10 edges

## Surprising Connections (you probably didn't know these)
- `Notification Forwarder Queue Screen` --semantically_similar_to--> `Notification Forwarder Webhook Screen`  [INFERRED] [semantically similar]
  C:/Users/Joe/Desktop/Kean/Project/NotificationForwarder/screenshots/queue.jpg → C:/Users/Joe/Desktop/Kean/Project/NotificationForwarder/screenshots/webhook.jpg
- `MainScreen()` --calls--> `NotificationRepository`  [EXTRACTED]
  app/src/main/java/com/itsazni/notificationforwarder/MainActivity.kt → app/src/main/java/com/itsazni/notificationforwarder/data/NotificationRepository.kt
- `MainScreen()` --references--> `SettingsStore`  [EXTRACTED]
  app/src/main/java/com/itsazni/notificationforwarder/MainActivity.kt → app/src/main/java/com/itsazni/notificationforwarder/settings/SettingsStore.kt
- `saveSettings()` --references--> `SettingsStore`  [EXTRACTED]
  app/src/main/java/com/itsazni/notificationforwarder/MainActivity.kt → app/src/main/java/com/itsazni/notificationforwarder/settings/SettingsStore.kt
- `QueueWorker` --calls--> `WebhookClient`  [EXTRACTED]
  app/src/main/java/com/itsazni/notificationforwarder/worker/QueueWorker.kt → app/src/main/java/com/itsazni/notificationforwarder/network/WebhookClient.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Supported webhook targets** — readme_telegram_bot_api, readme_discord_webhooks, readme_custom_api [EXTRACTED 1.00]
- **Notification delivery components** — readme_notification_capture, readme_webhook_forwarding, readme_queue_system, readme_retry_system [INFERRED 0.85]
- **Background reliability measures** — readme_queue_system, readme_retry_system, readme_background_support, readme_auto_queue_scheduling, readme_boot_completed [INFERRED 0.85]

## Communities (19 total, 0 thin omitted)

### Community 0 - "Main UI Screens"
Cohesion: 0.17
Nodes (24): androidx, DropdownSelector(), FilterScreen(), HomeScreen(), isBatteryUnrestricted(), isNotificationListenerEnabled(), Context, MainActivity (+16 more)

### Community 1 - "Settings & Delivery Worker"
Cohesion: 0.09
Nodes (16): NotificationRepository, buildHeadersPreview(), AppSettings, AuthMode, BEARER, CUSTOM, NONE, FilterMode (+8 more)

### Community 2 - "Webhook Server"
Cohesion: 0.09
Nodes (19): dotenv, express, author, dependencies, dotenv, express, description, keywords (+11 more)

### Community 3 - "Queue Persistence"
Cohesion: 0.12
Nodes (9): QueueDao, QueueItem, QueueStats, QueueStatus, FAILED, PENDING, SENDING, SENT (+1 more)

### Community 4 - "App Lifecycle Scheduling"
Cohesion: 0.21
Nodes (7): NotificationForwarderApp, BootCompletedReceiver, Context, Context, WorkerScheduler, Application, BroadcastReceiver

### Community 5 - "Notification Listener"
Cohesion: 0.38
Nodes (5): AppNotificationListenerService, RecentEvent, Notification, NotificationListenerService, StatusBarNotification

### Community 6 - "README Product Overview"
Cohesion: 0.06
Nodes (52): Authentication, Authorization header, Automatic queue scheduling, Background support, Backoff, Battery Settings, Bearer authentication, BOOT_COMPLETED (+44 more)

### Community 7 - "Webhook HTTP Client"
Cohesion: 0.43
Nodes (3): SendResult, WebhookClient, OkHttpClient

### Community 8 - "Database Setup"
Cohesion: 0.47
Nodes (3): AppDatabase, Context, RoomDatabase

### Community 9 - "Navigation Tabs"
Cohesion: 0.40
Nodes (5): AppTab, FILTER, HOME, QUEUE, WEBHOOK

### Community 10 - "Gradle Wrapper Utilities"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 14 - "Home Screen Capture"
Cohesion: 0.12
Nodes (17): Battery Optimization: No restriction, Failed: 0, Filter Tab, Notification Forwarder Home Screen, Home Tab, Notification Access: Granted, Notification Forwarder, Open Access Settings (+9 more)

### Community 15 - "Webhook Screen Capture"
Cohesion: 0.18
Nodes (14): Authentication Mode, Custom Headers, Enable Forwarding, HTTP Method, JSON Title and Text Placeholders, No Authentication, Notification Forwarder, Payload Template (+6 more)

### Community 16 - "Queue Screen Capture"
Cohesion: 0.22
Nodes (11): Attempt 0, Checking for New Messages, Clear All Queue Action, com.whatsapp.w4b Package, Delete This Queue Action, Notification Forwarder, Pending Status, Queue Navigation Tab (+3 more)

### Community 17 - "Filter Screen Capture"
Cohesion: 0.25
Nodes (8): ALL_APPS, Batch Size: 20, Filter & Retry, Filter Mode, Filter and Retry Screen, Max Retries: 10, Packages List (comma/newline), Save Filter & Retry

### Community 18 - "License Terms"
Cohesion: 0.43
Nodes (7): ItsAzni, Liability disclaimer, LICENSE document, MIT License, MIT permission grant, Warranty disclaimer, MIT License (README)

## Knowledge Gaps
- **85 isolated node(s):** `BEARER`, `CUSTOM`, `NONE`, `ALL_APPS`, `BLACKLIST` (+80 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 105 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NotificationRepository` connect `Settings & Delivery Worker` to `Main UI Screens`, `Queue Persistence`, `Notification Listener`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **Why does `README: Notification Forwarder` connect `README Product Overview` to `License Terms`?**
  _High betweenness centrality (0.042) - this node is a cross-community bridge._
- **Why does `QueueItem` connect `Queue Persistence` to `Main UI Screens`, `Webhook HTTP Client`?**
  _High betweenness centrality (0.042) - this node is a cross-community bridge._
- **What connects `BEARER`, `CUSTOM`, `NONE` to the rest of the system?**
  _85 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Settings & Delivery Worker` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Webhook Server` be split into smaller, more focused modules?**
  _Cohesion score 0.08695652173913043 - nodes in this community are weakly interconnected._
- **Should `Queue Persistence` be split into smaller, more focused modules?**
  _Cohesion score 0.12121212121212122 - nodes in this community are weakly interconnected._