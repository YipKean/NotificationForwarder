const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const express = require("express");
const dotenv = require("dotenv");
const { evaluate: filterSensitiveNotification } = require("./sensitive-notification-filter");
const { DEFAULT_DATABASE_PATH, openNotificationStore } = require("./storage");

const EXAMPLE_BEARER_TOKEN = "replace-with-a-long-random-token";
const JSON_CONTENT_TYPE_PATTERN = /^(?:application\/json|application\/[\w.+-]+\+json)(?:\s*;|$)/i;

function loadEnvironment() {
	const environment = { ...process.env };
	try {
		const fileEnvironment = dotenv.parse( fs.readFileSync( path.join( __dirname, ".env" ) ) );
		return { ...fileEnvironment, ...environment };
	} catch ( error ) {
		if ( error && error.code === "ENOENT" ) {
			return environment;
		}
		throw new Error( "Unable to load webhook environment." );
	}
}

function loadConfig( environment ) {
	const configuredEnvironment = environment === undefined ? loadEnvironment() : environment;
	const configuredPort = configuredEnvironment.PORT === undefined ? 3000 : Number( configuredEnvironment.PORT );

	return {
		host: configuredEnvironment.HOST || "127.0.0.1",
		port: configuredPort,
		webhookPath: configuredEnvironment.WEBHOOK_PATH || "/webhook",
		bearerToken: ( configuredEnvironment.WEBHOOK_BEARER_TOKEN || "" ).trim(),
		jsonLimit: configuredEnvironment.JSON_LIMIT || "1mb",
		databasePath: configuredEnvironment.DATABASE_PATH || DEFAULT_DATABASE_PATH,
		sourceId: configuredEnvironment.WEBHOOK_SOURCE_ID === undefined ? "personal-phone" : configuredEnvironment.WEBHOOK_SOURCE_ID
	};
}

function validateConfig( config ) {
	return Boolean(
		config &&
		typeof config.host === "string" &&
		config.host &&
		Number.isInteger( config.port ) &&
		config.port >= 0 &&
		config.port <= 65535 &&
		typeof config.webhookPath === "string" &&
		config.webhookPath &&
		config.webhookPath.startsWith( "/" ) &&
		typeof config.bearerToken === "string" &&
		config.bearerToken &&
		config.bearerToken !== EXAMPLE_BEARER_TOKEN &&
		typeof config.databasePath === "string" &&
		config.databasePath &&
		typeof config.sourceId === "string" && config.sourceId.trim()
	);
}

function requireBearerAuth( bearerToken ) {
	return ( req, res, next ) => {
		const authHeader = req.get( "authorization" ) || "";
		if ( !bearerToken || !authHeader.startsWith( "Bearer " ) ) {
			return res.status( 401 ).json( {
				ok: false,
				message: "Unauthorized.",
				code: "unauthorized"
			} );
		}

		const suppliedDigest = crypto.createHash( "sha256" ).update( authHeader.slice( 7 ).trim() ).digest();
		const expectedDigest = crypto.createHash( "sha256" ).update( bearerToken ).digest();
		if ( !crypto.timingSafeEqual( suppliedDigest, expectedDigest ) ) {
			return res.status( 403 ).json( {
				ok: false,
				message: "Forbidden.",
				code: "forbidden"
			} );
		}

		return next();
	};
}

function isJsonContentType( contentType ) {
	return JSON_CONTENT_TYPE_PATTERN.test( contentType );
}

function requireJsonContentType( req, res, next ) {
	const contentType = req.get( "content-type" ) || "";
	if ( !isJsonContentType( contentType ) ) {
		return res.status( 415 ).json( {
			ok: false,
			message: "Unsupported content type.",
			code: "unsupported_content_type"
		} );
	}

	return next();
}

function isValidString( value, maximum, allowEmpty = true ) {
	return typeof value === "string" && ( allowEmpty || value.length > 0 ) && value.length <= maximum;
}

function isValidPayload( payload ) {
	if ( !payload || typeof payload !== "object" || Array.isArray( payload ) ) {
		return false;
	}

	const allowedFields = new Set( [
		"schemaVersion",
		"eventId",
		"packageName",
		"appName",
		"title",
		"text",
		"bigText",
		"postedAt",
		"deviceId",
		"notificationKey"
	] );
	if ( Object.keys( payload ).some( ( field ) => !allowedFields.has( field ) ) ) {
		return false;
	}

	if (
		!isValidString( payload.packageName, 255, false ) ||
		!isValidString( payload.appName, 512 ) ||
		!isValidString( payload.title, 65536 ) ||
		!isValidString( payload.text, 65536 ) ||
		!isValidString( payload.eventId, 128, false ) ||
		!Number.isSafeInteger( payload.postedAt ) ||
		payload.postedAt < 0
	) {
		return false;
	}

	if ( payload.schemaVersion !== 2 || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(payload.eventId) ) {
		return false;
	}
	if (payload.bigText !== undefined && payload.bigText !== null && !isValidString(payload.bigText, 65536)) return false;
	if ( payload.deviceId !== undefined && !isValidString( payload.deviceId, 4096 ) ) {
		return false;
	}
	if ( payload.notificationKey !== undefined && !isValidString( payload.notificationKey, 4096 ) ) {
		return false;
	}

	return true;
}

function normalizePayload( payload ) {
	return {
		schemaVersion: 2,
		eventId: payload.eventId.toLowerCase(),
		packageName: payload.packageName,
		appName: payload.appName,
		title: payload.title,
		text: payload.text,
		bigText: payload.bigText || null,
		postedAt: payload.postedAt,
		...( payload.deviceId === undefined ? {} : { deviceId: payload.deviceId } ),
		...( payload.notificationKey === undefined ? {} : { notificationKey: payload.notificationKey } )
	};
}

