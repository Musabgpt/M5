import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { runProcess } from './process.mjs';

/** Optional trusted code-server process, isolated host and password auth. */
export class IDEManager {
  constructor({ workspace, state }) {
    this.workspace = workspace;
    this.state = state;
    this.child = null;
    this.passwordFile = path.join(state, 'ide-password');
  }
  async installed() {
    try {
      const result = await runProcess('code-server', ['--version'], this.workspace, { timeoutMs: 8000 });
      return result.success;
    } catch { return false; }
  }
  async occupied() {
    return await new Promise(resolve => {
      const s = net.connect({ host:'localhost', port:8778 });
      s.once('connect', () => { s.destroy(); resolve(true); });
      s.once('error', () => resolve(false));
      s.setTimeout(1200, () => { s.destroy(); resolve(false); });
    });
  }
  async status() {
    return { installed: await this.installed(), running: !!this.child && this.child.exitCode === null, port:8778, target:'http://localhost:8778', requiresPassword:true };
  }
  async start() {
    if (!(await this.installed())) throw new Error('code-server is not installed. In Termux: pkg install tur-repo && pkg install code-server');
    await fs.mkdir(this.state, { recursive:true, mode:0o700 });
    let password;
    try { password = (await fs.readFile(this.passwordFile, 'utf8')).trim(); }
    catch (e) {
      if (e.code !== 'ENOENT') throw e;
      password = crypto.randomBytes(24).toString('hex');
      await fs.writeFile(this.passwordFile, password, { flag:'wx', mode:0o600 });
    }
    if (!/^[a-f0-9]{48}$/.test(password)) throw new Error('Invalid code-server password state');
    if (this.child && this.child.exitCode === null) return { ...(await this.status()), password };
    if (await this.occupied()) throw new Error('IDE port 8778 is owned by another process; refusing to attach');
    const environment = Object.fromEntries(['HOME','PATH','TMPDIR','LANG','TERM'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
    environment.PASSWORD = password;
    environment.CODE_SERVER_COOKIE_SUFFIX = 'm5-ide';
    this.child = spawn('code-server', ['--bind-addr','localhost:8778','--auth','password','--disable-telemetry','--disable-update-check','--user-data-dir',path.join(this.state,'vscode'),'--disable-proxy',this.workspace], {
      cwd:this.workspace, env:environment, stdio:['ignore','ignore','ignore']
    });
    this.child.on('error', () => { this.child = null; });
    let ready = false;
    for (let tries = 0; tries < 32; tries++) {
      if (!this.child || this.child.exitCode !== null) break;
      if (await this.occupied()) { ready = true; break; }
      await new Promise(resolve => setTimeout(resolve, 200));
    }
    if (!ready) throw new Error('code-server did not open a listening IDE; inspect Termux installation and runtime');
    return { installed:true, running:true, port:8778, target:'http://localhost:8778', requiresPassword:true, password };
  }
}
