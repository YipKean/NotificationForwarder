# Notification Forwarder (Android)

Android app to listen for incoming notifications and forward them to a configurable webhook API.

## Features

- Notification capture using `NotificationListenerService`
- Webhook forwarding with configurable URL, HTTP method, auth mode, custom headers, query params, and payload template
- Compatible with Telegram Bot API, Discord webhooks, and any custom API
- Queue system with Room (durable local storage)
- Retry system with WorkManager (network constraints + backoff)
- Encrypted queue payloads with configurable retry retention (24 hours by default)
- Whitelist-only capture; changing delivery settings clears pending notifications
- Background support
- Auto queue scheduling after reboot (`BOOT_COMPLETED`)

## Background Reliability Setup

1. Open app -> **Home**.
2. Tap **Open Access Settings** and enable Notification Access.
3. Tap **Open Battery Settings** and set app to no restriction if available.
4. On some OEM ROMs (MIUI/ColorOS/Funtouch), enable Auto Start for the app.

## Build

For Windows tooling, APK installation, phone configuration and testing, see [SETUP.md](SETUP.md).

```bash
./gradlew assembleDebug
```

## Application Identity

The application ID is now `com.notificationforwarder.app`. This is a new app identity, so configure notification access, the allowlist and webhook settings again after installing it. Disable or uninstall the old `com.itsazni.notificationforwarder` app first to prevent duplicate forwarding.

The historical Graphify snapshots under `graphify-out/` retain source paths from before this rename. Use current-source searches and the active tree as the authority for package paths.

## Webhook Configuration

Forwarding starts disabled after the security upgrade. Add at least one package to the allowlist and configure an HTTPS webhook before enabling it. Retry retention can be set from 1–24 hours or `OFF` for manual clearing; delivered, permanently failed and expired notification content is deleted.

The earlier security upgrade removes the legacy plaintext queue and keeps webhook settings while requiring forwarding to be enabled again. The ingestion-hardening upgrade preserves the existing encrypted queue, filters legacy items and persists missing event IDs before delivery. Existing backups created by older versions are not removed by the app.

### Supported HTTP Methods
- `GET` — no request body, query params appended to URL
- `POST` — with JSON body
- `PUT` — with JSON body
- `PATCH` — with JSON body

### Authentication
- **None** — no auth header
- **Bearer** — adds `Authorization: Bearer <token>`
- **Custom** — define any headers manually

### Custom Query Params
Add per line as `key=value`:
```
chat_id=123456789
token=abc123
```

### Custom Payload Template
Use JSON with variable placeholders. Leave blank for default payload.

Available variables:
- `{deviceId}`
- `{packageName}`
- `{appName}`
- `{title}`
- `{text}`
- `{postedAt}`
- `{notificationKey}`
- `{eventId}` (quoted string), `{bigText}` (quoted string), and `{bigTextJson}` (JSON string or `null`)

The blank template emits schema v2 automatically. For the local receiver, this custom template omits device ID and the raw notification key:

```json
{
  "schemaVersion": 2,
  "eventId": "{eventId}",
  "packageName": "{packageName}",
  "appName": "{appName}",
  "title": "{title}",
  "text": "{text}",
  "bigText": {bigTextJson},
  "postedAt": {postedAt}
}
```

Leave `{bigTextJson}` and `{postedAt}` unquoted. Use `"{bigText}"` when a destination requires a string (absent expanded text becomes `""`). Replacements run once, so literal placeholder-like notification text survives unchanged. Existing custom templates are not rewritten automatically; changing the saved template clears pending items under the existing security policy.

#### Example: Telegram Bot API
- URL: `https://api.telegram.org/bot<token>/sendMessage`
- Method: `POST`
- Payload template:
```json
{"chat_id":"123456789","text":"*{appName}*\n*{title}*\n{text}","parse_mode":"Markdown"}
```

#### Example: Discord Webhook
- URL: `https://discord.com/api/webhooks/.../...`
- Method: `POST`
- Payload template:
```json
{"content":"**{appName}**\n**{title}**\n{text}"}
```

#### Example: Custom GET API
- URL: `https://example.com/api/alert`
- Method: `GET`
- Query params:
```
device={deviceId}
msg={title}
```

## Local Webhook API (`webhook/`)

This repository includes a Node.js webhook receiver in `webhook/` for synthetic local testing. It authenticates requests and stores accepted notification payloads in a local SQLite database before acknowledging them. An optional Hermes worker then classifies saved synthetic receipts asynchronously through a dedicated Hermes profile.

The receiver requires schema v2 and UUID-v4 event IDs. Matching source/event retries return the original receipt; different content under the same ID returns `409`. OTP/TAC, login/device-verification and approval/security notices are rejected before storage with `422`. Filtering runs on Android before queueing too, including expanded text. This is transport deduplication, not financial transaction deduplication. Keep `WEBHOOK_SOURCE_ID` unchanged when rotating the bearer token. See [coordinated upgrade steps](SETUP.md#coordinated-ingestion-upgrade).

### Setup

```powershell
cd webhook
npm ci
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

### Run

```bash
npm run start
```

Default endpoint:

- `POST /webhook`

Health check:

- `GET /health`

Environment config (`webhook/.env`):

| Key | Description |
|-----|-------------|
| `HOST` | Server host (defaults to `127.0.0.1`; use a controlled private interface only) |
| `PORT` | Server port |
| `WEBHOOK_PATH` | Webhook endpoint path |
| `WEBHOOK_BEARER_TOKEN` | Required bearer token |
| `JSON_LIMIT` | Max JSON body size |
| `DATABASE_PATH` | SQLite path; relative paths resolve from `webhook/` |
| `WEBHOOK_SOURCE_ID` | Stable authenticated sender identity (defaults to `personal-phone`) |

The receiver must be deployed behind an HTTPS reverse proxy for any non-local use, with its upstream port inaccessible from the public network. Configure the proxy to disable access logging. The receiver writes only a generated receipt ID, timestamp and outcome to stdout. SQLite files are local synthetic-test storage and are ignored by Git; stop the receiver before copying an existing database to another machine.

Example Caddy reverse proxy (keep the Node port bound to localhost):

```caddyfile
notifications.example.com {
    log {
        output discard
    }
    reverse_proxy 127.0.0.1:3000
}
```

### Hermes processing worker

After the receiver is running and the `finance-notifications` Hermes profile is configured, use:

```powershell
npm run process:status
npm run process:once
npm run process:results
npm run process:watch
```

The worker stores draft classifications and retry state in the local SQLite database. It uses the profile's configured model (Luna on the verified laptop), disables tools in that dedicated profile, and validates the entire quiet response as JSON. It is an at-least-once synthetic classifier, not a finance ledger; sensitive-content filtering, persistent event IDs, event deduplication and storage protection remain required before banking use. See [webhook/HERMES_SETUP.md](webhook/HERMES_SETUP.md).

## Screenshots

### Home & Webhook Page

<img src="screenshots/home.jpg" alt="Home" width="240" />
<img src="screenshots/webhook.jpg" alt="Webhook" width="240" />

### Filter & Queue Page

<img src="screenshots/filter.jpg" alt="Filter" width="240" />
<img src="screenshots/queue.jpg" alt="Queue" width="240" />

## License

This project is licensed under the MIT License.
See [LICENSE](LICENSE) for details.
