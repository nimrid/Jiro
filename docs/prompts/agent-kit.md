# AGENT KIT: Spare-change stock investing for Solana Seeker

## How to use
1. Create the repo layout from CLAUDE.md. Copy each `FILE:` section below to the path shown.
2. One agent session per workstream. Kickoff line for any session:
   "Read CLAUDE.md and docs/prompts/<X>.md, then follow its Process section."
3. Order and dependencies:
   - Run A and B first, in parallel.
   - C starts its pure-logic parts immediately and its program-facing parts once B's fixtures exist.
   - D starts once C's interfaces exist (use fakes).
   - F needs A. E needs C and A. G needs C. H is last and optional.
4. Anything the agent cannot verify, it must report instead of guessing. Mints, program IDs, endpoints and API parameters always come from fetched data or official docs, never from memory.

Changes from earlier chat versions:
- The program now has FOUR instructions (init_vault, deposit, withdraw, close_vault). close_vault is new, so the "refundable setup deposit" shown in the UI is actually true.
- Remote catalog data is no longer "narrow only". The catalog is signed by the service, and the app re-verifies the mint on-chain before every first buy.
- Stocks are never hidden from the picker. "Shown" and "buyable" are separate flags.

---

# FILE: CLAUDE.md

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

---

# FILE: docs/DESIGN.md

## First principles
At any moment the user asks: where is my money, what happens next, and can I get out?
1. Make the invisible mechanism visible. A round-up is tiny with a delayed payoff, so show the split live,
   let the jar fill, and let the pour into a stock be a moment.
2. Remove decisions, not control. A style chosen once, one automatic pick per buy, and "Why this stock?" one tap away.
3. Honest numbers. Contributions only, no projected returns. Fees and price impact before signing.
4. Verifiable. Every amount links to the chain. Withdraw is always visible.

## Feel
A calm ledger crossed with a coin jar. Dark-first (Seeker OLED). Not a trading terminal, not a bank. Numbers are the hero.

## Tokens
Ink #0E1013 (bg), Surface #161A1F, Line #232932, Text #F2EFE8, Muted #8A93A0, Amber #FFB84D (waiting/pending),
Mint #5CE0B0 (owned/up), Coral #FF6B5B (error), Sky #7FB5FF (verified/links). One accent per screen.
Type: Space Grotesk (headings), Inter with tabular numerals (body/numbers), JetBrains Mono (addresses). Bundle with
OFL license files. 8dp grid, 16dp radii, 48dp touch targets.
Motion: drop falls 300ms, jar level eases 500ms, pour 600ms, haptic tick on each drop. "Reduce motion" = fades only.
Contrast AA minimum. Every interactive element has a TalkBack label. Support large font scale without clipping.

## Navigation
Bottom bar: Home, Portfolio, Activity. Centered Pay button. Settings via avatar. Single-activity, Compose Navigation,
`solana:` Solana Pay deep link intent filter.

## Screens
1. Onboarding (first value under 60s): connect wallet -> pick a style (3 cards, each previews its basket) -> set rules
   (round-up step, boost, jar cap; defaults pre-selected) -> create jar (one signature; shows refundable setup deposit
   in SOL). "Try demo data" link.
2. Home: Canvas jar filling toward the threshold, amber liquid. Under it: "$3.50 of $5.00 -> next: VOO" (or the next pick).
   Last 3 drops as tiny receipts. Primary button morphs Pay -> "Invest $5.20". Persistent "Withdraw anytime" chip.
   If a larger-deficit stock was skipped: one line "Skipped TSLA: paused".
3. Pay: amount entry or Solana Pay QR scan. Live split bar [ Pay $33.50 | +$0.50 to jar ], fee line beneath.
   Success: the drop animates into the jar.
4. Invest ("the pour"): chosen stock monogram, estimated tokens, price impact, total cost, quote countdown, market
   open/closed badge, "Why this stock?" expander (shows the underweight math). Confirm -> MWA.
5. Portfolio: treemap. Tile size = current weight, dashed outline = target weight, next-buy tile pulses amber.
   Tap a tile: units, value, contributions, mint, explorer link.
