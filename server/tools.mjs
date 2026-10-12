import fs from 'node:fs/promises';
import path from 'node:path';
import { workspacePath, sha256, assertContent } from './security.mjs';
import { runProcess } from './process.mjs';

const ignored = new Set(['node_modules', '.git', '.m5', '.gradle', 'dist', 'build']);
export const TOOL_SPECS = Object.freeze({
  list_files: { risk: 'read', required: [], props: { path: 'string' } },
  read_file: { risk: 'read', required: ['path'], props: { path: 'string' } },
  search_code: { risk: 'read', required: ['query'], props: { query: 'string' } },
  code_context: { risk: 'read', required: ['query'], props: { query: 'string' } },
  git_status: { risk: 'read', required: [], props: {} },
  git_diff: { risk: 'read', required: [], props: {} },
  write_file: { risk: 'write', required: ['path', 'content'], props: { path: 'string', content: 'string', expectedHash: 'string' } },
  replace_text: { risk: 'write', required: ['path', 'oldText', 'newText'], props: { path: 'string', oldText: 'string', newText: 'string', expectedHash: 'string' } },
  run_command: { risk: 'execute', required: ['command'], props: { command: 'string', timeoutMs: 'number' } },
  finish: { risk: 'read', required: ['summary'], props: { summary: 'string' } }
});
export function validateCall(name, args) {
  const spec = TOOL_SPECS[name];
  if (!spec) throw new Error('Unknown tool');
  if (!args || typeof args !== 'object' || Array.isArray(args)) throw new Error('Expected object');
  for (const [key, value] of Object.entries(args)) {
    if (!spec.props[key] || typeof value !== spec.props[key]) throw new Error('Invalid argument ' + key);
  }
  for (const key of spec.required) if (!(key in args)) throw new Error('Missing ' + key);
  return spec;
}
export async function enumerate(workspace, relative = '', max = 250) {
  const start = workspacePath(workspace, relative);
  const out = [];
  async function walk(dir, prefix, depth) {
    if (depth > 7 || out.length >= max) return;
    for (const ent of await fs.readdir(dir, { withFileTypes: true })) {
      if (ignored.has(ent.name) || ent.isSymbolicLink() || ent.name.startsWith('.env')) continue;
      const rel = path.posix.join(prefix, ent.name);
      try {
        workspacePath(workspace, rel);
        out.push({ path: rel, type: ent.isDirectory() ? 'directory' : 'file' });
        if (ent.isDirectory()) await walk(path.join(dir, ent.name), rel, depth + 1);
      } catch {}
      if (out.length >= max) break;
    }
  }
  await walk(start, relative, 0);
  return out;
}
async function read(workspace, p, max = 200_000) {
  const target = workspacePath(workspace, p);
  const stat = await fs.stat(target);
  if (!stat.isFile() || stat.size > max) throw new Error('File too large or not regular');
  const b = await fs.readFile(target);
  if (b.includes(0)) throw new Error('Binary file');
  return b.toString('utf8');
}
async function save(workspace, name, content, expectedHash) {
  assertContent(content);
  const target = workspacePath(workspace, name, { write: true });
  let prior = null;
  try { prior = await read(workspace, name, 1_000_000); } catch (e) { if (e.code !== 'ENOENT') throw e; }
  if (prior !== null && expectedHash === undefined) throw new Error('expectedHash required to change existing file');
  if (expectedHash !== undefined && expectedHash !== (prior === null ? '' : sha256(prior))) throw new Error('Concurrent file change');
  const temp = target + '.m5-' + Math.random().toString(16).slice(2);
  try {
    await fs.writeFile(temp, content, { mode: 0o600, flag: 'wx' });
    workspacePath(workspace, name, { write: true });
    await fs.rename(temp, target);
    return { path: name, sha256: sha256(content), bytes: Buffer.byteLength(content) };
  } finally { await fs.rm(temp, { force: true }); }
}
export async function invokeTool(workspace, name, args, { signal, contextIndex } = {}) {
  validateCall(name, args);
  switch (name) {
    case 'list_files': return { entries: await enumerate(workspace, args.path || '') };
    case 'read_file': { const content = await read(workspace, args.path); return { path: args.path, content, sha256: sha256(content) }; }
    case 'search_code': {
      if (!args.query.trim() || args.query.length > 180) throw new Error('Invalid query');
      const matches = [];
      for (const ent of await enumerate(workspace, '', 400)) {
        if (ent.type !== 'file') continue;
        let text; try { text = await read(workspace, ent.path, 100_000); } catch { continue; }
        text.split('\n').forEach((line, i) => {
          if (line.includes(args.query) && matches.length < 100) matches.push({ path: ent.path, line: i + 1, text: line.slice(0, 240) });
        });
        if (matches.length >= 100) break;
      }
      return { matches, truncated: matches.length >= 100 };
    }
    case 'code_context': {
      if (!contextIndex) throw new Error('Context index is not configured');
      return contextIndex.query(args.query);
    }
    case 'git_status': return runProcess('git', ['status', '--short'], workspace, { timeoutMs: 10000, signal });
    case 'git_diff': return runProcess('git', ['diff', '--no-ext-diff', '--'], workspace, { timeoutMs: 10000, signal, maxOutput: 90_000 });
    case 'write_file': return save(workspace, args.path, args.content, args.expectedHash);
    case 'replace_text': {
      const prior = await read(workspace, args.path, 1_000_000);
      if (!args.oldText || prior.split(args.oldText).length !== 2) throw new Error('Old text not found exactly once');
      if (args.expectedHash !== undefined && args.expectedHash !== sha256(prior)) throw new Error('Concurrent change');
      return save(workspace, args.path, prior.replace(args.oldText, args.newText), sha256(prior));
    }
    case 'run_command': {
      if (!args.command.trim() || args.command.length > 3000) throw new Error('Invalid command length');
      return runProcess('bash', ['-lc', args.command], workspace, { timeoutMs: args.timeoutMs || 60_000, signal });
    }
    case 'finish': return { summary: args.summary };
    default: throw new Error('Unknown tool');
  }
}
