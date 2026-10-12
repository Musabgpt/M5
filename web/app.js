const $ = id => document.getElementById(id);
const state = { files: [], file: null, hash: '', source: '', jobId: null, poll: null, lastIndex: -1, approval: null, panel: 'files' };

async function api(url, options = {}) {
  const result = await fetch(url, { credentials: 'same-origin', ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) } });
  const value = await result.json();
  if (!result.ok) throw new Error(value.error || 'HTTP ' + result.status);
  return value;
}
const post = (url, data = {}) => api(url, { method: 'POST', body: JSON.stringify(data) });
const mobile = () => matchMedia('(max-width:580px)').matches;
const notice = text => { $('connection').textContent = text; };
const element = (tag, attrs = {}, text = '') => {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) if (key === 'className') el.className = value; else el.setAttribute(key, value);
  el.textContent = text;
  return el;
};
function panel(name) {
  state.panel = name;
  document.querySelectorAll('.rail button').forEach(button => button.classList.toggle('active', button.dataset.panel === name));
  $('sidebar-title').textContent = { files:'EXPLORER',agent:'AGENT',ide:'VS CODE',search:'SEARCH',git:'SOURCE CONTROL',terminal:'TERMINAL',settings:'PROVIDERS' }[name] || 'EXPLORER';
  $('welcome').hidden = name !== 'files' || !!state.file;
  $('editor-view').hidden = name !== 'files' || !state.file;
  $('agent-view').hidden = name !== 'agent';
  $('secondary-view').hidden = ['files','agent'].includes(name);
  $('tab-label').textContent = name === 'files' ? (state.file || 'Welcome') : name.toUpperCase();
  if (mobile()) $('sidebar').classList.toggle('collapsed', name !== 'files');
  if (!['files','agent'].includes(name)) secondary(name).catch(e => notice(e.message));
}
async function loadFiles() {
  const data = await api('/api/tree');
  state.files = data.files;
  const tree = $('file-tree'); tree.replaceChildren();
  for (const file of data.files) {
    const b = element('button', { className: 'file-row' + (file.type === 'directory' ? ' folder' : '') + (state.file === file.path ? ' selected' : '') }, (file.type === 'directory' ? '▸ ' : '  ') + file.path);
    b.onclick = () => file.type === 'file' && openFile(file.path);
    tree.append(b);
  }
}
async function openFile(path) {
  if (state.source !== $('editor').value && state.file && !confirm('Discard unsaved changes?')) return;
  try {
    const file = await api('/api/file?path=' + encodeURIComponent(path));
    state.file = path; state.source = file.content; state.hash = file.sha256;
    $('editor').value = file.content;
    window.M5Editor?.setFile(file.content, path);
    $('file-name').textContent = path; $('tab-label').textContent = path;
    $('dirty-dot').textContent = ''; $('editor').scrollTop = 0;
    updateLines(); panel('files'); await loadFiles(); notice('Opened ' + path);
  } catch (e) { notice(e.message); }
}
function updateLines() {
  const t = $('editor');
  $('line-numbers').textContent = Array.from({ length: Math.min(5000, t.value.split('\n').length) }, (_, i) => String(i + 1)).join('\n');
  $('dirty-dot').textContent = t.value === state.source ? '' : '●';
  const first = t.value.slice(0, t.selectionStart);
  const ln = first.split('\n');
  $('editor-status').textContent = 'Ln ' + ln.length + ', Col ' + (ln.at(-1).length + 1) + ' · ' + t.value.length + ' chars';
}
async function save() {
  if (!state.file) return;
  try {
    const text = $('editor').value;
    const result = await post('/api/file', { path: state.file, content: text, expectedHash: state.hash });
    state.source = text; state.hash = result.sha256; updateLines();
    notice('Saved · SHA-256 ' + result.sha256.slice(0, 12)); await loadFiles();
  } catch (e) { alert('Save blocked: ' + e.message); }
}
async function createFile() {
  const filename = prompt('New workspace-relative filename (existing folder only):');
  if (!filename) return;
  try {
    await post('/api/file', { path: filename, content: '', expectedHash: '' });
    await loadFiles(); await openFile(filename);
  } catch (e) { alert(e.message); }
}
function addEvent(event) {
  const div = element('div', { className: 'event ' + event.type });
  const labels = { step: 'STEP', tool: 'TOOL', result: 'RESULT', route: 'PROVIDER', approval:'APPROVAL', error:'ERROR', warning:'WARNING', answer:'M5', cancelled:'STOPPED' };
  div.textContent = (labels[event.type] || event.type).toUpperCase() + '\n' + (typeof event.data === 'string' ? event.data : JSON.stringify(event.data, null, 2));
  $('conversation').append(div);
  $('conversation').scrollTop = $('conversation').scrollHeight;
}
async function updateJob() {
  if (!state.jobId) return;
  const job = await api('/api/agent/' + state.jobId + '?after=' + state.lastIndex);
  for (const ev of job.events) { addEvent(ev); state.lastIndex = Math.max(state.lastIndex, ev.index); }
  $('agent-status').textContent = job.status.toUpperCase() + ' · edits ' + job.evidence.writes + ' · checks ' + job.evidence.checks;
  $('agent-stop').disabled = !['running','approval'].includes(job.status);
  if (job.pending) {
    state.approval = job.pending;
    $('approval-detail').textContent = JSON.stringify(job.pending, null, 2);
    $('approval').hidden = false;
  } else { state.approval = null; $('approval').hidden = true; }
  if (!['running','approval'].includes(job.status)) {
    clearInterval(state.poll); state.poll = null; await loadFiles();
  }
}
async function beginAgent(goal) {
  try {
    $('conversation').replaceChildren(); state.lastIndex = -1;
    const data = await post('/api/agent/start', { goal, family: $('provider-family').value });
    state.jobId = data.id;
    $('agent-prompt').value = '';
    $('agent-stop').disabled = false;
    let fetching = false;
    state.poll = setInterval(async () => {
      if (fetching) return;
      fetching = true;
      try { await updateJob(); } catch (e) { notice(e.message); } finally { fetching = false; }
    }, 850);
    await updateJob();
  } catch (e) { alert('Agent blocked: ' + e.message); }
}
async function decision(approved) {
  if (!state.approval || !state.jobId) return;
  try {
    await post('/api/agent/' + state.jobId + '/approve', { approvalId: state.approval.id, approved });
    $('approval').hidden = true; state.approval = null; await updateJob();
  } catch (e) { notice(e.message); }
}
function outputView(parent, text) { parent.append(element('pre', { className:'output' }, text)); }
async function secondary(name) {
  const view = $('secondary-view'); view.replaceChildren();
  const title = element('h2', {}, { search:'Workspace Search', git:'Source Control', terminal:'Termux Shell', settings:'Model Gateways', ide:'VS Code Engine' }[name] || name);
  view.append(title);
  if (name === 'ide') {
    view.append(element('p', {}, 'Optional free, open-source code-server mode: authentic VS Code browser IDE inside this Android app. Install in Termux: pkg install tur-repo && pkg install code-server. Some desktop extensions are unsupported on Android.'));
    const status = await api('/api/ide/status');
    view.append(element('p', { className:'muted' }, 'Installed: ' + status.installed + ' · Running: ' + status.running));
    const start = element('button', {}, 'Start secure VS Code engine');
    const result = element('div');
    view.append(start, result);
    start.disabled = !status.installed;
    start.onclick = async () => {
      start.disabled = true;
      try {
        const ready = await post('/api/ide/start');
        result.replaceChildren();
        result.append(element('p', {}, 'Local VS Code password (never sent to an LLM):'));
        const password = element('input', { readonly:'readonly', value:ready.password, style:'width:100%' });
        result.append(password);
        const copy = element('button', {}, 'Copy password');
        copy.onclick = async () => { password.select(); try { await navigator.clipboard.writeText(ready.password); } catch { document.execCommand('copy'); } };
        const open = element('button', {}, 'Open VS Code in this window');
        open.onclick = () => { window.location.assign(ready.target); };
        const tools = element('div', { className:'secondary-actions' }); tools.append(copy, open); result.append(tools);
      } catch (e) { result.textContent = e.message; }
      finally { start.disabled = false; }
    };
  } else if (name === 'search') {
    const input = element('input', { placeholder: 'Search text in project files' });
    const button = element('button', {}, 'Find exact text');
    const smart = element('button', {}, 'Find relevant code');
    const result = element('div');
    const row = element('div', { className:'input-row' }); row.append(input, button, smart); view.append(row, result);
    smart.onclick = async () => {
      result.replaceChildren();
      if (!input.value.trim()) { result.textContent = 'Enter a symbol or description to search'; return; }
      try {
        const data = await api('/api/context?q=' + encodeURIComponent(input.value));
        for (const hit of data.results) {
          const card = element('div', { className: 'card' });
          const link = element('button', { className:'file-row' }, hit.path + ' · relevance ' + hit.score);
          link.onclick = () => openFile(hit.path);
          card.append(link);
          for (const line of hit.snippets) {
            const row = element('div', { className:'muted' }, 'L' + line.line + '  ' + line.text);
            card.append(row);
          }
          result.append(card);
        }
        if (!data.results.length) result.textContent = 'No relevant code found in the indexed workspace';
      } catch (e) { result.textContent = e.message; }
    };
    button.onclick = async () => {
      result.replaceChildren();
      try { const data = await api('/api/search?q=' + encodeURIComponent(input.value));
        for (const m of data.matches) {
          const b = element('button', { className:'file-row' }, m.path + ':' + m.line + ' · ' + m.text);
          b.onclick = () => openFile(m.path); result.append(b);
        }
        if (!data.matches.length) result.textContent = 'No matches';
      } catch (e) { result.textContent = e.message; }
    };
  } else if (name === 'git') {
    const bar = element('div', { className:'secondary-actions' });
    const statusButton = element('button', {}, 'Git status'), diffButton = element('button', {}, 'Git diff'), checkpointButton = element('button', {}, 'Restore files');
    const output = element('div');
    bar.append(statusButton, diffButton, checkpointButton); view.append(bar, output);
    const inspect = async action => {
      output.replaceChildren();
      try { const data = await api('/api/git/' + action); outputView(output, (data.stdout || '') + '\n' + (data.stderr || '') + '\nExit code: ' + data.code); }
      catch (e) { outputView(output, e.message); }
    };
    statusButton.onclick = () => inspect('status'); diffButton.onclick = () => inspect('diff');
    checkpointButton.onclick = async () => {
      output.replaceChildren();
      try {
        const data = await api('/api/checkpoints');
        if (!data.entries.length) output.textContent = 'No checkpoints yet';
        for (const point of data.entries) {
          const b = element('button', { className:'file-row' }, point.path + ' · ' + point.createdAt + ' · ' + point.id.slice(0, 8));
          b.onclick = async () => {
            if (!confirm('Restore checkpoint of ' + point.path + '? This will overwrite the current file or remove a newly created file.')) return;
            try {
              let currentHash = '';
              try { currentHash = (await api('/api/file?path=' + encodeURIComponent(point.path))).sha256; } catch (e) { if (!String(e.message).includes('ENOENT')) throw e; }
              const response = await post('/api/checkpoints/restore', { id: point.id, expectedCurrentHash: currentHash });
              notice('Restored ' + response.path); await loadFiles(); if (state.file === response.path && !response.deletedNewFile) await openFile(response.path);
            } catch (e) { alert('Restore blocked: ' + e.message); }
          };
          output.append(b);
        }
      } catch (e) { outputView(output, e.message); }
    };
    inspect('status');
  } else if (name === 'terminal') {
    view.append(element('p', {}, 'Runs real commands in the workspace via Termux. Output and exit codes are observed; not a full interactive PTY.'));
    const input = element('input', { value:'pwd', placeholder:'Shell command' });
    const button = element('button', {}, 'Run');
    const output = element('div');
    const row = element('div', { className:'input-row' }); row.append(input, button); view.append(row, output);
    button.onclick = async () => {
      if (!input.value.trim() || !confirm('Execute this command in the local workspace?\n\n' + input.value)) return;
      button.disabled = true; output.replaceChildren();
      try { const data = await post('/api/terminal', { command: input.value }); outputView(output, (data.stdout || '') + '\n' + (data.stderr || '') + '\nExit code: ' + data.code + (data.timedOut ? '\nTIMEOUT' : '')); }
      catch (e) { outputView(output, 'Command failed: ' + e.message); }
      finally { button.disabled = false; }
    };
  } else if (name === 'settings') {
    view.append(element('p', {}, 'The original four M4 free gateway families remain. No guarantee of uptime, unlimited usage, or tool-calling quality.'));
    const data = await api('/api/status');
    for (const route of data.providers) {
      const card = element('div', { className:'card' });
      card.append(element('strong', {}, route.name), element('p', { className:'muted' }, route.family + ' · ' + route.model + (route.local ? ' · local server required' : ' · external free route')));
      view.append(card);
    }
  }
}
document.querySelectorAll('.rail button').forEach(b => b.onclick = () => panel(b.dataset.panel));
$('editor').addEventListener('input', updateLines);
$('editor').addEventListener('keyup', updateLines);
$('editor').addEventListener('click', updateLines);
$('editor').addEventListener('scroll', () => { $('line-numbers').scrollTop = $('editor').scrollTop; });
$('editor').addEventListener('keydown', e => {
  if (e.key === 'Tab') { e.preventDefault(); const t = $('editor'); t.setRangeText('  ', t.selectionStart, t.selectionEnd, 'end'); updateLines(); }
});
document.addEventListener('keydown', e => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); save(); } });
$('save').onclick = save;
$('new-file').onclick = createFile;
$('refresh').onclick = () => loadFiles().catch(e => notice(e.message));
$('open-agent').onclick = () => panel('agent');
$('open-files').onclick = () => panel('files');
$('ai-edit').onclick = () => { const instruction = prompt('Describe the code change:'); if (instruction) { panel('agent'); $('agent-prompt').value = 'Edit ' + state.file + ': ' + instruction + '. Inspect file, apply a minimal safe edit, and run relevant tests.'; } };
$('preview').onclick = () => {
  if (!state.file?.match(/\.html?$/i)) return alert('Preview requires an HTML file');
  const w = $('secondary-view'); panel('search'); w.replaceChildren();
  w.append(element('h2', {}, 'Sandboxed HTML Preview: ' + state.file));
  const frame = element('iframe', { sandbox:'allow-scripts', title:'HTML preview', style:'width:100%;height:75%;border:1px solid #444', src:'/preview?path=' + encodeURIComponent(state.file) });
  w.append(frame);
};
$('agent-form').onsubmit = e => { e.preventDefault(); if (state.poll) return; beginAgent($('agent-prompt').value); };
$('approve').onclick = () => decision(true);
$('deny').onclick = () => decision(false);
$('agent-stop').onclick = async () => { if (state.jobId) { await post('/api/agent/' + state.jobId + '/stop'); await updateJob(); } };
(async () => {
  import('/editor.bundle.js').then(module => {
    window.M5Editor = module.attachEditor({ textarea: $('editor'), host: document.querySelector('.editor-shell'), lineNumbers: $('line-numbers') });
    if (state.file) window.M5Editor.setFile($('editor').value, state.file);
    notice('CodeMirror 6 loaded · language-aware editor');
  }).catch(() => { /* Offline / no npm build: editable textarea remains. */ });
  try {
    const status = await api('/api/status');
    $('workspace-path').textContent = status.workspace;
    $('workspace-name').textContent = status.workspace.split('/').at(-1);
    notice('Connected · M5 local agent');
    if (mobile()) $('sidebar').classList.add('collapsed');
    await loadFiles();
    panel('files');
  } catch (e) { notice('Connection error: ' + e.message); $('welcome').replaceChildren(element('p', {}, e.message)); }
})();
