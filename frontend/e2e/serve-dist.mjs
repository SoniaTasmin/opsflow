import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const ROOT = process.argv[2] ?? 'dist/opsflow-frontend/browser';
const PORT = Number(process.argv[3] ?? 4200);
const TYPES = {
    '.html': 'text/html',
    '.js': 'text/javascript',
    '.css': 'text/css',
    '.ico': 'image/x-icon',
    '.json': 'application/json',
    '.map': 'application/json',
    '.woff2': 'font/woff2',
};

createServer(async (request, response) => {
    const path = new URL(request.url, 'http://localhost').pathname;
    try {
        const file = join(ROOT, normalize(path));
        const body = await readFile(file);
        response.writeHead(200, {
            'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream',
        });
        response.end(body);
    } catch {
        // Client-side routes have no file of their own.
        response.writeHead(200, { 'Content-Type': 'text/html' });
        response.end(await readFile(join(ROOT, 'index.html')));
    }
}).listen(PORT, () => console.log(`serving ${ROOT} on http://localhost:${PORT}`));
