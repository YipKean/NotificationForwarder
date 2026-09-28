const { loadConfig, validateConfig } = require('../server');
const config = loadConfig();
const [major, minor] = process.versions.node.split('.').map(Number);
if (major !== 24 || minor < 13 || !validateConfig(config) || config.host !== '127.0.0.1' || !config.port) {
    process.exit(1);
}
console.log(JSON.stringify({ port: config.port }));
