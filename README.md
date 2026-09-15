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

```bash
./gradlew assembleDebug
```

## Application Identity

The application ID is now `com.notificationforwarder.app`. This is a new app identity, so configure notification access, the allowlist and webhook settings again after installing it. Disable or uninstall the old `com.itsazni.notificationforwarder` app first to prevent duplicate forwarding.

The historical Graphify snapshots under `graphify-out/` retain source paths from before this rename. Use current-source searches and the active tree as the authority for package paths.

## Webhook Configuration

Forwarding starts disabled after the security upgrade. Add at least one package to the allowlist and configure an HTTPS webhook before enabling it. Retry retention can be set from 1–24 hours or `OFF` for manual clearing; delivered, permanently failed and expired notification content is deleted.

The first launch after upgrading removes the legacy plaintext queue and keeps webhook settings while requiring forwarding to be enabled again. Existing backups created by older versions are not removed by the app.

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

This repository includes a Node.js webhook receiver in `webhook/` for synthetic local testing. It acknowledges authenticated requests and stores no notification contents.

### Setup

```bash
cd webhook
npm install
cp .env.example .env
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

The receiver must be deployed behind an HTTPS reverse proxy for any non-local use, with its upstream port inaccessible from the public network. Configure the proxy to disable access logging. The receiver writes only a generated receipt ID, timestamp and outcome to stdout; existing `webhook.log` files from older versions are not removed automatically.

Example Caddy reverse proxy (keep the Node port bound to localhost):

```caddyfile
notifications.example.com {
    log {
        output discard
    }
    reverse_proxy 127.0.0.1:3000
}
```

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
