# Workstream E: Jupiter Integration & Smoke Test Report (`docs/SMOKE_TEST.md`)

## 1. Executive Summary

Workstream E implements the **Jupiter Swap API V2** integration for JIRO, enabling atomic composition of on-chain vault withdrawals with Jupiter swaps into a single Solana v0 versioned transaction using Address Lookup Tables (ALTs).

### Key Implementation Deliverables:
1. **[JupiterSwapProvider](file:///Users/hng/Documents/antigravity/JIRO/android/core/src/main/java/com/jiro/core/JupiterSwapProvider.kt)**: Pure Kotlin/JVM client connecting to `https://api.jup.ag/swap/v2/build` using API key authentication, fetching raw instructions (`setupInstructions`, `swapInstruction`, `cleanupInstruction`, `computeBudgetInstructions`), resolving ALTs, and enforcing security caps.
2. **[InvestTxBuilder Compositions](file:///Users/hng/Documents/antigravity/JIRO/android/core/src/main/java/com/jiro/core/TransactionBuilders.kt)**: Composes `vault.withdraw` + Jupiter instructions + ALTs into a single atomic v0 transaction. Implements two-transaction fallback (`withdraw` then `swap`) if wire size exceeds 1,232 bytes.
3. **[Quote Policy](file:///Users/hng/Documents/antigravity/JIRO/android/core/src/main/java/com/jiro/core/InvestFlowModels.kt)**: 30-second quote expiry TTL, max 100 bps price impact cap, max 100 bps slippage cap, and output mint program verification against the verified catalog.
4. **[SwapTxValidator](file:///Users/hng/Documents/antigravity/JIRO/android/core/src/main/java/com/jiro/core/SwapTxValidator.kt)**: Security validator updated to permit temp wSOL account cleanup while forbidding unauthorized `closeAccount`, `approve`, or `setAuthority` on user assets.
5. **[Address Lookup Table Compilation](file:///Users/hng/Documents/antigravity/JIRO/android/core/src/main/java/com/jiro/core/MessageV0.kt)**: Completed `MessageV0.compile` ALT resolution to compress 40+ Jupiter routing accounts into 1-byte lookup indices.

---

## 2. Test Verification & Empirical Results

All **75 unit and contract tests** in `:core` pass (`./gradlew :core:test`).

### 2.1 Recorded Contract Tests
- Tested `parseBuildResponse` against recorded mainnet JSON fixture (`fixtures/jupiter_build_v2.json`):
  - Correctly resolved 2 Address Lookup Tables with 252 addresses each.
  - Successfully parsed 46 accounts on Jupiter Aggregator (`JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4`).
  - Wire transaction size of atomic composed `[withdraw + swap]` transaction: **847 bytes** (well under the 1,232-byte limit).
  - Passed `SwapTxValidator.validatePlan` static inspection.

### 2.2 Live Jupiter API Verification
- Queried live `https://api.jup.ag/swap/v2/build` using the configured API key (`7533d7b5...`):
  - Route: USDC (`EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v`) $\to$ SOL (`So11111111111111111111111111111111111111112`)
  - Status: HTTP 200 OK
  - Quoted Input: 5.00 USDC (`5,000,000` base units)
  - Quoted Output: ~0.0456 SOL (`45,601,148` lamports)
  - Price Impact: 0.00% (0 bps)
  - Plan compiled and verified successfully against security allowlist.

### 2.3 Policy & Invariant Tests
- **Quote Expiration**: Quotes older than 30s throw `QuoteExpiredException`.
- **Price Impact Cap**: Quotes with >100 bps price impact throw `PriceImpactExceededException`.
- **Program Mismatch**: Routes yielding non-Token-2022 programs for catalog assets throw `OutputProgramMismatchException`.
- **Transfer Fee Deductions**: Calculated accurately based on token extension parameters.
- **Size Overflow Fallback**: Successfully tested two-transaction fallback (`TransactionPlan.TwoStep`) when transaction exceeds 1,232 bytes; both steps pass validator.

---

## 3. Mainnet Tokenized Stock Liquidity Status

When probing Jupiter for Backpack Securities tokenized stocks on Solana mainnet (e.g. `AAPL.US`, `MSFT.US`):
- **Response**: `{"error": "No routes found"}`
- **Reason**: As documented in Workstream A ([docs/DISCOVERY.md](file:///Users/hng/Documents/antigravity/JIRO/docs/DISCOVERY.md)), Backpack Securities tokenized equities are not currently listed in public Solana AMM pools (e.g., Orca, Raydium, Meteora).
- **Architecture Handling**: 
  - The app displays verified stocks from the catalog snapshot and checks route liquidity.
  - If a route is absent, `NoRouteFoundException` is surfaced gracefully with a plain-language explanation.
  - Backpack RFQ endpoint serves as fallback path ([docs/BACKPACK_FALLBACK.md](file:///Users/hng/Documents/antigravity/JIRO/docs/BACKPACK_FALLBACK.md)).

---

## 4. Final Workstream E Status Report

- **Verified**:
  - Live Jupiter Swap V2 `/build` API connectivity and authentication with API key.
  - Parsing of swap, setup, cleanup, compute budget instructions, and blockhash metadata.
  - Address Lookup Table (ALT) compilation in `MessageV0` bringing wire size below 1,232 bytes.
  - `InvestTxBuilder` single-transaction composition and two-transaction fallback.
  - 30-second quote expiry, price impact cap, and output token program validation.
  - Static security rules in `SwapTxValidator` for Jupiter transactions.
  - 75/75 passing unit & contract tests in `:core`.
- **Failed**:
  - None.
- **Untested**:
  - On-chain mainnet settlement of a real stock purchase (awaiting liquidity pool seeding on Solana DEXes or Backpack RFQ bridge activation, and funded mainnet wallet).
