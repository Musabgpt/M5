/**
 * Conservative classification of observed validation commands.
 * An arbitrary successful shell command (echo, cat, pwd) is not a test.
 * Reject chaining and short-circuiting such as npm test OR true.
 */
const PATTERNS = [
  /^npm\s+(?:test|run\s+(?:test(?::[\w-]+)?|lint|check|typecheck|build))(?:\s|$)/,
  /^pnpm\s+(?:test|lint|typecheck|run\s+(?:test|lint|check|typecheck))(?:\s|$)/,
  /^yarn\s+(?:test|lint|typecheck)(?:\s|$)/,
  /^node\s+--(?:test|check)(?:\s|$)/,
  /^python(?:3(?:\.\d+)?)?\s+-m\s+(?:pytest|unittest|py_compile)(?:\s|$)/,
  /^pytest(?:\s|$)/,
  /^bash\s+-n(?:\s|$)/,
  /^sh\s+-n(?:\s|$)/,
  /^grep\s+-q(?:\s|$)/,
  /^test\s+(?:-e|-f|-d|-s)(?:\s|$)/,
  /^go\s+test(?:\s|$)/,
  /^cargo\s+(?:test|check|clippy)(?:\s|$)/,
  /^(?:gradle|\.\/gradlew)\s+(?:(?:[\w-]+:)?(?:test|check|lint|assembleDebug))(?:\s|$)/,
  /^mvn\s+(?:test|verify)(?:\s|$)/
];

export function isVerificationCommand(command) {
  if (typeof command !== 'string' || command.length > 3000) return false;
  const s = command.trim();
  if (!s || /[\n\r;`]|\$\(|\|\||&&|\|>|&>|\s>\s|\s>>/.test(s)) return false;
  return PATTERNS.some(pattern => pattern.test(s));
}
