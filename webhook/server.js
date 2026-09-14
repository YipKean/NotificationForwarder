const crypto = require("crypto");
const express = require("express");
require("dotenv").config();

const app = express();
const HOST = process.env.HOST || "127.0.0.1";
const PORT = Number(process.env.PORT) || 3000;
const WEBHOOK_PATH = process.env.WEBHOOK_PATH || "/webhook";
const BEARER_TOKEN = process.env.WEBHOOK_BEARER_TOKEN || "";
const JSON_LIMIT = process.env.JSON_LIMIT || "1mb";

function requireBearerAuth(req, res, next) {
  const authHeader = req.get("authorization") || "";
  if (!BEARER_TOKEN || !authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ ok: false, message: "Unauthorized.", code: "unauthorized" });
  }

  const suppliedDigest = crypto.createHash("sha256").update(authHeader.slice(7).trim()).digest();
  const expectedDigest = crypto.createHash("sha256").update(BEARER_TOKEN).digest();
  if (!crypto.timingSafeEqual(suppliedDigest, expectedDigest)) {
    return res.status(403).json({ ok: false, message: "Forbidden.", code: "forbidden" });
  }

  return next();
}

app.get("/health", (req, res) => {
  res.json({ ok: true, service: "webhook-api" });
});

app.post(WEBHOOK_PATH, requireBearerAuth, express.json({ limit: JSON_LIMIT }), (req, res) => {
  const receiptId = crypto.randomUUID();
  const receivedAt = new Date().toISOString();
  console.log(JSON.stringify({ receiptId, receivedAt, outcome: "accepted" }));
  return res.status(200).json({ ok: true, message: "Webhook received.", receiptId });
});

app.use((req, res) => {
  res.status(404).json({ ok: false, message: "Not found.", code: "not_found" });
});

app.use((err, req, res, next) => {
  if (err && err.type === "entity.too.large") {
    return res.status(413).json({ ok: false, message: "Request too large.", code: "request_too_large" });
  }
  if (err instanceof SyntaxError && "body" in err) {
    return res.status(400).json({ ok: false, message: "Invalid JSON body.", code: "invalid_json" });
  }
  return res.status(500).json({ ok: false, message: "Internal server error.", code: "internal_error" });
});

function start() {
  if (!BEARER_TOKEN) {
    console.error("WEBHOOK_BEARER_TOKEN is required");
    process.exitCode = 1;
    return null;
  }
  return app.listen(PORT, HOST);
}

if (require.main === module) {
  start();
}

module.exports = { app, start };
