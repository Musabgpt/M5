import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';

function run(home,...args){
  const p=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
    cwd:process.cwd(),encoding:'utf8',env:{...process.env,HOME:home},timeout:16000
  });
  assert.ok(p.stdout.trim(),'No JSON receipt: '+p.stderr);
  return {exit:p.status,body:JSON.parse(p.stdout.trim())};
}

test('native JS check+run returns real stdout and exit result',async()=>{
  const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-run-'));
  try{
    assert.equal(run(home,'ping').body.ok,true);
    assert.equal(run(home,'write','hello.mjs',Buffer.from('console.log("REAL_RESULT_42")\n').toString('base64'),'').body.ok,true);
    const syntax=run(home,'check_file','hello.mjs').body;
    assert.equal(syntax.verified,true);
    assert.equal(syntax.mode,'check');
    assert.equal(syntax.exitCode,0);
    const executed=run(home,'run_file','hello.mjs').body;
    assert.equal(executed.success,true);
    assert.match(executed.stdout,/REAL_RESULT_42/);
    assert.equal(executed.exitCode,0);
    await fs.writeFile(path.join(home,'m5-workspace','bad.js'),'const = ;\n');
    const bad=run(home,'check_file','bad.js').body;
    assert.equal(bad.verified,false);
    assert.equal(bad.success,false);
    assert.notEqual(bad.exitCode,0);
    assert.ok(bad.stderr.length>0);
  }finally{await fs.rm(home,{recursive:true,force:true});}
});

test('native source tools reject unsupported extensions, traversal, symlinks and oversize files',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-run-guard-'));
 try{
    run(home,'ping');
    const ws=path.join(home,'m5-workspace');
    await fs.writeFile(path.join(ws,'secret.txt'),'do not run');
    await fs.symlink('/etc/passwd',path.join(ws,'link.js'));
    await fs.writeFile(path.join(ws,'big.js'),Buffer.alloc(41000,65));
    for(const relative of ['secret.txt','link.js','big.js','../escape.js','/etc/passwd','.env']){
      assert.equal(run(home,'check_file',relative).body.ok,false,relative);
      assert.equal(run(home,'run_file',relative).body.ok,false,relative);
    }
 }finally{await fs.rm(home,{recursive:true,force:true});}
});

test('CREATE-ONLY writes never overwrite existing empty files, even with matching empty SHA',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-create-'));
 try{
   run(home,'ping');
   const ws=path.join(home,'m5-workspace');
   await fs.writeFile(path.join(ws,'existing.js'),'');
   const body=Buffer.from('console.log(123)').toString('base64');
   assert.equal(run(home,'write','existing.js',body,'').body.ok,false);
   assert.equal(run(home,'write','new.js',body,'').body.ok,true);
   assert.equal(run(home,'write','new.js',body,'').body.ok,false);
   assert.equal(await fs.readFile(path.join(ws,'existing.js'),'utf8'),'');
 }finally{await fs.rm(home,{recursive:true,force:true});}
});

test('bundled Termux bridge includes exactly the audited runtime source',async()=>{
 const source=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url));
 const packaged=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url));
 assert.deepEqual(packaged,source);
 const android=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
 assert.match(android,/check_file\|run_file/);
});
