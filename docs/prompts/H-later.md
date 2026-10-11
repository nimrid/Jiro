# Workstream H: later phases (do not start before B-G are stable and the program is audited)
1. AUTO-INVEST: requires on-chain price bounds. Design doc first: a keeper may swap vault USDC only through audited CPI with
   on-chain minOut derived from an oracle, an allowlisted output mint set, per-day limits, expiry, and one-tap revocation by the
   owner. Threat-model it before any code.
2. CARD ROUND-UPS: provider shortlist (Immersve for self-custody funding, Rain, Bridge/Stripe) needs program approval and a sponsor
   bank; sandbox access is separate from production. Implement `Card` RoundUpSource behind a feature flag with a provider-agnostic
   interface; never mix card data into the app without PCI scoping.
3. GEO/KYC: implement EligibilityGate for real (jurisdiction rules for tokenized securities) before any public launch.
4. Per-user streak/notifications, multi-vault goals (vault_id already supports it), and optional Backpack exchange RFQ fallback.
