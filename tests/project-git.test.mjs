import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

function call(home,...args){
 const p=spawnSync(process.execPath,['scripts/native-bridge.mjs',...args],{
   cwd:process.cwd(),env:{...process.env,HOME:home},encoding:'utf8',timeout:18000});
 assert.ok(p.stdout.trim(),'No one-shot JSON response '+p.stderr);
 return JSON.parse(p.stdout.trim());
}
function git(project,args){
 const out=spawnSync('git',args,{cwd:project,encoding:'utf8',env:{
   ...process.env,GIT_AUTHOR_NAME:'M5 Test',GIT_AUTHOR_EMAIL:'m5-test@example.invalid',
   GIT_COMMITTER_NAME:'M5 Test',GIT_COMMITTER_EMAIL:'m5-test@example.invalid'
 }});
 assert.equal(out.status,0,out.stderr);
 return out.stdout;
}
test('local Git init, status and diff are real and never use GitHub',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-project-git-'));
 try{
   call(home,'ping');
   const dir=path.join(home,'m5-workspace','DemoRepo');
   await fs.mkdir(dir);
   await fs.writeFile(path.join(dir,'README.md'),'first line\n');
   await fs.writeFile(path.join(dir,'.env'),'PRIVATE_DATA\n');
   const init=call(home,'git_project_init','DemoRepo');
   assert.equal(init.ok,true,JSON.stringify(init));
   assert.equal(init.created,true);
   const ignores=await fs.readFile(path.join(dir,'.gitignore'),'utf8');
   assert.match(ignores,/\.env/);
   assert.match(ignores,/node_modules/);
   const second=call(home,'git_project_init','DemoRepo');
   assert.equal(second.ok,false);
   git(dir,['add','README.md','.gitignore']);
   git(dir,['commit','-m','initial baseline']);
   await fs.writeFile(path.join(dir,'README.md'),'first line\nsecond line\n');
   const status=call(home,'git_project_status','DemoRepo');
   assert.equal(status.ok,true);
   assert.equal(status.success,true);
   assert.match(status.stdout,/M README\.md/);
   assert.doesNotMatch(status.stdout,/\.env/);
   const diff=call(home,'git_project_diff','DemoRepo');
   assert.equal(diff.ok,true);
   assert.equal(diff.success,true);
   assert.match(diff.stdout,/\+second line/);
   assert.match(diff.note,/Untracked files are not included/);
   const log=git(dir,['log','--oneline','-1']);
   assert.match(log,/initial baseline/);
 }finally{await fs.rm(home,{force:true,recursive:true});}
});
test('project Git refuses traversal, symlink folders and workspace root',async()=>{
 const home=await fs.mkdtemp(path.join(os.tmpdir(),'m5-git-guard-'));
 try{
   call(home,'ping');
   const ws=path.join(home,'m5-workspace');
   await fs.symlink('/tmp',path.join(ws,'linked'));
   for(const p of ['../outside','/etc','', '.ssh','linked']){
     assert.equal(call(home,'git_project_init',p).ok,false,p);
     assert.equal(call(home,'git_project_status',p).ok,false,p);
   }
 }finally{await fs.rm(home,{force:true,recursive:true});}
});
test('native Git tools are separate from generic arbitrary shell execution',async()=>{
 const java=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
 const src=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url),'utf8');
 assert.match(java,/git_project_status\|git_project_diff\|git_project_init/);
 assert.match(src,/spawn\('git',safeArgs/);
 assert.match(src,/GIT_CONFIG_GLOBAL='\/dev\/null'/);
 assert.doesNotMatch(src,/git\s+push|git\s+clone/);
});
