---
type: "query"
date: "2026-09-14T15:56:47.389212+00:00"
question: "Audit sensitive banking notification data flows, persistence, logging, exported components and dependencies"
contributor: "graphify"
outcome: "useful"
source_nodes: ["WebhookClient", "SettingsStore", "QueueWorker", "QueueDao"]
---

# Q: Audit sensitive banking notification data flows, persistence, logging, exported components and dependencies

## Answer

Expanded graph vocabulary: notification webhook token headers queue settings send worker database receiver payload. Graph identified NotificationListenerService to NotificationRepository to QueueDao and QueueWorker to WebhookClient, plus MainScreen test and webhook/server.js. Source verification found cleartext permitted, unrestricted redirects, BASIC URL logging, backup enabled, plaintext settings and indefinitely retained queue, UI exposure, current-settings delivery of old queue, ANDROID_ID in default payload, and server payload/header/query logging. No direct exported-component extraction found in application source; merged APK unavailable. See SECURITY_AUDIT.md for source-verified findings and limitations.

## Outcome

- Signal: useful

## Source Nodes

- WebhookClient
- SettingsStore
- QueueWorker
- QueueDao