const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { openNotificationStore } = require("./storage");
const { openProcessingStore, validateResult, parseOutput, inferNotification, processNext, runCommand, LEASE_MS } = require("./hermes-worker");

const result = { kind: "non_transaction", amount: null, currency: null, direction: null, merchant: null, summary: "Synthetic test notification." };

function fixture(t) {
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), "hermes-worker-"));
	const databasePath = path.join(directory, "events.sqlite");
	const receiver = openNotificationStore(databasePath);
	receiver.insert("receipt-1", "2026-09-18T00:00:00Z", JSON.stringify({ title: "Test", text: 'Untrusted $(echo secret) `stuff` " quotes' }));
	receiver.close();
	const store = openProcessingStore(databasePath);
	t.after(() => { store.close(); fs.rmSync(directory, { recursive: true, force: true }); });
	return { store, databasePath };
}

test("successful processing persists a result and never reprocesses that receipt after reopening", async t => {
	const { store, databasePath } = fixture(t);
	let calls = 0;
	const infer = async prompt => { calls++; assert.match(prompt, /Untrusted/); return { result, model: "configured-luna" }; };
	assert.equal((await processNext(store, infer)).state, "completed");
	const reopened = openProcessingStore(databasePath);
	try {
		assert.equal((await processNext(reopened, infer)).state, "idle");
		assert.equal(reopened.results()[0].model, "configured-luna");
		assert.deepEqual(JSON.parse(reopened.results()[0].result_json), result);
	} finally { reopened.close(); }
	assert.equal(calls, 1);
});

test("two workers cannot claim the same active receipt; expired leases recover and stale writes fail", t => {
	const { store, databasePath } = fixture(t);
	const second = openProcessingStore(databasePath);
	try {
		const first = store.claim(1000);
		assert.equal(second.claim(1001), undefined);
		const recovered = second.claim(1000 + LEASE_MS);
		assert.equal(recovered.receipt_id, first.receipt_id);
		assert.equal(store.finish(first, result, "luna"), false);
		assert.equal(second.finish(recovered, result, "luna"), true);
	} finally { second.close(); }
});

test("failures back off, remain durable and stop after three attempts", t => {
	const { store } = fixture(t);
	for (let i = 0; i < 3; i++) {
		const now = 1000 + i * 60000;
		const row = store.claim(now);
		store.fail(row, "hermes_failed", now);
		assert.equal(store.claim(now + 1), undefined);
	}
	assert.equal(store.claim(999999), undefined);
	assert.equal(store.results()[0].state, "failed");
	assert.equal(store.results()[0].attempts, 3);
});

test("crashes on every attempt eventually stop retrying", t => {
	const { store } = fixture(t);
	for (let i = 0; i < 3; i++) assert.ok(store.claim(i * LEASE_MS));
	assert.equal(store.claim(3 * LEASE_MS), undefined);
	assert.equal(store.results()[0].state, "failed");
});

test("invalid output and exceptions never persist notification contents as errors", async t => {
	const { store } = fixture(t);
	const outcome = await processNext(store, async () => { throw new Error("private notification and secret token"); });
	assert.equal(outcome.code, "invalid_result");
	assert.equal(store.results()[0].last_error, "invalid_result");
	assert.equal(store.results()[0].result_json, null);
});

test("quiet Hermes output accepts only a complete classification JSON object", () => {
	const output = JSON.stringify(result, null, 2);
	assert.deepEqual(parseOutput("\n" + output + "\r\n"), { model: null, result });
	for (const invalid of ["", "Error: provider failed", "Banner\n" + output,
		"```json\n" + output + "\n```", output + "\n" + output,
		'{"type":"result","exit_code":0,"text":"OK"}']) assert.throws(() => parseOutput(invalid));
});

test("Hermes 0.21 invocation uses quiet stdin chat without unsupported format flags", async () => {
	const prompt = 'untrusted "quotes" $(commands) 你好';
	const runner = async (executable, args, input) => {
		assert.equal(executable, "hermes.exe");
		assert.deepEqual(args, ["--profile", "finance-notifications", "chat", "--ignore-rules",
			"--query-file", "-", "--quiet", "--max-turns", "1"]);
		assert.equal(input, prompt);
		return JSON.stringify(result);
	};
	assert.deepEqual(await inferNotification("hermes.exe", prompt, runner), { result, model: null });
	await assert.rejects(inferNotification("hermes.exe", prompt, async () => { throw new Error("hermes_failed"); }), /hermes_failed/);
});

test("classification rejects invented structure, missing transaction fields and invalid amounts", () => {
	assert.deepEqual(validateResult(result), result);
	for (const change of [{ extra: true }, { kind: "transaction" }, { amount: "-10" }, { amount: "1e6" }, { currency: "RM" }, { summary: "x".repeat(501) }]) {
		assert.throws(() => validateResult({ ...result, ...change }));
	}
	assert.equal(validateResult({ ...result, kind: "transaction", amount: "12.50", currency: "MYR", direction: "debit" }).amount, "12.50");
});

test("subprocess transports quotes, Unicode and shell syntax as literal stdin", async () => {
	const input = '你好 "quoted" $(echo secret) `backticks`\nsecond line';
	const output = await runCommand(process.execPath, ["-e", "process.stdin.pipe(process.stdout)"], input);
	assert.equal(output, input);
	await assert.rejects(runCommand(process.execPath, ["-e", "process.stderr.write('secret'); process.exit(2)"]), /hermes_failed/);
});

test("a hung process is terminated within its timeout", async () => {
	await assert.rejects(runCommand(process.execPath, ["-e", "setInterval(() => {}, 1000)"], "", 300), /hermes_timeout/);
});
