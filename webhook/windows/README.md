# Windows laptop startup and recovery

The `NotificationForwarder-Laptop` Windows scheduled task runs the receiver,
ngrok and the Hermes watch worker under your signed-in Windows account. It uses
the existing `.env`, database, ngrok authentication and Hermes profile.

From the repository root:

- **Start Forwarder.cmd** starts the installed task in the background. Repeated
  clicks do not start extra copies.
- **Stop Forwarder.cmd** stops the task and its entire process tree and disables
  automatic startup. **Start Forwarder.cmd** enables automatic startup again.
- **Forwarder Status.cmd** shows the task state, receiver health, tunnel URL and
  recent lifecycle logs.
- **Install Startup.cmd** installs or updates the sign-in task. Run again after
  moving the repository. The recovery check starts it within approximately a minute.

The task starts **at sign-in**, not before login. It can run while the screen is
locked, but the laptop must remain awake and connected for delivery. Sleep,
hibernation, shutdown and signing out interrupt service. No power settings are
changed by these scripts. Set Windows sleep to Never while plugged in if this
laptop is intended to receive continuously.

## Recovery behavior

The supervisor checks every five seconds and retries exited services after a
15-second delay. Three failed receiver health checks trigger a receiver restart.
Task Scheduler has a one-minute repeating trigger to recover a stopped supervisor;
already-running instances are ignored. Failure retries are also configured at one
minute, up to 999 times, but forced termination did not trigger those reliably on
this laptop, so the repeating trigger provides the recovery guarantee while signed in.
The task has no execution time limit and may run on battery. A Windows Job Object
terminates its owned processes when the supervisor exits or is killed, so a
supervisor restart does not leave old receiver, tunnel or worker processes behind.

This checks process survival and receiver health; it does not detect a live but
stuck ngrok tunnel, prove public delivery, or validate Hermes provider availability.
Hermes requests retain the worker's existing timeout, retries and five-minute
claim leases. Abrupt interruption during inference may cause a repeated model
call after lease expiry. Completed receipts are skipped. Incoming notification
deduplication and sensitive filtering are separate, outstanding work.

## Inspect locally

```powershell
Get-ScheduledTask -TaskName NotificationForwarder-Laptop
Get-ScheduledTaskInfo -TaskName NotificationForwarder-Laptop
Get-Content .\webhook\logs\supervisor.log -Tail 30
Invoke-RestMethod http://127.0.0.1:3000/health
(Invoke-RestMethod http://127.0.0.1:4040/api/tunnels).tunnels.public_url
node .\webhook\hermes-worker.js --status
```

Task result `267009` means the task is running. Logs contain lifecycle events,
process IDs and exit codes; child console output is discarded. The log rotates
at approximately 1 MB with one backup. For detailed troubleshooting, stop the
task and run the failing service manually, then stop that manual service before
starting the task again. Never run the old three manual start commands alongside
the scheduled task.

## Machine-specific configuration

`settings.json` (ignored by Git) stores executable paths and the assigned ngrok
HTTPS hostname, with no bearer token. A template is in `settings.example.json`.
The receiver port comes from its existing configuration; HOST must remain
`127.0.0.1`. Preserve the assigned ngrok hostname so the phone URL stays the same.

On this laptop the Microsoft Store ngrok executable could not be assigned to a
Windows Job Object (Windows error 5). An identical copy of that installed binary
is used in `bin/ngrok.exe`, also ignored by Git. This copy is independent of Store
updates. To refresh it after updating ngrok, stop the task, run the following in
**Windows PowerShell**, then start the task:

```powershell
$package = Get-AppxPackage -Name ngrok.ngrok
Copy-Item -LiteralPath (Join-Path $package.InstallLocation 'ngrok.exe') -Destination .\webhook\windows\bin\ngrok.exe -Force
```

Keep this project folder available locally (including through OneDrive) at sign-in.
The scheduled task uses absolute paths. No Windows account password is stored.

To disable automatic startup without deleting configuration:

```powershell
Disable-ScheduledTask -TaskName NotificationForwarder-Laptop
Stop-ScheduledTask -TaskName NotificationForwarder-Laptop
```

To remove the task:

```powershell
Stop-ScheduledTask -TaskName NotificationForwarder-Laptop
Unregister-ScheduledTask -TaskName NotificationForwarder-Laptop -Confirm:$false
```

Task settings reference: [Microsoft ScheduledTasks documentation](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtasksettingsset).

## Verification on this laptop — 2026-09-18

- All 27 receiver/worker automated tests passed.
- All three managed services restarted after forced termination.
- Killing the supervisor removed its child services; the repeating scheduled
  trigger restored the supervisor and all three services.
- Stop disabled the task and removed its children; Start re-enabled it.
- Repeated starts left exactly one supervisor with three service processes.
- Local and public HTTPS `/health` checks passed; the assigned ngrok URL stayed
  unchanged. Services were left running.
- The user subsequently confirmed the reboot test was completed in the previous session (2026-09-18 clarification). The separate sleep/resume test remains unconfirmed.
- Final database status was 15 completed receipts and 3 `hermes_failed` receipts.
  Failed processing records were not reset or retried by this setup work.

Subsequent user confirmation: the webhook receipt test "looks good" after guidance
to compare `process:status` counts and inspect `process:results`. This is
user-reported acceptance; no updated counts or explicit reboot/sleep-resume result
were provided. The outstanding checks above remain open.
