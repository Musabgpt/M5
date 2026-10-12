import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { CheckpointStore } from '../server/checkpoints.mjs';
import { sha256 } from '../server/security.mjs';

test('file snapshots retain content and require matching current hash to restore', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-checkpoints-'));
  const workspace = path.join(dir, 'workspace');
  const state = path.join(dir, 'state');
  await fs.mkdir(workspace);
  const store = new CheckpointStore({ workspace, state });
  try {
    await fs.writeFile(path.join(workspace, 'a.txt'), 'before');
    const snapshot = await store.capture('owner-1', 'a.txt');
    assert.equal(snapshot.beforeHash, sha256('before'));
    assert.ok((await store.list()).some(row => row.id === snapshot.id));
    await fs.writeFile(path.join(workspace, 'a.txt'), 'after');
    await assert.rejects(() => store.restore(snapshot.id, sha256('different')));
    assert.equal(await fs.readFile(path.join(workspace, 'a.txt'), 'utf8'), 'after');
    const result = await store.restore(snapshot.id, sha256('after'));
    assert.equal(result.restored, true);
    assert.equal(await fs.readFile(path.join(workspace, 'a.txt'), 'utf8'), 'before');
  } finally { await fs.rm(dir, { recursive:true, force:true }); }
});
test('restoring a checkpoint of a newly created file safely removes it', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'm5-newfile-'));
  const workspace = path.join(dir, 'workspace');
  await fs.mkdir(workspace);
  const store = new CheckpointStore({ workspace, state:path.join(dir,'state') });
  try {
    const snapshot = await store.capture('editor', 'new.txt');
    await fs.writeFile(path.join(workspace,'new.txt'),'generated');
    const restored = await store.restore(snapshot.id, sha256('generated'));
    assert.equal(restored.deletedNewFile, true);
    await assert.rejects(fs.readFile(path.join(workspace,'new.txt')));
  } finally { await fs.rm(dir, { recursive:true, force:true }); }
});
