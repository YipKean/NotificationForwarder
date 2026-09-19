const assert = require( "node:assert/strict" );
const fs = require( "node:fs" );
const net = require( "node:net" );
const os = require( "node:os" );
const path = require( "node:path" );
const { spawn } = require( "node:child_process" );
const { DatabaseSync } = require( "node:sqlite" );
const { after, test } = require( "node:test" );

const { createApp, loadConfig, start, validateConfig } = require( "./server" );
const { openNotificationStore, resolveDatabasePath } = require( "./storage" );

const webhookDirectory = __dirname;
const openServers = new Set();

after( async () => {
	await Promise.all( [ ...openServers ].map( ( server ) => new Promise( ( resolve ) => server.close( resolve ) ) ) );
} );

function makeConfig( databasePath, overrides = {} ) {
	return {
		host: "127.0.0.1",
		port: 0,
		webhookPath: "/webhook",
		bearerToken: "test-secret",
		jsonLimit: "1mb",
		databasePath,
		sourceId: "personal-phone",
		...overrides
	};
}

function makePayload( overrides = {} ) {
	return {
		schemaVersion: 2,
		eventId: "4a4a2f3c-929b-4988-896c-790733d68237",
		deviceId: "test-device",
		packageName: "com.test.package",
		appName: "Test App",
		title: "Forwarder test",
		text: "Synthetic notification 001",
		bigText: null,
		postedAt: 1735689600000,
		notificationKey: "test-notification-key",
		...overrides
	};
}

function makeTemporaryDatabasePath() {
	const directory = fs.mkdtempSync( path.join( os.tmpdir(), "notification-forwarder-" ) );
	return path.join( directory, "events.sqlite" );
}

function readRows( databasePath ) {
	const database = new DatabaseSync( databasePath );
	try {
		return database.prepare( "SELECT receipt_id, received_at, payload_json FROM notification_events ORDER BY received_at" ).all();
	} finally {
		database.close();
	}
}

async function withServer( storage, config, callback ) {
	const server = createApp( { config, storage } ).listen( config.port, config.host );
	openServers.add( server );
	await new Promise( ( resolve ) => server.once( "listening", resolve ) );
	const baseUrl = `http://${config.host}:${server.address().port}`;
	try {
		return await callback( baseUrl );
	} finally {
		openServers.delete( server );
		await new Promise( ( resolve ) => server.close( resolve ) );
	}
}

async function postJson( baseUrl, payload, options = {} ) {
	const body = typeof payload === "string" ? payload : JSON.stringify( payload );
	const headers = {
		authorization: "Bearer test-secret",
		"content-type": "application/json",
		...options.headers
	};
	return fetch( `${baseUrl}${options.path || "/webhook"}`, {
		method: "POST",
		headers,
		body
	} );
}

function quietStart( config ) {
	const originalError = console.error;
	console.error = () => {};
	try {
		return start( { config, installSignalHandlers: false, setExitCode: false } );
	} finally {
		console.error = originalError;
	}
}

async function withDatabaseCloseSpy( callback ) {
	const originalClose = DatabaseSync.prototype.close;
	let closeCalls = 0;
	DatabaseSync.prototype.close = function ( ...args ) {
		closeCalls += 1;
		return originalClose.apply( this, args );
	};
	try {
		await callback();
		return closeCalls;
	} finally {
		DatabaseSync.prototype.close = originalClose;
	}
}

function waitForListening( server ) {
	return new Promise( ( resolve, reject ) => {
		server.once( "listening", resolve );
		server.once( "error", reject );
	} );
}

function closeServer( server ) {
	return new Promise( ( resolve, reject ) => {
		server.close( ( error ) => error ? reject( error ) : resolve() );
	} );
}

function removeAddedSignalListeners( listenersBefore ) {
	for ( const signalName of [ "SIGINT", "SIGTERM" ] ) {
		for ( const listener of process.listeners( signalName ) ) {
			if ( !listenersBefore[ signalName ].includes( listener ) ) {
				process.removeListener( signalName, listener );
			}
		}
	}
}

