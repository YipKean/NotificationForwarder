# Process synthetic notifications with Hermes

The receiver and ngrok continue running as before. This separate worker reads saved
receipts, asks the `finance-notifications` Hermes profile to classify each one, and
saves a validated draft result to `hermes_processing` in the same SQLite database.
It does not post to Telegram, create verified transactions, or change the phone URL.

## On the old Windows laptop

Copy the updated `webhook` source files to the laptop, preserving its `.env` and
`data` directory. The new files are `hermes-worker.js`, `hermes-worker.test.js` and
this guide; `package.json` also changed. These source changes do not require new
npm dependencies. Do not copy another machine's database or credentials over them.

The `finance-notifications` profile must already respond using its configured
provider/model (Luna in this setup). The worker uses that configuration without
guessing or overriding the model identifier. It requires the Hermes CLI's
`chat --query-file - --quiet` support. Hermes 0.21.0 does not support
`--format stream-json`; the worker uses quiet plain-text output and validates the
entire response as JSON instead.

In a third PowerShell window:

```powershell
cd C:\Users\kean5\OneDrive\Desktop\Project\NotificationForwarder\webhook
npm test
npm run process:status
npm run process:once
npm run process:results
```

`process:once` processes one pending receipt, including old saved test receipts.
Expect `state: completed`, then a saved result with `kind: non_transaction` for a
connectivity test. The results command displays the latest ten processing records,
including the JSON result. The `model` field is null because quiet output does not
report model metadata; the profile still selects the model.
No pending events produces `state: idle`.

After that succeeds, keep the worker running:

```powershell
npm run process:watch
```

Generate a new synthetic phone notification. The receiver should acknowledge it
immediately; the worker should later log its receipt ID and `completed`. Use
`process:results` in another window to see the draft classification. Ctrl+C stops
the watch loop after the current request finishes (up to two minutes).

The default executable is `%LOCALAPPDATA%\hermes\bin\hermes.exe`. For another
installation, set a path to an executable, without extra arguments:

```powershell
$env:HERMES_EXECUTABLE = 'C:\path\to\hermes.exe'
npm run process:once
```

## Profile changes and data handling

Before processing, the worker runs the equivalent of:

```powershell
hermes --profile finance-notifications config set agent.disabled_toolsets '["all"]'
```

This persistently disables tools in this dedicated parser profile. Processing runs
also use `--ignore-rules` and a one-turn limit. Keep this profile dedicated to
classification, without custom plugins, shell hooks or MCP integrations.
Notification text is passed through stdin, never a shell command. Results must
match the expected JSON schema; banners, error text, Markdown fences and extra
objects are rejected. Quiet output has no tool-call event stream, so tool
restriction relies on the dedicated profile's disabled toolsets.

Saved notification contents are sent to the profile's configured model provider.
Hermes may retain its own session history. The worker does not send the webhook
bearer token or phone authentication headers. Console status and stored errors
contain only receipt IDs and fixed error codes; `process:results` deliberately
shows model-produced content and is intended for local inspection.

## Recovery and limits

- Each receipt has durable processing state and a claim lease. Completed receipts
  are skipped after restart. Concurrent workers cannot claim an active receipt.
- Failed attempts wait at least 60 seconds, up to three attempts. In `--once`
  mode, run again after the delay; `--watch` retries automatically. A crashed
  worker's receipt becomes eligible after its five-minute lease expires.
- `failed` rows remain available for inspection; they are not silently deleted or
  retried forever. `last_error` identifies launch, timeout or output-validation
  failures. Run the dedicated Hermes profile interactively to diagnose provider
  or authentication errors without printing its raw stderr into receiver logs.
- A crash after model completion but before saving can cause another model call.
  This is at-least-once inference, not exactly-once billing. Duplicate incoming
  phone deliveries still produce different receipts and can both be classified.
- Draft classifications are not a financial ledger. Missing or ambiguous fields
  must go to review; schema validation cannot prove model accuracy.
- Continue using synthetic notifications. Sensitive-content filtering, source
  event IDs, delivery deduplication, storage protection and retention remain
  prerequisites for regular financial notification use.

## Validation status

Automated tests use a fake classifier and real temporary SQLite databases and
subprocesses. They cover saved results, restart recovery, competing claims,
expired leases, bounded retries, invalid output, shell-literal stdin and timeouts.
Actual Luna inference on the old laptop must be verified with `process:once`.
