# JIRO Vault Security Specification

- **Program ID**: `AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc`
- **Current Network**: Solana Devnet (`https://api.devnet.solana.com`)
- **ProgramData Address**: `EtBJZiZRfynUHozF98LtmJHvzZTVCPfn9mUyNEBTv74K`
- **Deployer Authority**: `BBNyzG9Kn1xf8ZFbwK2nKr3XW4MGr4XE8pQ9iJ1rsi57`

---

## 1. Upgrade Authority & Key Management

### Upgrade Authority
- On Devnet, the upgrade authority is set to `BBNyzG9Kn1xf8ZFbwK2nKr3XW4MGr4XE8pQ9iJ1rsi57`.
- For Mainnet deployment:
  - The upgrade authority **must** be transferred to a Squads multisig or revoked permanently (`solana program set-upgrade-authority AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc --final`) after final verification.
  - Making the program immutable ensures complete non-custodial trust for end users holding real USDC.

### Program Keypair
- `program/vault-keypair.json` is committed to the repository to guarantee a deterministic Program ID (`AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc`) across local test validator, devnet, and client SDK configurations.

---

## 2. Invariants & Hard Cap Guarantees

### Constant Hard Cap
```rust
pub const HARD_MAX_BALANCE: u64 = 100_000_000; // 100 USDC (6 decimals)
```
- The vault program is specifically designed as a spare-change staging jar, not a high-balance savings account.
- `init_vault` strictly enforces:
  ```rust
  require!(max_balance > 0 && max_balance <= HARD_MAX_BALANCE, VaultError::InvalidMaxBalance);
  ```
  Attempting to initialize a vault with `max_balance == 0` or `max_balance > 100_000_000` is rejected with `InvalidMaxBalance`.

### Decimals Constraint
- The vault strictly enforces `mint.decimals == 6` (`VaultError::InvalidDecimals`).
- This guarantees that unit calculations correspond directly to micro-USDC.

### Arithmetic Invariants
- All deposit and swap calculations use checked arithmetic:
  - `vault_balance.checked_add(amount).ok_or(VaultError::MathOverflow)?`
  - `(usdc_in as u128).checked_mul(factor).ok_or(VaultError::MathOverflow)?`
- Under no circumstances can `u64::MAX` amounts cause integer overflow or panic.

---

## 3. Security Checklist & Verification

| Check | Specification & Rust Implementation | Verification Status |
| :--- | :--- | :---: |
| **Signer Checks** | `owner: Signer<'info>` is required for `init_vault`, `deposit`, `withdraw`, `close_vault`. `admin: Signer<'info>` is required for `mock_init`, `mock_set_price`. | Verified |
| **Owner / Seed Constraints** | `seeds = [b"vault", owner.key().as_ref(), &[vault_id]]`, `has_one = owner`, `has_one = mint`. Verified that a different owner cannot deposit or withdraw from another user's vault. | Verified |
| **Vault ATA Authority** | Vault ATA authority is strictly the `vault_config` PDA (`associated_token::authority = vault_config`). No individual key holds signing authority over the ATA. | Verified |
| **Cross-User Protection** | A different user's config cannot be paired with another vault ATA: Anchor validates that `vault_token_account` is the ATA of `vault_config` and `mint`, and seeds link `vault_config` to `owner`. | Verified |
| **Token-2022 Isolation** | Vault ATA requires `mint.to_account_info().owner == &anchor_spl::token::ID` (`VaultError::Token2022Rejected`). Classic SPL Token program is strictly enforced for the USDC vault ATA. | Verified |
| **No Arbitrary CPI** | CPIs are hardcoded to `token::transfer_checked` and `token::close_account` (or `token_interface::transfer_checked` in mock). No arbitrary target program IDs are accepted. | Verified |
| **No Remaining Accounts** | Instructions execute strictly with declared accounts; remaining accounts are ignored. | Verified |
| **Stored Bump** | Canonical bump is computed on init, stored in `VaultConfig.bump`, and passed into `signer_seeds` for PDA signing. | Verified |
| **Zero Balance on Close** | `close_vault` requires `vault_token_account.amount == 0` (`VaultError::VaultNotEmpty`). Remaining ATA rent and PDA rent are refunded to `owner`. | Verified |

---

## 4. Mainnet Safety Protection

To guarantee that mock swap and testing facilities can never be compiled into the mainnet binary:

1. **Compile-time Guard**:
   In `program/programs/vault/src/lib.rs`:
   ```rust
   #[cfg(all(feature = "mainnet", feature = "mock-swap"))]
   compile_error!("Feature 'mock-swap' cannot be compiled with 'mainnet' feature!");
   ```
2. **Build Inspection Script**:
   `scripts/check-no-mock-in-mainnet.sh` compiles the program with `--no-default-features --features mainnet` and verifies that the resulting IDL contains 0 occurrences of `mock_init`, `mock_set_price`, `mock_swap`, or `mock_config`.

---

## 5. Known Limitations & Edge Cases

### Cap Enforcement is Program-Path Only
- The vault token account is an SPL Associated Token Account whose authority is the `vault_config` PDA.
- Solana SPL token accounts can receive transfers directly from third parties (e.g. unsolicited airdrops or manual SPL token transfers) without calling the program's `deposit` instruction.
- **Mitigation & Invariant**:
  - Any subsequent program `deposit` instruction checks current balance + amount against `max_balance`. If external funds caused the balance to reach or exceed `max_balance`, further `deposit` instructions are rejected with `DepositExceedsCap`.
  - Only the owner can withdraw funds or close the vault.
  - The vault can still be emptied via `withdraw` and closed via `close_vault`.

### Vault ID Cardinality
- `vault_id` is a `u8` (0 to 255), allowing up to 256 independent vaults per user wallet address.
