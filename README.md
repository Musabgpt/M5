# M5 — Android-first AI Coding Workbench

**An independent, open-source coding workbench for Android/Termux.** Functional inspiration comes from agentic IDEs such as Cursor; this project does not copy Cursor source, branding, proprietary models or services.

[Development PR](https://github.com/Musabgpt/M5/pull/1) · [Node tests](https://github.com/Musabgpt/M5/actions/workflows/m5-ci.yml) · [Android APK builds](https://github.com/Musabgpt/M5/actions/workflows/android-apk.yml)

> **Status: developer preview, not Cursor parity or a production release.** GitHub tests and APK compilation must pass on the exact commit; physical-device / real-model acceptance remains separate.

## Working architecture

- **One Android WebView application**, independently signed as the M5 debug package. No old Arabic terminal, root or ADB requirement.
- **Official Termux RUN_COMMAND** to start a local Node.js engine, using Android's user-granted permission and local authenticated pairing.
- **Source editor:** a mobile-responsive CodeMirror 6 editor (bundled via npm for offline serving), with editable plain-text fallback, project explorer, search, file hashes, Git status/diff and isolated HTML preview.
- **Optional genuine VS Code mode:** start the official open-source [code-server](https://github.com/coder/code-server) locally if you have installed it in Termux. M5 launches it on localhost with a generated password; it opens inside the same Android WebView. Some desktop/VS Code extensions do not work on Android.
- **Agent:** multi-step tool calls for project files, bounded commands, git inspection and tests, explicit user approvals, cancellation and accurate blocked states if unverified.
- **Before-edit checkpoints:** snapshots of files outside the active project, with explicit hash-verified restore.

## Free models retained from M4

| Family | Routes/requirements |
|---|---|
| FreeLLMPool | Kilo Auto Free, LLM7, Pollinations, OVHcloud, subject to actual provider availability |
| GPT4Free | Optional service on 127.0.0.1:1337 |
| Free LLM Gateway | Optional service on 127.0.0.1:8080 |
| Free LLM API | Optional service on 127.0.0.1:3001 |

These are provider adapters, **not promises of unlimited use or ongoing free access**. Local service gateways are not included as running servers. The app falls back to approved free routes when possible. A model without real tool-call support may be blocked; M5 will not fabricate successful commands or tests.

## Quick start — Termux

Install official Termux and run:

~~~bash
pkg update -y
pkg install -y git nodejs-lts
git clone --depth 1 --branch feat/cursor-grade-workbench https://github.com/Musabgpt/M5.git "$HOME/m5"
cd "$HOME/m5"
npm install --no-audit --no-fund
npm run build
npm test
npm start
~~~

Open the **local pairing URL** printed in Termux. M5 binds to 127.0.0.1:8765 and never to all network interfaces. User projects are kept at $HOME/m5-workspace, not in shared /sdcard storage.

### Android APK

1. Download the *M5-Termux-Workbench-debug-APK* artifact from the successful [Android build](https://github.com/Musabgpt/M5/actions/workflows/android-apk.yml).
2. Install official Termux, enable **allow-external-apps = true** in ~/.termux/termux.properties, reload Termux settings, and grant M5 the RUN_COMMAND permission from Android's app permissions.
3. Open M5 and choose **Start M5 using Termux**. The app clones its own developer branch on first use, builds CodeMirror if possible and starts the authenticated local engine; M5 displays the integrated IDE in the same window.
4. If auto-setup fails, inspect Termux network/package/permission status and use the manual commands above. Git, Node and CodeMirror may require initial internet access.

The Android APK does not update M4 or NewAl-Cloud in place. **Do not delete your previous app or Termux project folders** until you have backups and verified the new version.

### Optional real VS Code editor

Inside official Termux, install the free code-server package using the supported method:

~~~bash
pkg install tur-repo
pkg install code-server
~~~

Open **VS Code** in M5, start its authenticated local service, copy the displayed local password and open the editor in the same app window. This is optional; the native M5 editor and agent do not require code-server. It is a separate local IDE surface, not yet fully integrated with the M5 agent UI and language server workflow.

## Security and honesty

- Workspace reads/writes reject traversal, private key filenames, .git and symlink escapes.
- Existing file writes require an optimistic SHA-256 hash; AI mutations and shell commands require individual approval.
- Command output is bounded by time/output budgets, not a hardened sandbox. Never approve unknown shell commands.
- Session tokens are stored locally; web actions require cookie authentication and origin checks. HTML previews have a restricted sandbox.
- The free provider router never sends Termux session tokens or source control credentials in model request headers.
- Android may stop Termux background work. Persistence and recovery of a running agent turn across process death are not complete.

## Recent implementation upgrades — 12 October 2026

- **Real read-only Plan mode:** workspace research is permitted but tool writes and command execution are refused at runtime, regardless of model prompts.
- **Agent mode:** approved file changes and shell commands; before-edit checkpoints and line-level review diffs.
- **Private contextual code retrieval:** incremental, ranked filename/symbol/text lookup with line references. It is lexical retrieval, **not** vector-embedding semantic search.
- **Validation evidence:** executing any successful shell command is no longer treated as passing a test; qualifying validation commands must pass after the latest edit.
- **Termux contract:** official nested `result` callback with `err`, `exitCode`, `stdout` and bounded receipt validation; the startup script verifies actual localhost M5 health before pairing.
- **Safer upgrades:** source engine only fast-forwards when tracked files are clean, preserving all user projects in `~/m5-workspace`.
- **Efficient CI:** pull-request checks run once, instead of duplicate push and PR executions for each developer commit.

These capabilities are implemented and testable in source/CI. Samsung physical-device acceptance, full language-server integration and model reliability are **still open**.

## Verification and planned parity

Read [architecture](docs/ARCHITECTURE.md) and [test matrix](docs/TEST_MATRIX.md). Current CI exercises real local HTTP security, free gateway routing with mocks, file tools, approval denial, action evidence and rollback. An Android compile is not proof of working real-device UI or free route uptime.

Outstanding for high Cursor functional parity: fully supported VS Code extension host on Android, LSP diagnostics in the same agent context, advanced inline AI completion, semantic codebase index, hunk review, interactive PTY, durable resumable tasks and multi-agent isolation. These are **not complete**.

MIT-friendly components may have their own third-party licences; M5 does not redistribute Cursor software.
