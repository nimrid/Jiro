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
