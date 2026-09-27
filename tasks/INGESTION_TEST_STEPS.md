# Run ingestion integration tests on an Android phone

Latest result (2026-09-26): all nine isolated instrumentation tests passed on the connected Samsung SM-S918B (Android 16), observed through Gradle. This includes the two new outage-retention regressions. The original seven-test pass on 2026-09-22 remains historical user-reported evidence.

These tests use synthetic notifications, temporary databases and isolated preferences. They do not send webhooks or invoke Hermes. No emulator is required.

## 1. Prepare the phone

Enable Developer options, then USB debugging. Connect the unlocked phone using a data-capable USB cable and approve the computer's debugging prompt.

Do not uninstall or change settings in your existing Notification Forwarder app. The command below builds a separate test app with application ID `com.notificationforwarder.app.ingestiontest`, which has its own Android storage and Keystore identity. Do not grant this test app notification access or configure forwarding.

## 2. Prepare PowerShell

Open PowerShell in the project folder and run:

```powershell
Set-Location 'C:\Users\kean5\OneDrive\Desktop\Project\NotificationForwarder'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\kean5\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'C:\Users\kean5\.gradle'
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

The phone should appear with status `device`. If it says `unauthorized`, unlock it and approve the prompt. If no device appears, check the cable, USB debugging and Windows driver. Disconnect other Android devices and close any running emulator before the next step.

## 3. Run the tests

```powershell
.\gradlew.bat -PisolatedIngestionTests=true :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.notificationforwarder.app.QueueMigrationInstrumentedTest' --console=plain
```

Keep the phone connected and unlocked. Gradle builds and installs the separate app and its test runner, then executes the test class. The isolation property is mandatory: the tests check the target package before touching a database or encryption key. This command does not upgrade your existing forwarder app.

Expected result: **9 tests passed**, zero failures, and `BUILD SUCCESSFUL`. Compilation alone is not sufficient: the output must include the connected instrumentation test task, and the report must show executed tests.

Open the report:

```powershell
Start-Process '.\app\build\reports\androidTests\connected\debug\index.html'
```

If your Gradle version places the report elsewhere, use the report path printed in its output. Send the final command output and any failing test names/assertions back for review. These tests use synthetic content; do not include real notifications, tokens or device identifiers in shared logs.

## 4. Optional cleanup

After reviewing the result, remove only the two isolated test packages:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" uninstall com.notificationforwarder.app.ingestiontest.test
& "$env:ANDROID_HOME\platform-tools\adb.exe" uninstall com.notificationforwarder.app.ingestiontest
```

Do not uninstall `com.notificationforwarder.app`; that is the existing app. You can disable USB debugging after testing.

## Scope

The outage regression applies four transient failures to five encrypted rows with
a backoff growth limit of two, checks retention and unchanged ciphertext, reopens
the database, then drains three repository batches. It advances row eligibility
explicitly; it does not prove automatic WorkManager scheduling or live HTTP recovery.
The second regression checks permanent-failure deletion and expiry after transient
failure. Run the separate controlled device acceptance below for scheduling evidence.

## Controlled device outage acceptance

### Snackbar interaction check

Observed on SM-S918B on 2026-09-26 in the isolated app: Webhook Save feedback
displayed the accessible Dismiss control; close, left swipe and right swipe
removed it. Ten rapid Save taps left no feedback backlog after five seconds.
These checks exercised the validation-error message through the shared host.
Large-font/TalkBack and successful-save copy checks remain manual follow-up.

In the isolated app, tap Save repeatedly on the Webhook and Filter screens.
Verify only the latest result remains, with no old messages appearing afterward.
Dismiss via the close button, then swipe left and right on fresh messages. Verify
each new message starts in its normal position and clears without affecting the
save. Check a long message at large font size and the close button with TalkBack.
Use synthetic settings only; changing live delivery settings can clear its queue.

### Outage and recovery

Use an isolated test-app configuration and a separate authenticated test receiver
with temporary SQLite storage, no Hermes worker and an HTTPS tunnel that stays up
throughout. Configure these before queueing; changing the endpoint or toggling
forwarding clears queued content. Do not use Stop Forwarder to simulate downtime.

Start the supplied receiver in a separate PowerShell window from the repository:

```powershell
$env:OUTAGE_TEST_TOKEN = [guid]::NewGuid().ToString()
node .\webhook\outage-receiver.js
```

Configure a separate HTTPS tunnel to localhost:3301 and use its `/webhook` URL
and that test token in the isolated app. Keep the token private; it can be copied
from the local environment before starting Node. Do not reuse or stop the live
receiver tunnel. The receiver begins in outage mode and prints only response
status/timestamps and receipt metadata. Type `recover` in its console to restore
storage, or `outage` to fail it again. Ctrl+C stops it. Its printed temporary
database contains only synthetic data and can be removed after stopping.

The earlier instruction not to configure the isolated app applies to the
repository suite; configure it only for this separate manual acceptance phase.

1. Configure batch size 2, backoff growth limit 1 and retention 1 hour. Use only
   synthetic payment notices from an allowlisted source.
2. Leave the isolated receiver in its initial 503 mode while keeping
   HTTP and the tunnel running. Queue five distinct synthetic notifications.
3. Wait through at least four attempts per item. Collect only local row IDs,
   attemptCount, nextRetryAt, status and lastErrorCode through the debug database
   inspector. Never export encrypted payloads, decoded content, headers or tokens.
   All five must remain PENDING; Failed must not increase. The minimum row retry
   delay is 60–64 seconds with this setting; Android may delay work further.
4. Type `recover` in the receiver console without changing app settings, creating
   notifications or tapping Sync Queue. Verify five distinct receipts and an
   empty Android queue after at least three automatic batches. Capture timestamps
   and counts, including a worker run while every row is still future-due.
5. Repeat with the screen locked, then test process restart and phone reboot
   separately. Verify unchanged event identity through replay deduplication.
6. Test tunnel-host network loss or sleep separately from receiver-only 503s.
   ngrok returns 404 with `ngrok-error-code: ERR_NGROK_3200` when its endpoint is
   offline. The updated client must retain these rows with error
   `HTTP 404 (tunnel_offline)`, then drain automatically after the tunnel returns.
   Capture safe errors with `adb logcat -s QueueDelivery:I`; do not dump all
   application/system logs. Ordinary 404 without this header remains terminal.

The source fix and repository tests do not establish these device results. A phone
was not connected when the regression work began; retain this gate until observed.

This checks Room migration, encrypted legacy queue handling and repository event identity. Actual notification-listener capture, HTTPS delivery, response-loss replay on the phone, Samsung background behavior and reboot acceptance remain a separate end-to-end checklist in `SETUP.md`.
