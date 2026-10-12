import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';

test('native app uses encrypted Android Keystore drafts, not plaintext preferences',async()=>{
 const vault=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/DraftVault.java',import.meta.url),'utf8');
 assert.match(vault,/AndroidKeyStore/);
 assert.match(vault,/AES\/GCM\/NoPadding/);
 assert.match(vault,/GCMParameterSpec/);
 assert.match(vault,/\.commit\(\)/);
 assert.match(vault,/MAX_DOCUMENT_BYTES=40_000/);
 assert.match(vault,/MAX_DRAFTS=12/);
 assert.doesNotMatch(vault,/putString\([^,]+,\s*contents\)/);
});
test('editor binds autosave to the actual open file, blocks stale async loads and recovers conflicts',async()=>{
 const app=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/M5Activity.java',import.meta.url),'utf8');
 assert.match(app,/private String editorFile=/);
 assert.match(app,/String path=editorFile/);
 assert.match(app,/new Handler\(Looper\.getMainLooper\(\)\)/);
 assert.match(app,/autosaveHandler\.postDelayed\(autosaveTask,700\)/);
 assert.match(app,/onPause\(\)[\s\S]{0,100}persistVisibleDraft\(\)/);
 assert.match(app,/request!=fileOpenRequest/);
 assert.match(app,/DraftRecoveryPolicy\.evaluate/);
 assert.match(app,/DraftRecoveryPolicy\.Decision\.CONFLICT/);
 assert.match(app,/new String\[\]\{path,encoded,expectedHash\}/);
});
test('Android native activity never requires WebView or localhost server',async()=>{
 const app=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/M5Activity.java',import.meta.url),'utf8');
 assert.doesNotMatch(app,/new WebView|http:\/\/127\.0\.0\.1|startServer/);
});
