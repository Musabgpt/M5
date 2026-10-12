import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { workspacePath, sha256, safeCompare } from '../server/security.mjs';
import { invokeTool, validateCall } from '../server/tools.mjs';
import { AgentManager, parseCalls } from '../server/agent.mjs';
import { ROUTES, validateRoute, orderedRoutes, chatCompletion } from '../server/providers.mjs';

const tmp = async () => fs.mkdtemp(path.join(os.tmpdir(), 'm5-test-'));
async function cleanup(root) { await fs.rm(root, { recursive: true, force: true }); }

test('free catalog retains all four M4 provider families, fixed safe routes', () => {
  assert.deepEqual(new Set(ROUTES.map(r => r.family)), new Set(['freellmpool','gpt4free','free-llm-gateway','free-llm-api']));
  assert.ok(ROUTES.every(validateRoute));
  assert.equal(orderedRoutes('gpt4free')[0].family, 'gpt4free');
  assert.equal(validateRoute({ base: 'http://evil.example/v1' }), false);
});
test('free provider fallback reports actual response route', async () => {
  const fetcher = async url => {
    if (url.startsWith('http://127.0.0.1')) return { ok: false, status: 503 };
    return { ok: true, text: async () => JSON.stringify({ choices: [{ message: { content: 'hello' } }] }) };
  };
  const got = await chatCompletion([{ role:'user', content:'hello' }], { family:'gpt4free', fetcher });
  assert.equal(got.content, 'hello');
  assert.equal(got.route, 'Kilo Auto Free');
});
test('path containment rejects traversal, secrets and symlinks', async () => {
  const root = await tmp();
  try {
    await fs.writeFile(path.join(root, 'ok.txt'), 'ok');
    await fs.symlink('/etc', path.join(root, 'escape'));
    assert.equal(workspacePath(root, 'ok.txt'), path.join(root, 'ok.txt'));
    for (const bad of ['../x', '/etc/passwd', '.git/config', '.env', 'escape/passwd', 'abc/../../tmp']) {
      assert.throws(() => workspacePath(root, bad));
    }
    assert.ok(safeCompare('123', '123'));
    assert.ok(!safeCompare('123', '124'));
  } finally { await cleanup(root); }
});
test('file write is verified with hashes; existing files cannot be silently overwritten', async () => {
  const root = await tmp();
  try {
    const created = await invokeTool(root, 'write_file', { path:'hello.txt', content:'hello', expectedHash:'' });
    assert.equal(created.sha256, sha256('hello'));
    await assert.rejects(() => invokeTool(root, 'write_file', { path:'hello.txt', content:'oops' }));
    await assert.rejects(() => invokeTool(root, 'write_file', { path:'hello.txt', content:'oops', expectedHash:'invalid' }));
    await invokeTool(root, 'replace_text', { path:'hello.txt', oldText:'hello', newText:'world', expectedHash:created.sha256 });
    assert.equal((await invokeTool(root, 'read_file', { path:'hello.txt' })).content, 'world');
    await assert.rejects(() => invokeTool(root, 'replace_text', { path:'hello.txt', oldText:'missing', newText:'something' }));
  } finally { await cleanup(root); }
});
test('literal search and workspace enumeration actually read files', async () => {
  const root = await tmp();
  try {
    await fs.writeFile(path.join(root, 'app.py'), 'print("example")\n');
    const found = await invokeTool(root, 'search_code', { query:'example' });
    assert.equal(found.matches[0].line, 1);
    assert.equal((await invokeTool(root, 'list_files', {})).entries[0].path, 'app.py');
  } finally { await cleanup(root); }
});
test('invalid tools, unexpected schemas and fake tool envelopes fail closed', () => {
  assert.throws(() => validateCall('delete_everything', {}));
  assert.throws(() => validateCall('read_file', { path:'x', command:'rm -rf' }));
  assert.deepEqual(parseCalls({ content: 'This is text and NOT a tool result' }), []);
  assert.deepEqual(parseCalls({ content: '{"tool_calls":[{"name":"list_files","arguments":{}}]}' }), [{ name:'list_files', arguments:{} }]);
});
test('agent waits for per-tool approval and verifies successful shell check', async () => {
  const root = await tmp();
  try {
    const hash = sha256('hello');
    await fs.writeFile(path.join(root,'hello.txt'), 'hello');
    const answers = [
      { content: '{"tool_calls":[{"name":"read_file","arguments":{"path":"hello.txt"}}]}' },
      { content: JSON.stringify({tool_calls:[{name:'replace_text',arguments:{path:'hello.txt',oldText:'hello',newText:'world',expectedHash:hash}}]}) },
      { content: '{"tool_calls":[{"name":"run_command","arguments":{"command":"cat hello.txt"}}]}' },
      { content: '{"tool_calls":[{"name":"finish","arguments":{"summary":"Updated hello.txt and checked shell output"}}]}' }
    ];
    const manager = new AgentManager({ workspace:root, complete:async () => answers.shift(), maxSteps:5 });
    const job = manager.start('Edit and test hello.txt');
    const waitUntil = async predicate => {
      for(let i=0;i<250;i++) { if (predicate()) return; await new Promise(r=>setTimeout(r,8)); }
      throw new Error('Agent test timed out');
    };
    await waitUntil(() => manager.snapshot(job.id).pending?.name === 'replace_text');
    assert.equal((await fs.readFile(path.join(root,'hello.txt'),'utf8')), 'hello');
    manager.approve(job.id, manager.snapshot(job.id).pending.id, true);
    await waitUntil(() => manager.snapshot(job.id).pending?.name === 'run_command');
    manager.approve(job.id, manager.snapshot(job.id).pending.id, true);
    await waitUntil(() => manager.snapshot(job.id).status === 'completed');
    const done = manager.snapshot(job.id);
    assert.equal(done.evidence.writes, 1);
    assert.equal(done.evidence.checks, 1);
    assert.equal((await fs.readFile(path.join(root, 'hello.txt'), 'utf8')), 'world');
  } finally { await cleanup(root); }
});
test('denied tool cannot write files', async () => {
  const root = await tmp();
  try {
    const mock = async () => ({ content: '{"tool_calls":[{"name":"write_file","arguments":{"path":"bad.txt","content":"bad","expectedHash":""}}]}' });
    const manager = new AgentManager({ workspace:root, complete:mock, maxSteps:1 });
    const j = manager.start('write file');
    for (let i=0;i<50 && !manager.snapshot(j.id).pending;i++) await new Promise(r=>setTimeout(r,5));
    manager.approve(j.id, manager.snapshot(j.id).pending.id, false);
    for(let i=0;i<50 && manager.snapshot(j.id).status==='running';i++) await new Promise(r=>setTimeout(r,5));
    await assert.rejects(fs.readFile(path.join(root, 'bad.txt')));
  } finally { await cleanup(root); }
});

test('action request answered without tool execution remains blocked', async () => {
  const root = await tmp();
  try {
    const agent = new AgentManager({ workspace:root, complete:async () => ({ content:'Done, fixed everything.' }) });
    const j = agent.start('Fix the project and create code');
    for(let i=0;i<60 && agent.snapshot(j.id).status==='running';i++) await new Promise(r=>setTimeout(r,5));
    const state = agent.snapshot(j.id);
    assert.equal(state.status, 'blocked');
    assert.ok(state.events.some(e => e.type === 'warning'));
  } finally { await cleanup(root); }
});
