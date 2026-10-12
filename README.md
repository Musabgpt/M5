# M5 Native Studio — Android AI Coding Workbench

**Native Android application. No WebView. No localhost server.**

**Development PR:** https://github.com/Musabgpt/M5/pull/2
**Android APK builds:** https://github.com/Musabgpt/M5/actions/workflows/android-apk.yml

This branch replaces the original WebView-based app with native Java Android screens for Files, Editor, Agent, Terminal, and Free Model Providers. Termux runs an individual tool process through the official RUN_COMMAND PendingIntent protocol. The app connects to AI providers directly over HTTPS.

## Installation (developer preview)

1. Download M5-Native-Studio-APK from a **successful** GitHub Android workflow artifact for PR #2.
2. Install it alongside the older M5. Native Studio v0.5 uses the separate application ID com.musabgpt.m5.studio and label M5 Native Studio. Install it alongside older M5 builds without uninstalling them.
3. Ensure official Termux is installed, enable allow-external-apps=true in ~/.termux/termux.properties, and grant M5 Native Android's additional RUN_COMMAND permission.
4. Launch M5 Native. The native UI appears immediately; tap ↻ Termux to connect once. The app installs its bundled tool script into ~/m5-native and preserves ~/m5-workspace project files. It does not clone GitHub or run a server. Node.js must be present in Termux (M5 may request Termux to install it if missing).
5. Use Files, Agent, and Terminal in one native Android activity. No HTTP service is started.

## Features and limitations

- Native code editor supports text files to 40KB, hashes to reject stale writes, and separate file operations in Termux.
- Plan vs Agent execution, explicit consent for changes and shell commands, bounded steps, direct free HTTPS model routing.
- Free model families retained: FreeLLMPool built-in routes; GPT4Free, Free LLM Gateway, Free LLM API support optional configured HTTPS-compatible endpoints. Uptime and pricing constraints depend on those services.
- Current release is a **native preview**, not full Cursor feature parity: LSP, semantic autocomplete, rich diff editor, interactive PTY, and durable multi-agent workflows remain incomplete.
- Passing CI does not demonstrate success on a physical Samsung device. Real-device acceptance must verify Termux permissions, filesystem, AI network requests and background restrictions.

Full architecture: [docs/NATIVE_ANDROID.md](docs/NATIVE_ANDROID.md).
Legacy M5 WebView server code remains in the repository for historical compatibility with the earlier branch but is not used or needed by the new native Android Activity.

## Native phone agent (0.4.0 preview)

The new Phone screen opens installed apps directly without using an LLM (try **افتح واتساب**), lists launchable apps, and opens Android Accessibility settings. On-demand Android accessibility supports visible UI inspection, tap, type, scroll, Back and Home. Requests to share visible UI with a free model need explicit consent; actions need approval. Approval can appear as a visible accessibility overlay above the target application. Accessibility must be enabled manually by the user, and can be disabled any time. No root, ADB, hidden monitoring, or local HTTP server.

Additional native actions: HTTPS browser links, dialer screen, and **unsent** SMS drafts. M5 cannot bypass Android restrictions or guarantee operation of every app. Full Cursor editor/agent parity is NOT complete.

Free provider choices now include optional official Groq/OpenRouter/Google AI Studio free tiers with a user-supplied key stored using Android Keystore AES-GCM. Free quotas, uptime and access are not unlimited or guaranteed. Use the Providers screen to test a configured route. See [phone acceptance plan](docs/PHONE_ACCEPTANCE.md).

## Native Studio 0.5 engineering milestone

- Fixed a critical defect where all model-origin `phone_*` calls were rejected by the workspace-only whitelist before Android execution.
- Offline project creation from native **New Project**: a working HTML/CSS/JavaScript calculator starter, plus Python and Node starters. Real files are created under Termux, with no remote model. JavaScript source is syntax-checked using Node.
- Source-only model replies (without tool calls) can become explicit write proposals for new files, each requiring approval and verified by content read-back. The system does **not** claim these files have been runtime-tested.
- Native editor with syntax coloring and line-number gutter; no WebView and no HTTP server.
- Folder ZIP export via Android's user-selected document destination; built-in source access prevents path escapes and sensitive files.
- Cleaner native workspace and agent UI, with a switchable full tool trace. A bounded in-memory conversation context preserves simple chats until cleared, without retaining tool observations.
- New native source-creation and model-artifact tests; CI cancels superseded PR runs to conserve free build minutes.

