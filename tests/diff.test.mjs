import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { compactDiff, previewAgentEdit } from '../server/diff.mjs';
import { AgentManager } from '../server/agent.mjs';

test('review diff shows contextual additions/deletions and limits output size', () => {
  const d = compactDiff('const value = 1;\nreturn value;', 'const value = 2;\nreturn value;', 'src/code.js');
  assert.match(d, /--- a\/src\/code.js/);
  assert.match(d, /-const value = 1;/);
  assert.match(d, /\+const value = 2;/);
  assert.equal(compactDiff('same', 'same'), 'NO CHANGE');
  assert.match(compactDiff('', 'x'.repeat(10000),'big.txt'), /PREVIEW TRUNCATED/);
});
test('agent does not request write approval if it cannot produce a safe real preview', async () => {
  const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-review-'));
  try {
    await fs.writeFile(path.join(workspace,'a.txt'), 'original');
    const d = await previewAgentEdit(workspace, 'replace_text', { path:'a.txt', oldText:'original', newText:'updated' });
    assert.match(d, /-original/);
    assert.match(d, /\+updated/);
    await assert.rejects(() => previewAgentEdit(workspace, 'replace_text', { path:'a.txt', oldText:'missing', newText:'updated' }), /Expected unique/);
    const answers = [
      { content:'{"tool_calls":[{"name":"replace_text","arguments":{"path":"a.txt","oldText":"missing","newText":"unsafe"}}]}' },
      { content:'{"tool_calls":[{"name":"finish","arguments":{"summary":"Cannot edit because requested match is absent"}}]}' }
    ];
    const agent = new AgentManager({ workspace, complete:async()=>answers.shift(), maxSteps:4 });
    const j=agent.start('Explain why the text replacement cannot be applied', 'freellmpool', 'agent');
    for(let i=0;i<150 && agent.snapshot(j.id).status==='running';i++) await new Promise(r=>setTimeout(r,5));
    assert.equal(agent.snapshot(j.id).pending,null);
    assert.equal(await fs.readFile(path.join(workspace,'a.txt'),'utf8'),'original');
  } finally { await fs.rm(workspace,{recursive:true,force:true}); }
});