6. Pick: searchable list of ALL verified stocks (monogram, ticker, price, 24h change, liquidity badge Deep/OK/Thin,
   market status). Filter chips: All, Buyable, Index, Tech. Status chip per row: Buyable, Thin liquidity, Paused,
   Needs review. Detail sheet: issuer, mint, token program, plain disclosures ("issuer can pause transfers",
   "issuer can move tokens"). Add-to-basket with weight stepper and live stacked bar (sum 100%, max 5, min 10%).
7. Activity: one feed (round-up, owed, deposit, invest, withdraw); each row opens the explorer.
8. Settings/Safety: round-up step, multiplier, cap, withdraw all, close jar, cluster, disclosures, "unaudited" notice.

## States (design and build every one)
First run; catalog loading (skeleton rows, never spinner-only); catalog failed (error + retry + snapshot age);
jar empty; jar full; insufficient USDC; insufficient SOL; wallet declined; quote expired; thin liquidity (warn/block
buy, stock stays visible); market closed (warn); token changed since verification (blocked); offline (snapshot banner);
transaction pending / confirmed / failed (with explorer link); owed vs in-jar (never claim money is set aside when
it is only owed).

## Microcopy rules
Plain words. "In your jar" not "vault balance". "Owed" for round-ups not yet moved. Always say "tokenized stock".
No exclamation marks, no promises about returns.

## Do not
Show projected returns. Use stock logos (ticker monograms only). Hide fees. Use purple gradients or stock-photo
imagery. Use a bare spinner where progress can be shown.

---

# FILE: docs/prompts/A-discovery.md

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

---

# FILE: docs/prompts/B-program.md

# Workstream B: Anchor vault program
Read CLAUDE.md. Check `anchor --version`, `solana --version`, `rustc --version` and match the scaffold. Do not upgrade
toolchains. This program will hold real USDC: smaller and stricter beats clever.

## Spec
State VaultConfig: owner, mint, vault_id(u8), max_balance(u64), bump. Seeds [b"vault", owner, [vault_id]].
Vault token account: ATA(mint, config PDA), PDA is authority. Classic Token program only for the vault; reject Token-2022.
Const HARD_MAX_BALANCE = 100_000_000 (100 USDC at 6 decimals). Require mint.decimals == 6.
Instructions (checked arithmetic, explicit constraints on every account, transfer_checked everywhere):
1. init_vault(vault_id, max_balance): 0 < max_balance <= HARD_MAX; creates config + vault ATA; re-init fails.
2. deposit(amount): owner signer; amount > 0; balance + amount <= max_balance; wrong mint fails.
3. withdraw(amount): owner signer; 0 < amount <= balance; destination token account owner == signer and mint == config.mint.
4. close_vault(): owner signer; balance must be 0; close vault ATA via PDA-signed CPI, close config with `close = owner`.
Events: Deposited, Withdrawn, Closed (owner, amount, vault_id). Custom error enum, one code per failure.
Feature `mock-swap` (devnet/localnet only): MockConfig PDA [b"mock", stock_mint] with admin, price (micro-USDC per whole
token), treasury ATA (stock tokens), sink ATA (USDC). Instructions mock_init, mock_set_price (admin only), mock_swap
(owner signer: USDC -> stock tokens at price, min_out check). Support classic and Token-2022 stock mints via token_interface.
`mainnet` + `mock-swap` => compile_error!. scripts/check-no-mock-in-mainnet.sh inspects the mainnet-built IDL and fails if any
mock_* instruction exists.

## Security checklist (state how each is verified)
Signer checks; owner/mint/authority/seed constraints; vault ATA authority == config PDA and mint == config.mint; a different
user's config cannot be paired with my vault ATA; no remaining_accounts; no arbitrary CPI; stored bump; u64::MAX amounts
never overflow or panic; cap is documented as program-path only.

