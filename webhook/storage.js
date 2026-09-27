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

		const version = database.prepare( "pragma user_version" ).get().user_version;
		if ( version > 1 ) {
			throw new Error( "Unsupported database version." );
		}
		if ( version < 1 ) {
			database.exec( "begin; create index if not exists notification_arrival on notification_events (received_at, receipt_id); pragma user_version = 1; commit;" );
		}
		const projection = `receipt_id as receiptId, received_at as receivedAt,
			json_extract(payload_json, '$.packageName') as packageName,
			json_extract(payload_json, '$.appName') as appName,
			json_extract(payload_json, '$.title') as title,
			json_extract(payload_json, '$.postedAt') as postedAt`;
		const insertEvent = database.prepare(
			"INSERT INTO notification_events ( receipt_id, received_at, payload_json ) VALUES ( ?, ?, ? )"
		);

		return {
			insert( receiptId, receivedAt, payloadJson ) {
				insertEvent.run( receiptId, receivedAt, payloadJson );
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
