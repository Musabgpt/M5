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


## Native Studio 0.6 real-device acceptance

- Install `com.musabgpt.m5.studio6` alongside Studio 0.5. Confirm older app and private projects remain unchanged.
- Create the offline HTML calculator project. Open `app.js`, press **Check** and confirm the actual exitCode 0. Test an intentionally malformed JS file and verify a non-zero exit and error output.
- Open a small Node `.mjs` file that prints a unique value; press **Run file**, approve the confirmation and verify that exact stdout and code 0 appear.
- Deny the Run confirmation; ensure no program executes.
- Modify source but do not save it. Verify Check and Run refuse to execute stale disk contents. Switch tabs and return: unsaved source must still be shown.
- Create a brand-new source file, save it and verify it appears. Recreate an existing empty file through the create-only path and confirm it is not overwritten.
- Search for a distinctive symbol, open a result and inspect the correct file. Ensure secrets, symlinks and workspace traversal are not displayed or modified.
- Agent tool protocol: request `check_file` on a real file, then attempt an `run_file` tool call and verify human approval happens before execution.
- Run a code file that returns a non-zero exit; verify the UI shows failure, not successful completion.
- Rotate the device, background it and reopen; save any in-progress source first. Unverified crash-survival must not be assumed.
- Export the project as ZIP and verify the source contents match the workspace.

**Known limitation:** `Check` validates Python/Node/shell syntax only, not web browser behavior or a complete unit test suite. Executing untrusted code inside Termux is dangerous; do not approve it.


## Native Studio 0.8 physical device acceptance — History and Git

1. Install the separately signed debug app `com.musabgpt.m5.studio8` beside older M5 builds and reconnect Termux.
2. Open a source file, record its contents, make an edit and Save. Verify the save result. Make a second edit and Save.
3. Tap **History**. Select an earlier version. Confirm that the SHA-256 values, file path and line-by-line preview match the edits.
4. Accept restoration. Inspect the file after reload and repeat History to undo the restore. Check the exact original content is restored.
5. Edit the same file more than ten times. Confirm that only the most recent ten snapshots remain, older snapshots are pruned and the workspace explorer hides `.m5`.
6. Start a restore, then change the file externally in Termux. Confirm restore fails without replacing the newer file.
7. Open a workspace folder and select **Initialize local Git repository**. Verify `.gitignore` exists and contains entries for secrets, build files and `node_modules`.
8. Inspect **Git status** before and after modifying tracked source. Inspect **Git diff** and verify it shows actual Git output and notes its exclusion of untracked files.
9. Try Git actions in an uninitialized folder and with Git uninstalled. Confirm the interface reports failure instead of success.
10. Reject restoration/initialization confirmations; verify no file operation occurs.
11. Confirm no GitHub login, remote, fetch, push, or commit is triggered by History or Git inspection.
12. Confirm existing Termux Check/Run, encrypted draft recovery, project ZIP export, and the agent's explicit approvals continue functioning.

No claim of universal device automation, full Cursor extension support, LSP or stable interactive PTY is made.
