import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createServer} from 'node:http';
function task(home,...params) {
  const p=spawnSync(process.execPath,['scripts/native-bridge.mjs',...params],{
    cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:18000});
  assert.ok(p.stdout.trim(),p.stderr);
  return JSON.parse(p.stdout.trim());
}
const encode=text=>Buffer.from(text,'utf8').toString('base64');
test('AEOS patch applies exact hunk, checks syntax, preserves checkpoint and refuses stale hash',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'aeos-patch-'));
 try {
   task(home,'ping');
   const initial=task(home,'write','main.js',encode('const number = 5;\nconsole.log(number);\n'),'');
   assert.equal(initial.ok,true);
   const diff='--- a/main.js\n+++ b/main.js\n@@ -1,2 +1,2 @@\n-const number = 5;\n+const number = 9;\n console.log(number);\n';
   const patched=task(home,'patch_apply','main.js',encode(diff),initial.hash);
   assert.equal(patched.ok,true,JSON.stringify(patched));
   assert.equal(patched.syntax.passed,true);
   assert.equal(patched.hunks,1);
   assert.equal(task(home,'read','main.js').content,'const number = 9;\nconsole.log(number);\n');
   const older=task(home,'history_list','main.js').entries;
   assert.equal(older.length,1);
   assert.equal(older[0].hash,initial.hash);
   const stale=task(home,'patch_apply','main.js',encode(diff),initial.hash);
   assert.equal(stale.ok,false);
   assert.match(stale.error,/changed/);
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('AEOS patch fails closed if syntax is wrong or hunk mismatches',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'aeos-reject-'));
 try {
   task(home,'ping');
   const old=task(home,'write','safe.js',encode('console.log("OK");\n'),'');
   const bad='--- a/safe.js\n+++ b/safe.js\n@@ -1 +1 @@\n-console.log("OK");\n+const = invalid;\n';
   const rejected=task(home,'patch_apply','safe.js',encode(bad),old.hash);
   assert.equal(rejected.ok,false);assert.match(rejected.error,/syntax/);
   assert.equal(task(home,'read','safe.js').hash,old.hash);
   assert.equal(task(home,'history_list','safe.js').entries.length,0);
   const unrelated=bad.replace('console.log("OK");','console.log("WRONG");');
   assert.equal(task(home,'patch_apply','safe.js',encode(unrelated),old.hash).ok,false);
   const escaping=bad.replaceAll('safe.js','../other.js');
   assert.equal(task(home,'patch_apply','safe.js',encode(escaping),old.hash).ok,false);
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('AEOS CDP browser observes only explicit loopback and reports unavailable port',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'aeos-cdp-'));
 try{
   task(home,'ping');
   assert.equal(task(home,'browser_observe','80').ok,false);
   assert.equal(task(home,'browser_observe','notaport').ok,false);
   const missing=task(home,'browser_observe','59233');
   assert.equal(missing.ok,false);
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('bundled Android Termux engine equals audited AEOS engine',async()=>{
 const source=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url));
 const bundled=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url));
 assert.deepEqual(bundled,source);
});
