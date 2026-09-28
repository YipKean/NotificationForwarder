const fs = require("node:fs");
const path = require("node:path");
const { DatabaseSync } = require("node:sqlite");

const DEFAULT_DATABASE_PATH = "./data/notifications.sqlite";

function resolveDatabasePath( configuredPath = DEFAULT_DATABASE_PATH ) {
	if ( path.isAbsolute( configuredPath ) ) {
		return configuredPath;
	}

	return path.resolve( __dirname, configuredPath );
}

function openNotificationStore( configuredPath ) {
	const databasePath = resolveDatabasePath( configuredPath );
	fs.mkdirSync( path.dirname( databasePath ), { recursive: true } );

	const database = new DatabaseSync( databasePath, { timeout: 5000 } );
	try {
		database.exec(`
			PRAGMA journal_mode = WAL;
			PRAGMA synchronous = FULL;
			PRAGMA busy_timeout = 5000;
		`);
		database.exec( "BEGIN IMMEDIATE" );
		try {
			const version = database.prepare( "PRAGMA user_version" ).get().user_version;
			if ( version > 1 ) throw new Error( "Unsupported database version." );
			database.exec(`CREATE TABLE IF NOT EXISTS notification_events (
				receipt_id TEXT PRIMARY KEY NOT NULL,
				received_at TEXT NOT NULL,
				payload_json TEXT NOT NULL
			)`);
			const columns = new Set( database.prepare( "PRAGMA table_info(notification_events)" ).all().map( column => column.name ) );
			for ( const name of [ "source_id", "event_id", "payload_hash" ] ) {
				if ( !columns.has( name ) ) database.exec( `ALTER TABLE notification_events ADD COLUMN ${name} TEXT` );
			}
			database.exec( "CREATE UNIQUE INDEX IF NOT EXISTS notification_events_source_event ON notification_events(source_id, event_id) WHERE source_id IS NOT NULL AND event_id IS NOT NULL" );
			database.exec( "CREATE INDEX IF NOT EXISTS notification_arrival ON notification_events(received_at, receipt_id)" );
			if ( version < 1 ) database.exec( "PRAGMA user_version = 1" );
			database.exec( "COMMIT" );
		} catch ( error ) {
			database.exec( "ROLLBACK" );
			throw error;
		}

		const projection = `receipt_id as receiptId, received_at as receivedAt,
			json_extract(payload_json, '$.packageName') as packageName,
			json_extract(payload_json, '$.appName') as appName,
			json_extract(payload_json, '$.title') as title,
			json_extract(payload_json, '$.postedAt') as postedAt`;
		const findEvent = database.prepare( "SELECT receipt_id, payload_hash FROM notification_events WHERE source_id = ? AND event_id = ?" );
		const insertEvent = database.prepare( "INSERT INTO notification_events (receipt_id, received_at, payload_json, source_id, event_id, payload_hash) VALUES (?, ?, ?, ?, ?, ?)" );

		return {
			// Legacy fixture/import helper; the HTTP ingestion path always uses insertOrResolve.
			insert( receiptId, receivedAt, payloadJson ) {
				database.prepare( "INSERT INTO notification_events (receipt_id, received_at, payload_json) VALUES (?, ?, ?)" ).run( receiptId, receivedAt, payloadJson );
			},
			insertOrResolve( sourceId, eventId, receiptId, receivedAt, payloadJson, payloadHash ) {
				if ( typeof sourceId !== "string" || !sourceId.trim() || typeof eventId !== "string" || !eventId ) {
					throw new Error( "invalid_event_identity" );
				}
				database.exec( "BEGIN IMMEDIATE" );
				try {
					const existing = findEvent.get( sourceId, eventId );
					if ( existing ) {
						database.exec( "COMMIT" );
						return existing.payload_hash === payloadHash ? { receiptId: existing.receipt_id, duplicate: true } : { conflict: true };
					}
					insertEvent.run( receiptId, receivedAt, payloadJson, sourceId, eventId, payloadHash );
					database.exec( "COMMIT" );
					return { receiptId, duplicate: false };
				} catch ( error ) {
					database.exec( "ROLLBACK" );
					throw error;
				}
			},
			list( { limit, before, packageName } ) {
				const rows = database.prepare( `select ${projection}, substr(json_extract(payload_json, '$.text'), 1, 240) as text
					from notification_events where (? = '' or json_extract(payload_json, '$.packageName') = ?)
					and (? = '' or (received_at, receipt_id) < (?, ?))
					order by received_at desc, receipt_id desc limit ?` ).all(
					packageName, packageName, before?.[ 0 ] || '', before?.[ 0 ] || '', before?.[ 1 ] || '', limit + 1
				);
				const items = rows.slice( 0, limit );
				const last = items.at( -1 );
				return { items, nextCursor: rows.length > limit ? Buffer.from( JSON.stringify( [ last.receivedAt, last.receiptId ] ) ).toString( 'base64url' ) : null };
			},
			get( receiptId ) {
				return database.prepare( `select ${projection}, json_extract(payload_json, '$.text') as text from notification_events where receipt_id = ?` ).get( receiptId );
			},
			apps() {
				return database.prepare( "select json_extract(payload_json, '$.packageName') as packageName, max(json_extract(payload_json, '$.appName')) as appName from notification_events group by packageName order by appName, packageName limit 1000" ).all();
			},
			close() {
				database.close();
			}
		};
	} catch ( error ) {
		try {
			database.close();
		} catch ( closeError ) {
			// The initialization error is the relevant startup failure.
		}
		throw error;
	}
}

module.exports = { DEFAULT_DATABASE_PATH, openNotificationStore, resolveDatabasePath };
