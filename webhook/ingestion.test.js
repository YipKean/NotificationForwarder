const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");
const http = require("node:http");
const { Worker } = require("node:worker_threads");
const { DatabaseSync } = require("node:sqlite");
const { createApp, loadConfig, validateConfig } = require("./server");
const { openNotificationStore } = require("./storage");
const { openProcessingStore, processNext } = require("./hermes-worker");
const { CATALOGUE_VERSION, evaluate, normalize } = require("./sensitive-notification-filter");
const fixtures = require("../test-fixtures/sensitive-notifications.json");

function payload( overrides = {} ) {
	return { schemaVersion: 2, eventId: crypto.randomUUID(), packageName: "com.example.bank", appName: "Synthetic Bank",
		title: "Payment successful", text: "MYR 18.90 paid", bigText: null, postedAt: 1789441200000, ...overrides };
}

function temporaryDatabase( t ) {
	const directory = fs.mkdtempSync( path.join( os.tmpdir(), "ingestion-test-" ) );
	t.after( () => {
		assert.equal( path.dirname( path.resolve( directory ) ), path.resolve( os.tmpdir() ) );
		assert( path.basename( directory ).startsWith( "ingestion-test-" ) );
		fs.rmSync( directory, { recursive: true, force: true } );
	} );
	return path.join( directory, "events.sqlite" );
}

async function withReceiver( databasePath, callback, overrides = {}, decorate = store => store ) {
	const config = { ...loadConfig( { WEBHOOK_BEARER_TOKEN: "synthetic-test-secret" } ), port: 0, databasePath, ...overrides };
	const store = openNotificationStore( databasePath );
	const server = createApp( { config, storage: decorate( store ) } ).listen( 0, "127.0.0.1" );
	await new Promise( ( resolve, reject ) => { server.once( "listening", resolve ); server.once( "error", reject ); } );
	const url = `http://127.0.0.1:${server.address().port}/webhook`;
	const headers = { authorization: `Bearer ${config.bearerToken}`, "content-type": "application/json" };
	const post = async value => {
		const response = await fetch( url, { method: "POST", headers, body: JSON.stringify( value ) } );
		return { status: response.status, body: await response.json() };
	};
	try { return await callback( { post, url, headers, store } ); }
	finally { await new Promise( resolve => server.close( resolve ) ); store.close(); }
}

function rows( databasePath ) {
	const db = new DatabaseSync( databasePath, { timeout: 5000 } );
	try { return db.prepare( "SELECT * FROM notification_events ORDER BY received_at, receipt_id" ).all(); }
	finally { db.close(); }
}

test( "shared fixtures exercise the production catalogue with stable rule IDs", () => {
	assert.equal( CATALOGUE_VERSION, 1 );
	for ( const fixture of fixtures ) {
		const result = evaluate( payload( fixture ) );
		assert.equal( result !== null, fixture.blocked, fixture.name );
		assert.equal( result?.ruleId ?? null, fixture.ruleId, fixture.name );
	}
	assert.equal( normalize( " Ｏ\u2060ＴＰ\u0085 code " ), "otp code" );
	const packageRule = [ { ruleId: "source-example", category: "security", packages: [ "com.example.bank" ], pattern: /example/ } ];
	assert.equal( evaluate( payload( { text: "example" } ), packageRule ).ruleId, "source-example" );
	assert.equal( evaluate( payload( { packageName: "com.other", text: "example" } ), packageRule ), null );
} );

test( "shared fixtures reach HTTP filtering before insertion and sensitive content never enters logs", async t => {
	const databasePath = temporaryDatabase( t );
	const logs = [];
	const originalLog = console.log;
	console.log = line => logs.push( String( line ) );
	try {
		await withReceiver( databasePath, async ( { post } ) => {
			for ( const fixture of fixtures ) {
				const response = await post( payload( { title: fixture.title, text: fixture.text, bigText: fixture.bigText } ) );
				assert.equal( response.status, fixture.blocked ? 422 : 200, fixture.name );
				if ( fixture.blocked ) {
					assert.deepEqual( response.body, { ok: false, message: "Sensitive notification rejected.", code: "sensitive_notification" } );
				}
			}
		} );
	} finally { console.log = originalLog; }
	assert.equal( rows( databasePath ).length, fixtures.filter( item => !item.blocked ).length );
	assert.doesNotMatch( logs.join( "\n" ), /123456|654321|synthetic-test-secret|Kopitiam|OTP|pengesahan/ );
} );

