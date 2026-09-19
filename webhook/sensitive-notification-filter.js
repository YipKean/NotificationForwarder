// Keep this catalogue aligned with SensitiveNotificationFilter.kt and the shared fixtures.
// Add verified package constraints only when source-specific formats are available.
const CATALOGUE_VERSION = 1;
const boundaryStart = "(?<![\\p{L}\\p{N}_])(?:";
const boundaryEnd = ")(?![\\p{L}\\p{N}_])";
const definitions = [
	[ "otp_one_time_password", "otp", "otp|tac|one[- ]time (?:password|passcode|code)|kata laluan sekali guna" ],
	[ "verification_code", "verification", "(?:verification|authentication|login|security|pengesahan) (?:code|kod)|kod pengesahan" ],
	[ "login_notice", "login_device", "(?:log ?in|sign[ -]?in)(?: (?:was|is|has been))? (?:successful|success|detected|attempt|alert|approved|denied|request|required)|(?:successful|new|unrecognized|unknown) (?:log ?in|sign[ -]?in)|log masuk(?: (?:anda|telah))? (?:berjaya|dikesan|diluluskan)|signed in" ],
	[ "device_verification", "login_device", "(?:new|unrecognized|unknown) device|device (?:verification|registered|registration|linked|activated)|(?:verify|register|link) (?:your |this |a )?device|peranti (?:baharu|baru|tidak dikenali|disahkan|didaftar(?:kan)?|pendaftaran)|peranti.{0,50}(?:didaftar(?:kan)?|disahkan)" ],
	[ "approval_request", "approval", "approve|authori[sz]e|approval|authori[sz]ation|luluskan|meluluskan|kelulusan|(?:tap|click|please|sila).{0,40}(?:confirm|sahkan)" ],
	[ "secure2u_security", "security", "secure ?2u" ],
	[ "security_notice", "security", "security (?:alert|notice|request)|keselamatan (?:akaun|transaksi)" ]
];
const rules = definitions.map( ( [ ruleId, category, pattern ] ) => ( {
	ruleId,
	category,
	packages: null,
	pattern: new RegExp( boundaryStart + pattern + boundaryEnd, "u" )
} ) );

function normalize( value ) {
	return value.normalize( "NFKC" )
		.replace( /\p{Cf}/gu, "" )
		.replace( /[\s\p{Z}\u0085]+/gu, " " )
		.trim().toLowerCase();
}

function evaluate( payload, catalogue = rules ) {
	const fields = [ payload.title, payload.text, payload.bigText || "" ];
	const candidates = [ ...fields, fields.join( " " ) ].map( normalize );
	for ( const rule of catalogue ) {
		if ( rule.packages && !rule.packages.includes( payload.packageName ) ) continue;
		if ( candidates.some( value => rule.pattern.test( value ) ) ) {
			return { ruleId: rule.ruleId, category: rule.category };
		}
	}
	return null;
}

module.exports = { CATALOGUE_VERSION, evaluate, normalize };
