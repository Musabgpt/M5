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
