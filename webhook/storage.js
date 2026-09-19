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
			database.exec( "COMMIT" );
		} catch ( error ) {
			database.exec( "ROLLBACK" );
			throw error;
		}

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
