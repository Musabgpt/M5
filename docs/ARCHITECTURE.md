# M5 — Architecture and evidence gates

## Goal and constraints

Open-source Android-first coding workbench with functional parity as the goal, not copied Cursor UI, trademarks, proprietary engine, or undocumented closed services. Preserve four source families: freellmpool, gpt4free, free-llm-gateway, free-llm-api.

## Current layers (implemented)

1. **Android package:** independent M5 debug identifier, one WebView workbench window, official Termux RUN_COMMAND bridge; first-run bootstrap and protected loopback pairing token.
2. **Node (Termux):** zero-install-dependency HTTP server; bound to 127.0.0.1; state token, cookie session, strict origins, project-root file containment and denylisted secrets/symlinks.
3. **Editor:** accessible in-window source editing with line numbers, search, undo via browser, atomic-save content hashes, sandboxed HTML preview and read-only Git inspection. **Not a VS Code-equivalent editor yet.**
4. **Agent:** free route adapter, structured tool-call envelope for text-only LLMs, step budget, explicit per-operation approval, cancellation, filesystem/shell tools and observation-based final status.
5. **Tests:** Node security, file, provider and consent-loop tests + Android debug compilation workflow.

## Dependencies and limitations

- Termux from a supported trusted source; RUN_COMMAND permission and allow-external-apps enabled. Android can stop background work.
- Free provider routes are not guaranteed online, unlimited or able to return valid tool calls.
- VS Code parity requires code-server / extension host, real LSP, advanced terminal PTY and tested keyboard support. See https://github.com/coder/code-server/blob/main/docs/termux.md
- No code-server, VS Code extensions, remote multiagent fleet, checkpoints, hunk patch review UI, or complete agent runtime persistence is verified.
- User approves every AI write or command; manual shell commands require confirmation. M5 is not a hardened hostile-code sandbox.
- Models must not receive local auth tokens, Termux credentials or user secrets.

## Next acceptance milestones

A. CI tests and Android APK on exact head commit.
B. Real Android device: Termux permission, clone, startup, callback, editing, Git, stop/reconnect.
C. Secure code-server integration under same WebView; test LSP/keyboard/memory and Android extension limitations.
D. Semantic index with change invalidation and context packing.
E. Transaction checkpoints, hunk diffs, agent memory, multiagent worktrees, MCP, browser verification and resumable jobs.
F. Live free gateway probes; blocked rather than invented unlimited access.