## Tests (negative first)
init: zero cap, cap over hard max, wrong decimals, Token-2022 mint, re-init. deposit: zero, exactly cap, cap+1, wrong mint,
non-owner. withdraw: partial, full, non-owner, over balance, destination owned by someone else, wrong-mint destination.
close: non-zero balance fails, success returns rent, non-owner fails. mock: swap math, min_out failure, non-admin price set,
Token-2022 stock mint. Use the project's installed test tooling (verify whether LiteSVM or anchor mocha fits the installed versions).

## Deliverables
1. Program + passing tests; devnet deploy (report program ID); build for mainnet feature passes the no-mock check.
2. docs/PROGRAM_INTERFACE.md: program ID, PDA seeds, per-instruction account list in exact order with signer/writable
   flags, 8-byte discriminators (hex; sha256("global:<snake_name>")[..8]), Borsh arg layouts, error codes, event layouts,
   and the cap caveat.
3. docs/fixtures/vault_vectors.json: for a fixed owner pubkey and vault_ids 0 and 1: vault PDA, bump, vault ATA, and exact
   instruction-data hex for each instruction with sample args. Android tests assert against it.
4. scripts/ CLI for init/deposit/withdraw/close (+mock when feature on) against devnet.
5. IDL committed. docs/SECURITY.md updated (upgrade authority, caps, known limits).

## Process
Plan in under 15 lines + assumptions, wait for go-ahead. Final report: verified / failed / untested.

---

# FILE: docs/prompts/C-android-core.md

# Workstream C: Android core (:core pure Kotlin + wiring in :app)
Read CLAUDE.md, docs/PROGRAM_INTERFACE.md, docs/fixtures, docs/DISCOVERY.md (as they appear). Pure logic goes in `:core`
so it is unit-testable on the JVM.

## Library decision (report before coding)
Evaluate maintained Kotlin Solana libs and the MWA client artifact (verify coordinates/versions on Maven Central).
Criteria: v0 messages + address lookup tables, PDA derivation, ed25519, Borsh, active maintenance. If none supports v0 + ALT,
hand-roll message compilation (account ordering: signer-writable, signer-readonly, nonsigner-writable, nonsigner-readonly;
compact-u16 lengths; ALT loading only for non-signer, non-program accounts) with golden tests against Rust-generated fixtures.

## Build in this order, each with tests
1. Money.kt: Long base units, parse/format USDC, no floats.
2. RoundUp.kt: rules from CLAUDE.md. Tests: 33.50->0.50, 33.01->0.99, 33.00->0 (default), 0.01->0.99, 2.00 step with boost,
   clamp to jar capacity, skip < 0.01. Property tests: result in [0, step*mult], (amount+roundUp) divisible by step when mult==1.
3. Pda.kt + Ata.kt: derive vault PDA and ATAs (seeds [owner, tokenProgramId, mint]; allow off-curve owner for PDA). Assert
   against fixtures. Works for Token and Token-2022.
4. Instructions.kt: init_vault, deposit, withdraw, close_vault; SPL transfer_checked; create-ATA-idempotent; Memo; compute
   budget. Golden tests: data hex equals fixtures.
5. SolanaPay.kt: parse `solana:` URIs (recipient, amount, spl-token, reference[], label, message, memo) with strict validation;
   unsupported/ambiguous input is rejected with a typed error.
6. PaymentTxBuilder.kt, InvestTxBuilder.kt (v0 compose: withdraw + swap instructions + ALTs, size check < 1232 bytes, fallback
   to two-transaction plan), SetupTxBuilder.kt (init_vault + basket ATAs, batching to fit size).
7. Catalog models + signature verification (ed25519 over canonical JSON) + CatalogRepository: snapshot -> service -> cache; never
   returns an empty list while a snapshot exists; exposes snapshot age.
8. AllocationEngine.kt: spec in CLAUDE.md; returns Reason object. Table + property tests (empty portfolio, ties, all-ineligible,
   scaled-UI multiplier, rounding, 1-asset basket, deficits exceeding buy amount).
9. BasketValidator.kt: max 5, bps sum, min weight, steps; preset resolution from presets.json + catalog (drop ambiguous/missing
   tickers, report why).
