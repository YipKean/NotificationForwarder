// Synthetic-only receiver. Never reads .env or starts Hermes.
const fs = require( "node:fs" );
const os = require( "node:os" );
const path = require( "node:path" );
const readline = require( "node:readline" );
const { createApp, loadConfig } = require( "./server" );
const { openNotificationStore } = require( "./storage" );

function createOutageReceiver( { token, databasePath } ) {
	if ( !token || !databasePath ) throw new Error( "Explicit test token and database required." );
	const store = openNotificationStore( databasePath );
	let outage = true;
	const config = loadConfig( {
		WEBHOOK_BEARER_TOKEN: token,
		DATABASE_PATH: databasePath,
		WEBHOOK_SOURCE_ID: "isolated-outage-test"
	} );
	const app = createApp( {
		config,
		storage: {
			insertOrResolve( ...args ) {
				if ( outage ) throw new Error( "Synthetic storage outage" );
				return store.insertOrResolve( ...args );
			}
		}
	} );
	return { app, setOutage: ( value ) => { outage = Boolean( value ); }, close: () => store.close() };
}

if ( require.main === module ) {
	const token = process.env.OUTAGE_TEST_TOKEN;
	if ( !token || token.length < 24 ) {
		console.error( "Set OUTAGE_TEST_TOKEN to a separate random test token (at least 24 characters)." );
		process.exitCode = 1;
	} else {
		const directory = fs.mkdtempSync( path.join( os.tmpdir(), "forwarder-outage-" ) );
		const databasePath = path.join( directory, "synthetic.sqlite" );
		const receiver = createOutageReceiver( { token, databasePath } );
		const server = require( "node:http" ).createServer( ( req, res ) => {
			res.on( "finish", () => console.log( JSON.stringify( {
				at: new Date().toISOString(), status: res.statusCode
			} ) ) );
			receiver.app( req, res );
		} );
		const input = readline.createInterface( { input: process.stdin } );
		input.on( "line", ( line ) => {
			if ( line.trim() === "recover" ) {
				receiver.setOutage( false );
				console.log( "Storage recovered." );
			} else if ( line.trim() === "outage" ) {
				receiver.setOutage( true );
				console.log( "Storage outage enabled." );
			}
		} );
		server.once( "close", () => { input.close(); receiver.close(); } );
		server.once( "error", () => {
			console.error( "Test receiver could not listen on localhost:3301." );
			input.close();
			receiver.close();
			process.exitCode = 1;
		} );
		process.once( "SIGINT", () => server.close() );
		process.once( "SIGTERM", () => server.close() );
		server.listen( 3301, "127.0.0.1", () => {
			console.log( "Synthetic receiver: localhost:3301/webhook; starts in outage mode. Type recover or outage." );
			console.log( `Synthetic database: ${databasePath}` );
		} );
	}
}

module.exports = { createOutageReceiver };
