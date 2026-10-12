import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { ContextIndex } from '../server/context.mjs';
import { invokeTool } from '../server/tools.mjs';

const fixture = async () => await fs.mkdtemp(path.join(os.tmpdir(), 'm5-context-'));

test('codebase context ranks filename and snippets while ignoring credentials and symlinks', async () => {
  const root = await fixture();
  try {
    await fs.mkdir(path.join(root, 'src'));
    await fs.writeFile(path.join(root, 'src', 'userService.ts'), 'export function createUserAccount() {\n  return "account";\n}\n');
    await fs.writeFile(path.join(root, 'src', 'network.ts'), 'export function fetchServerStatus() { return true; }\n');
    await fs.writeFile(path.join(root, '.env.production'), 'PRIVATE_PASSWORD=createUserAccount');
    await fs.symlink('/etc/passwd', path.join(root, 'src', 'evil.ts'));
    const index = new ContextIndex(root);
    const result = await index.query('create user account');
    assert.equal(result.results[0].path, 'src/userService.ts');
    assert.ok(result.results[0].snippets.some(x => x.line === 1));
    assert.ok(result.results.every(x => !x.path.includes('.env') && !x.path.includes('evil')));
    assert.equal(result.index.files, 2);
  } finally { await fs.rm(root, { force: true, recursive: true }); }
});

test('code index refresh detects edits and removals; agent code_context uses injected index', async () => {
  const root = await fixture();
  try {
    const file = path.join(root, 'alpha.py');
    await fs.writeFile(file, 'def calculate_invoice():\n  return 3\n');
    const index = new ContextIndex(root);
    assert.equal((await invokeTool(root, 'code_context', { query:'calculate invoice' }, { contextIndex:index })).results.length, 1);
    await fs.writeFile(file, 'def parse_invoice():\n  return 3\n');
    await fs.utimes(file, new Date(2020, 0, 2), new Date(2030, 0, 2));
    assert.equal((await index.query('calculate invoice')).results.length, 1); // invoice remains relevant
    assert.equal((await index.query('calculate')).results.length, 0);
    assert.equal((await index.query('parse')).results[0].path, 'alpha.py');
    await fs.unlink(file);
    assert.equal((await index.query('parse')).results.length, 0);
  } finally { await fs.rm(root, { force:true, recursive:true }); }
});

test('Android uses official Termux result fields without an HTTP server', async () => {
  const bridge = await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java', import.meta.url), 'utf8');
  const receiver = await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxResultReceiver.java', import.meta.url), 'utf8');
  const activity = await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/M5Activity.java', import.meta.url), 'utf8');
  assert.match(bridge, /getBundleExtra\("result"\)/);
  assert.match(bridge, /getInt\("exitCode"/);
  assert.match(bridge, /getInt\("err"/);
  assert.match(receiver, /TermuxBridge\.completed/);
  assert.match(activity, /extends Activity/);
  assert.doesNotMatch(activity, /new WebView/);
});