10. PreBuyVerifier.kt: fetch mint account, compare owner program, decimals, extensionFingerprint to the catalog entry.
11. SwapTxValidator.kt: exactly as in CLAUDE.md safety, including simulation-based balance delta assertions. Tests with crafted
    malicious transactions (extra transfer out, authority change, wrong fee payer, unknown program, lower minOut).
12. Preflight.kt: USDC, SOL for fees and rent, cap, quote age, catalog freshness.
13. MwaRepository: connect, reauthorize, signAndSend, confirmation at `confirmed` honoring lastValidBlockHeight; distinct states
    for no wallet, declined, timeout, unsupported tx version.
14. Interfaces + fakes: SwapProvider (+MockSwapProvider), PriceProvider, RoundUpSource, EligibilityGate, ClusterConfig,
    FakeCatalogRepository. Local storage (DataStore/Room) for baskets, settings, activity cache; secrets only in Keystore.
15. ActivityRepository: derive feed from chain history (signatures for wallet/vault, parse program events) + local cache.

## Done when
All tests green; devnet run: one MWA approval produces a transaction with payment + vault.deposit and the jar balance rises;
MockSwapProvider invest works end to end on devnet; report real-wallet vs fake-wallet verification honestly.

## Process
Plan in under 15 lines + assumptions, then proceed unless a decision blocks you.

---

# FILE: docs/prompts/D-android-ui.md

# Workstream D: Android UI (Compose)
Read CLAUDE.md and docs/DESIGN.md fully. Build against FakeCatalogRepository + MockSwapProvider + fake wallet so UI never
blocks on other work. MVVM, StateFlow, coroutines, single activity, strings externalized.

## Build
- Theme from DESIGN.md tokens; bundle Space Grotesk, Inter (tabular numerals), JetBrains Mono with license files.
- JarView (Canvas): liquid level animation, surface wave, drop animation into the jar, haptic tick; reduced-motion fallback.
- TreemapView (Canvas/Compose layout): size by weight, dashed target outline, pulsing next-buy tile, tap to detail.
- SplitBar (live pay/round-up split), StackedWeightBar (basket editor with validation), Monogram (ticker avatar, deterministic
  color from ticker), StatusChip, LiquidityBadge, MarketBadge, QuoteCountdown.
- Screens: Onboarding (4 steps + demo data), Home, Pay (+QR scan), Invest sheet, Portfolio, Pick (all verified stocks, filters,
  detail sheet with disclosures), Activity, Settings/Safety.
- Every state in DESIGN.md has a preview and a UI test: loading skeletons, catalog failed with retry and snapshot age, jar full,
  insufficient funds, declined, expired quote, thin liquidity (buy blocked but stock visible), market closed, token changed,
  offline, pending/confirmed/failed, owed vs in-jar.
- "Why this stock?" renders the AllocationEngine Reason object, including skipped assets and reasons.
- Accessibility: TalkBack labels, 48dp targets, font scale 200% screenshots, AA contrast check.
- Screenshot tests for every screen and state (verify Paparazzi/Roborazzi fit the toolchain).

## Rules
No business logic in composables. No projected returns, no logos, no hidden fees. Never show an empty stock list.

## Done when
Demo mode walks through the whole flow with no network; all state previews exist; accessibility checks pass; final report lists
anything you could not verify on a real Seeker.

---

# FILE: docs/prompts/E-jupiter.md

# Workstream E: Jupiter integration and mainnet smoke test
1. READ current official Jupiter developer docs. Report: base URLs, auth/API key requirements, rate limits, quote and
   swap-instructions endpoints, parameter names, program ID (record the doc URL in a code comment). Wait for confirmation if a
   paid plan or key is required.
2. Implement JupiterSwapProvider: ExactIn USDC -> stock mint, slippage from config, retrieve instructions + ALT addresses, fetch ALT
   accounts, compose with vault.withdraw into one v0 transaction (InvestTxBuilder), respect compute-budget guidance from the API.
3. Quote policy: 30s expiry, refresh before signing, reject price impact over the cap, reject routes whose output mint program does
   not match the catalog, account for transfer-fee and scaled-UI extensions in expected output.
