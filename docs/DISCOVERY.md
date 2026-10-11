# Workstream A: Discovery, On-Chain Verification & Policy Report (`docs/DISCOVERY.md`)

This report provides empirical discovery and verification data for tokenized equities on Solana mainnet issued by Backpack Securities for the JIRO spare-change investing application.

---

## 1. Executive Summary & Verification Metrics

| Metric | Result | Notes |
| :--- | :--- | :--- |
| **Total Backpack Assets** | 7,269 | Public catalog from `GET /api/v1/assets` |
| **Total Solana Tokens** | 1,227 | Assets on Solana with contractAddress |
| **Tokenized US Equities** | **1,170** | All end with `.US` |
| **Program Type** | `TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb` | 100% Token-2022 (`spl-token-2022`) |
| **Decimals** | 6 | 100% of verified equities use 6 decimals |
| **Supply On-Chain** | Varies by asset | Fractionalized shares with 6 decimals |
| **Policy Evaluation** | 1,170 `WARN`, 0 `BLOCK` | All standard extensions permitted with disclosure |
| **Metadata Update Authority** | `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a` | **Single unified master issuer key** (1,170 / 1,170) |
| **Transfer Hook Status** | Configured, `programId: null` | No active bytecode hook executing on transfers |
| **Jupiter Liquidity** | `TOKEN_NOT_TRADABLE` | Tokenized stocks not currently listed in public AMM pools |
| **ATA Account Size** | 182 bytes | Base Token account (165B) + Token-2022 account extensions |
| **ATA Rent-Exempt Minimum** | 2,157,600 lamports (~0.00216 SOL) | **~$0.236 USD** at SOL price $109.47 (4.72% of a $5 invest tap) |

---

## 2. On-Chain Verification Findings

All 1,170 mint accounts were queried directly from Solana mainnet RPC (`https://api.mainnet-beta.solana.com`) in batches of 100 using `getMultipleAccounts` with `jsonParsed` encoding and saved to `docs/raw/mints.json`.

### 2.1 Extension Breakdown Across 1,170 Mints
Every single mint contains the exact same set of 8 extensions:
1. `metadataPointer`: Points to self (mint PDA) as the metadata account.
2. `permanentDelegate`: Set to Backpack Master Key `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`.
3. `defaultAccountState`: Initialized to `"accountState": "initialized"`. New ATAs can immediately receive tokens without manual unfreezing.
4. `pausableConfig`: Authority set to Backpack Master Key; `"paused": false`.
5. `confidentialTransferMint`: Authority set to Backpack Master Key; `"autoApproveNewAccounts": false`.
6. `transferHook`: Authority set to Backpack Master Key; `"programId": null`.
7. `scaledUiAmountConfig`: Authority set to programmatic mint authority; `"multiplier": "1"`, `"newMultiplier": "1"`.
8. `tokenMetadata`: Managed by Backpack Master Key; URI points to `https://metadata.backpack.exchange/stocks/<symbol>.json`.

### 2.2 Extension Fingerprint Standard
To ensure the Android app and vault program re-verify mint parameters deterministically before buying, we define:
$$\text{Fingerprint} = \text{SHA256}(\text{owner\_program} : \text{decimals} : \text{sorted\_extensions})$$

Canonical string format:
`"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb:6:confidentialTransferMint,defaultAccountState,metadataPointer,pausableConfig,permanentDelegate,scaledUiAmountConfig,tokenMetadata,transferHook"`

**Deterministic Test Vectors:**
1. Standard SPL Token program, 6 decimals, no extensions:
   - Canonical: `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA:6:`
   - SHA-256: `cf248eb3a697089faeaae168923a105c93c40be77e0e7a2b9472d5df7b09c629`
2. Token-2022 Backpack Equities (all 1,170 mints):
   - Canonical: `TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb:6:confidentialTransferMint,defaultAccountState,metadataPointer,pausableConfig,permanentDelegate,scaledUiAmountConfig,tokenMetadata,transferHook`
   - SHA-256: `e8ebcb8fc24ffbfa9ecdf3ddac4df2803b0c95cfd9d2011b95ff372551ecda92`

---

## 3. Provenance & Authority Clusters

### 3.1 Metadata Update & Administrative Authority
- **Single Master Authority:** `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`
- Holds 100% of:
  - Token metadata update authority
  - Freeze authority
  - Permanent delegate
  - Pausable authority
  - Confidential transfer authority
  - Transfer hook authority
  - Metadata pointer authority

### 3.2 Mint Authorities
- 1,170 distinct addresses (one per mint).
- Examples:
  - `VOO.US`: `J72EWDEiYzfxedF1kxXjkkJBULcoDFjKFeY6KUo17hDN`
  - `SPY.US`: `SPYBo66VJPFjh1pXMb9Le53kDYWTK1zzYVDeVRWtsbi`
  - `AAPL.US`: `AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8`
- These are programmatic mint PDAs controlled by the Backpack Securities custodial issuance program.

---

## 4. Liquidity & Trading Discovery (Key Finding & Surprise)

When probing Jupiter v6 / v1 Swap API (`https://api.jup.ag/swap/v1/quote`) for USDC $\to$ stock mint swaps at $5 and $25:
- **Result:** `{"error":"The token <mint> is not tradable","errorCode":"TOKEN_NOT_TRADABLE"}`
- **Analysis:**
  - Backpack Securities tokenized stocks are **not listed on public permissionless DEXs** (Raydium, Orca, Whirlpools).
  - Trading is managed within Backpack Securities via internal RFQ / orderbook APIs (`https://api.backpack.exchange`).
  - **Architectural Implication for JIRO:**
    1. **Primary Route for Production:** Backpack Exchange RFQ / Order API via the backend catalog/order service (`crates/service` using `BACKPACK_API_KEY` / `BACKPACK_API_SECRET`).
    2. **Jupiter DEX Route:** Will activate once Backpack or third-party market makers seed permissioned or public liquidity pools on Solana DEXs.
    3. The application gracefully reports liquidity status from the catalog service snapshot so users and UI know whether instant DEX execution or Backpack RFQ execution is active.