test( "uses explicit configuration without loading an environment file", () => {
	assert.deepEqual( loadConfig( {
		HOST: "127.0.0.1",
		PORT: "4321",
		WEBHOOK_PATH: "/explicit",
		WEBHOOK_BEARER_TOKEN: "explicit-secret",
		JSON_LIMIT: "2kb",
		DATABASE_PATH: "./explicit.sqlite",
		WEBHOOK_SOURCE_ID: "personal-phone"
	} ), {
		host: "127.0.0.1",
		port: 4321,
		webhookPath: "/explicit",
		bearerToken: "explicit-secret",
		jsonLimit: "2kb",
		databasePath: "./explicit.sqlite",
		sourceId: "personal-phone"
	} );
} );

test( "requires bearer authentication before parsing the body", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			const response = await fetch( `${baseUrl}/webhook`, {
				method: "POST",
				headers: { "content-type": "application/json" },
				body: "not-json"
			} );
			assert.equal( response.status, 401 );

			const wrongToken = await postJson( baseUrl, makePayload(), {
				headers: { authorization: "Bearer wrong-secret" }
			} );
			assert.equal( wrongToken.status, 403 );
			assert.deepEqual( await wrongToken.json(), {
				ok: false,
				message: "Forbidden.",
				code: "forbidden"
			} );
		} );
	} finally {
		storage.close();
	}
	assert.equal( readRows( databasePath ).length, 0 );
} );

test( "persists the default Android payload and returns its receipt", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	const payload = makePayload();
	let responseBody;
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			const response = await postJson( baseUrl, payload );
			assert.equal( response.status, 200 );
			responseBody = await response.json();
		} );
	} finally {
		storage.close();
	}

	assert.equal( responseBody.ok, true );
	assert.match( responseBody.receiptId, /^[0-9a-f-]{36}$/ );
	const rows = readRows( databasePath );
	assert.equal( rows.length, 1 );
	assert.equal( rows[ 0 ].receipt_id, responseBody.receiptId );
	assert.deepEqual( JSON.parse( rows[ 0 ].payload_json ), payload );
} );

test( "rejects the schema-v1 payload", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	const payload = {
		schemaVersion: 1,
		packageName: "com.instagram.android",
		appName: "Instagram",
		title: "Forwarder test",
		text: "Line one\nLine two with \"quotes\" and café",
		postedAt: 1735689600000
	};
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			const response = await postJson( baseUrl, payload );
			assert.equal( response.status, 400 );
		} );
	} finally {
		storage.close();
	}

	assert.equal( readRows( databasePath ).length, 0 );
} );

test( "parses accepted application plus-json media types", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	const payload = makePayload();
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			const response = await postJson( baseUrl, payload, {
				headers: { "content-type": "application/vnd.notification+json" }
			} );
			assert.equal( response.status, 200 );
		} );
	} finally {
		storage.close();
	}

	assert.equal( readRows( databasePath ).length, 1 );
} );

test( "rejects invalid payloads and unsupported content types without inserting", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	const invalidPayloads = [
		makePayload( { unknown: "field" } ),
		makePayload( { packageName: "" } ),
		makePayload( { appName: "x".repeat( 513 ) } ),
		makePayload( { title: "x".repeat( 65537 ) } ),
		makePayload( { postedAt: -1 } ),
		makePayload( { postedAt: Number.MAX_SAFE_INTEGER + 1 } ),
		makePayload( { schemaVersion: 1 } ),
		makePayload( { notificationKey: 42 } )
	];
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			for ( const payload of invalidPayloads ) {
				const response = await postJson( baseUrl, payload );
				assert.equal( response.status, 400 );
				assert.deepEqual( await response.json(), {
					ok: false,
					message: "Invalid notification payload.",
					code: "invalid_payload"
				} );
			}

			const malformed = await postJson( baseUrl, "not-json" );
			assert.equal( malformed.status, 400 );
			assert.equal( ( await malformed.json() ).code, "invalid_json" );

			const unsupported = await postJson( baseUrl, makePayload(), {
				headers: { "content-type": "text/plain" }
			} );
			assert.equal( unsupported.status, 415 );
			assert.equal( ( await unsupported.json() ).code, "unsupported_content_type" );

			const unsupportedCharset = await postJson( baseUrl, makePayload(), {
				headers: { "content-type": "application/json; charset=invalid" }
			} );
			assert.equal( unsupportedCharset.status, 415 );
			assert.equal( ( await unsupportedCharset.json() ).code, "unsupported_content_type" );
		} );
	} finally {
		storage.close();
	}
	assert.equal( readRows( databasePath ).length, 0 );
} );

