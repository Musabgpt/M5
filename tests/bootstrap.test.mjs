import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';

test('native Termux setup ships within APK; never clones GitHub or starts HTTP server',async()=>{
  const java=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
  const asset=await fs.readFile(new URL('../app/src/main/assets/native-bridge.mjs',import.meta.url),'utf8');
  const script=await fs.readFile(new URL('../scripts/native-bridge.mjs',import.meta.url),'utf8');
  assert.equal(asset,script,'APK-bundled and source one-shot bridge must remain identical');
  assert.match(java,/getAssets\(\)\.open\("native-bridge\.mjs"\)/);
  assert.match(java,/Base64\.encodeToString/);
  assert.match(java,/base64 -d/);
  assert.match(java,/native-bridge\.mjs\.m5new/);
  assert.match(java,/RUN_COMMAND_BACKGROUND", true/);
  assert.doesNotMatch(java,/git clone|git fetch|git merge|nohup|server\/index\.mjs|http:\/\/127\.0\.0\.1/);
});
test('native Termux result validates errors before delivering JSON',async()=>{
  const java=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxBridge.java',import.meta.url),'utf8');
  assert.match(java,/getBundleExtra\("result"\)/);
  assert.match(java,/getInt\("exitCode"/);
  assert.match(java,/getInt\("err"/);
  assert.match(java,/stdout_original_length/);
  assert.match(java,/new JSONObject\(stdout\.trim\(\)\)/);
  assert.match(java,/toolFailure\.optString\("error"/);
});
