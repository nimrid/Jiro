# Token-2022 Security & Investment Policy (`docs/POLICY.md`)

This document defines the JIRO evaluation policy for Solana Token-2022 extensions on tokenized equities issued by Backpack Securities. It maps each extension to a policy outcome (`OK`, `WARN`, `BLOCK`), establishes mandatory user disclosures, and documents empirical findings from on-chain verification across all 1,170 live stock mints on Solana mainnet.

---

## 1. Extension Evaluation Matrix

| Extension Name | On-Chain Name | Policy Action | Rationale | User Disclosure / Action |
| :--- | :--- | :--- | :--- | :--- |
| **Metadata Pointer** | `metadataPointer` | `OK` | Standard extension directing clients to on-chain token metadata. | None required. |
| **Token Metadata** | `tokenMetadata` | `OK` | Standard on-chain metadata containing official symbol, company name, and icon URI. | Displayed in UI headers and confirmation modals. |
| **Permanent Delegate** | `permanentDelegate` | `WARN` | Issuer holds a permanent delegate key to allow transfers and liquidations required by securities regulations. | *"Issuer maintains permanent delegate authority for regulatory compliance."* |
| **Pausable** | `pausableConfig` | `WARN` | Issuer can pause trading/transfers during market halts, corporate splits, or regulatory halts. | *"Transfers may be temporarily paused by issuer during market halts."* |
| **Freeze Authority** | `freezeAuthority` | `WARN` | Standard mint freeze authority held by the issuer compliance wallet. | *"Issuer maintains freeze authority for compliance."* |
| **Scaled UI Amount** | `scaledUiAmountConfig` | `WARN` | Allows fractional display scaling. Multiplier currently configured to 1 across all verified assets. | Token displays raw amounts scaled by multiplier `1`. |
| **Transfer Hook** | `transferHook` | `WARN` | Hook configured on mints. Program ID is currently `null` across all 1,170 mints. If set, must match allowed pinned programs. | *"Transfers invoke verified issuer compliance hook (currently unassigned)."* |
| **Default Account State** | `defaultAccountState` | `WARN` / `BLOCK` | When state is `initialized` (`WARN` / `OK`): ATA is immediately spendable. If state is `frozen` (`BLOCK`): ATA requires manual unfreezing before receipt. | All 1,170 verified mints default to `initialized`. |
| **Confidential Transfer** | `confidentialTransferMint` | `WARN` | Enabled on mints but optional for token accounts. Standard `TransferChecked` instructions function without confidential proof. | *"Confidential transfer extension is configured by issuer on mint but optional for swaps."* |
| **Transfer Fee** | `transferFeeConfig` | `WARN` | Incurs a basis point fee on transfers. Capped at maximum fee lamports. | Disclosed to user prior to transaction signing; factored into net yield. |
| **Interest Bearing** | `interestBearingConfig` | `WARN` | Accrues positive or negative rate on balance. | Disclosed in asset overview. |
| **Non-Transferable** | `nonTransferable` | `BLOCK` | Soulbound tokens that cannot be transferred or deposited into vault / swapped. | App blocks purchase and marks non-buyable. |
| **Mint Close Authority** | `mintCloseAuthority` | `BLOCK` | Allows destroying the token mint and invalidating user balances. | App blocks purchase and marks non-buyable. |
| **Unknown Extension** | Any unlisted | `BLOCK` | Default policy for any unexpected extension. | App blocks purchase. |

---

## 2. On-Chain Findings Across 1,170 Backpack Stocks

A full query of Solana mainnet via `getMultipleAccounts` with `jsonParsed` encoding on all 1,170 `.US` stock mints listed by Backpack revealed a uniform configuration:

### Extension Breakdown Across 1,170 Mints
- **Total Mints Evaluated**: 1,170
- **Overall Policy Outcome**:
  - `OK`: 0
  - `WARN`: 1,170 (100%)
  - `BLOCK`: 0 (0%)

### Extension Frequencies
| Extension | Frequency | Details from On-Chain State |
| :--- | :--- | :--- |
| `metadataPointer` | 1,170 / 1,170 (100%) | `authority`: `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`<br>`metadataAddress`: Self (Mint PDA) |
| `permanentDelegate` | 1,170 / 1,170 (100%) | `delegate`: `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a` |
| `defaultAccountState` | 1,170 / 1,170 (100%) | `accountState`: `"initialized"` (Usable immediately) |
| `pausableConfig` | 1,170 / 1,170 (100%) | `authority`: `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`<br>`paused`: `false` |
| `confidentialTransferMint` | 1,170 / 1,170 (100%) | `authority`: `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`<br>`autoApproveNewAccounts`: `false` |
| `transferHook` | 1,170 / 1,170 (100%) | `authority`: `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`<br>`programId`: `null` (No external hook executed) |
| `scaledUiAmountConfig` | 1,170 / 1,170 (100%) | `multiplier`: `"1"`<br>`newMultiplier`: `"1"`<br>`newMultiplierEffectiveTimestamp`: 0 |
| `tokenMetadata` | 1,170 / 1,170 (100%) | `updateAuthority`: `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`<br>`uri`: `https://metadata.backpack.exchange/stocks/<symbol>.json` |

---

## 3. Authority Provenance Analysis

1. **Master Update & Administrative Authority**:
   - Every single stock mint (1,170 out of 1,170) is governed by a single authority:
     `2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a`
   - This address holds:
     - Metadata Update Authority
     - Freeze Authority
     - Permanent Delegate
     - Pausable Authority
     - Transfer Hook Authority
     - Confidential Transfer Authority
     - Metadata Pointer Authority

2. **Programmatic Mint Authorities**:
   - Each individual mint has an individual mint authority (1,170 distinct addresses).
   - Example (`VOO.US`): `J72EWDEiYzfxedF1kxXjkkJBULcoDFjKFeY6KUo17hDN`.
   - These are programmatic mint PDAs controlled by Backpack Securities issuance smart contracts.

---

## 4. Policy Execution Rules in Catalog Engine

1. **Pre-Buy Verification (`extensionFingerprint`)**:
   - The Android client re-computes `extensionFingerprint` before signing any investment transaction:
     $$\text{Fingerprint} = \text{SHA256}(\text{owner\_program} : \text{decimals} : \text{sorted\_extensions})$$
   - If the on-chain mint fingerprint does not match the catalog snapshot fingerprint, the trade is rejected immediately.
2. **Transfer Hook Enforcement**:
   - If a mint's `transferHook` extension updates `programId` from `null` to a non-null address, that address MUST be listed in `allowed_programs` in `policy.toml`, otherwise policy immediately evaluates to `BLOCK`.
3. **Pausable Check**:
   - If `pausableConfig.paused == true`, the asset is marked `not_buyable` with reason: `"Trading currently paused by issuer"`.
