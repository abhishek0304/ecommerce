import { cp, mkdir } from 'node:fs/promises';
const source = new URL('../dist/', import.meta.url);
const destination = new URL('../../api-gateway/src/main/resources/static/react/', import.meta.url);
await mkdir(destination, { recursive: true });
await cp(source, destination, { recursive: true, force: true });
console.log('React build copied to the gateway. Rebuild/restart Spring, then open http://localhost:8081/react/index.html');