test( "rejects oversized JSON before insertion", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	try {
		await withServer( storage, makeConfig( databasePath, { jsonLimit: "1kb" } ), async ( baseUrl ) => {
			const response = await postJson( baseUrl, makePayload( { text: "x".repeat( 2048 ) } ) );
			assert.equal( response.status, 413 );
			assert.equal( ( await response.json() ).code, "request_too_large" );
		} );
	} finally {
		storage.close();
	}
	assert.equal( readRows( databasePath ).length, 0 );
} );

test( "returns storage_unavailable without logging acceptance when insertion fails", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	storage.close();
	const logs = [];
	const originalLog = console.log;
	console.log = ( line ) => logs.push( String( line ) );
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			const response = await postJson( baseUrl, makePayload( { text: "PAYLOAD_SECRET" } ) );
			assert.equal( response.status, 503 );
			assert.deepEqual( await response.json(), {
				ok: false,
				message: "Storage unavailable.",
				code: "storage_unavailable"
			} );
		} );
	} finally {
		console.log = originalLog;
	}
	assert.doesNotMatch( logs.join( "\n" ), /PAYLOAD_SECRET|accepted/ );
} );

test( "does not log supplied secrets or notification content", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	const logs = [];
	const originalLog = console.log;
	console.log = ( line ) => logs.push( String( line ) );
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			const response = await postJson( baseUrl, makePayload( {
				title: "BODY_SECRET",
				text: "BODY_SECRET"
			} ), {
				path: "/webhook?QUERY_SECRET=1",
				headers: { "x-api-key": "HEADER_SECRET" }
			} );
			assert.equal( response.status, 200 );
		} );
	} finally {
		console.log = originalLog;
		storage.close();
	}
	assert.doesNotMatch( logs.join( "\n" ), /BODY_SECRET|QUERY_SECRET|HEADER_SECRET/ );
} );

test( "repeated requests resolve to one receipt and row", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const storage = openNotificationStore( databasePath );
	const receipts = [];
	try {
		await withServer( storage, makeConfig( databasePath ), async ( baseUrl ) => {
			for ( let index = 0; index < 2; index += 1 ) {
				const response = await postJson( baseUrl, makePayload() );
				assert.equal( response.status, 200 );
				receipts.push( ( await response.json() ).receiptId );
			}
		} );
	} finally {
		storage.close();
	}
	assert.equal( receipts[ 0 ], receipts[ 1 ] );
	assert.equal( readRows( databasePath ).length, 1 );
} );

