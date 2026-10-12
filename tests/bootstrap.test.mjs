import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';

test('Android starts only the auditable Termux bootstrap script and honors the official result contract', async () => {
  const activity = await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/M5Activity.java', import.meta.url), 'utf8');
  const receiver = await fs.readFile(new URL('../app/src/main/java/com/musabgpt/m5/TermuxResultReceiver.java', import.meta.url), 'utf8');
  assert.match(activity, /scripts\/termux-bootstrap\.sh/);
  assert.doesNotMatch(activity, /nohup node server\/index\.mjs/);
  assert.match(receiver, /getBundleExtra\(RESULT_BUNDLE\)/);
  assert.match(receiver, /exit == 0/);
});
test('Bootstrap requires a confirmed local M5 health reply before pairing', async () => {
  const source = await fs.readFile(new URL('../scripts/termux-bootstrap.sh', import.meta.url), 'utf8');
  assert.match(source, /127\.0\.0\.1:8765\/health/);
  assert.match(source, /v\.name==="M5"/);
  assert.match(source, /check_engine \|\|/);
  assert.match(source, /M5_PORT_CONFLICT/);
  assert.match(source, /git merge --ff-only/);
  assert.match(source, /m5-workspace/);
  assert.doesNotMatch(source, /git reset --hard|git clean -fd|rm -rf/);
  assert.match(source, /printf 'M5_PAIR=%s\\n'/);
});
