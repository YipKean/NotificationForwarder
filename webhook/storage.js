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
			CREATE TABLE IF NOT EXISTS notification_events (
				receipt_id TEXT PRIMARY KEY NOT NULL,
				received_at TEXT NOT NULL,
				payload_json TEXT NOT NULL
			);
		`);

		const insertEvent = database.prepare(
			"INSERT INTO notification_events ( receipt_id, received_at, payload_json ) VALUES ( ?, ?, ? )"
		);

		return {
			insert( receiptId, receivedAt, payloadJson ) {
				insertEvent.run( receiptId, receivedAt, payloadJson );
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
