import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';

test('phone automation only with opt-in Android accessibility and explicit app actions',async()=>{
  const manifest=await fs.readFile(new URL('../app/src/main/AndroidManifest.xml',import.meta.url),'utf8');
  const xml=await fs.readFile(new URL('../app/src/main/res/xml/m5_accessibility_service.xml',import.meta.url),'utf8');
  const service=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/M5AccessibilityService.java',import.meta.url),'utf8');
  const agent=await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/NativeAgent.java',import.meta.url),'utf8');
  assert.match(manifest,/BIND_ACCESSIBILITY_SERVICE/);
  assert.match(manifest,/m5_accessibility_service/);
  assert.match(xml,/canRetrieveWindowContent="true"/);
  assert.match(service,/getRootInActiveWindow/);
  assert.match(service,/ACTION_SET_TEXT/);
  assert.match(service,/ACTION_CLICK/);
  assert.match(service,/TYPE_ACCESSIBILITY_OVERLAY/);
  assert.match(service,/onAccessibilityEvent\(AccessibilityEvent event\)[\s\S]*?Intentionally no event logging/);
  assert.match(agent,/consentedScreenRead/);
  assert.match(agent,/phone\.open\(app\)/);
});
