import fs from 'node:fs/promises';
import path from 'node:path';
import { enumerate } from './tools.mjs';
import { workspacePath } from './security.mjs';

const MAX_FILES = 300;
const MAX_BYTES = 180_000;
const MAX_RESULTS = 12;
const IGNORE_EXT = /\.(png|jpe?g|gif|webp|ico|pdf|zip|gz|tar|sqlite|db|lock|map|woff2?|ttf|otf|mp[34]|wasm)$/i;

/**
 * Incremental, privacy-preserving lexical + symbol codebase index.
 * This is a REAL on-device retrieval engine, not an embedding vector model:
 * no network calls, no API keys, no remote upload and no false "semantic" claim.
 */
export class ContextIndex {
  constructor(workspace) {
    this.workspace = workspace;
    this.files = new Map();
    this.revision = 0;
  }

  static terms(text) {
    return [...new Set(String(text)
      .replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
      .split(/[^\p{L}\p{N}_$]+/u).flatMap(word => word.split(/[_$]+/))
      .filter(word => word.length >= 2 && word.length <= 48))].slice(0, 40);
  }

  async refresh() {
    const entries = (await enumerate(this.workspace, '', MAX_FILES)).filter(f => f.type === 'file' && !IGNORE_EXT.test(f.path));
    const visible = new Set();
    let updated = 0;
    for (const entry of entries) {
      const name = entry.path;
      visible.add(name);
      try {
        const file = workspacePath(this.workspace, name);
        const stat = await fs.stat(file);
        if (!stat.isFile() || stat.size > MAX_BYTES) { this.files.delete(name); continue; }
        const previous = this.files.get(name);
        if (previous && previous.mtimeMs === stat.mtimeMs && previous.size === stat.size) continue;
        const text = await fs.readFile(file, 'utf8');
        if (text.includes('\0')) { this.files.delete(name); continue; }
        const lines = text.split(/\r?\n/);
        this.files.set(name, {
          mtimeMs: stat.mtimeMs, size: stat.size, lines,
          tokens: new Set(ContextIndex.terms(text.slice(0, MAX_BYTES))),
          filenameTokens: new Set(ContextIndex.terms(path.basename(name))),
        });
        updated++;
      } catch { this.files.delete(name); }
    }
    for (const key of this.files.keys()) if (!visible.has(key)) this.files.delete(key);
    if (updated) this.revision++;
    return { files: this.files.size, updated, revision: this.revision, limited: entries.length >= MAX_FILES };
  }

  async query(text, { limit = MAX_RESULTS } = {}) {
    if (typeof text !== 'string' || text.length > 400 || !text.trim()) throw new Error('Invalid context query');
    const terms = ContextIndex.terms(text);
    if (!terms.length) return { results: [], total: 0 };
    const status = await this.refresh();
    const candidates = [];
    const N = Math.max(1, this.files.size);
    for (const [name, file] of this.files) {
      let score = 0;
      for (const term of terms) {
        if (!file.tokens.has(term) && !file.filenameTokens.has(term)) continue;
        let df = 0;
        for (const item of this.files.values()) if (item.tokens.has(term) || item.filenameTokens.has(term)) df++;
        const idf = Math.log(1 + (N - df + 0.5) / (df + 0.5));
        score += idf * (file.filenameTokens.has(term) ? 5 : 1);
      }
      if (score <= 0) continue;
      const snippets = [];
      for (let i = 0; i < file.lines.length && snippets.length < 3; i++) {
        const line = file.lines[i];
        const lower = line.toLowerCase();
        const matched = terms.filter(t => lower.includes(t));
        if (matched.length) {
          snippets.push({ line: i + 1, text: line.slice(0, 320), matched: matched.slice(0, 5) });
        }
      }
      // A file-name match without matching lines is still useful.
      candidates.push({ path: name, score: Math.round(score * 1000) / 1000, snippets });
    }
    candidates.sort((a,b) => b.score - a.score || a.path.localeCompare(b.path));
    return { results: candidates.slice(0, Math.max(1, Math.min(limit, MAX_RESULTS))), total: candidates.length, index: status };
  }

  invalidate(relative) {
    this.files.delete(relative);
  }
}
