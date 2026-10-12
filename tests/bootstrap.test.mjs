import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
test('native Termux bootstrap uses a separate checkout, never starts Node server',async()=>{
  const bridge=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
  assert.match(bridge,/m5-native\/scripts\/native-bridge\.mjs/);
  assert.match(bridge,/git merge --ff-only FETCH_HEAD/);
  assert.match(bridge,/node scripts\/native-bridge\.mjs ping/);
  assert.match(bridge,/RUN_COMMAND_BACKGROUND", true/);
  assert.doesNotMatch(bridge,/nohup|server\/index\.mjs|http:\/\/127\.0\.0\.1/);
});
test('native Termux result checks exit code and truncation before returning JSON',async()=>{
  const bridge=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
  assert.match(bridge,/getBundleExtra\("result"\)/);
  assert.match(bridge,/getInt\("exitCode"/);
  assert.match(bridge,/getInt\("err"/);
  assert.match(bridge,/stdout_original_length/);
  assert.match(bridge,/new JSONObject\(stdout\.trim\(\)\)/);
});