This is a tangible native agent/coding preview, **not full Cursor parity**. Features such as full LSP, interactive PTY, extension host, semantic repo-indexing on Android, durable multi-agent execution and dependable broad phone automation remain unfinished.


## Native Studio 0.6 — checked execution and safer editing

Development PR: https://github.com/Musabgpt/M5/pull/4

This version installs side by side as `com.musabgpt.m5.studio6` (**M5 Studio 0.6**). Previous Studio and Phone Agent installations and their private data are unchanged.

- The **Files** editor now offers **Check** and **Run file** for actual `.js`, `.mjs`, `.cjs`, `.py`, and `.sh` sources. Tools run in Termux directly via one-shot RUN_COMMAND, show observed stdout/stderr, exit codes, timeouts and verification status. **Run** requires a separate user approval since source code has Termux user's privileges.
- The coding agent can request `check_file {path}` or `run_file {path}` as first-class, permission-controlled tools. Check uses syntax parsing, not arbitrary shell string generation. Verified successful checks can satisfy post-edit validation, but compile/syntax success is **not** equivalent to running end-to-end tests.
- New file writes are **create-only**; existing files require a SHA-256 hash from a prior read. The previous empty-file overwrite loophole was removed.
- The source editor preserves unsaved edits in memory while switching its tabs, and refuses to Check/Run content which differs from the saved copy on disk. **Unsaved drafts are not currently crash-proof**, so save before closing or killing the app.
- Files Explorer includes **Search** with clickable file/line snippets from the real Termux workspace.
- Integration tests actually create source files, execute a Node program, check intentionally broken syntax, and validate path and symlink refusals. The Termux bridge inside the APK must exactly match the audited source file.

Usage: install the debug APK from the successful Android Actions workflow on PR #4, connect Termux, create a project or open source in Files, **Save**, then **Check** or **Run file**. For HTML projects, check the included `app.js`; HTML browser rendering still requires a browser.

Not implemented: a full VS Code language server, interactive PTY, reliable phone automation across all applications, production-grade project builds, semantic autocomplete, or persistent multi-agent execution. A passing Android CI build does not establish physical Samsung acceptance.


## M5 Native Studio 0.7 — reliability release

**Development branch:** `feat/native-studio-v07-resilience`. **Separate Android app:** `com.musabgpt.m5.studio7` (M5 Studio 0.7), installed alongside earlier M5 previews without replacing them.

- **Encrypted draft recovery:** edits to local source are stored in app-private AES-256-GCM records using Android Keystore, with a short 700 ms debounce and a synchronous save on Android pause. A **Drafts** button shows up to 12 recoverable source drafts, max 40 KB each. Recovery works even if Termux is offline, but saving to the actual project requires Termux.
- **Disk version safety:** opening another source no longer associates the first source's draft with the wrong path. Asynchronous read/write callbacks track which file they belong to. When the source on disk differs from the hash at which an unsaved draft began, M5 presents an explicit conflict choice before showing the draft. External modifications are not silently overwritten.
- **Safer code execution:** native Check/Run actions and the agent's `run_file` now require an exact SHA-256 revision for execution; the one-shot Termux bridge rejects stale files. Source execution still has the Termux user's privileges and requires explicit approval.
- **Project-wide syntax audit:** tap a folder in Files, then **Audit syntax**. This checks up to 12 JS/Python/Shell files, reports actual parser exit results, and never treats syntax checks as runtime/functional testing. Each check is bounded to 4 seconds.
- **Improved phone layout:** Files toolbar actions scroll horizontally, instead of squeezing multiple controls into a tiny row. The number of stored drafts is shown in Files.
- **Testing:** unit tests cover recovery decisions and SHA-256 freshness, while integration tests run the Termux project syntax audit on valid/invalid source, cap the file count, prevent path escapes, and ensure the JS bridge shipped in the APK matches the audited source.

**Limitations:** Drafts are preserved while the same app installation remains installed and the device's Android Keystore key is available. Uninstalling an APK also deletes its private drafts; a different debug app ID does not automatically import drafts from previous installations. The last sub-second of edits may be lost on sudden power failure before the debounce is committed. For long-term security, manually save or export your actual project. No guarantee of physical Samsung behavior from CI; device testing is still required. Free provider quotas remain limited, and full Cursor-level language servers/PTY/extensions are still unfinished.


## M5 Native Studio 0.8 — local History + Git workbench

