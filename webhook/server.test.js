const assert = require("node:assert/strict");
const { after, before, test } = require("node:test");

process.env.WEBHOOK_BEARER_TOKEN = "test-secret";
process.env.WEBHOOK_PATH = "/webhook";
const { app } = require("./server");

let server;
let baseUrl;
const output = [];
const originalLog = console.log;

before(async () => {
  console.log = (line) => output.push(String(line));
  server = await new Promise((resolve) => {
    const instance = app.listen(0, "127.0.0.1", () => resolve(instance));
  });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  console.log = originalLog;
  await new Promise((resolve) => server.close(resolve));
});

test("requires bearer authentication before parsing the body", async () => {
  const response = await fetch(`${baseUrl}/webhook`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: "not-json"
  });
  assert.equal(response.status, 401);
});

test("sanitizes malformed JSON errors", async () => {
  const response = await fetch(`${baseUrl}/webhook`, {
    method: "POST",
    headers: {
      authorization: "Bearer test-secret",
      "content-type": "application/json"
    },
    body: "not-json"
  });
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    ok: false,
    message: "Invalid JSON body.",
    code: "invalid_json"
  });
});

test("rejects oversized JSON with a fixed response", async () => {
  const response = await fetch(`${baseUrl}/webhook`, {
    method: "POST",
    headers: {
      authorization: "Bearer test-secret",
      "content-type": "application/json"
    },
    body: JSON.stringify({ body: "x".repeat(1024 * 1024) })
  });
  assert.equal(response.status, 413);
  assert.deepEqual(await response.json(), {
    ok: false,
    message: "Request too large.",
    code: "request_too_large"
  });
});

test("returns a receipt and logs no request content", async () => {
  const response = await fetch(`${baseUrl}/webhook?token=URL_SECRET`, {
    method: "POST",
    headers: {
      authorization: "Bearer test-secret",
      "content-type": "application/json",
      "x-api-key": "HEADER_SECRET"
    },
    body: JSON.stringify({ title: "BANKING_SECRET" })
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.match(body.receiptId, /^[0-9a-f-]{36}$/);
  const logged = output.join("\n");
  assert.doesNotMatch(logged, /URL_SECRET|HEADER_SECRET|BANKING_SECRET/);
  assert.match(logged, /accepted/);
});
