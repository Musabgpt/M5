# M5 verification matrix

| Requirement | Evidence required | Current verdict |
|---|---|---|
| Separate M5 repo and private workspace | Repo and path checks | Implemented; CI pending |
| Preserve four free gateways | Catalog tests | Implemented, uptime unverified |
| Reject outside-workspace reads | Traversal and symlink tests | Test written |
| Atomic saves | Hash conflict test | Test written |
| Model calls real tools | Mocked approval test | Test written; live provider pending |
| Denied tool cannot write | Denial test | Test written |
| Git status and diff | subprocess code | Device unverified |
| Termux only; no old Arabic terminal | Android manifest and bridge | Source built; device unverified |
| Installable APK | CI build and physical installation | Build pending |
| Language server and predictive inline completions | LSP test suite | NOT IMPLEMENTED |
| Cursor-grade checkpoint/resume workflow | Crash and reboot tests | NOT IMPLEMENTED |
| Fully interactive PTY | Live Android device | NOT IMPLEMENTED |
| Browser and phone control | Scoped Android acceptance | NOT IMPLEMENTED |
| Production build | Physical acceptance, update signing | NOT READY |

A green build is not equivalent to full feature acceptance.
