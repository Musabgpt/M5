# M5 Native — Android AI Coding Workbench

**Native Android application. No WebView. No localhost server.**

**Development PR:** https://github.com/Musabgpt/M5/pull/2
**Android APK builds:** https://github.com/Musabgpt/M5/actions/workflows/android-apk.yml

This branch replaces the original WebView-based app with native Java Android screens for Files, Editor, Agent, Terminal, and Free Model Providers. Termux runs an individual tool process through the official RUN_COMMAND PendingIntent protocol. The app connects to AI providers directly over HTTPS.

## Installation (developer preview)

1. Download M5-Native-debug-APK from a **successful** GitHub Android workflow artifact for PR #2.
2. Install it alongside the older M5. This preview uses the separate application ID com.musabgpt.m5.phoneagent and label M5 Phone Agent. Install it beside the older M5 Native Fix; do not uninstall or lose existing files.
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
