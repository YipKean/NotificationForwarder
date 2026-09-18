# Setup and phone testing

Windows / PowerShell guide for building Notification Forwarder and testing it on an Android phone, including the Samsung Galaxy S23 Ultra.

## Current status

- Application ID: `com.notificationforwarder.app`.
- Minimum phone version: Android 8.0 (API 26).
- On 2026-09-16, the user reported completing the build/setup commands and installing the app on their phone. They explicitly confirmed that the Discord webhook worked and a notification was forwarded successfully with Instagram configured as the test source (`com.instagram.android`). This is user-confirmed device evidence; build/lint logs were not captured by the agent.
- The earlier missing-Java blocker was superseded after switching environments: Android Studio's bundled Java 21.0.6 and Android SDK 36 were found locally. The documented AGP/SDK compatibility gap below and the full device acceptance checklist have not been independently cleared.
- The local SQLite receiver passed its automated tests and parent review. The user has since confirmed old-laptop deployment, ngrok HTTPS delivery, and automatic Hermes processing of synthetic receipts. The full Android background/recovery checklist remains outstanding. See the [laptop handoff](PROJECT.md#windows-laptop-handoff).
- This installs separately from `com.itsazni.notificationforwarder`. Settings, queue data and notification access do not transfer. Disable forwarding in the old app before testing the new one.

## 1. Prepare the Windows build tools

Install a JDK 17 distribution and Android Studio. In Android Studio's SDK Manager, install Android SDK Platform 36, Android SDK Platform-Tools and SDK Build-Tools 34.0.0 (the current plugin's default). Accept the SDK licenses. Note the SDK location shown there. See the [official SDK Manager guide](https://developer.android.com/studio/intro/update).

Open PowerShell in this repository. Replace the example JDK path below with the actual installed JDK 17 directory; change the SDK path if your installation uses another location:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
java -version
adb version
.\gradlew.bat --version
```

These environment changes apply to this PowerShell session. Java should report version 17, and the wrapper should report Gradle 8.7. If `local.properties` already defines `sdk.dir`, ensure it points to the same SDK. See [Android environment variables](https://developer.android.com/tools/variables).

**Known compatibility gap:** this repository uses Android Gradle plugin 8.5.2 with `compileSdk` and `targetSdk` 36. AGP 8.5 officially supports up to API 34. Installing SDK 36 does not resolve that mismatch. Record any build warning or failure; a compatible plugin/wrapper upgrade is separate work. Do not treat suppressing the warning as validation. See [AGP 8.5 compatibility](https://developer.android.com/build/releases/agp-8-5-0-release-notes).

## 2. Build and run lint

From the repository root:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

The first build needs internet access to download Gradle and dependencies. Check for `BUILD SUCCESSFUL`; resolve failures before treating this as a validated build.

Outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint report, when generated: `app/build/reports/lint-results-debug.html`

The debug APK is signed automatically for testing. A long-term release should use a retained personal signing key so future updates can use the same identity and signature.

## 3. Install on the phone

### USB installation

Enable Developer options and USB debugging on the phone, connect a data-capable USB cable, and accept the computer's debugging authorization prompt. Windows may need the phone manufacturer's USB driver. See [Android developer options](https://developer.android.com/studio/debug/dev-options) and the [ADB guide](https://developer.android.com/tools/adb).

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.notificationforwarder.app/.MainActivity
```

`adb devices` should show the phone as `device`, not `unauthorized`. With multiple devices attached, add `-s SERIAL` immediately after `adb` in each command.

### APK transfer alternative

Copy `app-debug.apk` to the phone and open it from the file manager. Allow installation from that source if Android asks. Launch Notification Forwarder after installation. USB debugging is unnecessary for this method.

## 4. Configure phone access and delivery

1. Open the new app and go to **Home**.
2. Tap **Open Access Settings** and enable notification access for it. If Android blocks this for a sideloaded app, check its App info menu for **Allow restricted settings**, then retry; availability varies by Android version.
3. Tap **Open Battery Settings** and exempt the app from battery optimization where available. On Samsung, also check that it is not in Sleeping or Deep sleeping apps; menu wording varies by One UI version.
4. In **Filter**, add the exact package name of a harmless test app to **Packages list (comma/newline)**. Keep the default retry settings initially. Tap **Save Filter & Retry**.
5. In **Webhook**, enter your phone-reachable HTTPS endpoint, select `POST`, choose `Bearer` authentication and enter the receiver's token. Leave custom headers, query parameters and payload template empty for the first test.
6. Tap **Save Webhook Settings**, then **Test Webhook**. Confirm acceptance on the receiver too.
7. Turn on **Enable forwarding** and save the webhook settings. Confirm the enabled state persists.

Use the final HTTPS URL with a valid certificate: the client rejects HTTP and redirects. `localhost` on the phone means the phone itself, not your PC or Hermes server.

Saving changes to delivery settings clears pending notifications and disables forwarding. Finish configuration before queue testing, then explicitly enable forwarding again after later changes.

## 5. Receiver requirements

For Hermes, configure an authenticated HTTPS route reachable from the phone. Match its route and bearer token in the app. The verified laptop setup uses a temporary ngrok HTTPS tunnel to the loopback receiver; use synthetic notifications until the remaining security and reliability work is complete.

The bundled `webhook/` server can check connectivity:

```powershell
Set-Location webhook
npm ci
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Run the copy command only for initial setup; preserve an existing `.env`. Edit `.env` and replace `WEBHOOK_BEARER_TOKEN` with a long random token, then start the server:

```powershell
npm test
npm start
```

This requires Node.js 24.13.0 or a later Node 24 patch and npm. The built-in SQLite module is marked experimental by Node and may print a warning. By default the server listens on `127.0.0.1:3000`, accepts authenticated `POST /webhook`, stores accepted payloads in `webhook/data/notifications.sqlite`, and provides `GET /health`. Put a phone-reachable HTTPS reverse proxy in front of it; the local HTTP listener alone is not a usable app endpoint. HTTPS hosting and certificate setup depend on your server environment.

The receiver commits each accepted payload before returning success. It stores the generated receipt ID, UTC receipt time and validated JSON in SQLite, with no public read endpoint and no automatic deletion. The Hermes worker then records draft classification and bounded retry state in `hermes_processing`. Repeated deliveries currently create separate rows because the installed Android app has no persistent event ID. The database is not application-encrypted, so use this receiver and worker for synthetic testing only until ingestion and deployment hardening are complete.

To move this receiver to another Windows machine, copy or clone the `webhook/` directory, install Node 24.13.0 or later within the Node 24 line, run `npm ci`, create `.env` from `.env.example` without overwriting an existing file, and set a new local bearer token and database path. Stop the receiver before copying an existing SQLite database. Keep the Node listener on localhost until a later HTTPS reverse-proxy setup is complete.

### Optional Hermes processing for synthetic receipts

The user has completed this synthetic step on the old Windows laptop. For a fresh
machine, once phone delivery succeeds and the `finance-notifications` Hermes profile
can reply, follow [the Hermes worker setup](webhook/HERMES_SETUP.md). The worker uses
the profile's configured model, records draft classifications in SQLite, and
keeps model processing separate from phone acknowledgement. It disables tools
in that dedicated profile. Start with `npm run process:once`; after verifying the
saved result, use `npm run process:watch`.

## 6. Phone acceptance checklist

For automatic Windows sign-in startup and service recovery, see
[the laptop startup guide](webhook/windows/README.md). The repository root includes
double-click Start, Stop and Install Startup commands.

Generate a new notification from the allowlisted test app for each case, using distinct text such as `Forwarder test 001`. Existing notifications are not a reliable capture test. Record receiver timestamps and the app's queue counters.

| Test | Action | Expected result |
| --- | --- | --- |
| Endpoint | Tap **Test Webhook** | App reports success and receiver records a request. This alone does not test notification capture. |
| Capture | Generate a notification from the allowlisted app | Receiver accepts it; Sent counter increases. |
| Filtering | Generate a notification from an app outside the allowlist | No corresponding request or queued item. |
| Screen locked | Lock the phone and generate an allowlisted notification | Delivery occurs without opening the forwarder. |
| Offline queue | Disconnect phone networking and generate a local notification from the allowlisted app | Notification appears as pending. Use a test app that can notify offline. |
| Reconnect | Restore networking without editing settings | Pending item delivers eventually. **Sync Queue** can separately test manual delivery. |
| Reboot | Reboot, unlock once, then generate an allowlisted notification without opening the forwarder | Background capture and delivery resume. |
| Disabled | Disable forwarding, save, and generate another notification | No new forwarded request. Re-enable afterward if continuing. |

WorkManager retries are scheduled, so reconnection need not deliver immediately. Record delays rather than assuming a failure after a few seconds. Do not force-stop the app during the normal background test; force-stop is different from locking the screen.

## 7. Troubleshooting

| Symptom | Check |
| --- | --- |
| Java/JAVA_HOME error | JDK 17 path exists and its `bin/java.exe` is available in this shell. |
| SDK location or license error | `ANDROID_HOME`, any `local.properties` override, installed SDK packages and accepted licenses. |
| Unsupported SDK / dependency build error | Resolve the AGP/SDK compatibility gap in section 1 and preserve the original error output. |
| Phone absent from ADB | Data cable, USB debugging, authorization prompt and Windows USB driver. |
| Signature mismatch on reinstall | Update using the same signing key. Uninstalling the new app clears its local data and requires fresh setup. |
| Test Webhook fails | HTTPS certificate, final route without redirects, server reachability and matching bearer token. |
| Test succeeds but notifications do not forward | Notification access, exact allowlisted package, forwarding enabled and saved, and a newly generated notification. |
| Pending queue never drains | Connectivity, retry timing, receiver responses and queue error codes. Try **Sync Queue**. |
| Duplicate forwarding | Old app still active, repeated notification updates or a receiver needing deduplication. |

## Test record

Latest user-reported result: webhook receipt testing succeeded ("Tested. Looks good")
after instructions to compare `npm run process:status` before/after a harmless
notification and inspect `npm run process:results` from `webhook/`. Exact counts
and output were not supplied. This does not independently confirm reboot/sign-in,
sleep/resume, or resolution of previously failed Hermes processing records.

- Build date / commit:
- Build and lint result:
- Phone / Android / One UI version:
- Receiver environment (no secrets):
- Acceptance cases passed / failed:
- Remaining errors and observed delays:

Keep tokens and real notification contents out of shared test notes.
