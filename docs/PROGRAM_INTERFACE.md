# Vault Program Interface Specification

- **Program ID**: `AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc`
- **Network**: Solana Devnet
- **Token Standard**: Classic SPL Token (`TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA`)
- **Hard Cap**: `100_000_000` base units (100 USDC, 6 decimals)

---

## PDA Derivation

### 1. VaultConfig PDA
- **Seeds**: `[b"vault", owner.publicKey.toBytes()]`
- **Program ID**: `AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc`

### 2. Vault Token Account (ATA)
- Associated token account of `vault_config` PDA for the given mint:
  - **Mint**: USDC mint (`EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v` on Mainnet, or devnet mint)
  - **Authority**: `vault_config` PDA (allow off-curve authority = `true`)
  - **Token Program**: `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA`
  - **Associated Token Program**: `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL`

---

## Instructions

### 1. `init_vault`
Initializes user's `VaultConfig` PDA and creates the vault Associated Token Account.

- **8-byte Discriminator (hex)**: `4d4f559621d9346a`
- **Borsh Arguments**:
  - `threshold`: `u64` (8 bytes, little-endian)
  - `max_balance`: `u64` (8 bytes, little-endian)
- **Total Instruction Data Size**: 24 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | User wallet paying for initialization rent |
| 1 | `keeper` | No | No | Registered keeper authority for sweep |
| 2 | `mint` | No | No | USDC Mint |
| 3 | `destination` | No | No | Pre-registered Backpack sweep token account |
| 4 | `vault_config` | No | **Yes** | PDA `[b"vault", owner]` |
| 5 | `vault_token_account` | No | **Yes** | ATA for (`vault_config`, `mint`) |
| 6 | `system_program` | No | No | `11111111111111111111111111111111` |
| 7 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |
| 8 | `associated_token_program`| No | No | `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL` |

---

### 2. `deposit`
Transfers USDC from the owner's token account into the vault ATA and enforces that `vault_balance + amount <= max_balance`.

- **8-byte Discriminator (hex)**: `f223c68952e1f2b6`
- **Borsh Arguments**:
  - `amount`: `u64` (8 bytes, little-endian)
- **Total Instruction Data Size**: 16 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | Owner wallet signing deposit and paying fees |
| 1 | `vault_config` | No | No | PDA `[b"vault", owner]` |
| 2 | `mint` | No | No | USDC Mint |
| 3 | `vault_token_account` | No | **Yes** | Vault ATA receiving funds |
| 4 | `owner_token_account` | No | **Yes** | Owner's USDC token account (source) |
| 5 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |

---

### 3. `sweep`
Keeper-callable instruction that transfers the full balance to the pre-registered `destination` token account once `balance >= threshold`.

- **8-byte Discriminator (hex)**: `2817eaaf0e3d9ab1`
- **Borsh Arguments**: None
- **Total Instruction Data Size**: 8 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `keeper` | **Yes** | No | Keeper authority registered in `vault_config` |
| 1 | `vault_config` | No | No | PDA `[b"vault", vault_config.owner]` |
| 2 | `mint` | No | No | USDC Mint |
| 3 | `vault_token_account` | No | **Yes** | Vault ATA emptied to destination |
| 4 | `destination_token_account`| No | **Yes** | Must equal stored `vault_config.destination` |
| 5 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |

---

### 4. `withdraw`
Owner-callable instruction allowing full or partial withdrawal of accumulated funds at any time.

- **8-byte Discriminator (hex)**: `b712469c946da122`
- **Borsh Arguments**:
  - `amount`: `u64` (8 bytes, little-endian)
- **Total Instruction Data Size**: 16 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | Vault owner wallet |
| 1 | `vault_config` | No | No | PDA `[b"vault", owner]` |
| 2 | `mint` | No | No | USDC Mint |
| 3 | `vault_token_account` | No | **Yes** | Vault ATA |
| 4 | `destination_token_account`| No | **Yes** | Owner's USDC token account receiving funds |
| 5 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |

---

## Error Codes

| Code | Hex | Name | Message |
| :---: | :---: | :--- | :--- |
| 6000 | `0x1770` | `InvalidThreshold` | Threshold must be greater than zero |
| 6001 | `0x1771` | `MaxBalanceBelowThreshold` | Max balance must be at least equal to threshold |
| 6002 | `0x1772` | `MaxBalanceExceedsHardCap` | Max balance exceeds hard cap of 100 USDC |
| 6003 | `0x1773` | `ZeroDepositAmount` | Deposit amount must be greater than zero |
| 6004 | `0x1774` | `DepositExceedsCap` | Deposit would exceed max balance cap |
| 6005 | `0x1775` | `BelowSweepThreshold` | Vault balance is below sweep threshold |
| 6006 | `0x1776` | `InvalidDestinationAddress` | Destination account address does not match config |
| 6007 | `0x1777` | `InvalidDestinationMint` | Destination account mint does not match config mint |
| 6008 | `0x1778` | `InvalidWithdrawOwner` | Destination account owner must match vault owner |
| 6009 | `0x1779` | `ZeroWithdrawAmount` | Withdraw amount must be greater than zero |
| 6010 | `0x177a` | `InsufficientVaultBalance` | Withdraw amount exceeds vault balance |
| 6011 | `0x177b` | `MathOverflow` | Arithmetic overflow |

---

## Events

### `Swept`
- **8-byte Discriminator (hex)**: `e44d6b0c66eb7e7a`
- **Payload**:
  - `owner`: `Pubkey` (32 bytes)
  - `amount`: `u64` (8 bytes, little-endian)
