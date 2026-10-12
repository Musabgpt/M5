# M5 verification matrix

| Requirement | Evidence required | Current verdict |
|---|---|---|
| Separate M5 repo and private workspace | Repo and path checks | Implemented; CI pending |
| Preserve four free gateways | Catalog tests | Implemented, uptime unverified |
| Reject outside-workspace reads | Traversal and symlink tests | Test written |
| Atomic saves and restore snapshots | Hash conflict, checkpoint tests | Implemented; physical acceptance pending |
| Model calls real tools | Mocked approval test | Test written; live provider pending |
| Denied tool cannot write | Denial test | Test written |
| Git status and diff | subprocess code | Device unverified |
| Termux only; no old Arabic terminal | Android manifest and bridge | Source built; device unverified |
| Installable APK | CI build and physical installation | Build pending |
| CodeMirror 6 syntax editor | Bundled CI dependency build | Implemented; device verification pending |
| Optional code-server IDE | Local password-authenticated startup | Implemented; device verification pending |
| Language server and predictive inline completions | LSP test suite | NOT IMPLEMENTED |
| Cursor-grade checkpoint/resume workflow | Crash and reboot tests | NOT IMPLEMENTED |
| Fully interactive PTY | Live Android device | NOT IMPLEMENTED |
| Browser and phone control | Scoped Android acceptance | NOT IMPLEMENTED |
| Production build | Physical acceptance, update signing | NOT READY |

A green build is not equivalent to full feature acceptance.

## Additional acceptance gates

| Feature | Source gate | Physical-device acceptance |
|---|---|---|
| Plan cannot write/execute | `tests/modes.test.mjs` | Pending |
| Ranked local code context | `tests/context.test.mjs` | Pending |
| Actual Termux callback contract | `tests/context.test.mjs` and `tests/bootstrap.test.mjs` | Pending |
| Verified M5 health before pairing | `tests/bootstrap.test.mjs` | Pending |
| Before-edit approval diff | `tests/diff.test.mjs` | Pending |
| False-success prevention | `tests/verification.test.mjs` | Pending |
