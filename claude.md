# CLAUDE.md

## Product
Spare-change investing, crypto-native, for the Solana Seeker (Android, Seed Vault, Mobile Wallet Adapter).
The user pays in USDC. The app rounds the payment up and moves the difference into the user's own
on-chain vault ("the jar") in the SAME transaction. When the jar is ready, the user taps once and the
app buys ONE tokenized stock (the most underweight in their basket) straight into their own wallet.
- Non-custodial: no pooled funds, no exchange account, no keeper holding money.
- Savings is not a feature: no yield, no goals. The jar is a staging box, labeled "waiting to invest".
- Round-up sources: InAppPay (v1), ObservedTransfers (outgoing USDC transfers from the wallet), Card (future stub).

## Stack
- Android: Kotlin, Jetpack Compose, MWA 2.x. Modules: `:core` (pure Kotlin/JVM, no Android deps), `:app`.
- On-chain: Anchor (Rust). Service: Rust (axum). Discovery tooling: Rust.
- Clusters: localnet and devnet for dev, mainnet for real tokens. Tokenized stocks exist on mainnet only
  (Backpack Securities issues them as SPL tokens; Token or Token-2022 program, to be verified on-chain).
- Never use a program ID, mint, or endpoint from memory. Use constants from official crates/docs and cite the source in a code comment.

## Vault program (Anchor): FOUR instructions
PDA: seeds [b"vault", owner, [vault_id u8]]. State: owner, mint, vault_id, max_balance, bump.
Vault token account = ATA of (mint, config PDA). HARD_MAX_BALANCE = 100 USDC raw (verify mint decimals == 6 at init).
Config is immutable after init.
- init_vault(vault_id, max_balance): owner signs and pays; creates config and vault ATA.
- deposit(amount): owner signs; transfer_checked owner ATA -> vault ATA; vault_balance + amount <= max_balance.
- withdraw(amount): owner signs; destination must be owner's token account of the same mint.
- close_vault(): owner signs; requires balance 0; closes vault ATA and config; rent returns to owner.
- NO sweep, NO keeper, NO destination, NO threshold on-chain. The threshold is an app rule.
- The cap guards the program path only. Third parties can transfer tokens directly into the vault ATA
  (harmless; the owner can still withdraw). Document this.
- Cargo feature `mock-swap` adds mock_init, mock_set_price, mock_swap for devnet/localnet ONLY.
  Cargo feature `mainnet` + `mock-swap` together must `compile_error!`. A script fails CI if the
  mainnet IDL contains any `mock_*` instruction.
- Upgrade authority is the trust root. Disclose it. Plan to revoke or multisig before real users.

## Flows
- SETUP (one approval): init_vault + create ATAs for each basket stock (create-idempotent with the
  CORRECT token program per mint). Show total refundable rent in SOL.
- PAY (one approval): [create recipient ATA idempotent] + [USDC transfer_checked to recipient, plus Solana Pay
  `reference` keys as read-only non-signer keys, plus Memo if provided] + [vault.deposit(roundUp)].
- INVEST (one approval): [vault.withdraw(amount) -> owner USDC ATA] + [Jupiter swap USDC -> chosen stock].
  Versioned (v0) transaction with lookup tables. If it cannot fit in 1232 bytes: fall back to two sequential
  transactions (withdraw, confirm, then swap), two approvals. Never send dependent transactions in parallel.
- WITHDRAW: withdraw(amount) any time. CLOSE: withdraw all, then close_vault.
- Check MWA wallet capabilities for supported transaction versions before building v0.

## Round-up rules (config; defaults in brackets)
step in {0.50, [1], 2}; multiplier in {[1], 2, 5}; whole-dollar payments round up 0 [default] or one step.
roundUp = ((step - amount mod step) mod step) * multiplier, clamped to jar remaining capacity.
Skip when < 0.01 USDC. Integer base units only (USDC = 1e6). Never floats for money.
Invest becomes available when jar >= max($5, 100 x estimatedFixedCostUsd), so fixed costs stay near 1% or less.
Jar full: prompt to invest or withdraw. Never submit a transaction that will fail the cap.

## Asset universe: Backpack Securities tokenized stocks (PRIMARY)
Pipeline: Backpack public GET /api/v1/assets (Solana tokens with contractAddress) -> on-chain verification
(getMultipleAccounts, jsonParsed, public mainnet RPC for discovery; keyed RPC for service/app) -> provenance
-> extension policy -> liquidity -> catalog.
Entry fields: symbol, name, mint, tokenProgram, decimals, extensions[], authorities,
provenance{listedByBackpack, issuerMatch}, policy{OK|WARN|BLOCK, reasons[]},
buyable{bool, reasons[]}, liquidity{impactBpsAt5Usd, impactBpsAt25Usd, routeFound, asOf},
marketStatus, ataRentLamports, extensionFingerprint.
- SHOWN vs BUYABLE (critical): the picker shows EVERY verified, provenance-checked stock. Buyable is a
  separate flag with plain-language reasons. Never filter a stock out of the UI for liquidity, market
  hours, or policy WARN. Non-buyable stocks can be added to baskets; the allocation engine skips them until
  buyable. The UI must never render an empty catalog: if live fetch and snapshot both fail, show an error with
  retry and the snapshot age.
