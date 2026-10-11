# Workstream A: discovery, on-chain verification, policy
Read CLAUDE.md. Build Rust crate `crates/catalog-core` (reused by the service) and CLI `tools/discovery`.
Never invent data. If a fetch fails, report the raw error and stop that step.

## Steps
1. DISCOVER: fetch Backpack GET /api/v1/assets (public). Save raw to docs/raw/assets.json. Extract Solana tokens with
   a contractAddress; keep symbol, mint, deposit/withdraw flags. If none, STOP and report the response shape.
2. VERIFY ON-CHAIN: getMultipleAccounts (batches of 100, jsonParsed) via SOLANA_RPC_URL (default public mainnet RPC,
   backoff on 429). Record per mint: owner program (Token or Token-2022, else flag), decimals, supply, mintAuthority,
   freezeAuthority, every extension with parsed config, TokenMetadata and update authority. Save docs/raw/mints.json.
3. PROVENANCE: cluster mints by mintAuthority and by metadata update authority. Report clusters and PROPOSE issuer keys.
   Do not pin anything; list candidates for developer confirmation.
4. POLICY: table-driven, config-driven (policy.toml + docs/POLICY.md): OK/WARN/BLOCK per extension, covering transfer
   hook (record hook program ids), permanent delegate, pausable, scaled-UI-amount (multiplier and scheduled changes),
   transfer fee (bps, max), default account state, non-transferable, confidential transfer, mint close authority,
   interest bearing. Unknown extension => BLOCK. Output counts per outcome and per extension.
5. LIQUIDITY: for each non-BLOCK mint, request Jupiter quotes for $5 and $25 USDC -> mint. Record routeFound,
   priceImpact, error text. Report API key needs and rate limits. Throttle.
6. ACCOUNT COST: associated token account size per mint (including required account-side extensions), rent-exempt
   lamports, and % of a $5 buy at current SOL price.
7. MARKET STATUS: fetch Backpack /stockSessions and /stockHolidays; document shapes and ticker->session mapping.
8. EXTENSION FINGERPRINT: define a stable hash over (owner program, decimals, sorted extension configs) used by the app
   for pre-buy re-verification. Document it in docs/DISCOVERY.md with test vectors.
9. PRESETS: propose docs/presets.json (tickers only) for Index and Big Tech from what actually exists.

## Outputs
docs/DISCOVERY.md (tables, counts, anomalies, "Decisions needed from the developer": issuer pins, hook programs),
docs/catalog_snapshot.json with EVERY verified entry including BLOCK and not-buyable ones with reasons,
docs/policy_results.json, docs/POLICY.md, policy.toml, docs/presets.json. Tests: extension parsing, policy table,
scaled-UI math, transfer-fee math, fingerprint vectors, snapshot schema round-trip.

## Process
Plan in under 15 lines + assumptions, wait for go-ahead. Final report: verified / failed / untested / surprises.