test( "strict v2 validation rejects bad identities, types, sizes and client source overrides", async t => {
	const databasePath = temporaryDatabase( t );
	await withReceiver( databasePath, async ( { post } ) => {
		for ( const invalid of [
			{ schemaVersion: undefined }, { schemaVersion: 1 }, { schemaVersion: "2" },
			{ eventId: undefined }, { eventId: null }, { eventId: 5 }, { eventId: "not-a-uuid" },
			{ eventId: "4a4a2f3c-929b-1988-896c-790733d68237" },
			{ eventId: "4a4a2f3c-929b-4988-796c-790733d68237" },
			{ bigText: 42 }, { bigText: [] }, { bigText: "x".repeat( 65537 ) },
			{ sourceId: "other-phone" }, { source_id: "other-phone" }
		] ) assert.equal( ( await post( payload( invalid ) ) ).status, 400 );
		assert.equal( rows( databasePath ).length, 0 );
		assert.equal( ( await post( payload( { bigText: "x".repeat( 65536 ) } ) ) ).status, 200 );
	} );
	for ( const sourceId of [ "", " ", null, 42 ] ) {
		assert.equal( validateConfig( { ...loadConfig( { WEBHOOK_BEARER_TOKEN: "test" } ), sourceId } ), false );
	}
	assert.equal( loadConfig( {} ).sourceId, "personal-phone" );
	assert.equal( validateConfig( loadConfig( { WEBHOOK_BEARER_TOKEN: "test", WEBHOOK_SOURCE_ID: "" } ) ), false );
} );

test( "expanded text is exact and equivalent retries canonicalize UUID casing, order and missing bigText", async t => {
	const databasePath = temporaryDatabase( t );
	const expanded = 'MYR 18.90 paid to "Kopitiam" 😀\nReference {postedAt}\t\u0001\\end';
	const event = payload( { bigText: expanded } );
	await withReceiver( databasePath, async ( { post } ) => {
		const first = await post( event );
		const reordered = Object.fromEntries( Object.entries( event ).reverse() );
		const retry = await post( { ...reordered, eventId: event.eventId.toUpperCase() } );
		assert.equal( first.status, 200 );
		assert.equal( first.body.duplicate, false );
		assert.equal( retry.body.duplicate, true );
		assert.equal( retry.body.receiptId, first.body.receiptId );
		const missing = payload( { bigText: undefined } );
		const accepted = await post( missing );
		const withNull = await post( { ...missing, bigText: null } );
		assert.equal( withNull.body.receiptId, accepted.body.receiptId );
		assert.equal( withNull.body.duplicate, true );
	} );
	const stored = rows( databasePath ).find( row => row.event_id === event.eventId );
	assert.deepEqual( JSON.parse( stored.payload_json ), event );
} );

test( "conflicting content never overwrites an event, while distinct event IDs remain distinct", async t => {
	const databasePath = temporaryDatabase( t );
	const event = payload();
	await withReceiver( databasePath, async ( { post } ) => {
		await post( event );
		for ( const changed of [ { text: "MYR 29.90 paid" }, { bigText: "New details" }, { postedAt: event.postedAt + 1 }, { packageName: "com.other" } ] ) {
			const response = await post( { ...event, ...changed } );
			assert.equal( response.status, 409 );
			assert.equal( response.body.code, "event_id_conflict" );
		}
		assert.equal( rows( databasePath ).length, 1 );
		assert.deepEqual( JSON.parse( rows( databasePath )[ 0 ].payload_json ), event );
		assert.equal( ( await post( { ...event, eventId: crypto.randomUUID() } ) ).status, 200 );
	} );
	assert.equal( rows( databasePath ).length, 2 );
} );

