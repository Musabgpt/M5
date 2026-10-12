import { spawn } from 'node:child_process';

export async function runProcess(executable, args, cwd, { timeoutMs = 60_000, signal, maxOutput = 40_000 } = {}) {
  if (!Array.isArray(args) || args.some(x => typeof x !== 'string')) throw new Error('Invalid process arguments');
  timeoutMs = Math.min(120_000, Math.max(1000, timeoutMs));
  return await new Promise((resolve, reject) => {
    // Never inject provider credentials or the IDE session token into child process environments.
    const env = Object.fromEntries(['PATH', 'HOME', 'TMPDIR', 'LANG', 'TERM'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
    const child = spawn(executable, args, { cwd, env, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '', stderr = '', timedOut = false, truncated = false;
    const add = (key, bytes) => {
      const chunk = bytes.toString('utf8');
      if (key === 'out') {
        const remaining = maxOutput - stdout.length;
        stdout += chunk.slice(0, Math.max(remaining, 0));
        if (chunk.length > remaining) truncated = true;
      } else {
        const remaining = maxOutput - stderr.length;
        stderr += chunk.slice(0, Math.max(remaining, 0));
        if (chunk.length > remaining) truncated = true;
      }
    };
    child.stdout.on('data', d => add('out', d));
    child.stderr.on('data', d => add('err', d));
    const timer = setTimeout(() => { timedOut = true; child.kill('SIGKILL'); }, timeoutMs);
    const abort = () => child.kill('SIGKILL');
    signal?.addEventListener('abort', abort, { once: true });
    child.on('error', error => { clearTimeout(timer); signal?.removeEventListener('abort', abort); reject(error); });
    child.on('close', code => {
      clearTimeout(timer); signal?.removeEventListener('abort', abort);
      resolve({ code, stdout, stderr, timedOut, truncated, success: code === 0 && !timedOut && !signal?.aborted });
    });
  });
}
