import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';

const forbidden = new Set(['.git', '.ssh', '.gnupg', '.aws', '.config', 'node_modules']);
const secretName = /(^\.env(?:\.|$)|(?:^|[._-])(secret|password|private.key|credential|token)(?:[._-]|$)|\.pem$|\.p12$|\.keystore$)/i;

export function workspacePath(root, requested = '', { write = false } = {}) {
  if (typeof requested !== 'string' || requested.length > 500 || requested.includes('\0') || path.isAbsolute(requested)) {
    throw new Error('Invalid workspace path');
  }
  const normalized = requested.replaceAll('\\', '/');
  const segments = normalized.split('/').filter(Boolean);
  if (segments.some(s => s === '..' || s === '.' || forbidden.has(s) || secretName.test(s))) {
    throw new Error('Restricted workspace path');
  }
  const base = fs.realpathSync(root);
  let current = base;
  for (const piece of segments) {
    current = path.join(current, piece);
    if (fs.existsSync(current) || fs.lstatSync(path.dirname(current)).isDirectory()) {
      try {
        const stat = fs.lstatSync(current);
        if (stat.isSymbolicLink()) throw new Error('Symlinks are not allowed');
      } catch (e) {
        if (e.code !== 'ENOENT') throw e;
      }
    }
  }
  const target = path.resolve(base, ...segments);
  if (target !== base && !target.startsWith(base + path.sep)) throw new Error('Path escapes workspace');
  if (write && !fs.existsSync(path.dirname(target))) throw new Error('Parent directory does not exist');
  return target;
}

export function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export function safeCompare(a, b) {
  const aa = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  return aa.length === bb.length && crypto.timingSafeEqual(aa, bb);
}

export function assertContent(value, maxBytes = 1_000_000) {
  if (typeof value !== 'string' || Buffer.byteLength(value, 'utf8') > maxBytes) throw new Error('Content exceeds safety limit');
}
