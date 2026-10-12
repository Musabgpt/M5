# M5 AEOS 0.10 — Free Tier Mesh Engineering and Acceptance

## Invariants

1. All provider endpoints use HTTPS, no redirects, and bearer tokens are attached only to official fixed origins for opt-in keyed families. Public routes never receive private keys.
2. The model conversation JSON is serialized once for each logical inference request and reused without modification at every provider transition. Neither failures nor retries fabricate a tool result.
3. Only configured opt-in user-keyed free-tier routes and public free endpoints are attempted. Billing cannot be guaranteed by software alone: account owner must ensure any pay-as-you-go is disabled.
4. HTTP 429 never causes an immediate same-provider retry. Default quarantine 30s; capped Retry-After may extend quarantine to one hour. 401/402/403/404 cooldown is one hour.
5. Transient HTTP 502/503/504 and timeouts permit at most two same-provider retries after 1s and 2s respectively, then fail over. Total task dispatch budget is 60s, plus bounded Java HTTP transport and callback overhead.
6. Provider-route telemetry contains route alias, sanitized HTTP category, attempt and cooldown only; never prompt contents, API tokens or raw HTTP error bodies.
7. If all remote routes fail, the user sees an honest limited offline-template offer when the intent matches exactly and Termux is available. No automatic file write without approval.
8. The app may operate without Termux for simple Android intents, but Termux file synthesis needs user-enabled Termux integration.

## Engineering tests

Java JUnit: simulated 429 with 75s Retry-After, transient 503 requiring 1s/2s waits then switching, time budget exhaustion under repeated fake 30s timeouts, 404 quarantine, monotonic health scoring, and deterministic safe offline fallback intent recognition. Node regression checks verify Java files are wired to live M5 app, provider families retained and support tool consent. No real provider API keys are placed in CI.

## Manual device acceptance

- Install `com.musabgpt.m5.mesh10` alongside M5 0.9 on Samsung. Confirm prior app unaffected.
- Connect Termux, request a simple code task, verify selected provider and status events. Open AEOS tab to inspect route scores and cooldown.
- Configure **Groq Free** key and select it. Trigger a transient failure / 429 using a separately controlled test gateway to verify the circuit and fallback, rather than deliberately exhausting paid or free real quotas.
- Check selected keyed free provider failure does not leak its token into logs and that an unauthorized endpoint cannot receive an official key.
- Simulate all providers down; request an HTML calculator. Confirm the offline project template appears only after your approval, the generated project exists and its actual reported syntax validation is accurate.
- Attempt an unsupported complex app request with all providers down. Confirm M5 truthfully reports it cannot complete it instead of presenting a generic template as success.
- Verify Cancel/Stop and offline screen transitions, IPv4/IPv6 network changes, and Android process restarts. These paths need physical-device validation.

## Not guaranteed

Unlimited free calls, a 150ms end-to-end network handoff, perfect token accounting, entirely RAM-only key storage, a model that can complete any goal offline, and zero crashes under Android's lifecycle/process kills.
