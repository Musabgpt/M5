import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function session(home,...args){
  const result=spawnSync(process.execPath,['scripts/termux-sessions.mjs',...args],{
    cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:12000
  });
  assert.ok(result.stdout.trim(),'Missing JSON: '+result.stderr);
  return JSON.parse(result.stdout.trim());
}
async function awaitDone(home,id,maxMs=9000){
  const end=Date.now()+maxMs;
  while(Date.now()<end){
    const current=session(home,'read',id);
    if(current.status==='finished'||current.status==='failed')return current;
    await sleep(100);
  }
  assert.fail('Background worker did not finish inside limit');
}
test('detached Termux command worker captures real stdout, stderr, exit status and survives caller exit',async()=>{
  const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-sessions-test-'));
  try{
    await fs.mkdir(path.join(home,'m5-workspace'),{recursive:true});
    const value=Buffer.from('echo out-marker; echo err-marker >&2; exit 7').toString('base64');
    const started=session(home,'start',value,'','700');
    assert.equal(started.ok,true,JSON.stringify(started));
    assert.match(started.session_id,/^[a-f0-9]{24}$/);
    const done=await awaitDone(home,started.session_id);
    assert.equal(done.exit_code,7,JSON.stringify(done));
    assert.match(done.stdout,/out-marker/);
    assert.match(done.stderr,/err-marker/);
    assert.equal(done.status,'finished');
    assert.equal(session(home,'read',started.session_id).exit_code,7);
  }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('session rejects workspace path escapes, invalid IDs and unsupported interactive writes',async()=>{
  const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-sessions-guard-'));
  try{
    await fs.mkdir(path.join(home,'m5-workspace'),{recursive:true});
    const arg=Buffer.from('echo safe').toString('base64');
    for(const bad of ['../','/tmp','.//../../'])assert.equal(session(home,'start',arg,bad,'100').ok,false,bad);
    assert.equal(session(home,'read','not-an-id').ok,false);
    const started=session(home,'start',arg,'','700');
    assert.equal(started.ok,true);
    assert.equal(session(home,'input',started.session_id,Buffer.from('data').toString('base64')).ok,false);
    await awaitDone(home,started.session_id);
  }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('Android APK bundles the exact persistent Termux script from repo',async()=>{
  const source=await fs.readFile(new URL('../scripts/termux-sessions.mjs',import.meta.url));
  const apk=await fs.readFile(new URL('../app/src/main/assets/termux-sessions.mjs',import.meta.url));
  assert.deepEqual(apk,source);
});
