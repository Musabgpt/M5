#!/usr/bin/env python3
"""Termux PTY daemon over private authenticated Unix socket. No localhost HTTP UI."""
import errno,json,os,pathlib,pty,secrets,select,signal,socket,socketserver,subprocess,sys,threading,time,uuid
HOME=pathlib.Path.home()
ROOT=HOME/'m5-workspace'
DIR=HOME/'m5-native'/'runtime'
SOCK=DIR/'pty.sock'
KEY=DIR/'pty.token'
PID=DIR/'pty.pid'
SESSIONS={}
LOCK=threading.RLock()
def setup():
    os.umask(0o077)
    DIR.mkdir(mode=0o700,parents=True,exist_ok=True)
    ROOT.mkdir(mode=0o700,parents=True,exist_ok=True)
    if not KEY.exists():
        try:
            fd=os.open(KEY,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
            with os.fdopen(fd,'w') as out:out.write(secrets.token_hex(32))
        except FileExistsError:pass
    if KEY.is_symlink() or KEY.stat().st_mode&0o077:
        raise ValueError('Unsafe private session token permissions')
def cwd_for(value):
    if not isinstance(value,str) or len(value)>250 or '\0' in value:
        raise ValueError('Invalid workspace path')
    p=pathlib.PurePosixPath(value)
    if p.is_absolute() or '..' in p.parts:raise ValueError('Outside workspace')
    base=ROOT.resolve()
    folder=(base/value).resolve()
    if not folder.is_relative_to(base) or not folder.is_dir():
        raise ValueError('Workdir must be an existing project folder')
    return folder
class Session:
    def __init__(self,command,folder):
        self.id=uuid.uuid4().hex
        self.master,slave=pty.openpty()
        self.lock=threading.RLock()
        self.data=b''
        self.base=0
        self.done=False
        self.touched=time.monotonic()
        env={k:v for k,v in os.environ.items() if k in
            ('PATH','HOME','TMPDIR','LANG','LC_ALL','PREFIX','SHELL','TERM','ANDROID_ROOT','ANDROID_DATA')}
        env['TERM']='xterm-256color'
        shell=pathlib.Path(env.get('PREFIX','')+'/bin/bash') if env.get('PREFIX') else pathlib.Path('/bin/bash')
        if not shell.is_file():shell=pathlib.Path('/bin/sh')
        try:
            self.proc=subprocess.Popen([str(shell),'-c',command],cwd=folder,
                env=env,stdin=slave,stdout=slave,stderr=slave,
                start_new_session=True,close_fds=True)
        finally:os.close(slave)
        threading.Thread(target=self.pump,daemon=True).start()
    def pump(self):
        try:
            while True:
                ready,_,_=select.select([self.master],[],[],0.5)
                if not ready:
                    if self.proc.poll() is not None:
                        ready,_,_=select.select([self.master],[],[],0.08)
                        if not ready:break
                    else:continue
                try:part=os.read(self.master,4096)
                except OSError as ex:
                    if ex.errno in (errno.EBADF,errno.EIO):break
                    raise
                if not part:break
                with self.lock:
                    self.data+=part
                    if len(self.data)>65536:
                        over=len(self.data)-65536
                        self.data=self.data[over:]
                        self.base+=over
        finally:
            with self.lock:self.done=True
            try:os.close(self.master)
            except OSError:pass
    def output(self,cursor,wait):
        if not isinstance(cursor,int) or cursor<0:raise ValueError('Invalid output offset')
        if not isinstance(wait,int) or wait<0 or wait>10000:raise ValueError('Invalid yield_time_ms')
        deadline=time.monotonic()+wait/1000
        while True:
            with self.lock:
                self.touched=time.monotonic()
                position=max(self.base,cursor)
                chunk=self.data[position-self.base:position-self.base+14000]
                end=self.base+len(self.data)
                code=self.proc.poll()
                result={'session_id':self.id,'cursor':position+len(chunk),
                    'output':chunk.decode('utf-8','replace'),
                    'combined_output':True,
                    'truncated':cursor<self.base or position+len(chunk)<end,
                    'running':code is None,'completed':code is not None and self.done,
                    'exit_code':code,'pid':self.proc.pid}
            if chunk or code is not None or time.monotonic()>=deadline:return result
            time.sleep(0.05)
    def write(self,value):
        if not isinstance(value,str) or len(value.encode())>8192:raise ValueError('Invalid stdin (8KB max)')
        if self.proc.poll() is not None:raise ValueError('Process has exited')
        written=os.write(self.master,value.encode('utf-8'))
        self.touched=time.monotonic()
        return {'session_id':self.id,'written_bytes':written}
    def terminate(self):
        if self.proc.poll() is None:os.killpg(self.proc.pid,signal.SIGTERM)
        return {'session_id':self.id,'termination_requested':True,'exit_code':self.proc.poll()}
def run(data):
    action=data.get('action')
    if action=='ping':return {'ready':True,'transport':'unix-pty','session_count':len(SESSIONS)}
    if action=='exec_command':
        cmd=data.get('command','')
        if not isinstance(cmd,str) or not cmd.strip() or len(cmd.encode())>4096:
            raise ValueError('Command must be 1–4096 bytes')
        folder=cwd_for(data.get('workdir',''))
        with LOCK:
            for sid,item in list(SESSIONS.items()):
                if time.monotonic()-item.touched>3600:
                    if item.proc.poll() is None:item.terminate()
                    del SESSIONS[sid]
            if sum(1 for s in SESSIONS.values() if s.proc.poll() is None)>=4:
                raise ValueError('Maximum four active PTY jobs')
            session=Session(cmd,folder)
            SESSIONS[session.id]=session
        return session.output(0,int(data.get('yield_time_ms',1000)))
    sid=data.get('session_id')
    if not isinstance(sid,str) or len(sid)!=32:raise ValueError('Invalid session ID')
    with LOCK:session=SESSIONS.get(sid)
    if session is None:raise ValueError('Unknown or expired session; never replay command automatically')
    if action=='read_output':return session.output(int(data.get('cursor',0)),int(data.get('yield_time_ms',0)))
    if action=='write_stdin':return session.write(data.get('text',''))
    if action=='terminate_process':return session.terminate()
    raise ValueError('Unknown PTY action')
class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        try:
            raw=self.rfile.readline(23000)
            if len(raw)>=23000:raise ValueError('Request size exceeded')
            req=json.loads(raw)
            if not secrets.compare_digest(req.get('token',''),KEY.read_text()):
                raise ValueError('PTY authentication rejected')
            answer={'ok':True,**run(req)}
        except Exception as error:answer={'ok':False,'error':str(error)[:240]}
        try:self.wfile.write((json.dumps(answer,ensure_ascii=False)+'\n').encode())
        except OSError:pass
class Server(socketserver.ThreadingMixIn,socketserver.UnixStreamServer):
    daemon_threads=True
def daemon():
    setup()
    if SOCK.exists():
        try:
            with socket.socket(socket.AF_UNIX) as sock:
                sock.settimeout(0.3);sock.connect(str(SOCK))
            return
        except OSError:SOCK.unlink(missing_ok=True)
    server=Server(str(SOCK),Handler)
    os.chmod(SOCK,0o600)
    PID.write_text(str(os.getpid()))
    try:server.serve_forever(poll_interval=0.5)
    finally:server.server_close();SOCK.unlink(missing_ok=True)
def cli(action,args):
    setup()
    limits={'ping':(0,0),'exec_command':(1,3),'read_output':(1,3),
        'write_stdin':(2,2),'terminate_process':(1,1)}
    if action not in limits or not limits[action][0]<=len(args)<=limits[action][1]:
        raise ValueError('Invalid PTY tool arguments')
    data={'action':action,'token':KEY.read_text()}
    if action=='exec_command':
        data.update(command=args[0],workdir=args[1] if len(args)>1 else '',
            yield_time_ms=int(args[2]) if len(args)>2 else 1000)
    elif action=='read_output':
        data.update(session_id=args[0],cursor=int(args[1]) if len(args)>1 else 0,
            yield_time_ms=int(args[2]) if len(args)>2 else 0)
    elif action=='write_stdin':data.update(session_id=args[0],text=args[1])
    elif action=='terminate_process':data.update(session_id=args[0])
    for attempt in range(2):
        try:
            with socket.socket(socket.AF_UNIX) as sock:
                sock.settimeout(15)
                sock.connect(str(SOCK))
                sock.sendall((json.dumps(data,ensure_ascii=False)+'\n').encode())
                sock.shutdown(socket.SHUT_WR)
                response=bytearray()
                while not response.endswith(b'\n'):
                    b=sock.recv(4096)
                    if not b:break
                    response.extend(b)
                    if len(response)>23000:raise ValueError('PTY response too large')
                if not response:raise ValueError('PTY daemon returned no data')
                return json.loads(response)
        except (FileNotFoundError,ConnectionRefusedError,ConnectionResetError):
            if attempt:raise
            subprocess.Popen([sys.executable,str(pathlib.Path(__file__).resolve()),'daemon'],
                stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
                start_new_session=True,close_fds=True)
            for _ in range(35):
                time.sleep(0.08)
                if SOCK.exists():break
    raise ValueError('PTY daemon unavailable')
if __name__=='__main__':
    if len(sys.argv)>1 and sys.argv[1]=='daemon':daemon()
    else:
        try:
            if len(sys.argv)<2:raise ValueError('PTY tool name required')
            result=cli(sys.argv[1],sys.argv[2:])
            print(json.dumps(result,ensure_ascii=False))
            sys.exit(0 if result.get('ok') else 1)
        except Exception as error:
            print(json.dumps({'ok':False,'error':str(error)[:300]}))
            sys.exit(1)