- Provenance trust root: pinned issuer authority keys (learned in workstream A, CONFIRMED BY THE DEVELOPER).
  New mints are accepted automatically only if listed by Backpack AND issuer match AND policy not BLOCK.
- Extension policy lives in config (policy.toml), not code. Defaults: metadata OK; permanent delegate, pausable,
  freeze authority WARN with user disclosure; scaled-UI-amount changes value math (value = raw x multiplier);
  transfer fee reduces expected output; non-transferable, default-frozen accounts, unknown extensions BLOCK;
  transfer hook needs a one-time review per hook program, then pinned.
- Catalog delivery: service signs catalog JSON (ed25519); app verifies the signature with an embedded pubkey,
  ships catalog_snapshot.json, and BEFORE EVERY FIRST BUY re-fetches the mint account on-chain and checks owner
  program, decimals, and extensionFingerprint. Mismatch blocks the buy with reason "token changed since verification".
- Ticker presets: presets.json holds TICKERS only (search keys). Mints always come from the verified catalog.
  If a ticker maps to multiple tokens or none, drop it and say so.

## Stock selection engine (pure Kotlin, property-tested)
Basket: max 5 holdings, target weights in bps summing 10_000, min 10% each, 5% steps.
Presets: Index, Big Tech, Build Your Own.
For buy amount A: portfolioValue V = sum(value_i); deficit_i = max(0, target_i * (V + A) - value_i) over
BUYABLE holdings; pick the largest deficit; ties -> larger target; if all deficits < $1 -> largest target.
One buy = one swap. Returns a Reason object (inputs, deficits, skipped assets and why) for the "Why this stock?" UI.
Value uses price x raw x scaled-UI multiplier / 10^decimals.

## Safety (non-negotiable)
- SwapTxValidator before any wallet prompt: fee payer == user; program allowlist (ComputeBudget, System only for
  rent/wSOL, Token, Token-2022, ATA, Memo, Jupiter, our vault program); no approve/setAuthority/closeAccount on
  user accounts except temp wSOL; then simulateTransaction (sigVerify=false, return accounts) and assert: user USDC
  delta == -amount exactly, output delta >= quoted minOut, no other token balance decreases, SOL decrease <= fees +
  declared rent.
- Slippage cap 100 bps (config). Quotes expire in 30s; refresh before signing.
- Show mint short-address and a verified mark; every amount links to an explorer.
- Copy: these are tokenized securities, not shares. Not financial advice. Unaudited; balances capped.
- `EligibilityGate` interface stub (always allow) so geo/KYC gating can be added later.
- No keys or API secrets in the app. Cluster is a build config. MWA auth token cached encrypted (Keystore).

## Threat model (keep updated in docs/SECURITY.md)
Malicious/compromised service (hence validator + signed catalog + pre-buy re-verification); fake token with
real ticker (mint provenance); thin liquidity or sandwiching (impact cap, minOut, slippage cap); issuer pause or
freeze (disclosed); wrong token program for ATA (derive per mint); program bug (caps, negative tests, unaudited
label); leaked RPC/API keys (service only); mock code reaching mainnet (feature gate + CI check).

## Abstractions
SwapProvider{quote, buildInstructions}: JupiterSwapProvider (mainnet), MockSwapProvider (devnet/localnet).
PriceProvider; CatalogRepository (snapshot + service + on-chain recheck); RoundUpSource: InAppPay, ObservedTransfers,
Card (stub); EligibilityGate; ClusterConfig.

## Known unknowns: resolve in workstream A and E, and report before building on them
1. Which Backpack tickers have Solana mints; token program; decimals; extensions; transfer hooks.
2. Jupiter: current endpoints, auth/API key needs, rate limits, quote impact per candidate.
3. Whether tokenized-stock transfers are restricted (hooks, freeze, allowlists).
4. MWA: supported transaction versions on the Seeker; multi-transaction behavior.
5. Whether a maintained Kotlin Solana library supports v0 messages + lookup tables (else hand-roll with golden tests).
Backpack's exchange RFQ API is a FALLBACK path only (docs/BACKPACK_FALLBACK.md).

## Hard rules
Integer money math. Never commit secrets (`.env.example` only). Failing test first for round-up math, allocation
engine, instruction bytes, PDA derivation, tx validator, policy. No new dependency without saying why. Match
installed toolchain versions. Small commits. After each task report what you verified, what failed, what is untested.
Ask one specific question rather than guessing.

## Test environments and funds budget
Unit and property tests (no network) -> localnet with program + mock mints (`anchor test`) -> devnet app run with
MockSwapProvider and real wallet -> optional mainnet fork (verify Surfpool or solana-test-validator --clone) ->
ONE mainnet smoke test with a dedicated throwaway wallet holding <= $25 total.

## Layout
/program  /crates/catalog-core  /tools/discovery  /service  /android (:core, :app)  /scripts
/docs (DESIGN.md, SECURITY.md, PROGRAM_INTERFACE.md, DISCOVERY.md, POLICY.md, catalog_snapshot.json,
presets.json, BACKPACK_FALLBACK.md, fixtures/, prompts/)

## Definition of done (any workstream)
Tests green and listed; negative cases covered; no TODOs hiding failures; docs updated; final report with
verified / failed / untested / surprises.