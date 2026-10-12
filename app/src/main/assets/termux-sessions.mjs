#!/data/data/com.termux/files/usr/bin/node
/**
 * Durable bounded execution sessions for M5, invoked through official
 * Termux RUN_COMMAND. A detached worker owns the child process and captures
 * bounded logs. No TCP listener, WebView or terminal window.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
const HOME=path.join(os.homedir(),'m5-workspace');
const DIR=path.join(HOME,'.m5','sessions');
const ID=/^[a-f0-9]{24}$/;
const MAX_LOG=60000;
const fail=m=>{throw Error(m)};
async function init(){await fs.mkdir(DIR,{recursive:true,mode:0o700});}
async function state(id){if(!ID.test(id||''))fail('Invalid session ID');return path.join(DIR,id);}
async function load(id){return JSON.parse(await fs.readFile(path.join(await state(id),'state.json'),'utf8'));}
async function save(dir,s){const tmp=path.join(dir,'state.tmp');await fs.writeFile(tmp,JSON.stringify(s),{mode:0o600});await fs.rename(tmp,path.join(dir,'state.json'));}
async function append(dir,file,chunk){
  const dest=path.join(dir,file);const old=await fs.readFile(dest).catch(()=>Buffer.alloc(0));
  const next=Buffer.concat([old,Buffer.from(chunk)]).subarray(-MAX_LOG);
  await fs.writeFile(dest,next,{mode:0o600});
}
async function start(encoded,workdir,yieldMs){
  const cmd=Buffer.from(encoded||'','base64').toString('utf8');
  if(!cmd||cmd.length>1500||cmd.includes('\0'))fail('Invalid command');
  if(typeof workdir!=='string'||workdir.startsWith('/')||workdir.split('/').some(x=>x==='..'))fail('Invalid relative workdir');
  const base=await fs.realpath(HOME);const cwd=await fs.realpath(path.join(HOME,workdir||''));
  if(cwd!==base&&!cwd.startsWith(base+path.sep))fail('Workdir escapes workspace');
  const id=crypto.randomBytes(12).toString('hex'),dir=await state(id);
  await fs.mkdir(dir,{mode:0o700});
  await save(dir,{id,status:'starting',command:cmd,cwd,created:Date.now(),exit_code:null});
  const child=spawn(process.execPath,[process.argv[1],'worker',id],{cwd:HOME,detached:true,stdio:'ignore',env:process.env});
  child.unref();
  const deadline=Date.now()+Math.max(0,Math.min(1200,Number(yieldMs)||0));
  while(Date.now()<deadline){const s=await load(id);if(s.status!=='starting')break;await new Promise(r=>setTimeout(r,80));}
  return await read(id,0);
}
async function worker(id){
  const dir=await state(id);let s=await load(id);
  const env=Object.fromEntries(['HOME','PATH','TMPDIR','LANG','TERM'].filter(k=>process.env[k]).map(k=>[k,process.env[k]]));
  const p=spawn('bash',['-lc',s.command],{cwd:s.cwd,env,detached:true,stdio:['pipe','pipe','pipe']});
  s={...s,status:'running',pid:p.pid};await save(dir,s);
  let out=Promise.resolve(),err=Promise.resolve();
  p.stdout.on('data',b=>{out=out.then(()=>append(dir,'stdout',b)).catch(()=>{});});
  p.stderr.on('data',b=>{err=err.then(()=>append(dir,'stderr',b)).catch(()=>{});});
  let closed=false;
  const timer=setTimeout(()=>{if(!closed){try{process.kill(-p.pid,'SIGTERM')}catch{}}},300000);
  p.once('error',async e=>{clearTimeout(timer);s.status='failed';s.error=e.message;await save(dir,s);});
  p.once('close',async(code,signal)=>{closed=true;clearTimeout(timer);await Promise.all([out,err]);s.status='finished';s.exit_code=code;s.signal=signal;await save(dir,s);});
  process.on('SIGTERM',()=>{try{process.kill(-p.pid,'SIGTERM')}catch{}});
}
async function read(id,offset){
  const dir=await state(id),s=await load(id);
  const logs={};
  for(const key of ['stdout','stderr']){const b=await fs.readFile(path.join(dir,key)).catch(()=>Buffer.alloc(0));logs[key]=b.toString('utf8').slice(-12000);}
  return {...s,...logs,session_id:id};
}
async function input(id,encoded){
  const s=await load(id);
  if(s.status!=='running')fail('Session not running');
  fail('Interactive stdin requires a separately authorized PTY worker; no stdin pipe exposed in this version');
}
async function terminate(id){
  const s=await load(id);
  if(s.status!=='running'||!s.pid)return {...s,terminated:false};
  try{process.kill(-s.pid,'SIGTERM')}catch(e){if(e.code!=='ESRCH')throw e}
  return {session_id:id,terminated:true};
}
async function main(){
  await init();const [op,...args]=process.argv.slice(2);
  if(op==='worker'){await worker(args[0]);return;}
  let result;
  if(op==='start')result=await start(args[0],args[1],args[2]);
  else if(op==='read')result=await read(args[0],args[1]);
  else if(op==='input')result=await input(args[0],args[1]);
  else if(op==='terminate')result=await terminate(args[0]);
  else fail('Unknown session operation');
  console.log(JSON.stringify({ok:true,...result}));
}
main().catch(e=>{console.log(JSON.stringify({ok:false,error:String(e.message).slice(0,400)}));process.exitCode=1});
