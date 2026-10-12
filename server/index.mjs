import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { AgentManager } from './agent.mjs';
import { CheckpointStore } from './checkpoints.mjs';
import { ROUTES } from './providers.mjs';
import { workspacePath, safeCompare } from './security.mjs';
import { enumerate, invokeTool } from './tools.mjs';

const PORT = Number(process.env.M5_PORT || '8765');
const HOST = '127.0.0.1';
const workspace = path.resolve(process.env.M5_WORKSPACE || path.join(os.homedir(), 'm5-workspace'));
const state = path.resolve(process.env.M5_STATE || path.join(os.homedir(), '.m5'));
await fsp.mkdir(workspace, { recursive: true, mode: 0o700 });
await fsp.mkdir(state, { recursive: true, mode: 0o700 });
const tokenFile = path.join(state, 'session-token');
if (!fs.existsSync(tokenFile)) await fsp.writeFile(tokenFile, crypto.randomBytes(32).toString('hex'), { mode: 0o600, flag: 'wx' });
const TOKEN = (await fsp.readFile(tokenFile, 'utf8')).trim();
if (TOKEN.length < 48) throw new Error('Insecure server token');
const checkpoints = new CheckpointStore({ workspace, state });
const agent = new AgentManager({ workspace, checkpoints });
const webRoot = path.resolve('web');
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png' };

function headers(req, res) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'");
}
function json(res, status, payload) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}
function cookie(req) {
  const row = (req.headers.cookie || '').split(';').map(x => x.trim()).find(x => x.startsWith('m5_session='));
  return row ? row.slice('m5_session='.length) : '';
}
function allowedHost(req) {
  const host = (req.headers.host || '').toLowerCase();
  return (host === '127.0.0.1:' + PORT || host === 'localhost:' + PORT)
    && ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(req.socket.remoteAddress);
}
function authorized(req) { return safeCompare(cookie(req), TOKEN); }
function validPost(req) {
  const origin = req.headers.origin;
  const expected = new Set(['http://127.0.0.1:' + PORT, 'http://localhost:' + PORT]);
  return (!origin || expected.has(origin)) && req.headers['sec-fetch-site'] !== 'cross-site';
}
async function body(req) {
  let length = 0;
  const chunks = [];
  for await (const piece of req) {
    length += piece.length;
    if (length > 1_150_000) throw new Error('Request body too large');
    chunks.push(piece);
  }
  const value = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('JSON object required');
  return value;
}
function ensureString(value, limit = 2000) {
  if (typeof value !== 'string' || value.length > limit) throw new Error('Invalid string argument');
  return value;
}
async function staticFile(res, name, root = webRoot) {
  if (!['/index.html', '/app.js', '/style.css'].includes(name)) return json(res, 404, { error: 'Not found' });
  const data = await fsp.readFile(path.join(root, name.slice(1)));
  res.writeHead(200, { 'Content-Type': MIME[path.extname(name)] });
  res.end(data);
}

