// Free-first, fixed catalog. A free route is never promised to be online, unlimited, or tool-capable.
export const ROUTES = Object.freeze([
  { family: 'freellmpool', name: 'Kilo Auto Free', base: 'https://api.kilo.ai/api/gateway', model: 'kilo-auto/free' },
  { family: 'freellmpool', name: 'LLM7 Free', base: 'https://api.llm7.io/v1', model: 'default' },
  { family: 'freellmpool', name: 'Pollinations Free', base: 'https://text.pollinations.ai/openai', model: 'openai-fast' },
  { family: 'freellmpool', name: 'OVHcloud Free', base: 'https://oai.endpoints.kepler.ai.cloud.ovh.net/v1', model: 'Qwen3-Coder-30B-A3B-Instruct' },
  { family: 'gpt4free', name: 'GPT4Free local', base: 'http://127.0.0.1:1337/v1', model: 'auto' },
  { family: 'free-llm-gateway', name: 'Free LLM Gateway local', base: 'http://127.0.0.1:8080/v1', model: 'auto' },
  { family: 'free-llm-api', name: 'Free LLM API local', base: 'http://127.0.0.1:3001/v1', model: 'auto' }
]);

export function orderedRoutes(family = 'freellmpool') {
  const selected = ROUTES.filter(r => r.family === family);
  const fallback = family === 'freellmpool' ? [] : ROUTES.filter(r => r.family === 'freellmpool');
  return (selected.length ? selected : ROUTES.filter(r => r.family === 'freellmpool')).concat(fallback);
}

export function validateRoute(route) {
  const u = new URL(route.base);
  if (u.username || u.password || u.search || u.hash) return false;
  const isLocal = ['127.0.0.1', 'localhost', '[::1]'].includes(u.hostname);
  return (u.protocol === 'https:' && !isLocal) || (u.protocol === 'http:' && isLocal);
}

function getMessage(json) {
  const msg = json?.choices?.[0]?.message;
  if (!msg) throw new Error('Provider returned no assistant message');
  const content = typeof msg.content === 'string' ? msg.content : Array.isArray(msg.content) ? msg.content.filter(x => x.type === 'text').map(x => x.text).join('') : '';
  const calls = Array.isArray(msg.tool_calls) ? msg.tool_calls.map(x => ({
    name: x.function?.name, arguments: JSON.parse(x.function?.arguments || '{}')
  })) : [];
  return { content, calls, usage: json.usage || null };
}

export async function chatCompletion(messages, { family = 'freellmpool', signal, fetcher = fetch, tools = [] } = {}) {
  const errors = [];
  for (const route of orderedRoutes(family)) {
    if (!validateRoute(route)) continue;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 35_000);
    const abort = () => controller.abort();
    signal?.addEventListener('abort', abort, { once: true });
    try {
      const payload = { model: route.model, messages, stream: false, temperature: 0.1 };
      // Native function calling is optional: prompt-envelope fallback handles text-only gateways.
      if (tools.length) payload.tools = tools;
      const response = await fetcher(route.base.replace(/\/$/, '') + '/chat/completions', {
        method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(payload), signal: controller.signal, redirect: 'error'
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const body = await response.text();
      if (body.length > 1_500_000) throw new Error('Provider response too large');
      const result = getMessage(JSON.parse(body));
      if (!result.content && !result.calls.length) throw new Error('Empty model response');
      return { ...result, route: route.name };
    } catch (e) {
      if (signal?.aborted) throw new Error('Cancelled');
      errors.push(route.name + ': ' + String(e.message).slice(0, 100));
    } finally {
      clearTimeout(timeout);
      signal?.removeEventListener('abort', abort);
    }
  }
  throw new Error('No free provider available. ' + errors.join(' | ').slice(0, 900));
}