test( "unread acknowledgement, restart, token rotation and concurrent HTTP retries keep one receipt", async t => {
	const databasePath = temporaryDatabase( t );
	const event = payload();
	await withReceiver( databasePath, async ( { url, headers } ) => {
		// Discard the response without parsing its acknowledgement; the sender must replay.
		await new Promise( ( resolve, reject ) => {
			const request = http.request( url, { method: "POST", headers }, response => { response.destroy(); resolve(); } );
			request.on( "error", reject );
			request.end( JSON.stringify( event ) );
		} );
	} );
	const receiptId = rows( databasePath )[ 0 ].receipt_id;
	await withReceiver( databasePath, async ( { post } ) => {
		const replies = await Promise.all( Array.from( { length: 12 }, () => post( event ) ) );
		for ( const reply of replies ) {
			assert.equal( reply.status, 200 );
			assert.equal( reply.body.receiptId, receiptId );
			assert.equal( reply.body.duplicate, true );
		}
	}, { bearerToken: "rotated-secret" } );
	assert.equal( rows( databasePath ).length, 1 );
	await withReceiver( databasePath, async ( { post } ) => {
		const second = await post( event );
		assert.equal( second.status, 200 );
		assert.notEqual( second.body.receiptId, receiptId );
	}, { sourceId: "second-phone" } );
	assert.equal( rows( databasePath ).length, 2 );
} );

test( "independent simultaneous SQLite connections resolve exactly one inserted event", async t => {
	const databasePath = temporaryDatabase( t );
	openNotificationStore( databasePath ).close();
	const event = payload();
	const payloadJson = JSON.stringify( event );
	const payloadHash = crypto.createHash( "sha256" ).update( payloadJson ).digest( "hex" );
	const script = `
		const { parentPort, workerData } = require('node:worker_threads');
		const { openNotificationStore } = require(workerData.modulePath);
		const store = openNotificationStore(workerData.databasePath);
		parentPort.postMessage('ready');
		parentPort.once('message', () => {
			try { parentPort.postMessage(store.insertOrResolve('phone', workerData.eventId, workerData.receiptId, '2026-09-19', workerData.payloadJson, workerData.payloadHash)); }
			finally { store.close(); parentPort.close(); }
		});`;
	const workers = Array.from( { length: 4 }, () => new Worker( script, { eval: true, workerData: {
		modulePath: require.resolve( "./storage" ), databasePath, eventId: event.eventId,
		receiptId: crypto.randomUUID(), payloadJson, payloadHash
	} } ) );
	try {
		await Promise.all( workers.map( worker => new Promise( ( resolve, reject ) => {
			worker.once( "message", resolve ); worker.once( "error", reject );
		} ) ) );
		const results = workers.map( worker => new Promise( ( resolve, reject ) => {
			worker.once( "message", resolve ); worker.once( "error", reject );
		} ) );
		workers.forEach( worker => worker.postMessage( "go" ) );
		const replies = await Promise.all( results );
		assert.equal( replies.filter( reply => !reply.duplicate ).length, 1 );
		assert.equal( new Set( replies.map( reply => reply.receiptId ) ).size, 1 );
		assert.equal( rows( databasePath ).length, 1 );
	} finally { await Promise.all( workers.map( worker => worker.terminate() ) ); }
} );