function createApp( { config = loadConfig(), storage = null } = {} ) {
	const app = express();

	app.get( "/health", ( req, res ) => {
		res.json( { ok: true, service: "webhook-api" } );
	} );

	app.post(
		config.webhookPath,
		requireBearerAuth( config.bearerToken ),
		requireJsonContentType,
		express.json( {
			limit: config.jsonLimit,
			type: ( req ) => isJsonContentType( req.get( "content-type" ) || "" )
		} ),
		( req, res ) => {
			if ( !isValidPayload( req.body ) ) {
				return res.status( 400 ).json( {
					ok: false,
					message: "Invalid notification payload.",
					code: "invalid_payload"
				} );
			}
			if (filterSensitiveNotification( req.body )) return res.status(422).json({ ok: false, message: "Sensitive notification rejected.", code: "sensitive_notification" });
			if ( !storage ) {
				return res.status( 503 ).json( {
					ok: false,
					message: "Storage unavailable.",
					code: "storage_unavailable"
				} );
			}

			const receiptId = crypto.randomUUID();
			const receivedAt = new Date().toISOString();
			try {
				const payloadJson = JSON.stringify( normalizePayload( req.body ) );
				const payloadHash = crypto.createHash( "sha256" ).update( payloadJson ).digest( "hex" );
				const result = storage.insertOrResolve( config.sourceId, req.body.eventId.toLowerCase(), receiptId, receivedAt, payloadJson, payloadHash );
				if (result.conflict) return res.status(409).json({ ok: false, message: "Event ID already exists with different content.", code: "event_id_conflict" });
				console.log( JSON.stringify( { receiptId: result.receiptId, receivedAt, outcome: result.duplicate ? "duplicate" : "accepted" } ) );
				return res.status(200).json({ ok: true, message: "Webhook received.", receiptId: result.receiptId, duplicate: result.duplicate });
			} catch ( error ) {
				return res.status( 503 ).json( {
					ok: false,
					message: "Storage unavailable.",
					code: "storage_unavailable"
				} );
			}

		}
	);

	app.use( ( req, res ) => {
		res.status( 404 ).json( { ok: false, message: "Not found.", code: "not_found" } );
	} );

	app.use( ( error, req, res, next ) => {
		if ( error && error.type === "entity.too.large" ) {
			return res.status( 413 ).json( { ok: false, message: "Request too large.", code: "request_too_large" } );
		}
		if ( error && ( error.type === "charset.unsupported" || error.type === "encoding.unsupported" ) ) {
			return res.status( 415 ).json( { ok: false, message: "Unsupported content type.", code: "unsupported_content_type" } );
		}
		if ( error instanceof SyntaxError && "body" in error ) {
			return res.status( 400 ).json( { ok: false, message: "Invalid JSON body.", code: "invalid_json" } );
		}
		return res.status( 500 ).json( { ok: false, message: "Internal server error.", code: "internal_error" } );
	} );

	return app;
}

function start( options = {} ) {
	const installSignalHandlers = options.installSignalHandlers === undefined ? true : options.installSignalHandlers;
	const setExitCode = options.setExitCode === undefined ? true : options.setExitCode;
	let config;
	try {
		config = options.config === undefined ? loadConfig() : options.config;
	} catch ( error ) {
		return reportStartupFailure( setExitCode );
	}

	if ( !validateConfig( config ) ) {
		return reportStartupFailure( setExitCode );
	}

	let storage;
	try {
		storage = openNotificationStore( config.databasePath );
	} catch ( error ) {
		return reportStartupFailure( setExitCode );
	}

	let server;
	try {
		server = createApp( { config, storage } ).listen( config.port, config.host );
	} catch ( error ) {
		closeStorage( storage );
		return reportStartupFailure( setExitCode );
	}

	let storageClosed = false;
	let isClosing = false;
	const closeStoredDatabase = () => {
		if ( storageClosed ) {
			return;
		}
		storageClosed = true;
		closeStorage( storage );
	};
	const handleSignal = () => {
		if ( isClosing ) {
			return;
		}
		isClosing = true;
		server.close( ( error ) => {
			closeStoredDatabase();
			if ( error && error.code !== "ERR_SERVER_NOT_RUNNING" ) {
				return reportStartupFailure( setExitCode, "Webhook server shutdown failed." );
			}
		} );
	};
	const cleanup = () => {
		closeStoredDatabase();
		if ( installSignalHandlers ) {
			process.removeListener( "SIGINT", handleSignal );
			process.removeListener( "SIGTERM", handleSignal );
		}
	};
	const originalClose = server.close.bind( server );
	server.close = ( callback ) => originalClose( ( error ) => {
		cleanup();
		if ( callback ) {
			callback( error );
		}
	} );
	const handleListenError = () => {
		cleanup();
		reportStartupFailure( setExitCode, "Webhook server failed to start." );
	};
	server.once( "close", cleanup );
	server.once( "error", handleListenError );
	if ( installSignalHandlers ) {
		process.once( "SIGINT", handleSignal );
		process.once( "SIGTERM", handleSignal );
	}
	return server;
}

function closeStorage( storage ) {
	try {
		storage.close();
	} catch ( error ) {
		// The process is already shutting down; there is no safe response to send.
	}
}

function reportStartupFailure( setExitCode, message = "Webhook configuration or storage initialization failed." ) {
	if ( setExitCode ) {
		process.exitCode = 1;
	}
	console.error( message );
	return null;
}

if ( require.main === module ) {
	start();
}

module.exports = { createApp, isValidPayload, loadConfig, start, validateConfig };
