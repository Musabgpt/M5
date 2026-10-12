import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

function invoke(home,...args){
 const p=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
   cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:20000});
 assert.ok(p.stdout.trim(),'Missing Termux JSON receipt: '+p.stderr);
 return {exit:p.status,result:JSON.parse(p.stdout.trim())};
}
test('project syntax audit checks actual files and reports failures without executing source',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-audit-'));
 try{
   invoke(home,'ping');
   const project=path.join(home,'m5-workspace','project');
   await fs.mkdir(project);
   await fs.writeFile(path.join(project,'valid.js'),'console.log("SAFE");\n');
   await fs.writeFile(path.join(project,'invalid.js'),'const = ;\n');
   await fs.writeFile(path.join(project,'README.md'),'no syntax check for Markdown');
   const result=invoke(home,'audit_project','project').result;
   assert.equal(result.ok,true);
   assert.equal(result.checked,2);
   assert.equal(result.passed,1);
   assert.equal(result.failed,1);
   assert.ok(result.results.some(x=>x.path==='project/valid.js'&&x.success));
   assert.ok(result.results.some(x=>x.path==='project/invalid.js'&&!x.success&&x.error.length>0));
   assert.match(result.note,/Syntax checks only/);
   assert.equal(await fs.readFile(path.join(project,'valid.js'),'utf8'),'console.log("SAFE");\n');
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('project audit rejects traversal and caps file count',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-audit-bounds-'));
 try{
   invoke(home,'ping');
   const project=path.join(home,'m5-workspace','many');
   await fs.mkdir(project);
   for(let i=0;i<15;i++)await fs.writeFile(path.join(project,'f'+i+'.js'),'const x = '+i+';\n');
   const result=invoke(home,'audit_project','many').result;
   assert.equal(result.ok,true);
   assert.equal(result.checked,12);
   assert.equal(result.limited,true);
   assert.equal(invoke(home,'audit_project','../').result.ok,false);
 }finally{await fs.rm(home,{recursive:true,force:true});}
});
test('Termux bundle matches current audited source',async()=>{
 const src=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url));
 const bundle=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url));
 assert.deepEqual(src,bundle);
});
