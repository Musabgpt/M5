import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

function call(home,...args){
 const p=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
   cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:18000});
 assert.ok(p.stdout.trim(),'Missing one-shot JSON: '+p.stderr);
 return {exit:p.status,result:JSON.parse(p.stdout.trim())};
}
const b64=s=>Buffer.from(s,'utf8').toString('base64');

test('automatic checkpoint before edits, preview and safe restore with SHA-256',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-history-'));
 try {
   call(home,'ping');
   const first=call(home,'write','main.js',b64('console.log("v1")\n'),'').result;
   assert.equal(first.ok,true);
   const second=call(home,'write','main.js',b64('console.log("v2")\n'),first.hash).result;
   assert.equal(second.ok,true);
   const list=call(home,'history_list','main.js').result;
   assert.equal(list.entries.length,1);
   assert.equal(list.entries[0].hash,first.hash);
   assert.equal(list.currentHash,second.hash);
   const prior=list.entries[0].id;
   const comparison=call(home,'history_compare','main.js',prior).result;
   assert.equal(comparison.oldHash,first.hash);
   assert.equal(comparison.currentHash,second.hash);
   assert.ok(comparison.changes.some(x=>x.before.includes('v1')&&x.after.includes('v2')));
   const stale=call(home,'history_restore','main.js',prior,first.hash).result;
   assert.equal(stale.ok,false);
   assert.match(stale.error,/changed/);
   const restored=call(home,'history_restore','main.js',prior,second.hash).result;
   assert.equal(restored.ok,true);
   assert.equal(restored.hash,first.hash);
   assert.equal(call(home,'read','main.js').result.content,'console.log("v1")\n');
   const undo=call(home,'history_list','main.js').result;
   assert.equal(undo.entries[0].hash,second.hash,'restore itself must be undoable');
   const reverse=call(home,'history_restore','main.js',undo.entries[0].id,first.hash).result;
   assert.equal(reverse.ok,true);
   assert.equal(call(home,'read','main.js').result.content,'console.log("v2")\n');
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('checkpoints are bounded and remain invisible in workspace explorer',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-history-bound-'));
 try{
   call(home,'ping');
   let receipt=call(home,'write','demo.js',b64('const value=0;\n'),'').result;
   for(let i=1;i<=14;i++){
     receipt=call(home,'write','demo.js',b64('const value='+i+';\n'),receipt.hash).result;
     assert.equal(receipt.ok,true,'edit '+i);
   }
   const versions=call(home,'history_list','demo.js').result.entries;
   assert.equal(versions.length,10);
   const folder=path.join(home,'m5-workspace','.m5','history');
   const [key]=await fs.readdir(folder);
   assert.equal((await fs.readdir(path.join(folder,key))).filter(name=>name.endsWith('.json')).length,10);
   const files=call(home,'list').result.files;
   assert.ok(files.some(f=>f.path==='demo.js'));
   assert.ok(!files.some(f=>f.path.includes('.m5')));
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('checkpoint actions reject wrong file, invalid IDs, symlinks and secrets',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-history-secure-'));
 try{
   call(home,'ping');
   const initial=call(home,'write','one.py',b64('print("old")\n'),'').result;
   call(home,'write','one.py',b64('print("new")\n'),initial.hash);
   const id=call(home,'history_list','one.py').result.entries[0].id;
   for(const bad of ['../one.py','.env','/etc/passwd','.ssh/id_rsa']){
     assert.equal(call(home,'history_list',bad).result.ok,false,bad);
   }
   assert.equal(call(home,'history_restore','one.py','bad-id',call(home,'read','one.py').result.hash).result.ok,false);
   assert.equal(call(home,'history_compare','two.py',id).result.ok,false);
   await fs.symlink('/etc/passwd',path.join(home,'m5-workspace','linked.js'));
   assert.equal(call(home,'history_list','linked.js').result.ok,false);
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('APK-embedded bridge source matches history-enabled Termux script',async()=>{
 const src=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url));
 const bundled=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url));
 assert.deepEqual(src,bundled);
});
