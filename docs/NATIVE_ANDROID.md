# M5 Native Android — no localhost, no WebView

Development branch: feat/native-android-no-server
Pull request: https://github.com/Musabgpt/M5/pull/2

## What changed

M5 is a native Java Android Activity with Android views for Files, Agent, Terminal and Providers. Its application screens are no longer HTML hosted by Android WebView. The Android app does not open an M5 HTTP port.

Termux serves only as a command execution environment. M5 sends official RUN_COMMAND intents with one-time PendingIntent callbacks. Each tool starts Node.js for a one-shot operation and returns its result via Termux's nested result bundle, then exits. This is a process execution bridge, not a persistent Node server.

First setup deploys the one-shot JavaScript bridge from the installed APK to ~/m5-native/scripts, with user project files in ~/m5-workspace. No GitHub checkout or network download of M5 code is needed; Termux only needs Node.js installed. Existing ~/m5 source and all project files are preserved. Official Termux plus allow-external-apps=true and the Run Commands in Termux permission are prerequisites.

## Implemented native features

- Native project explorer and text editor; files up to 40KB. Content uses SHA-256 conflict checks and atomic saves in the Termux workspace.
- One-shot Git status/diff, literal file search and user-confirmed shell commands.
- Native Plan and Agent modes with constrained tools, confirmation for writes and shell commands, and bounded model steps.
- HTTPS calls originate from Android and not from a localhost proxy.
- Original gateway families remain represented: FreeLLMPool direct HTTPS routes; GPT4Free, Free LLM Gateway and Free LLM API may use a user-configured compatible HTTPS service. No free/unlimited claims.

## Verification

Run Node tests and build the Android debug APK on GitHub Actions. Install on actual Samsung hardware and validate the Termux permission flow, connection, file create/edit, command result, and free provider interactions. A green compile is not a real-device acceptance test.

## Important limitations

Native text editing is not yet a Cursor-quality editor or a complete VS Code extension host, and no LSP or interactive PTY is implemented. This version has no multi-agent parallelism or persistent crash-resume. Approved shell commands execute with the user's Termux privileges and must be reviewed. The server and old WebView source remain in the repository as legacy code for the older development branch, but neither is referenced by the native Android activity.

Official RUN_COMMAND protocol: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent

## Phone UI automation (opt-in)

The native Phone tab uses Android PackageManager plus a declared user-enabled AccessibilityService; it does not need a server, a Termux device-control tool, or an ADB Wi-Fi connection. It can enumerate launchable apps, open them, read *visible* accessibility elements, click accessible nodes, set editable text, scroll, and perform Back/Home. Browser links, dialer and SMS **drafts** use Android intents. Sending is not automatic.

The user must explicitly enable the Accessibility service in Android system settings; some sideloaded Android 13+ builds require the user to approve restricted settings first. The service intentionally does not capture ongoing accessibility events. Screen observations sent to model providers require an explicit on-screen approval; actions have their own approval and can be rejected. Password accessibility nodes are redacted. To support approvals when a different app is in the foreground, an explicit Accessibility overlay presents Allow/Deny. Disabling the service in Android Settings immediately removes UI automation access.

Simple phrases such as **افتح واتساب** and **افتح https://example.com** are dispatched directly by Android, independently of free model availability. Multi-step UI automation is subject to model competence, app accessibility trees, foreground constraints and the user granting permissions. No promise of universal Android control.

Free model reliability: official Groq, OpenRouter free-router and Google AI Studio free-key options augment the original four provider families. Keys are encrypted at rest with Android Keystore, and public endpoints get a cooldown on HTTP 429. Free usage is limited by the providers.
