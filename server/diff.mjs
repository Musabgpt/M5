import fs from 'node:fs/promises';
import { workspacePath } from './security.mjs';

/**
 * Bounded, truthful single-region unified-style diff for human approval.
 * It does not claim to be a full Git patch and doesn't apply the change.
 */
export function compactDiff(before, after, relative = 'file', { context = 3, maxLength = 8000 } = {}) {
  if (before === after) return 'NO CHANGE';
  const oldLines = before.split('\n'), newLines = after.split('\n');
  let start = 0;
  while (start < oldLines.length && start < newLines.length && oldLines[start] === newLines[start]) start++;
  let oldEnd = oldLines.length, newEnd = newLines.length;
  while (oldEnd > start && newEnd > start && oldLines[oldEnd-1] === newLines[newEnd-1]) { oldEnd--; newEnd--; }
  const oldFirst = Math.max(0, start-context), newFirst = Math.max(0, start-context);
  const oldLast = Math.min(oldLines.length, oldEnd+context);
  const newLast = Math.min(newLines.length, newEnd+context);
  const prefix = oldLines.slice(oldFirst, start).map(x=>' '+x);
  const removed = oldLines.slice(start,oldEnd).map(x=>'-'+x);
  const inserted = newLines.slice(start,newEnd).map(x=>'+'+x);
  const suffix = oldLines.slice(oldEnd,oldLast).map(x=>' '+x);
  const header = [
    '--- a/'+relative,
    '+++ b/'+relative,
    '@@ -'+(oldFirst+1)+','+(oldLast-oldFirst)+' +'+(newFirst+1)+','+(newLast-newFirst)+' @@'
  ];
  let text = [...header,...prefix,...removed,...inserted,...suffix].join('\n');
  if (text.length > maxLength) text = text.slice(0,maxLength)+'\n[PREVIEW TRUNCATED — inspect file before approving]';
  return text;
}

export async function previewAgentEdit(workspace, name, args) {
  if (!['write_file','replace_text'].includes(name)) return null;
  const file = workspacePath(workspace, args.path);
  let old = '';
  try {
    const stat = await fs.stat(file);
    if (!stat.isFile() || stat.size > 1_000_000) throw new Error('Unable to review large or special file');
    old = await fs.readFile(file,'utf8');
  } catch(e) { if(e.code !== 'ENOENT') throw e; }
  let proposed;
  if (name === 'write_file') proposed = args.content;
  else {
    if (!args.oldText || old.split(args.oldText).length !== 2) throw new Error('Expected unique old text for review');
    proposed = old.replace(args.oldText,args.newText);
  }
  return compactDiff(old, proposed, args.path);
}
