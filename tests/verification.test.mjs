import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { isVerificationCommand } from '../server/verification.mjs';
import { AgentManager } from '../server/agent.mjs';

test('QA accepts validators but rejects arbitrary successful commands and fake shell short-circuits', () => {
  for (const command of ['npm test', 'node --test', 'node --check app.js', 'python3 -m py_compile agent.py', 'pytest -q', 'grep -q verified file.txt', 'bash -n script.sh', 'cargo test']) {
    assert.ok(isVerificationCommand(command), command);
  }
  for (const command of ['cat file.txt', 'pwd', 'echo success', 'npm test || true', 'npm test; exit 0', 'npm test && echo ok', 'rm -rf .', 'node -e "process.exit(0)"']) {
    assert.ok(!isVerificationCommand(command), command);
  }
});
test('agent does not claim verified completion merely because an echo command succeeds', async () => {
  const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-verify-'));
  try {
    const answers = [
      { content:'{"tool_calls":[{"name":"write_file","arguments":{"path":"new.txt","content":"hello","expectedHash":""}}]}' },
      { content:'{"tool_calls":[{"name":"run_command","arguments":{"command":"echo success"}}]}' },
      { content:'{"tool_calls":[{"name":"finish","arguments":{"summary":"done"}}]}' }
    ];
    const agent = new AgentManager({ workspace, complete: async () => answers.shift(), maxSteps:5 });
    const job=agent.start('Create a verified file', 'freellmpool', 'agent');
    let approved=0;
    for(let i=0;i<400;i++) {
      const snap=agent.snapshot(job.id);
      if(snap.pending) { agent.approve(job.id,snap.pending.id,true); approved++; }
      if(snap.status==='blocked') break;
      await new Promise(r=>setTimeout(r,5));
    }
    const snap=agent.snapshot(job.id);
    assert.equal(snap.status, 'blocked');
    assert.equal(approved, 2);
    assert.equal(snap.evidence.writes,1);
    assert.equal(snap.evidence.executions,1);
    assert.equal(snap.evidence.checks,0);
  } finally { await fs.rm(workspace,{recursive:true,force:true}); }
});