**Development branch:** `feat/native-studio-v08-history`. Separate Android package `com.musabgpt.m5.studio8` (debug build), installed alongside earlier versions.

- **Automatic saved-file checkpoints:** before a file is modified through the safe native M5 save or agent write tools, its previous contents are stored locally inside Termux's private `~/m5-workspace/.m5/history`. Keep at most **10 revisions per file** (each at most 40 KB). Nothing is uploaded. These are local revision checkpoints **not Git commits** and are distinct from unsaved encrypted editor drafts.
- **History UI:** in Files, open and Save an existing source file and tap **History**. View previous revision timestamps, SHA-256 fingerprints and a bounded line-by-line comparison, then confirm restoring it. M5 checks the current file hash and automatically checkpoints the pre-restore state. Undoing a restore is possible using History. This is not an interactive three-way merge or full Git diff.
- **Structured agent actions:** `history_list`, `history_compare` and approval-gated `history_restore` are actual local tools. Restoring requires a version ID and current SHA-256 and is treated as a modifying action requiring later verification.
- **Actual Git inspection per project:** tap a **folder** in Files for local Git status, tracked-file diff and the option to initialize Git. Commands execute using `spawn('git', argv)`, with no shell interpolation. Git init creates a local repository and a protective `.gitignore` if needed. It performs no commit, fetch, remote operation or push. This is a separate feature from the automatic local History.
- **No model API or server required:** local file snapshots, native Git and project tools run via the existing one-shot Termux bridge. Free model providers, opt-in Android Accessibility controls, source Check/Run, project export and saved encrypted drafts are retained.

**Limitations and safety:** Native History is under Termux's local files and is lost if Termux's private home is wiped. It is not encrypted separately from Termux app sandbox permissions. Don't confuse it with an off-device backup. The preview compares up to 35 changed line positions, not a semantic code diff. Git diff shows only tracked unstaged changes, and Git operations require that `git` be installed in Termux. `git init` changes local disk only. This remains a preview, not a full Cursor-compatible IDE or production-ready phone controller. GitHub Actions cannot establish that it works on a physical Samsung device.


## M5 AEOS 0.9 — native preview

Source branch: `feat/aeos-v09-core`. Pull request: https://github.com/Musabgpt/M5/pull/7

**Installable Android debug package:** `com.musabgpt.m5.aeos9`, labelled **M5 AEOS 0.9**. It installs alongside previous M5 previews and preserves their existing data. This does **not** automatically migrate old app-private encrypted drafts.

This is an implemented **partial** AEOS engineering milestone, not a literal or complete implementation of every aspirational property in the user specification.

### What works in the code

- **Native AEOS tab:** exposes real agent phase/counters, approximate character-derived context size, and provider mesh scoring/cooldowns. It also offers a user-approved local CDP read-only browser observation, which requires a Chromium debugging target to have been separately enabled on the device. The tab never starts a browser server.
- **Unified source patch:** Files → open an existing file → Save if needed → **Patch**. Paste a valid `--- a/file` / `+++ b/file` unified diff with standard hunks, confirm twice, and M5 validates the current saved SHA-256, exact hunk context and JS/Python/shell syntax (when supported), takes a local checkpoint and atomically renames the patched source. A patch that fails preflight does not update the source. This is an exact-context line diff, **not an AST-aware type analysis engine**.
- **Agent patch/CDP tools:** model schema supports `apply_patch {path,diff,expectedHash}` and `browser_observe {port}`. Both require human approval, and model output cannot bypass path validation or read current browser content without consent.
- **Adaptive free provider mesh:** an EWMA latency / success reliability scoring policy with per-route cooldowns for 429, 503 and other errors. Original provider families and optional user-keyed official free plans are retained. No claim of unlimited free API usage or a deterministic 150ms HTTP retry.
- **Bounded inference context:** maintains a separate character-bounded provider payload while keeping in-memory agent messages unchanged. This does not claim to implement true semantic AST compression.
- **Restricted AI command execution:** generic Shell calls only admit a small set of status, known test and explicit Termux package installation commands; arbitrary destructive shell metacharacters and privilege escalation requests are refused. Source execution remains user-approved and still runs with the Termux app's normal permissions.

See [AEOS implementation, limits and acceptance](docs/AEOS_ENGINEERING.md) and the existing device verification guides. Real Samsung physical-device acceptance and a full Chromium/CDP integration are outstanding. Full LSP/typed AST refactor, visual screenshot diffs, parallel worktrees and autonomous Git promotion remain future engineering work.

