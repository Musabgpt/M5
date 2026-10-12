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
  // Empty expectedHash means CREATE-ONLY. SHA-256 means UPDATE of a file
  // explicitly read before editing. Never clobber pre-existing empty files.
  if(existed && (!expected || sha(original)!==expected))
    fail('Existing file changed or not opened for editing; reload it before saving');
  if(!existed && expected!=='')fail('File did not exist when read; create using an empty expected hash');
  const temp=file+'.m5-'+crypto.randomBytes(6).toString('hex');
  try {
    await fs.writeFile(temp,bytes,{flag:'wx',mode:0o600});
    // Check target again before replacement; path is confined to the private Termux tree.
    await target(relative,true);
    await fs.rename(temp,file);
  }finally{await fs.rm(temp,{force:true});}
  return {path:relative,hash:sha(bytes),bytes:bytes.length};
}

/** Offline user-approved project creation with no API calls or dependencies. */
const PROJECT_PRESETS={"web-calculator":{"index.html":"<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n<title>M5 Calculator</title><link rel=\"stylesheet\" href=\"style.css\"></head><body>\n<main class=\"card\"><h1>Calculator</h1><p>Offline. No paid APIs or frameworks.</p>\n<label>First number <input id=\"a\" type=\"number\" step=\"any\" placeholder=\"0\"></label>\n<label>Operation <select id=\"operation\"><option value=\"+\">+</option><option value=\"-\">−</option><option value=\"*\">×</option><option value=\"/\">÷</option></select></label>\n<label>Second number <input id=\"b\" type=\"number\" step=\"any\" placeholder=\"0\"></label>\n<button id=\"calculate\" type=\"button\">Calculate</button><output id=\"answer\" aria-live=\"polite\">Enter numbers to begin</output>\n</main><script src=\"app.js\" defer></script></body></html>\n","style.css":"*{box-sizing:border-box}body{font:16px system-ui,sans-serif;background:#111827;color:#f9fafb;display:grid;place-items:center;min-height:100vh;margin:0;padding:16px}.card{background:#1f2937;border:1px solid #374151;border-radius:20px;max-width:400px;width:100%;padding:28px;box-shadow:0 20px 50px #0004}h1{margin:0 0 4px}p{color:#9ca3af}label{display:block;margin:16px 0;font-weight:600}input,select{display:block;width:100%;padding:14px;margin-top:6px;background:#111827;color:white;border:1px solid #6b7280;border-radius:10px;font-size:18px}button{background:#60a5fa;border:0;border-radius:10px;font-size:17px;font-weight:700;padding:14px;width:100%;margin-top:8px;cursor:pointer}button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #93c5fd}output{display:block;padding:18px;margin-top:12px;font-size:20px;font-weight:700;min-height:60px}\n","app.js":"\"use strict\";\nconst $=id=>document.getElementById(id);\nfunction calculate(){\n const a=$(\"a\").value.trim(),b=$(\"b\").value.trim(),op=$(\"operation\").value;\n if(a===\"\"||b===\"\"){$(\"answer\").textContent=\"Enter both numbers\";return;}\n const x=Number(a),y=Number(b);\n if(!Number.isFinite(x)||!Number.isFinite(y)){$(\"answer\").textContent=\"Invalid numbers\";return;}\n if(op===\"/\"&&y===0){$(\"answer\").textContent=\"Cannot divide by zero\";return;}\n const result=op===\"+\"?x+y:op===\"-\"?x-y:op===\"*\"?x*y:x/y;\n $(\"answer\").textContent=Number.isFinite(result)?String(Number(result.toPrecision(12))):\"Result is out of range\";\n}\n$(\"calculate\").addEventListener(\"click\",calculate);\n","README.md":"# Calculator\n\nOpen index.html. Works entirely offline.\n"},"python":{"main.py":"#!/usr/bin/env python3\n\ndef greet(name: str) -> str:\n    return f'Hello, {name}!'\n\nif __name__ == '__main__':\n    print(greet('M5'))\n","README.md":"# Python Starter\n\nRun python main.py in Termux after installing Python.\n"},"node":{"main.mjs":"const name=process.argv[2]||'M5';\nconsole.log('Hello, '+name+'!');\n","package.json":"{\"name\":\"m5-starter\",\"version\":\"1.0.0\",\"type\":\"module\",\"private\":true,\"scripts\":{\"start\":\"node main.mjs\",\"test\":\"node --check main.mjs\"}}\n","README.md":"# Node Starter\n\nRun npm start or npm test.\n"}};
async function scaffoldProject(name,preset){
  if(typeof name!=='string'||!/^[A-Za-z][A-Za-z0-9_-]{1,35}$/.test(name))
    fail('Use a project name of 2–36 letters, numbers, hyphens or underscores');
  const files=PROJECT_PRESETS[preset];
  if(!files)fail('Unsupported project template');
  const folder=await target(name,true);
  try{await fs.mkdir(folder,{mode:0o700});}
  catch(e){if(e.code==='EEXIST')fail('Project already exists; choose another name');throw e;}
  try {
    for(const [file,body] of Object.entries(files))
      await fs.writeFile(path.join(folder,file),body,{flag:'wx',mode:0o600});
    let verified='created';
    if(preset==='web-calculator'||preset==='node'){
      const check=path.join(folder,preset==='node'?'main.mjs':'app.js');
      const code=await new Promise((resolve,reject)=>{
        const p=spawn(process.execPath,['--check',check],{stdio:'ignore'});
        p.once('error',reject);
        p.once('close',resolve);
      });
      if(code!==0)fail('Generated source did not pass JavaScript syntax check');
      verified='node --check passed';
    }
    return {project:name,preset,files:Object.keys(files).map(file=>name+'/'+file),verification:verified};
  }catch(e){await fs.rm(folder,{recursive:true,force:true});throw e;}
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

/**
 * Structured syntax checking and explicit source execution, confined to the
 * selected workspace file. No shell interpolation. Running code can still
 * access the Termux user's files/network; the Android UI requires consent.
 */
async function sourceCommand(mode,relative,expectedHash){
  const filename=await target(relative);
  const stat=await fs.stat(filename);
  if(!stat.isFile()||stat.size>MAX_FILE)fail('Select a regular text source file smaller than 40 KB');
  if(mode==='run' && !/^[0-9a-f]{64}$/.test(expectedHash||''))fail('Run requires a read-backed SHA-256 content hash');
  if(expectedHash){
    if(!/^[0-9a-f]{64}$/.test(expectedHash))fail('Invalid source content hash');
    if(sha(await fs.readFile(filename))!==expectedHash)fail('Source file changed since last read; reload before executing');
  }
  const ext=path.extname(filename).toLowerCase();
  let commandName,args;
  if(ext==='.js'||ext==='.mjs'||ext==='.cjs'){
    commandName=process.execPath;
    args=mode==='check'?['--check',filename]:[filename];
  }else if(ext==='.py'){
    commandName='python';
    args=mode==='check'
      ?['-B','-c','import ast,sys;ast.parse(open(sys.argv[1],encoding="utf-8").read(),filename=sys.argv[1])',filename]
      :['-B',filename];
  }else if(ext==='.sh'){
    commandName='bash';
    args=mode==='check'?['-n',filename]:[filename];
  }else fail('Supported files for Check/Run: .js, .mjs, .cjs, .py, .sh');
  const cwd=path.dirname(filename);
  const env=Object.fromEntries(
    ['PATH','HOME','TMPDIR','LANG','TERM'].filter(k=>process.env[k]).map(k=>[k,process.env[k]])
  );
  const timeout=mode==='check'?15000:30000;
  return await new Promise((resolve,reject)=>{
    let stdout='',stderr='',truncated=false,timedOut=false,finished=false;
    const child=spawn(commandName,args,{cwd,env,stdio:['ignore','pipe','pipe']});
    const clip=(part,key,limit)=>{
      const text=part.toString('utf8');
      if(key.length+text.length>limit)truncated=true;
      return (key+text).slice(0,limit);
    };
    child.stdout.on('data',b=>{stdout=clip(b,stdout,14000);});
    child.stderr.on('data',b=>{stderr=clip(b,stderr,10000);});
    const clock=setTimeout(()=>{timedOut=true;child.kill('SIGKILL');},timeout);
    child.once('error',error=>{if(!finished){finished=true;clearTimeout(clock);reject(error);}});
    child.once('close',(code,signal)=>{
      if(finished)return;
      finished=true;clearTimeout(clock);
      resolve({file:relative,mode,exitCode:code,signal,success:code===0&&!timedOut,
        stdout,stderr,truncated,timedOut,verified:mode==='check'&&code===0&&!timedOut});
    });
  });
}

/**
 * Bounded, source-only project audit. Does not execute application logic.
 * Project source is parsed by language runtimes; results show real exit codes.
 */
async function auditProject(project){
  const listResult=await list(project);
  const candidates=listResult.files
    .filter(f=>f.type==='file' && /\.(?:js|mjs|cjs|py|sh)$/.test(f.path));
  const sample=candidates.slice(0,12);
  const results=[];
  for(const file of sample){
    try{
      const found=await sourceCommand('check',file.path);
      results.push({path:file.path,success:found.success,exitCode:found.exitCode,
        error:found.stderr.slice(0,550),timedOut:found.timedOut});
    }catch(e){
      results.push({path:file.path,success:false,error:String(e.message||e).slice(0,450)});
    }
  }
  const passed=results.filter(x=>x.success).length;
  return {project,checked:results.length,passed,failed:results.length-passed,
    limited:listResult.limited||candidates.length>sample.length,
    results,note:'Syntax checks only; not a unit-test suite or proof of runtime correctness'};
}

async function main(){
  await fs.mkdir(ROOT,{recursive:true,mode:0o700});
  const [verb,...args]=process.argv.slice(2);
  let data;
  switch(verb){
    case 'ping':data={ready:true,runtime:'native-termux',root:ROOT};break;
    case 'scaffold':data=await scaffoldProject(args[0],args[1]);break;
    case 'check_file':data=await sourceCommand('check',args[0],args[1]);break;
    case 'audit_project':data=await auditProject(args[0]||'');break;
    case 'run_file':data=await sourceCommand('run',args[0],args[1]);break;
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
