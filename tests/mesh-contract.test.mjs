import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';

test('all free route families retained, with bounded retry and transparent failure telemetry',async()=>{
  const model=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/FreeModels.java',import.meta.url),'utf8');
  assert.match(model,/Pollinations Free/);
  assert.match(model,/Groq Free/);
  assert.match(model,/OpenRouter Free/);
  assert.match(model,/Google AI Studio Free/);
  assert.match(model,/MeshFailover/);
  assert.match(model,/retryAfter\(conn.getHeaderField\("Retry-After"\)\)/);
  assert.match(model,/socket|temporary|Timeout/i);
  assert.match(model,/public synchronized String meshStatus/);
  const agent=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/NativeAgent.java',import.meta.url),'utf8');
  assert.match(agent,/fallbackOrReport\(\)/);
  assert.match(agent,/OfflineFallback\.match\(latestUserMessage\)/);
  assert.match(agent,/ui\.requestApproval\("Create offline /);
  assert.match(agent,/termux\.tool\("scaffold"/);
});
