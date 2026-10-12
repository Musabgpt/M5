# M5 Native — Android AI Coding Workbench

**Native Android application. No WebView. No localhost server.**

**Development PR:** https://github.com/Musabgpt/M5/pull/2
**Android APK builds:** https://github.com/Musabgpt/M5/actions/workflows/android-apk.yml

This branch replaces the original WebView-based app with native Java Android screens for Files, Editor, Agent, Terminal, and Free Model Providers. Termux runs an individual tool process through the official RUN_COMMAND PendingIntent protocol. The app connects to AI providers directly over HTTPS.

## Installation (developer preview)

1. Download M5-Native-debug-APK from a **successful** GitHub Android workflow artifact for PR #2.
2. Install it alongside the older M5. This debug build uses the separate application ID com.musabgpt.m5.nativepreview.
3. Ensure official Termux is installed, enable allow-external-apps=true in ~/.termux/termux.properties, and grant M5 Native Android's additional RUN_COMMAND permission.
4. Launch M5 Native. The native UI appears immediately; tap ↻ Termux to connect once. The app installs a separate engine copy in ~/m5-native and preserves ~/m5-workspace project files.
5. Use Files, Agent, and Terminal in one native Android activity. No HTTP service is started.

## Features and limitations

- Native code editor supports text files to 40KB, hashes to reject stale writes, and separate file operations in Termux.
- Plan vs Agent execution, explicit consent for changes and shell commands, bounded steps, direct free HTTPS model routing.
- Free model families retained: FreeLLMPool built-in routes; GPT4Free, Free LLM Gateway, Free LLM API support optional configured HTTPS-compatible endpoints. Uptime and pricing constraints depend on those services.
- Current release is a **native preview**, not full Cursor feature parity: LSP, semantic autocomplete, rich diff editor, interactive PTY, and durable multi-agent workflows remain incomplete.
- Passing CI does not demonstrate success on a physical Samsung device. Real-device acceptance must verify Termux permissions, filesystem, AI network requests and background restrictions.

Full architecture: [docs/NATIVE_ANDROID.md](docs/NATIVE_ANDROID.md).
Legacy M5 WebView server code remains in the repository for historical compatibility with the earlier branch but is not used or needed by the new native Android Activity.
