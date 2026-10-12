import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { workspacePath, sha256 } from './security.mjs';
import { invokeTool } from './tools.mjs';

/** Private, size-capped before-edit file checkpoints. Not a process or Git-history checkpoint. */
export class CheckpointStore {
  constructor({ workspace, state }) {
    this.workspace = workspace;
    this.dir = path.join(state, 'checkpoints');
    this.ready = fs.mkdir(this.dir, { recursive: true, mode: 0o700 });
  }
  async capture(owner, relative) {
    await this.ready;
    const p = workspacePath(this.workspace, relative);
    let previous = null;
    try {
      const stat = await fs.stat(p);
      if (!stat.isFile() || stat.size > 200_000) throw new Error('File too large for checkpoint');
      previous = await fs.readFile(p, 'utf8');
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
    }
    const id = crypto.randomUUID();
    const value = { id, owner: String(owner).slice(0, 70), path: relative, createdAt: new Date().toISOString(), beforeHash: previous === null ? '' : sha256(previous), previous };
    const destination = path.join(this.dir, id + '.json');
    await fs.writeFile(destination, JSON.stringify(value), { flag: 'wx', mode: 0o600 });
    return { id, path: relative, beforeHash: value.beforeHash, createdAt: value.createdAt, wasExisting: previous !== null };
  }
  async list() {
    await this.ready;
    const names = (await fs.readdir(this.dir)).filter(x => /^[a-f0-9-]{36}\.json$/.test(x)).slice(-100);
    const output = [];
    for (const file of names) {
      try {
        const data = JSON.parse(await fs.readFile(path.join(this.dir, file), 'utf8'));
        output.push({ id: data.id, path: data.path, createdAt: data.createdAt, owner: data.owner, beforeHash: data.beforeHash, wasExisting: data.previous !== null });
      } catch {}
    }
    return output.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
  async restore(id, expectedCurrentHash) {
    await this.ready;
    if (!/^[a-f0-9-]{36}$/.test(id) || typeof expectedCurrentHash !== 'string') throw new Error('Invalid checkpoint request');
    const record = JSON.parse(await fs.readFile(path.join(this.dir, id + '.json'), 'utf8'));
    const target = workspacePath(this.workspace, record.path, { write: true });
    let current = null;
    try { current = await fs.readFile(target, 'utf8'); }
    catch (e) { if (e.code !== 'ENOENT') throw e; }
    const actual = current === null ? '' : sha256(current);
    if (actual !== expectedCurrentHash) throw new Error('File changed since restore review');
    if (record.previous === null) {
      if (current !== null) await fs.unlink(target);
      return { path: record.path, restored: true, deletedNewFile: true };
    }
    const outcome = await invokeTool(this.workspace, 'write_file', { path: record.path, content: record.previous, expectedHash: actual });
    return { ...outcome, restored: true };
  }
}