export const server = http.createServer(async (req, res) => {
  headers(req, res);
  try {
    if (!allowedHost(req)) return json(res, 403, { error: 'Host or remote address rejected' });
    const url = new URL(req.url, 'http://127.0.0.1:' + PORT);
    if (url.pathname === '/health' && req.method === 'GET') return json(res, 200, { ready: true, name: 'M5' });
    if (url.pathname === '/' && req.method === 'GET' && safeCompare(url.searchParams.get('key'), TOKEN)) {
      res.writeHead(302, { Location: '/', 'Set-Cookie': 'm5_session=' + TOKEN + '; HttpOnly; SameSite=Strict; Path=/' });
      return res.end();
    }
    if (!authorized(req)) return json(res, 401, { error: 'M5 is locked. Use the local URL printed by Termux to pair the interface.' });
    if (req.method === 'POST' && !validPost(req)) return json(res, 403, { error: 'Cross-origin request rejected' });
    if (url.pathname === '/' && req.method === 'GET') return staticFile(res, '/index.html');
    if (url.pathname === '/app.js' || url.pathname === '/style.css') return staticFile(res, url.pathname);
    if (url.pathname === '/api/status' && req.method === 'GET') {
      return json(res, 200, { name: 'M5', workspace, platform: process.platform, runtime: 'Termux-compatible Node.js', providers: ROUTES.map(x => ({ name: x.name, family: x.family, model: x.model, local: x.base.startsWith('http://') })) });
    }
    if (url.pathname === '/api/tree' && req.method === 'GET') return json(res, 200, { files: await enumerate(workspace) });
    if (url.pathname === '/api/file' && req.method === 'GET') return json(res, 200, await invokeTool(workspace, 'read_file', { path: ensureString(url.searchParams.get('path')) }));
    if (url.pathname === '/api/search' && req.method === 'GET') return json(res, 200, await invokeTool(workspace, 'search_code', { query: ensureString(url.searchParams.get('q'), 180) }));
    if (url.pathname === '/api/file' && req.method === 'POST') {
      const input = await body(req);
      const relative = ensureString(input.path, 500);
      // A direct editor save is a deliberate user action; snapshot before any mutation.
      const args = { path: relative, content: ensureString(input.content, 1_000_000),
        ...(input.expectedHash === undefined ? {} : { expectedHash: ensureString(input.expectedHash, 100) }) };
      await checkpoints.capture('editor', relative);
      return json(res, 200, await invokeTool(workspace, 'write_file', args));
    }
    if (url.pathname === '/api/checkpoints' && req.method === 'GET') return json(res, 200, { entries: await checkpoints.list() });
    if (url.pathname === '/api/checkpoints/restore' && req.method === 'POST') {
      const input = await body(req);
      return json(res, 200, await checkpoints.restore(ensureString(input.id, 100), ensureString(input.expectedCurrentHash, 100)));
    }
    if (url.pathname === '/api/git/status' && req.method === 'GET') return json(res, 200, await invokeTool(workspace, 'git_status', {}));
    if (url.pathname === '/api/git/diff' && req.method === 'GET') return json(res, 200, await invokeTool(workspace, 'git_diff', {}));
    if (url.pathname === '/api/terminal' && req.method === 'POST') {
      const input = await body(req);
      return json(res, 200, await invokeTool(workspace, 'run_command', { command: ensureString(input.command, 3000), timeoutMs: 60000 }));
    }
    if (url.pathname === '/api/agent/start' && req.method === 'POST') {
      const input = await body(req);
      return json(res, 202, agent.start(ensureString(input.goal, 10000), ensureString(input.family || 'freellmpool', 80)));
    }
    const match = /^\/api\/agent\/([a-f0-9-]{36})(?:\/(approve|stop))?$/.exec(url.pathname);
    if (match) {
      if (!match[2] && req.method === 'GET') return json(res, 200, agent.snapshot(match[1], Number(url.searchParams.get('after') || -1)));
      if (match[2] === 'approve' && req.method === 'POST') {
        const input = await body(req);
        return json(res, 200, agent.approve(match[1], ensureString(input.approvalId, 100), input.approved));
      }
      if (match[2] === 'stop' && req.method === 'POST') return json(res, 200, agent.stop(match[1]));
    }
    if (url.pathname === '/preview' && req.method === 'GET') {
      const relative = ensureString(url.searchParams.get('path'), 500);
      if (!/\.html?$/i.test(relative)) return json(res, 400, { error: 'HTML previews only' });
      const target = workspacePath(workspace, relative);
      const stat = await fsp.stat(target);
      if (!stat.isFile() || stat.size > 500_000) throw new Error('Preview exceeds limit');
      res.setHeader('Content-Security-Policy', "sandbox allow-scripts; default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action 'none'");
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(await fsp.readFile(target));
    }
    return json(res, 404, { error: 'Unknown route' });
  } catch (e) {
    const known = /Invalid|Unknown|not found|ENOENT|Denied|Restricted|Too large|large|limit|Concurrent|required|File changed|Missing/.test(e.message);
    json(res, known ? 400 : 500, { error: String(e.message).slice(0, 600) });
  }
});

if (process.env.NODE_ENV !== 'test') server.listen(PORT, HOST, () => {
  console.log('M5 running on http://' + HOST + ':' + PORT);
  console.log('Unlock M5 locally: http://' + HOST + ':' + PORT + '/?key=' + TOKEN);
  console.log('Workspace: ' + workspace);
});