## M5 Mesh 0.10 — resilient free-tier inference

**Branch:** `feat/aeos-v10-resilient-free-mesh` · [PR #8](https://github.com/Musabgpt/M5/pull/8).
**App:** `com.musabgpt.m5.mesh10` installs beside prior debug APKs without replacing their local data.

- **Bounded failover across selected free route and anonymous public endpoints.** Exactly the same serialized model conversation is sent when switching routes; failed requests are not reported as tool execution. Each selected configured provider is opt-in, and no proprietary paid model is silently substituted.
- **429:** immediate provider quarantine, 30-second default, honoring a valid `Retry-After` response up to 1 hour. **503/502/504 or DNS/socket timeouts:** one retry after 1 second and another after 2 seconds, then switch provider; retrying is bounded by a 60-second total inference budget. **404/auth/402:** fail closed and quarantine rather than repeat.
- **Health-aware route selection** based on observed successes, failures and latency; eligible public routes are tried by score. `[Mesh]` status messages and history are shown inside the existing Agent trace and AEOS Mesh Dashboard without exposing user keys, prompt contents or raw error responses.
- **Context budget:** preserve the model request payload as a frozen serialized message body across a failover; agent context trimming occurs before the first request, not midway through handoff. Outgoing response length is capped at 1600 model tokens via the standard compatible request field; actual quotas and provider support vary.
- **Deterministic offline fallback:** when remote inference fails, for an explicit request to create an HTML calculator or a simple Python/Node starter, the agent can offer an already bundled Termux template. User consent is required, existing projects are not overwritten, and the result only claims file generation and the syntax check actually performed.
- **Hugging Face Inference Providers** is an optional user-keyed route with the OpenAI-compatible `https://router.huggingface.co/v1/chat/completions` endpoint. The Free user's monthly included credit is small and can change. Paid use may be possible if configured on the user's account; do not activate it without verifying billing settings. No automatic payment, top-up, plan upgrade or paid route switching is performed by M5.

### Honest engineering limits

No mobile process can be guaranteed never to crash, and network failover cannot be guaranteed within 150ms. The *decision to switch* after a 429 happens locally without a retry delay, but the next network call has its own latency. Free endpoints can be unavailable, change terms or reject requests. The local fallback deliberately supports only safe, predetermined templates; it cannot finish arbitrary tasks without a model. API keys are encrypted at rest with Android Keystore, not guaranteed to reside exclusively in volatile RAM. Android Accessibility actions still require user opt-in. GitHub Actions success is not physical-device acceptance.

[Implementation and device checks](docs/FREE_MESH.md).

## M5 Agent PTY 0.11 — autonomous Termux command tools

This update adds model-callable exec_command, write_stdin, read_output and terminate_process tools. Termux still runs in the background through Android RUN_COMMAND; no WebView, ttyd, embedded terminal or M5 HTTP server is created.

On Connect Termux, the APK installs the bundled pty_bridge.py to the private Termux scripts directory, and ensures Python is available. The Python daemon is started lazily when first needed; it uses an authenticated private Unix domain socket with a 0600 token inside Termux, preserving PTY sessions between one-shot Android calls.

exec_command accepts a shell command and workdir relative to ~/m5-workspace and yield_time_ms (0–10000). Results include a session ID, incremental cursor, real process exit code and running flag. Output is merged stdout/stderr because a PTY cannot separately attribute the two streams. read_output retrieves new output, write_stdin sends input after user approval, and terminate_process sends SIGTERM to that process group. Agent Stop also requests termination of tracked active sessions.

Session output and the actual command result are fed into the next LLM step, allowing model-driven run/check/fix loops. Existing file, patch and Git tools remain available. The command approval policy rejects obvious destructive/root/force-push commands; ordinary read-only commands may proceed while installs, arbitrary scripts and interactive stdin require a visible one-time approval.

Sessions do not survive Termux daemon or Android OS process termination. Lost sessions are reported and are not automatically replayed, preventing duplicate package installs or edits. This is an app-sandbox process engine, not a secure container for hostile commands. Real Samsung Android foreground-service and battery restrictions still apply.

Installable debug application ID: com.musabgpt.m5.termux11, independent of older versions. CI runs real Python PTY integration tests and Android Java/build tests. Full physical-phone acceptance is not implied by CI success.