test( "receiver migration preserves historical receipts and processing state and is repeatable", t => {
	const databasePath = temporaryDatabase( t );
	const db = new DatabaseSync( databasePath );
	db.exec( "CREATE TABLE notification_events (receipt_id TEXT PRIMARY KEY NOT NULL, received_at TEXT NOT NULL, payload_json TEXT NOT NULL)" );
	db.prepare( "INSERT INTO notification_events VALUES (?, ?, ?)" ).run( "legacy", "2026-09-18", '{"text":"legacy synthetic"}' );
	db.close();
	const worker = openProcessingStore( databasePath );
	const legacy = worker.claim();
	worker.finish( legacy, { kind: "non_transaction" }, "mock" );
	worker.close();
	for ( let attempt = 0; attempt < 2; attempt++ ) openNotificationStore( databasePath ).close();
	const stored = rows( databasePath )[ 0 ];
	assert.equal( stored.receipt_id, "legacy" );
	assert.equal( stored.payload_json, '{"text":"legacy synthetic"}' );
	assert.equal( stored.event_id, null );
	assert.equal( stored.source_id, null );
	assert.equal( stored.payload_hash, null );
	const reopened = openProcessingStore( databasePath );
	try { assert.equal( reopened.results()[ 0 ].state, "completed" ); }
	finally { reopened.close(); }
} );

test( "failed schema migration rolls back added columns and preserves historical rows", t => {
	const databasePath = temporaryDatabase( t );
	const db = new DatabaseSync( databasePath );
	db.exec( "CREATE TABLE notification_events (receipt_id TEXT PRIMARY KEY, received_at TEXT, payload_json TEXT, source_id TEXT, event_id TEXT)" );
	db.exec( "INSERT INTO notification_events VALUES ('one', 'date', '{}', 'phone', 'same'), ('two', 'date', '{}', 'phone', 'same')" );
	db.close();
	assert.throws( () => openNotificationStore( databasePath ) );
	const check = new DatabaseSync( databasePath );
	try {
		assert.equal( check.prepare( "SELECT COUNT(*) AS count FROM notification_events" ).get().count, 2 );
		assert( !check.prepare( "PRAGMA table_info(notification_events)" ).all().some( column => column.name === "payload_hash" ) );
	} finally { check.close(); }
} );

test( "failed commit is not acknowledged and a later replay can be accepted", async t => {
	const databasePath = temporaryDatabase( t );
	openNotificationStore( databasePath ).close();
	const db = new DatabaseSync( databasePath );
	db.exec( "CREATE TRIGGER fail_insert BEFORE INSERT ON notification_events BEGIN SELECT RAISE(ABORT, 'PRIVATE_DB_ERROR'); END" );
	const event = payload();
	try {
		await withReceiver( databasePath, async ( { post } ) => {
			const failed = await post( event );
			assert.deepEqual( failed, { status: 503, body: { ok: false, message: "Storage unavailable.", code: "storage_unavailable" } } );
			assert.equal( rows( databasePath ).length, 0 );
			db.exec( "DROP TRIGGER fail_insert" );
			assert.equal( ( await post( event ) ).status, 200 );
		} );
	} finally { db.close(); }
} );

test( "duplicate ingestion leaves one Hermes processing identity and expanded text reaches mocked inference", async t => {
	const databasePath = temporaryDatabase( t );
	const event = payload( { bigText: 'MYR 18.90 paid to "ABC"\nUntrusted {text} 😀' } );
	await withReceiver( databasePath, async ( { post } ) => {
		const first = await post( event );
		const worker = openProcessingStore( databasePath );
		let calls = 0;
		try {
			const result = await processNext( worker, async prompt => {
				calls++;
				assert.deepEqual( JSON.parse( prompt.split( "NOTIFICATION_JSON:\n" )[ 1 ] ), event );
				return { model: "mock", result: { kind: "non_transaction", amount: null, currency: null, direction: null, merchant: null, summary: "Synthetic" } };
			} );
			assert.equal( result.state, "completed" );
			assert.equal( ( await post( event ) ).body.receiptId, first.body.receiptId );
			assert.equal( ( await processNext( worker, async () => { calls++; throw new Error( "unexpected inference" ); } ) ).state, "idle" );
			assert.equal( calls, 1 );
			assert.equal( worker.results().length, 1 );
			assert.equal( worker.results()[ 0 ].attempts, 1 );
		} finally { worker.close(); }
	} );
} );