test( "persists data across a receiver process restart", async () => {
	const databasePath = makeTemporaryDatabasePath();
	const childScript = `
		const { start } = require(${JSON.stringify( path.join( webhookDirectory, "server.js" ) )});
		const server = start({
			config: {
				host: process.env.HOST,
				port: Number( process.env.PORT ),
				webhookPath: process.env.WEBHOOK_PATH,
				bearerToken: process.env.WEBHOOK_BEARER_TOKEN,
				jsonLimit: process.env.JSON_LIMIT || "1mb",
				databasePath: process.env.DATABASE_PATH,
				sourceId: process.env.WEBHOOK_SOURCE_ID || "personal-phone"
			},
			installSignalHandlers: false
		});
		if ( server ) {
			server.once("listening", () => process.stdout.write("READY:" + server.address().port + "\\n"));
			process.stdin.on("data", (chunk) => {
				if ( chunk.toString().includes("STOP") ) {
					server.close(() => process.exit(0));
				}
			});
		}
	`;
	const environment = {
		...process.env,
		HOST: "127.0.0.1",
		PORT: "0",
		WEBHOOK_PATH: "/webhook",
		WEBHOOK_BEARER_TOKEN: "restart-secret",
		DATABASE_PATH: databasePath
	};

	let first;
	let second;
	try {
		first = await launchChild( childScript, environment );
		const payload = makePayload( { text: "restart-payload" } );
		const firstResponse = await fetch( `http://127.0.0.1:${first.port}/webhook`, {
			method: "POST",
			headers: {
				authorization: "Bearer restart-secret",
				"content-type": "application/json"
			},
			body: JSON.stringify( payload )
		} );
		assert.equal( firstResponse.status, 200 );
		const firstReceipt = ( await firstResponse.json() ).receiptId;
		await stopChild( first.child );

		second = await launchChild( childScript, environment );
		const replay = await fetch( `http://127.0.0.1:${second.port}/webhook`, {
			method: "POST",
			headers: { authorization: "Bearer restart-secret", "content-type": "application/json" },
			body: JSON.stringify( payload )
		} );
		assert.equal( replay.status, 200 );
		const replayBody = await replay.json();
		assert.equal( replayBody.receiptId, firstReceipt );
		assert.equal( replayBody.duplicate, true );
		await stopChild( second.child );
		const rows = readRows( databasePath );
		assert.equal( rows.length, 1 );
		assert.deepEqual( JSON.parse( rows[ 0 ].payload_json ), payload );
	} finally {
		await ensureChildStopped( first && first.child );
		await ensureChildStopped( second && second.child );
	}
} );

test( "rejects missing and example tokens or an unusable database before listening", () => {
	assert.equal( validateConfig( makeConfig( makeTemporaryDatabasePath(), { bearerToken: "" } ) ), false );
	assert.equal( validateConfig( makeConfig( makeTemporaryDatabasePath(), { bearerToken: "replace-with-a-long-random-token" } ) ), false );
	const unusablePath = fs.mkdtempSync( path.join( os.tmpdir(), "notification-forwarder-directory-" ) );
	assert.equal( quietStart( makeConfig( unusablePath ) ), null );
	assert.equal( quietStart( makeConfig( makeTemporaryDatabasePath(), { bearerToken: "" } ) ), null );
} );

test( "closes storage when server.close is called without signal handlers", async () => {
	const closeCalls = await withDatabaseCloseSpy( async () => {
		const server = start( {
			config: makeConfig( makeTemporaryDatabasePath() ),
			installSignalHandlers: false,
			setExitCode: false
		} );
		await waitForListening( server );
		await closeServer( server );
	} );

	assert.equal( closeCalls, 1 );
} );

test( "closes storage and removes signal handlers on signal shutdown", async () => {
	const signalListenersBefore = {
		SIGINT: process.listeners( "SIGINT" ),
		SIGTERM: process.listeners( "SIGTERM" )
	};
	const closeCalls = await withDatabaseCloseSpy( async () => {
		const server = start( {
			config: makeConfig( makeTemporaryDatabasePath() ),
			installSignalHandlers: true,
			setExitCode: false
		} );
		try {
			await waitForListening( server );
			assert.equal( process.listenerCount( "SIGINT" ), signalListenersBefore.SIGINT.length + 1 );
			assert.equal( process.listenerCount( "SIGTERM" ), signalListenersBefore.SIGTERM.length + 1 );
			const closed = new Promise( ( resolve ) => server.once( "close", resolve ) );
			process.emit( "SIGINT" );
			await closed;
		} finally {
			if ( server.listening ) {
				await closeServer( server );
			}
			removeAddedSignalListeners( signalListenersBefore );
		}
	} );

	assert.equal( closeCalls, 1 );
	assert.deepEqual( process.listeners( "SIGINT" ), signalListenersBefore.SIGINT );
	assert.deepEqual( process.listeners( "SIGTERM" ), signalListenersBefore.SIGTERM );
} );