4. Run SwapTxValidator on every built transaction; add contract tests with recorded fixtures plus a live test behind a flag.
5. Failure handling: route disappears, blockhash expiry, size overflow -> two-transaction fallback, simulation mismatch -> abort
   with the reason, user decline, partial failure (withdraw succeeded, swap failed) -> funds are in the user's wallet; show a
   clear recovery state with a retry.
6. SMOKE TEST (mainnet): throwaway wallet funded <= $25. Pay with round-up -> deposit -> invest about $3-5 into one buyable stock
   with Deep liquidity. Record signatures, fees, price impact, and realized vs quoted output in docs/SMOKE_TEST.md.
Final report: verified / failed / untested.

---

# FILE: docs/prompts/F-service.md

# Workstream F: catalog service (Rust, axum)
Reuse `crates/catalog-core`. Keys live here only: SOLANA_RPC_URL, JUPITER_API_KEY (if needed), CATALOG_SIGNING_KEY.
- Background jobs: assets fetch (hourly), on-chain verification, liquidity probes ($5/$25, throttled), market sessions (daily),
  prices (cached 10-30s).
- Endpoints (document in docs/service-openapi.yaml): GET /v1/catalog (signed JSON + version + generatedAt), GET /v1/prices,
  GET /v1/quote and POST /v1/swap-instructions (Jupiter proxy that hides the key; the app still validates everything),
  GET /healthz.
- Catalog signing: canonical JSON, ed25519, publish the public key for embedding in the app. Never serve unsigned data.
- Never drop entries: serve BLOCK and not-buyable entries with reasons. Serve last-good if a refresh fails and expose its age.
- Rate limiting, request size limits, timeouts, structured logs with secrets and wallet addresses redacted, no user tracking.
- Tests: policy engine reuse, signature round-trip, stale-serving behavior, proxy error mapping, contract tests with the Kotlin client
  fixtures. Dockerfile and .env.example.
Final report: verified / failed / untested.

---

# FILE: docs/prompts/G-observed-transfers.md

# Workstream G: round-ups on outgoing transfers (ObservedTransfers)
Constraint: the app cannot add a deposit to someone else's transaction, so this is NOT atomic. The UI must say "owed", never
"in your jar", until settled.
- Detection (on-device, no server learns the wallet): periodic WorkManager polling plus foreground refresh of the user's USDC
  ATA signatures; getTransaction jsonParsed; count only `finalized` transactions.
- Qualify: SPL transfer/transferChecked where source is the user's USDC ATA and authority is the user, destination owner is a
  different wallet. Exclude: self transfers, transfers to the vault ATA, transactions involving Jupiter or our program, swaps, and
  anything older than the baseline timestamp chosen at opt-in.
- Ledger (Room): unique key = transaction signature (idempotent), amount, computed roundUp, status owed/settled. Cap owed total.
- Settle: one signature `vault.deposit(sum)` (batch to fit size); optionally fold into the next PAY or INVEST. Local notification
  when owed >= $1 or the jar is ready (Android 13+ POST_NOTIFICATIONS flow).
- Tests: parser fixtures from real devnet/mainnet transactions, idempotency, exclusion rules, reinstall behavior (baseline reset),
  cap handling.
Final report: verified / failed / untested.

---

# FILE: docs/prompts/H-later.md

# Workstream H: later phases (do not start before B-G are stable and the program is audited)
1. AUTO-INVEST: requires on-chain price bounds. Design doc first: a keeper may swap vault USDC only through audited CPI with
   on-chain minOut derived from an oracle, an allowlisted output mint set, per-day limits, expiry, and one-tap revocation by the
   owner. Threat-model it before any code.
2. CARD ROUND-UPS: provider shortlist (Immersve for self-custody funding, Rain, Bridge/Stripe) needs program approval and a sponsor
   bank; sandbox access is separate from production. Implement `Card` RoundUpSource behind a feature flag with a provider-agnostic
   interface; never mix card data into the app without PCI scoping.
3. GEO/KYC: implement EligibilityGate for real (jurisdiction rules for tokenized securities) before any public launch.
4. Per-user streak/notifications, multi-vault goals (vault_id already supports it), and optional Backpack exchange RFQ fallback.
