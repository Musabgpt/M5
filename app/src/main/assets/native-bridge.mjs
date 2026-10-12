#!/data/data/com.termux/files/usr/bin/node
/**
 * One-shot Termux project tools for the native Android M5 app.
 * No sockets, no HTTP server, no WebView, no background process.
 * stdout: one bounded JSON result consumed by official RUN_COMMAND PendingIntent.
 */
import fs from 'node:fs/promises';
import fss from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';

const ROOT = path.join(os.homedir(), 'm5-workspace');
const MAX_FILE = 40_000; // Within Termux's 100KB combined result-intent limit.
const OMIT = new Set(['node_modules','.git','.ssh','.aws','.config','.gnupg','dist','build','.m5']);
const SENSITIVE = /(^\.env(?:\.|$)|secret|credential|password|private.key|\.pem$|\.keystore$|\.p12$)/i;
const sha = data => crypto.createHash('sha256').update(data).digest('hex');
const fail = message => { throw new Error(message); };
const response = o => {
  let json=JSON.stringify(o);
  if (Buffer.byteLength(json)>70_000) json=JSON.stringify({ok:false,error:'Termux result exceeds transport limit'});
  process.stdout.write(json+'\n');
};
function valid(relative) {
  if(typeof relative!=='string'||relative.length>500||relative.includes('\0')||relative.startsWith('/')||relative.includes('\\')) fail('Invalid workspace-relative path');
  const segments=relative.split('/').filter(Boolean);
  if(segments.some(s=>s==='..'||s==='.'||OMIT.has(s)||SENSITIVE.test(s))) fail('Restricted path');
  return segments;
}
async function target(relative='',create=false) {
  const parts=valid(relative);
  let real=await fs.realpath(ROOT);
  for (let i=0;i<parts.length;i++){
    const next=path.join(real,parts[i]);
    try {
      const stat=await fs.lstat(next);
      if(stat.isSymbolicLink()) fail('Symlink refused');
      if(i!==parts.length-1&&!stat.isDirectory())fail('Not a directory');
      real=next;
    } catch(e) {
      if(e.code!=='ENOENT')throw e;
      if(!create||i!==parts.length-1)fail('Parent directory missing');
      real=next;
    }
  }
  return real;
}
async function read(relative) {
  const file=await target(relative);
  const stat=await fs.stat(file);
  if(!stat.isFile()||stat.size>MAX_FILE)fail('File is too large or not regular (max 40KB)');
  const data=await fs.readFile(file);
  if(data.includes(0)) fail('Binary file not supported');
  return {path:relative,content:data.toString('utf8'),hash:sha(data)};
}
async function list(relative='') {
  const start=await target(relative);
  const result=[];
  async function walk(directory,prefix,depth){
    if(result.length>=180||depth>5)return;
    for(const entry of await fs.readdir(directory,{withFileTypes:true})){
      if(OMIT.has(entry.name)||SENSITIVE.test(entry.name)||entry.isSymbolicLink())continue;
      const relative=path.posix.join(prefix,entry.name);
      result.push({path:relative,type:entry.isDirectory()?'directory':'file'});
      if(entry.isDirectory())await walk(path.join(directory,entry.name),relative,depth+1);
      if(result.length>=180)return;
    }
  }
  await walk(start,relative,0);
  return {files:result,limited:result.length>=180};
}
async function write(relative,b64,expected) {
  if(typeof b64!=='string'||b64.length>60_000||typeof expected!=='string')fail('Write exceeds the Android bridge limit');
  const bytes=Buffer.from(b64,'base64');
  if(bytes.length>MAX_FILE||bytes.includes(0))fail('Only UTF-8 text up to 40KB');
  const content=bytes.toString('utf8');
  if(!Buffer.from(content,'utf8').equals(bytes))fail('Invalid UTF-8 content');
  const file=await target(relative,true);
  let original='';
  let existed=false;
  try {
    const stat=await fs.stat(file);
    if(!stat.isFile()||stat.size>MAX_FILE)fail('Existing file too large');
    existed=true;
    original=await fs.readFile(file);
  }catch(e){if(e.code!=='ENOENT')throw e;}
  if(sha(original)!==expected && !(existed===false && expected===''))fail('File changed since it was read; reload before saving');
  const temp=file+'.m5-'+crypto.randomBytes(6).toString('hex');
  try {
    await fs.writeFile(temp,bytes,{flag:'wx',mode:0o600});
    // Check target again before replacement; path is confined to the private Termux tree.
    await target(relative,true);
    await fs.rename(temp,file);
  }finally{await fs.rm(temp,{force:true});}
  return {path:relative,hash:sha(bytes),bytes:bytes.length};
}
async function command(value,timeout=30000) {
  if(typeof value!=='string'||value.length>1600||!value.trim())fail('Invalid shell command');
  return await new Promise((resolve,reject)=>{
    const env=Object.fromEntries(['PATH','HOME','TMPDIR','LANG','TERM'].filter(k=>process.env[k]).map(k=>[k,process.env[k]]));
    const child=spawn('bash',['-lc',value],{cwd:ROOT,env,stdio:['ignore','pipe','pipe']});
    let stdout='',stderr='',truncated=false,timedOut=false;
    const add=(key,b)=>{
      const text=b.toString('utf8');
      if(key==='stdout'){if(stdout.length+text.length>12000)truncated=true;stdout=(stdout+text).slice(0,12000);}
      else {if(stderr.length+text.length>6000)truncated=true;stderr=(stderr+text).slice(0,6000);}
    };
    child.stdout.on('data',b=>add('stdout',b));
    child.stderr.on('data',b=>add('stderr',b));
    const timer=setTimeout(()=>{timedOut=true;child.kill('SIGKILL');},Math.min(60000,Math.max(1000,timeout)));
    child.once('error',e=>{clearTimeout(timer);reject(e);});
    child.once('close',code=>{clearTimeout(timer);resolve({code,stdout,stderr,truncated,timedOut,success:code===0&&!timedOut});});
  });
}
async function main(){
  await fs.mkdir(ROOT,{recursive:true,mode:0o700});
  const [verb,...args]=process.argv.slice(2);
  let data;
  switch(verb){
    case 'ping':data={ready:true,runtime:'native-termux',root:ROOT};break;
    case 'list':data=await list(args[0]||'');break;
    case 'read':data=await read(args[0]);break;
    case 'write':data=await write(args[0],args[1],args[2]);break;
    case 'search':{
      const q=String(args[0]||'').toLowerCase();
      if(!q||q.length>100)fail('Invalid search');
      const files=(await list()).files.filter(x=>x.type==='file');
      const matches=[];
      for(const file of files){
        let text;
        try{text=(await read(file.path)).content;}catch{continue;}
        text.split('\n').forEach((line,i)=>{if(matches.length<80&&line.toLowerCase().includes(q))matches.push({path:file.path,line:i+1,text:line.slice(0,150)});});
      }
      data={matches};break;
    }
    case 'git_status':data=await command('git status --short');break;
    case 'git_diff':data=await command('git diff --no-ext-diff --',12000);break;
    case 'shell':data=await command(args[0],Number(args[1]||30000));break;
    default:fail('Unsupported one-shot Termux tool');
  }
  response({ok:true,...data});
}
main().catch(e=>{response({ok:false,error:String(e.message||e).slice(0,500)});process.exitCode=1;});
