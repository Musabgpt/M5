import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';

function tool(root,...args){
  const out=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
    cwd:process.cwd(),env:{...process.env,HOME:root},encoding:'utf8',timeout:14000
  });
  return {status:out.status,stderr:out.stderr,body:JSON.parse(out.stdout.trim())};
}
const hash=data=>crypto.createHash('sha256').update(data).digest('hex');
test('one-shot Termux bridge creates/reads/edits projects without HTTP',async()=>{
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),'m5-native-'));
  try {
    const ping=tool(dir,'ping');
    assert.equal(ping.status,0);
    assert.equal(ping.body.runtime,'native-termux');
    const first=Buffer.from('print("hello")\n','utf8').toString('base64');
    const created=tool(dir,'write','hello.py',first,'');
    assert.equal(created.body.ok,true,created.stderr);
    const read=tool(dir,'read','hello.py');
    assert.equal(read.body.content,'print("hello")\n');
    assert.equal(read.body.hash,created.body.hash);
    const changed=tool(dir,'write','hello.py',Buffer.from('print("world")\n').toString('base64'),read.body.hash);
    assert.equal(changed.body.ok,true);
    const stale=tool(dir,'write','hello.py',Buffer.from('bad').toString('base64'),read.body.hash);
    assert.equal(stale.body.ok,false);
    const listing=tool(dir,'list');
    assert.ok(listing.body.files.some(f=>f.path==='hello.py'));
    const search=tool(dir,'search','world');
    assert.equal(search.body.matches[0].path,'hello.py');
    const shell=tool(dir,'shell','pwd');
    assert.equal(shell.body.success,true);
    assert.match(shell.body.stdout,/m5-workspace/);
  }finally{await fs.rm(dir,{recursive:true,force:true});}
});
test('native bridge refuses traversal, sensitive paths, symlinks and oversize files',async()=>{
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),'m5-native-sec-'));
  try {
    tool(dir,'ping');
    await fs.symlink('/etc/passwd',path.join(dir,'m5-workspace','escape.txt'));
    for(const name of ['../bad.txt','/etc/passwd','.env','escape.txt','.git/config']){
      assert.equal(tool(dir,'read',name).body.ok,false,name);
    }
    const huge=tool(dir,'write','huge.py',Buffer.alloc(41000,65).toString('base64'),'');
    assert.equal(huge.body.ok,false);
  }finally{await fs.rm(dir,{recursive:true,force:true});}
});
test('native Android app has no WebView or localhost runtime dependency',async()=>{
  const activity=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/M5Activity.java',import.meta.url),'utf8');
  const bridge=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
  const manifest=await fs.readFile(new URL('../app/src/main/AndroidManifest.xml',import.meta.url),'utf8');
  for(const source of [activity,bridge]){
    assert.doesNotMatch(source,/new WebView|http:\/\/127\.0\.0\.1|localhost:8765|startServer|node server\/index\.mjs/);
  }
  assert.match(activity,/extends Activity/);
  assert.match(bridge,/com\.termux\.RUN_COMMAND_PENDING_INTENT/);
  assert.match(bridge,/getBundleExtra\("result"\)/);
  assert.match(bridge,/FLAG_ONE_SHOT/);
  assert.doesNotMatch(manifest,/usesCleartextTraffic="true"|networkSecurityConfig/);
});
