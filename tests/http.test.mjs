import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

test('live localhost API enforces auth, origin, sandbox and real file operations', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-http-'));
  const workspace = path.join(root, 'workspace');
  const state = path.join(root, 'state');
  const port = 18500 + Math.floor(Math.random() * 700);
  const base = 'http://127.0.0.1:' + port;
  const child = spawn(process.execPath, ['server/index.mjs'], {
    cwd: process.cwd(),
    env: { ...process.env, M5_WORKSPACE: workspace, M5_STATE: state, M5_PORT: String(port) },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  try {
    let ready = false;
    for (let i = 0; i < 120; i++) {
      try { ready = (await fetch(base + '/health')).ok; } catch {}
      if (ready) break;
      await wait(50);
    }
    assert.ok(ready, 'HTTP server started');
    const unauth = await fetch(base + '/api/status');
    assert.equal(unauth.status, 401);
    const token = (await fs.readFile(path.join(state, 'session-token'), 'utf8')).trim();
    const unlock = await fetch(base + '/?key=' + token, { redirect: 'manual' });
    assert.equal(unlock.status, 302);
    const cookie = unlock.headers.get('set-cookie').split(';')[0];
    assert.ok(cookie.includes(token));
    const headers = { Cookie: cookie };
    const status = await fetch(base + '/api/status', { headers });
    assert.equal(status.status, 200);
    assert.equal((await status.json()).providers.length, 7);
    const badOrigin = await fetch(base + '/api/file', { method:'POST', headers: { ...headers, Origin:'https://attacker.example', 'Content-Type':'application/json' }, body: JSON.stringify({path:'ok.txt',content:'evil',expectedHash:''}) });
    assert.equal(badOrigin.status, 403);
    const save = await fetch(base + '/api/file', { method:'POST', headers: { ...headers, Origin:base, 'Content-Type':'application/json' }, body: JSON.stringify({path:'ok.txt',content:'real',expectedHash:''}) });
    assert.equal(save.status, 200);
    const file = await fetch(base + '/api/file?path=ok.txt', { headers });
    assert.equal((await file.json()).content, 'real');
    const attack = await fetch(base + '/api/file?path=..%2F..%2Fetc%2Fpasswd', { headers });
    assert.equal(attack.status, 400);
    await fs.writeFile(path.join(workspace,'view.html'), '<h1>Preview</h1>');
    const preview = await fetch(base + '/preview?path=view.html', { headers });
    assert.equal(preview.status, 200);
    assert.match(preview.headers.get('content-security-policy'), /sandbox allow-scripts/);
    assert.equal(preview.headers.get('x-frame-options'), 'SAMEORIGIN');
    const html = await fetch(base + '/', { headers });
    assert.match(await html.text(), /M5/);
  } finally {
    child.kill('SIGKILL');
    await new Promise(resolve => { if (child.exitCode !== null || child.signalCode !== null) resolve(); else child.once('close', resolve); });
    await fs.rm(root, { recursive:true, force:true });
  }
});