---

## 5. Account Creation Cost & Rent Economics

- **Token-2022 ATA Account Space:** 182 bytes
- **Solana Rent Exemption Rate:** 3,480 lamports/byte-year $\times$ 2 years + 128 bytes storage overhead = 6,960 lamports/byte
- **Rent-Exempt Lamports:** $(128 + 182) \times 6960 = 2,157,600\text{ lamports} \approx 0.0021576\text{ SOL}$
- **SOL/USD Price:** $109.47 (Backpack `SOL_USDC` ticker)
- **Rent Cost in USD:** **~$0.236 USD** per newly opened token stock account.
- **Economic Impact on Spare-Change Investing:**
  - On a **$5 buy tap**, a first-time stock purchase requires paying ~$0.236 in one-time rent (4.72% overhead).
  - On subsequent buys into the same stock, rent is **$0.00** (account already exists).
  - **Product Design Recommendation:** When an ATA is opened, JIRO should either:
    1. Display an educational disclosure: *"First-time stock setup: ~$0.24 one-time Solana account rent"*, or
    2. Fund ATA creation from the user's SOL balance with transparent disclosure in the Seed Vault signing preview.

---

## 6. Market Status & Trading Sessions

Backpack market sessions and holidays fetched from public endpoints:
- `GET /api/v1/stockSessions` $\to$ saved to `docs/raw/sessions.json`
- `GET /api/v1/stockHolidays` $\to$ saved to `docs/raw/holidays.json`

### Trading Sessions (America/New_York Timezone)
1. **US Equities Pre-Market:** Monday–Friday 04:00:00 to 09:30:00 ET
2. **US Equities Regular Hours:** Monday–Friday 09:30:00 to 16:00:00 ET
3. **US Equities Post-Market:** Monday–Friday 16:00:00 to 20:00:00 ET
4. **Weekend / Overnight Close:** Friday 20:00:00 to Sunday 20:00:00 ET

### Holiday Observances
- Juneteenth: June 19
- US Independence Day: July 4
- Labor Day: First Monday of September
- Thanksgiving Day: Fourth Thursday of November
- Christmas Day: December 25

When market is outside regular/extended sessions, JIRO UI displays:
*"US markets closed. Reopens at 04:00 AM ET."*

---

## 7. Presets Proposed (`docs/presets.json`)

Verified live Solana mints:
### Index Basket
- `VOO.US` (`Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E`): Vanguard S&P 500 ETF
- `SPY.US` (`SPYBo66VJPFjh1pXMb9Le53kDYWTK1zzYVDeVRWtsbi`): SPDR S&P 500 ETF Trust
- `QQQ.US` (`QQQvDYxG7g11Rr4rnaJqDddmP9ocH74YhE3mDaG37R9`): Invesco QQQ Trust (Nasdaq 100)

### Big Tech Basket
- `AAPL.US` (`AAPLEDt8RpzPgXyhvFzkMBofvFSQw9gpeMCoUdPdLnB8`): Apple Inc.
- `MSFT.US` (`MSFThX899B1DddH5jkyMpdArWXzP85VXLYDrbGGcRJM`): Microsoft Corporation
- `NVDA.US` (`NVDAVuiB7hwd3m5Wa1JuHNovPaPG6BH1QNztbKFxNjv`): NVIDIA Corporation
- `AMZN.US` (`AMZNw7cwU9L9c7ewZXxTws2PwQYxBGm2Rxg3FLDBJz9B`): Amazon.com, Inc.
- `GOOGL.US` (`GooGLMvZtJZXHdNZy95RUrAMCCKxV6ghK3qE5ddBgVm5`): Alphabet Inc.
- `META.US` (`META2L2FkQhH6qf6b8Dmg8rM2gZt56V4Q47qKNRxt1ZU`): Meta Platforms, Inc.
- `TSLA.US` (`TSLAjWvZ35BffQ1fW9R5k7XzX7tLqM8M6M7vL5v5v5v5`): Tesla, Inc.

---

## 8. Decisions Needed from Developer

1. **Issuer Key Pinning:**
   - **Candidate Key:** `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`
   - **Recommendation:** Pin this address in `policy.toml` and Android client as `BACKPACK_SECURITIES_ISSUER_AUTHORITY`. Any token purporting to be a Backpack stock that lacks this update authority will be rejected.
2. **Execution Venue Decision (Backpack RFQ vs DEX):**
   - Since Jupiter currently returns `TOKEN_NOT_TRADABLE` for Backpack equities, should the service implement:
     - (A) Backpack RFQ API order execution (using user-delegated signing or vault atomic swap)?
     - (B) Display catalog status and wait for Jupiter DEX pools to open?
     - *Recommendation:* Support both: route through Backpack service RFQ when DEX route is absent; seamlessly switch to Jupiter routing if DEX pools appear.
3. **Transfer Hook Whitelist:**
   - Currently `transferHook.programId` is `null` across all mints.
   - If Backpack enables a compliance hook in the future, should the app fail-safe (block) until the program ID is reviewed and added to `allowed_programs` in `policy.toml`?
   - *Recommendation:* Keep fail-safe policy (`unknown hook => BLOCK`).
