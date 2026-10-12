import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { AgentManager } from '../server/agent.mjs';

const sleep = ms => new Promise(r => setTimeout(r, ms));
async function until(predicate) {
  for (let i = 0; i < 120; i++) { if (predicate()) return; await sleep(8); }
  throw new Error('Agent did not reach expected state');
}
test('Plan mode reads project but cannot write even if a provider requests a mutation', async () => {
  const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-plan-'));
  try {
    await fs.writeFile(path.join(workspace, 'source.ts'), 'export const valid = true;');
    const answers = [
      { content: '{"tool_calls":[{"name":"read_file","arguments":{"path":"source.ts"}}]}' },
      { content: '{"tool_calls":[{"name":"write_file","arguments":{"path":"unsafe.ts","content":"mutated","expectedHash":""}}]}' },
      { content: '{"tool_calls":[{"name":"finish","arguments":{"summary":"Plan only, no files modified"}}]}' }
    ];
    const agent = new AgentManager({ workspace, complete: async () => answers.shift(), maxSteps: 5 });
    const job = agent.start('Plan how to create new files safely', 'freellmpool', 'plan');
    await until(() => agent.snapshot(job.id).status === 'completed');
    const snap = agent.snapshot(job.id);
    assert.equal(snap.mode, 'plan');
    assert.equal(snap.pending, null);
    assert.equal(snap.evidence.writes, 0);
    assert.ok(snap.events.some(e => e.type === 'tool_error' && /read-only/.test(e.data.error)));
    await assert.rejects(fs.readFile(path.join(workspace, 'unsafe.ts')));
  } finally { await fs.rm(workspace, { recursive: true, force: true }); }
});
test('Plan mode may produce a read-only answer without a tool call', async () => {
  const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-readonly-'));
  try {
    const agent = new AgentManager({ workspace, complete: async () => ({ content: 'Proposed implementation steps, without executing.' }) });
    const job = agent.start('Plan how to fix the app', 'freellmpool', 'plan');
    await until(() => agent.snapshot(job.id).status === 'completed');
    assert.equal(agent.snapshot(job.id).evidence.checks, 0);
    assert.throws(() => agent.start('anything', 'freellmpool', 'unsafe-mode'), /Invalid agent mode/);
  } finally { await fs.rm(workspace, { recursive: true, force: true }); }
});
