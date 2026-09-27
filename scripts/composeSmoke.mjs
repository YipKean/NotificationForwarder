import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
const project = `notification-smoke-${process.pid}`;
const env = { ...process.env, WEBHOOK_BEARER_TOKEN: randomBytes( 24 ).toString( "hex" ), DASHBOARD_BEARER_TOKEN: randomBytes( 24 ).toString( "hex" ), WEBHOOK_PATH: "/webhook", RECEIVER_PORT: process.env.SMOKE_RECEIVER_PORT || "3200", DASHBOARD_PORT: process.env.SMOKE_DASHBOARD_PORT || "3201" };
function compose( ...args ) {
	const result = spawnSync( "docker", [ "compose", "-p", project, ...args ], { env, encoding: "utf8" } );
	if ( result.status !== 0 ) { throw new Error( result.stderr || "Compose failed." ); }
	return result.stdout.trim();
}
const origin = `http://127.0.0.1:${env.DASHBOARD_PORT}`;
const headers = { Authorization: `Bearer ${env.DASHBOARD_BEARER_TOKEN}` };
try {
	compose( "config", "--quiet" );
	console.log( "Building isolated smoke-test images…" );
	compose( "build" );
	compose( "up", "-d", "--wait" );
	for ( const service of [ "dashboard", "receiver" ] ) { assert.notEqual( compose( "exec", "-T", service, "id", "-u" ), "0" ); }
	const accepted = await fetch( `http://127.0.0.1:${env.RECEIVER_PORT}/webhook`, { method: "POST", headers: { Authorization: `Bearer ${env.WEBHOOK_BEARER_TOKEN}`, "Content-Type": "application/json" }, body: JSON.stringify( { packageName: "test.docker", appName: "Docker test", title: "Synthetic receipt", text: "Persistence check", postedAt: Date.now() } ) } );
	assert.equal( accepted.status, 200 );
	const { receiptId } = await accepted.json();
	const detail = await fetch( `${origin}/api/notifications/${receiptId}`, { headers } );
	assert.equal( detail.status, 200 ); assert.equal( ( await detail.json() ).text, "Persistence check" );
	assert.equal( ( await fetch( `${origin}/api/notifications` ) ).status, 401 );
	assert.equal( ( await fetch( `${origin}/api/notifications`, { headers: { Authorization: `Bearer ${env.WEBHOOK_BEARER_TOKEN}` } } ) ).status, 403 );
	const html = await ( await fetch( origin ) ).text();
	const asset = html.match( /src="([^" ]+\.js[^" ]*)"/ )?.[ 1 ]; assert.ok( asset );
	assert.equal( ( await fetch( new URL( asset.replaceAll( "&amp;", "&" ), origin ) ) ).status, 200 );
	compose( "up", "-d", "--force-recreate", "--wait" );
	assert.equal( ( await fetch( `${origin}/api/notifications/${receiptId}`, { headers } ) ).status, 200 );
	compose( "stop", "receiver" );
	assert.equal( ( await fetch( `${origin}/api/notifications`, { headers } ) ).status, 503 );
	console.log( "PASS: non-root containers, ingest/read/detail, auth isolation, assets, persistence after recreation, receiver outage." );
} finally {
	// This unique test project contains only synthetic data created by this run.
	compose( "down", "--volumes" );
}
