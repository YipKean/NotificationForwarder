const assert = require( "node:assert/strict" );
const { test } = require( "node:test" );
const { mkdtempSync, copyFileSync } = require( "node:fs" );
const { tmpdir } = require( "node:os" );
const { join } = require( "node:path" );
const { randomUUID } = require( "node:crypto" );
const { DatabaseSync } = require( "node:sqlite" );
const { createApp, validateConfig } = require( "./server" );
const { openNotificationStore } = require( "./storage" );
const { parseListQuery } = require( "./readRoutes" );
const config = { host: "127.0.0.1", port: 0, webhookPath: "/webhook", bearerToken: "ingestion-test", dashboardToken: "reader-test", databasePath: "unused", jsonLimit: "1mb" };
async function serve( storage, overrides, run ) {
	const server = createApp( { storage, config: { ...config, ...overrides } } ).listen( 0, "127.0.0.1" );
	await new Promise( resolve => server.once( "listening", resolve ) );
	const read = ( path, token = "reader-test" ) => fetch( `http://127.0.0.1:${server.address().port}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} } );
	try { await run( read ); } finally { await new Promise( resolve => server.close( resolve ) ); }
}
test( "read authorization is separate, fails closed and never caches", async () => {
	await serve( null, {}, async read => {
		for ( const route of [ "/api/notifications", "/api/notification-apps", `/api/notifications/${randomUUID()}` ] ) {
			assert.equal( ( await read( route, "" ) ).status, 401 );
			assert.equal( ( await read( route, "ingestion-test" ) ).status, 403 );
			const response = await read( route );
			assert.equal( response.status, 503 );
			assert.equal( response.headers.get( "cache-control" ), "no-store" );
		}
	} );
	await serve( null, { dashboardToken: "" }, async read => assert.equal( ( await read( "/api/notifications" ) ).status, 503 ) );
	assert.equal( validateConfig( { ...config, dashboardToken: config.bearerToken } ), false );
	assert.equal( validateConfig( { ...config, webhookPath: "/api/notifications" } ), false );
} );
test( "legacy upgrade preserves data, stable cursors, projection, filtering and backup", async () => {
	const directory = mkdtempSync( join( tmpdir(), "dashboard-store-" ) );
	const file = join( directory, "events.sqlite" );
	const legacy = new DatabaseSync( file );
	legacy.exec( "create table notification_events (receipt_id text primary key, received_at text not null, payload_json text not null)" );
	const ids = Array.from( { length: 5 }, () => randomUUID() ).sort().reverse();
	const payload = { packageName: "test.app", appName: "Test", title: "Synthetic", text: "x".repeat( 500 ), postedAt: 0, deviceId: "private" };
	for ( const id of ids ) { legacy.prepare( "insert into notification_events values (?, ?, ?)" ).run( id, "2026-09-27T00:00:00.000Z", JSON.stringify( payload ) ); }
	legacy.close();
	const store = openNotificationStore( file );
	await serve( store, {}, async read => {
		let cursor = null;
		const seen = [];
		do {
			const page = await ( await read( `/api/notifications?limit=2${cursor ? `&before=${cursor}` : ""}` ) ).json();
			seen.push( ...page.items.map( item => item.receiptId ) );
			assert.equal( page.items[ 0 ].text.length, 240 );
			assert.equal( page.items[ 0 ].deviceId, undefined );
			cursor = page.nextCursor;
		} while ( cursor );
		assert.deepEqual( seen, ids );
		assert.equal( ( await ( await read( "/api/notifications?packageName=missing" ) ).json() ).items.length, 0 );
		assert.equal( ( await ( await read( `/api/notifications/${ids[ 0 ]}` ) ).json() ).text.length, 500 );
		assert.equal( ( await read( `/api/notifications/${randomUUID()}` ) ).status, 404 );
		assert.equal( ( await ( await read( "/api/notification-apps" ) ).json() ).items.length, 1 );
		for ( const query of [ "limit=0", "limit=101", "limit=2&limit=3", "before=bad", "unknown=yes", "packageName[x]=a" ] ) {
			assert.equal( ( await read( `/api/notifications?${query}` ) ).status, 400, query );
		}
	} );
	store.close();
	const backup = join( directory, "backup.sqlite" ); copyFileSync( file, backup );
	const restored = openNotificationStore( backup );
	assert.equal( restored.list( { limit: 50, packageName: "" } ).items.length, 5 ); restored.close();
	const inspection = new DatabaseSync( file ); assert.equal( inspection.prepare( "pragma user_version" ).get().user_version, 1 ); inspection.close();
} );
test( "list query rejects non-scalar and malformed cursors", () => {
	for ( const query of [ { limit: [ "2", "3" ] }, { before: "" }, { before: Buffer.from( "{}" ).toString( "base64url" ) }, { packageName: "a".repeat( 256 ) } ] ) { assert.throws( () => parseListQuery( query ) ); }
} );
