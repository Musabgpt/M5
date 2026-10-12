import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

function command(home,...args){
  const child=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
    cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:15000});
  return {exit:child.status,result:JSON.parse(child.stdout.trim()),stderr:child.stderr};
}

test('offline calculator creates a runnable 3-file web app and verifies JS syntax',async()=>{
  const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-project-'));
  try{
    const r=command(home,'scaffold','MyCalculator','web-calculator');
    assert.equal(r.exit,0,r.stderr);
    assert.equal(r.result.ok,true);
    assert.equal(r.result.verification,'node --check passed');
    const project=path.join(home,'m5-workspace','MyCalculator');
    const html=await fs.readFile(path.join(project,'index.html'),'utf8');
    const js=await fs.readFile(path.join(project,'app.js'),'utf8');
    assert.match(html,/id="calculate"/);
    assert.match(html,/viewport/);
    assert.match(js,/Cannot divide by zero/);
    assert.doesNotMatch(js,/\beval\s*\(/);
    const check=spawnSync(process.execPath,['--check',path.join(project,'app.js')],{encoding:'utf8'});
    assert.equal(check.status,0,check.stderr);
    const listed=command(home,'list');
    assert.equal(listed.result.ok,true);
    assert.ok(listed.result.files.some(x=>x.path==='MyCalculator/app.js'));
    const read=command(home,'read','MyCalculator/app.js');
    assert.equal(read.result.content,js);
    const repeat=command(home,'scaffold','MyCalculator','web-calculator');
    assert.equal(repeat.result.ok,false);
    assert.match(repeat.result.error,/exists/);
    assert.equal(await fs.readFile(path.join(project,'app.js'),'utf8'),js);
  }finally{await fs.rm(home,{recursive:true,force:true});}
});

test('offline templates work without API keys and reject invalid project paths',async()=>{
  const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-starter-'));
  try{
    for(const [name,type] of [['NodeStarter','node'],['PythonStarter','python']]){
      const r=command(home,'scaffold',name,type);
      assert.equal(r.result.ok,true,r.stderr);
      assert.ok(r.result.files.length>=2);
    }
    for(const name of ['../escape','/etc/passwd','a','bad space','.env','bad;rm']){
      const r=command(home,'scaffold',name,'node');
      assert.equal(r.result.ok,false,name);
    }
    assert.equal(command(home,'scaffold','SafeName','unknown-template').result.ok,false);
  }finally{await fs.rm(home,{recursive:true,force:true});}
});

test('APK copy of native bridge matches source after every edit',async()=>{
  const source=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url));
  const bundled=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url));
  assert.deepEqual(bundled,source);
});

test('NativeAgent dispatches phone actions before enforcing workspace tool whitelist',async()=>{
  const file=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/NativeAgent.java',import.meta.url),'utf8');
  const phone=file.indexOf('if(name.startsWith("phone_")){');
  const workspace=file.indexOf('if(!name.matches("list_files|read_file|search_code');
  assert.ok(phone>0 && workspace>phone,'phone tools must not be rejected by workspace-only whitelist');
});
