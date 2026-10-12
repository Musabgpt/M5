# M5 Phone Agent — real-device acceptance checklist

CI passing does not prove a phone task executed. These scenarios require explicit manual verification on physical Android 16.

1. Install the `com.musabgpt.m5.phoneagent` debug APK side by side with old M5; verify old projects remain.
2. Launch M5 Phone Agent without Termux. Native tabs render; agent and Phone controls remain available.
3. Type **افتح واتساب** in Agent mode. It must dispatch an actual Android WhatsApp launch without contacting any model. If WhatsApp is not installed, show an accurate error.
4. Type **افتح https://example.com**. It must open a real HTTPS browser activity; reject plain HTTP or dangerous schemes.
5. Tap Phone > Installed apps. Verify the Android launchable app list matches the real device.
6. Without Accessibility enabled, `phone_observe` must fail with a permission explanation and no contents.
7. Enable **M5 phone controls** manually in Android Accessibility Settings. Confirm no access is granted automatically.
8. Open a non-sensitive target app, request `phone_observe`, consent to sharing visible UI, and verify nodes have current IDs and package labels.
9. Request `phone_click` with a known node ID. Require a visible Allow/Deny overlay and verify a real UI transition; fail closed after screen changes.
10. Request `phone_type` on an ordinary editable field. Require approval; password fields must remain inaccessible.
11. Test scroll, Back and Home, verifying each reported result against actual UI state.
12. Test Phone dialer and SMS draft. Never auto-call or auto-send.
13. Deny a phone action. The target app must remain unchanged.
14. Disable Accessibility mid-task. Further observation and control must fail safely.
15. Request project file read/write and shell execution via Termux to verify legacy coding tools still work.
16. Send a greeting with Termux disabled; direct HTTPS provider can answer without device tools.
17. Trigger a free-provider HTTP 429. Ensure route is cooled down instead of hammered repeatedly, and the app reports actual provider status.
18. Test optional Groq/OpenRouter/Google AI Studio free API keys, ensuring they are stored encrypted and never returned in tool outputs.
19. Test Android rotation, keyboard insets, process restart and background behavior; tasks must not claim unobserved success.

**Not yet implemented/verified:** interactive PTY, robust language server, inline autocomplete like Cursor, multi-agent parallel worktrees, production signer and universal phone app automation.

Service reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

## Native Studio v0.5 acceptance

- Install separate app ID `com.musabgpt.m5.studio` and label `M5 Native Studio`; no uninstall of M5 Phone Agent.
- In Files, choose **New Project** then HTML calculator, Python starter and Node starter. Confirm each creates real files under Termux workspace, reports validation evidence and refuses existing folder overwrite.
- Tap a generated project folder; export ZIP through Android's document picker. Open the ZIP in a file manager and verify included source files, no secrets.
- Open index.html in a standard browser using a user-selected export mechanism and exercise arithmetic, including division by zero. A successful syntax check alone is **not** proof of browser runtime behavior.
- In Agent mode, request code without tool calls. If model returns recognized fenced source, M5 should request approval before writing, read back the file and report only byte-level validation. Reject files that already exist.
- Verify `phone_observe` or `phone_click` from an LLM actually dispatches to native PhoneTools (regression for prior incorrect whitelist).
- Native code editor shows line numbers and highlighting without launching a WebView.
- Switch Agent trace between compact chat and details; confirm tool failures are still visible.
- Confirm short-term chat context is available for a second ordinary question but is erased via Clear; screen observations are not included in that memory.
- Confirm the APK uses Android widgets and Termux one-shot processes, not a localhost server.

**Unverified on physical phone until tested:** Accessibility interactions within target apps, external browser HTML preview, ZIP export to each document provider, device-specific keyboard/IME behavior, background recovery.
