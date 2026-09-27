const test = require( "node:test" );
const assert = require( "node:assert/strict" );
const fs = require( "node:fs" );
const os = require( "node:os" );
const path = require( "node:path" );
const { randomUUID } = require( "node:crypto" );
const { once } = require( "node:events" );
const { createOutageReceiver } = require( "./outage-receiver" );

test( "controlled outage returns repeated 503 then durably accepts and deduplicates five events", async () => {
	const directory = fs.mkdtempSync( path.join( os.tmpdir(), "forwarder-outage-test-" ) );
	const token = randomUUID();
	const receiver = createOutageReceiver( { token, databasePath: path.join( directory, "test.sqlite" ) } );
	const server = receiver.app.listen( 0, "127.0.0.1" );
	await once( server, "listening" );
	try {
		const url = `http://127.0.0.1:${server.address().port}/webhook`;
		const payloads = Array.from( { length: 5 }, ( _, index ) => ( {
			schemaVersion: 2, eventId: randomUUID(), packageName: "com.example.synthetic",
			appName: "Synthetic", title: "Payment successful", text: `RM ${index + 1} paid`,
			bigText: null, postedAt: Date.now()
		} ) );
		const send = ( payload, bearer = token ) => fetch( url, {
			method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${bearer}` },
			body: JSON.stringify( payload )
		} );
		assert.equal( ( await send( payloads[0], "wrong-token" ) ).status, 403 );
		for ( let attempt = 0; attempt < 4; attempt++ ) {
			for ( const payload of payloads ) {
				const response = await send( payload );
				assert.equal( response.status, 503 );
				assert.equal( ( await response.json() ).code, "storage_unavailable" );
			}
		}
		receiver.setOutage( false );
		const receipts = new Set();
		for ( const payload of payloads ) {
			const accepted = await send( payload );
			assert.equal( accepted.status, 200 );
			const body = await accepted.json();
			assert.equal( body.duplicate, false );
			receipts.add( body.receiptId );
			const replay = await send( payload );
			assert.equal( replay.status, 200 );
			const duplicate = await replay.json();
			assert.equal( duplicate.duplicate, true );
			assert.equal( duplicate.receiptId, body.receiptId );
		}
		assert.equal( receipts.size, 5 );
	} finally {
		await new Promise( ( resolve ) => server.close( resolve ) );
		receiver.close();
		fs.rmSync( directory, { recursive: true, force: true } );
	}
} );
