function parseListQuery( query ) {
	if ( Object.keys( query ).some( ( key ) => ![ "limit", "before", "packageName" ].includes( key ) ) ) {
		throw new Error( "Invalid query." );
	}
	const limit = query.limit === undefined ? 50 : Number( query.limit );
	if ( !Number.isInteger( limit ) || limit < 1 || limit > 100 || ( query.limit !== undefined && ( typeof query.limit !== "string" || !/^\d+$/.test( query.limit ) ) ) ) {
		throw new Error( "Invalid limit." );
	}
	const packageName = query.packageName ?? "";
	if ( typeof packageName !== "string" || packageName.length > 255 ) {
		throw new Error( "Invalid package." );
	}
	let before = null;
	if ( query.before !== undefined ) {
		if ( typeof query.before !== "string" || query.before.length > 256 || !/^[A-Za-z0-9_-]+$/.test( query.before ) ) {
			throw new Error( "Invalid cursor." );
		}
		before = JSON.parse( Buffer.from( query.before, "base64url" ).toString() );
		if ( !Array.isArray( before ) || before.length !== 2 || typeof before[ 0 ] !== "string" || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test( before[ 0 ] ) || !Number.isFinite( Date.parse( before[ 0 ] ) ) || typeof before[ 1 ] !== "string" || !/^[a-f0-9-]{36}$/.test( before[ 1 ] ) ) {
			throw new Error( "Invalid cursor." );
		}
	}
	return { limit, before, packageName };
}

function installReadRoutes( app, config, storage, requireBearerAuth ) {
	const paths = [ "/api/notifications", "/api/notifications/:receiptId", "/api/notification-apps" ];
	app.use( paths, ( req, res, next ) => {
		res.set( "Cache-Control", "no-store" );
		if ( !config.dashboardToken || config.dashboardToken === config.bearerToken ) {
			return res.status( 503 ).json( { code: "dashboard_disabled", message: "Dashboard unavailable." } );
		}
		return requireBearerAuth( config.dashboardToken )( req, res, next );
	} );
	app.get( paths, ( req, res ) => {
		let query;
		try {
			query = parseListQuery( req.query );
		} catch {
			return res.status( 400 ).json( { code: "invalid_query", message: "Invalid query." } );
		}
		try {
			if ( req.path === "/api/notification-apps" ) {
				return res.json( { items: storage.apps() } );
			}
			if ( req.params.receiptId ) {
				if ( !/^[a-f0-9-]{36}$/.test( req.params.receiptId ) ) {
					return res.status( 400 ).json( { code: "invalid_receipt", message: "Invalid receipt." } );
				}
				const item = storage.get( req.params.receiptId );
				return item ? res.json( item ) : res.status( 404 ).json( { code: "not_found", message: "Notification not found." } );
			}
			return res.json( storage.list( query ) );
		} catch {
			return res.status( 503 ).json( { code: "storage_unavailable", message: "Storage unavailable." } );
		}
	} );
}

module.exports = { installReadRoutes, parseListQuery };
