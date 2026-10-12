import json
import os
import pathlib
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT=pathlib.Path(__file__).resolve().parents[1]/"scripts"/"pty_bridge.py"

class TermuxSessions(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.home=pathlib.Path(self.tmp.name)
        self.env=dict(os.environ,HOME=str(self.home))
        (self.home/"m5-workspace"/"demo").mkdir(parents=True)
    def tearDown(self):
        pid=self.home/"m5-native"/"runtime"/"pty.pid"
        if pid.exists():
            try:os.kill(int(pid.read_text()),15)
            except ProcessLookupError:pass
        self.tmp.cleanup()
    def tool(self,*arguments):
        r=subprocess.run([sys.executable,str(SCRIPT),*map(str,arguments)],
            capture_output=True,text=True,env=self.env,timeout=18)
        self.assertTrue(r.stdout,r.stderr)
        return json.loads(r.stdout)
    def test_real_exit_status_and_workdir(self):
        r=self.tool("exec_command",'python -c "print(6 * 7)"',"demo","1500")
        self.assertTrue(r["ok"],r)
        output=r["output"]
        if r["exit_code"] is None:
            next=self.tool("read_output",r["session_id"],r["cursor"],"2500")
            output+=next["output"]
            r=next
        self.assertEqual(r["exit_code"],0)
        self.assertIn("42",output)
        self.assertTrue(r["combined_output"])
    def test_interactive_stdin_session_persists_across_cli_calls(self):
        cmd="python -u -c 'import sys;print(\"ready\",flush=True);print(\"received:\"+sys.stdin.readline().strip(),flush=True)'"
        start=self.tool("exec_command",cmd,"","1200")
        self.assertTrue(start["ok"],start)
        sid=start["session_id"]
        self.assertIn("ready",start["output"])
        ack=self.tool("write_stdin",sid,"hello\n")
        self.assertTrue(ack["ok"],ack)
        r=self.tool("read_output",sid,start["cursor"],"3000")
        self.assertIn("received:hello",r["output"])
        self.assertEqual(r["exit_code"],0)
    def test_long_running_job_can_be_terminated(self):
        start=self.tool("exec_command","sleep 30","","100")
        self.assertTrue(start["ok"],start)
        result=self.tool("terminate_process",start["session_id"])
        self.assertTrue(result["termination_requested"])
        state=self.tool("read_output",start["session_id"],"0","2500")
        self.assertIsNotNone(state["exit_code"])
    def test_relative_workdir_required_and_no_session_replay(self):
        for directory in ["../","/tmp","demo/../../"]:
            self.assertFalse(self.tool("exec_command","pwd",directory,"0")["ok"])
        lost=self.tool("read_output","a"*32)
        self.assertFalse(lost["ok"])
        self.assertIn("Unknown",lost["error"])
    def test_bundle_is_byte_identical(self):
        source=SCRIPT.read_bytes()
        bundle=(SCRIPT.parent.parent/"app"/"src"/"main"/"assets"/"pty_bridge.py").read_bytes()
        self.assertEqual(source,bundle)

if __name__=="__main__":unittest.main()
