# AEOS 0.9 — implementation and measured limits

User system design: zero-root Android Termux agent, local Git, browser CDP, free AI mesh and human consent. This document distinguishes **actual code** from conceptual targets; it is not a promise of universal operating-system privileges.

## Current executable nodes

**Node 1 — Android native Termux core.** The APK embeds a one-shot Node.js bridge inside `app/src/main/assets/native-bridge.mjs` (byte-identical to `scripts/native-bridge.mjs`). No M5 localhost HTTP server or WebView screen is started. The Termux app is a separate Android UID/process with its own app sandbox; M5 cannot root, spy on other processes, or access arbitrary Android app data. Each tool call uses the Termux RUN_COMMAND PendingIntent result contract and a timeout. Users must grant its permissions and enable external commands.

**Code delta:** `patch_apply(path, base64UnifiedDiff, expectedSHA256)` validates a single workspace-relative source file, exact unified diff headers and context, a 40KB bound and optimistic SHA-256 precondition. JS/Python/Shell source receives real syntax preflight; changed source is checkpointed with the pre-existing local History tool before atomic rename. Syntax success is not runtime validation. This is a line-based verified unified patch, **not a complete typed AST rewriting engine**. Language-aware import/callgraph blast-radius analysis is not implemented.

**Node 2 — Git project state.** Existing `git_project_status`, `git_project_diff`, and consent-gated `git_project_init` operate on a project with direct argv invocation. Local saved-file History preserves up to ten versions. **Automated ephemeral worktree/hypothesis branches, per-patch Git commits and automatic promotion to `main` are not implemented**, because CI passing cannot prove universally error-free runtime behavior.

**Node 3 — browser observation.** `browser_observe(port)` makes a read-only, opt-in observation against an **already running** Chrome DevTools Protocol listener on `127.0.0.1:port`. It fetches a local page target and observes title, URL, document readiness, viewport overflow, DOM node count. All WebSocket destinations are restricted to loopback and the exact port. This tool **does not start Chromium**, does not render Canvas, take screenshots or automatically capture exceptions. Browser CDP availability on a physical Android handset must be verified separately; without a debugger, this tool reports unavailable.

## Control feedback and tool schema

`NativeAgent` has a bounded 12-step Observe → Decide → Execute → Observe loop with callbacks, explicit user approvals, stop/interrupt and blocked status. New tools are `apply_patch {path,diff,expectedHash}` and `browser_observe {port}`. All model tool calls are parsed through `AgentProtocol` and the Termux tool whitelist. A model's request alone is never proof of execution. Agent phase telemetry, writes, validation events and selected provider are exposed in the AEOS tab.

`ContextBudget.pack` creates a conservative inference-only character-bounded message envelope, preserving the original agent memory. ~4 chars/token is a rough estimate, **not semantic AST compression or model tokenizer accounting**. Blindly removing imports/class wrappers can break correctness, so M5 does not do it.

## Free provider routing

`ProviderMesh` assigns per-route scores based on observed successes, EWMA latency, and failures. 429s receive cooldown, 502/503/504 and authentication errors receive different cooldowns. The existing public endpoints and opt-in official free-tier API keys remain available. This **cannot guarantee unlimited tokens, provider availability, or network failover under 150ms**; outgoing requests can still take the transport timeout. API keys are stored encrypted via Android Keystore when explicitly configured; hardware backing and RAM-only protection depend on device and Android capabilities and must not be claimed unconditionally.

## Security boundary

Generic model-initiated shell commands now have a narrow allowlist: simple status commands, familiar test commands, and approved `pkg install`. Destructive/root/force-push shell text and metacharacter chains are rejected by the Node bridge. Per-tool approvals are required for patch, source execution, phone actions and CDP reads. Running a user-approved source file **still runs with Termux's normal app privileges**, and is not a hostile-code isolation system. Phone controls require separate user-enabled Android Accessibility and are not universal.

## Physical device acceptance gates

1. Native app starts without Termux and does not start a localhost HTTP server.
2. After connecting Termux, read a source file, apply a correct two-line diff via Patch and verify content hash and History checkpoint; refuse stale hash and malformed diff.
3. Apply syntactically invalid JavaScript and verify original content remains intact with no successful patch receipt.
4. Run valid/invalid source via Check and Run with human consent; verify actual exit and stdout/stderr.
5. Show AEOS dashboard; verify agent phase/counters and mesh cooldown feedback after provider errors.
6. Test Browser CDP with no local debugger (must report unavailable) and with an explicitly started localhost debugger (must return real DOM metrics with consent).
7. Test encrypted drafts, Git status/diff/initialization, phone Accessibility opt-in, stop controls, and 429 provider fallback on a physical Android device.

## Not delivered yet

A true language AST/index and call graph, LSP, independent headless Chromium installation, pixel-level Canvas screenshots/diffs, live CDP exception collection, hardened UNIX-socket IPC, guaranteed hardware-backed ephemeral RAM vault, autonomous Git hypothesis worktrees, proof that every test passed or all possible runtime behavior is perfect, and fully unattended Android cross-app actions. These features are not simulated in the UI and must not be advertised as finished.