test( "closes storage and sanitizes synchronous listen failures", async () => {
	const originalListen = net.Server.prototype.listen;
	const originalError = console.error;
	const errors = [];
	const signalListenersBefore = {
		SIGINT: process.listeners( "SIGINT" ),
		SIGTERM: process.listeners( "SIGTERM" )
	};
	let closeCalls;
	net.Server.prototype.listen = () => {
		throw new Error( "LISTEN_SECRET" );
	};
	console.error = ( line ) => errors.push( String( line ) );
	try {
		closeCalls = await withDatabaseCloseSpy( async () => {
			const server = start( {
				config: makeConfig( makeTemporaryDatabasePath() ),
				installSignalHandlers: true,
				setExitCode: false
			} );
			assert.equal( server, null );
		} );
	} finally {
		net.Server.prototype.listen = originalListen;
		console.error = originalError;
	}

	assert.equal( closeCalls, 1 );
	assert.deepEqual( errors, [ "Webhook configuration or storage initialization failed." ] );
	assert.deepEqual( process.listeners( "SIGINT" ), signalListenersBefore.SIGINT );
	assert.deepEqual( process.listeners( "SIGTERM" ), signalListenersBefore.SIGTERM );
} );

test( "sanitizes asynchronous listen failures", async () => {
	const errors = [];
	const originalError = console.error;
	console.error = ( line ) => errors.push( String( line ) );
	let closeCalls;
	try {
		closeCalls = await withDatabaseCloseSpy( async () => {
			const first = start( {
				config: makeConfig( makeTemporaryDatabasePath() ),
				installSignalHandlers: false,
				setExitCode: false
			} );
			try {
				await waitForListening( first );
				const second = start( {
					config: makeConfig( makeTemporaryDatabasePath(), { port: first.address().port } ),
					installSignalHandlers: false,
					setExitCode: false
				} );
				const listenError = await new Promise( ( resolve ) => second.once( "error", resolve ) );
				assert.equal( listenError.code, "EADDRINUSE" );
			} finally {
				await closeServer( first );
			}
		} );
	} finally {
		console.error = originalError;
	}

	assert.equal( closeCalls, 2 );
	assert.deepEqual( errors, [ "Webhook server failed to start." ] );
} );

test( "resolves relative database paths from the webhook directory", () => {
	assert.equal( resolveDatabasePath( "./data/relative.sqlite" ), path.join( webhookDirectory, "data", "relative.sqlite" ) );
} );

function launchChild( script, environment ) {
	return new Promise( ( resolve, reject ) => {
		const child = spawn( process.execPath, [ "-e", script ], {
			cwd: webhookDirectory,
			env: environment,
			stdio: [ "pipe", "pipe", "pipe" ]
		} );
		let output = "";
		const timer = setTimeout( () => {
			child.kill();
			reject( new Error( "child receiver did not start" ) );
		}, 5000 );
		child.stdout.on( "data", ( chunk ) => {
			output += chunk.toString();
			const match = output.match( /READY:(\d+)/ );
			if ( match ) {
				clearTimeout( timer );
				resolve( { child, port: Number( match[ 1 ] ) } );
			}
		} );
		child.once( "error", ( error ) => {
			clearTimeout( timer );
			reject( error );
		} );
	} );
}

function stopChild( child ) {
	if ( child.exitCode !== null ) {
		return Promise.resolve();
	}
	return new Promise( ( resolve, reject ) => {
		const timer = setTimeout( () => {
			child.kill();
			reject( new Error( "child receiver did not close" ) );
		}, 5000 );
		child.once( "close", ( code, signal ) => {
			clearTimeout( timer );
			if ( code === 0 ) {
				resolve();
				return;
			}
			reject( new Error( `child receiver exited with code ${code || "unknown"} (${signal || "no signal"})` ) );
		} );
		child.stdin.end( "STOP\n" );
	} );
}

function ensureChildStopped( child ) {
	if ( !child || child.exitCode !== null ) {
		return Promise.resolve();
	}

	return new Promise( ( resolve ) => {
		const timer = setTimeout( () => {
			child.kill();
			resolve();
		}, 3000 );
		child.once( "close", () => {
			clearTimeout( timer );
			resolve();
		} );
		if ( child.stdin && !child.stdin.destroyed ) {
			child.stdin.end( "STOP\n" );
		} else {
			child.kill();
		}
	} );
}
