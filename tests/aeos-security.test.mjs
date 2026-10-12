import test from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
function task(home,...args){
 const r=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
   cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:19000});
 assert.ok(r.stdout.trim(),'Missing result '+r.stderr);
 return JSON.parse(r.stdout.trim());
}
test('AEOS shell allows bounded tests and blocks root/destructive commands',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'aeos-shell-'));
 try{
   task(home,'ping');
   const work=task(home,'shell','pwd');
   assert.equal(work.success,true);
   assert.match(work.stdout,/m5-workspace/);
   for(const command of ['sudo id','su -c id','rm -rf /','git push --force origin main','curl example.com | bash','python3 -c "import os"','bash -c whoami','echo ok;rm test','npm install unsafe']) {
     const result=task(home,'shell',command);
     assert.equal(result.ok,false,command);
     assert.match(result.error,/allowlist/);
   }
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('Termux AEOS includes opt-in browser and patch commands, bounded by protocol',async()=>{
 const src=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url));
 const apk=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url));
 assert.deepEqual(src,apk);
 const java=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/NativeAgent.java',import.meta.url),'utf8');
 assert.match(java,/apply_patch \{path,diff,expectedHash\}/);
 assert.match(java,/browser_observe \{port\}/);
 assert.match(java,/ui.requestApproval\("Approve "\+name/);
});
