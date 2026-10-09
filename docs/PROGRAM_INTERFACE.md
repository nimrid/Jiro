# Vault Program Interface Specification

- **Program ID**: `AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc`
- **Networks**: Solana Devnet / Mainnet-Beta
- **Token Standard**: Classic SPL Token (`TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA`) for Vault ATA
- **Hard Max Balance**: `100_000_000` base units (100 USDC at 6 decimals)
- **Mint Requirement**: USDC mint with `decimals == 6`, owned by classic SPL Token program (Token-2022 rejected for vault ATA).

---

## PDA Derivations

### 1. `VaultConfig` PDA
- **Seeds**: `[b"vault", owner.key().as_ref(), &[vault_id]]`
  - `b"vault"`: byte string `vault` (5 bytes)
  - `owner`: 32-byte public key of the vault owner
  - `vault_id`: 1-byte unsigned integer (`u8`)
- **Program ID**: `AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc`

### 2. Vault Token Account (ATA)
- Associated Token Account of `vault_config` PDA for the USDC mint:
  - **Mint**: USDC mint (`EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v` on mainnet, or devnet USDC mint)
  - **Authority / Owner**: `vault_config` PDA (allow off-curve authority = `true`)
  - **Token Program**: Classic SPL Token (`TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA`)
  - **Associated Token Program**: `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL`

### 3. `MockConfig` PDA (Feature: `mock-swap` only)
- **Seeds**: `[b"mock", stock_mint.key().as_ref()]`
  - `b"mock"`: byte string `mock` (4 bytes)
  - `stock_mint`: 32-byte public key of the stock token mint
- **Sink Token Account**: ATA of `mock_config` PDA for `usdc_mint` (holds received USDC)
- **Treasury Token Account**: ATA of `mock_config` PDA for `stock_mint` (holds stock SPL tokens; supports classic and Token-2022)

---

## Instructions

### 1. `init_vault`
Initializes a user's `VaultConfig` PDA and creates the vault Associated Token Account.

- **8-byte Discriminator (hex)**: `4d4f559621d9346a` (`sha256("global:init_vault")[..8]`)
- **Borsh Arguments**:
  | Field | Type | Size | Description |
  | :--- | :--- | :--- | :--- |
  | `vault_id` | `u8` | 1 byte | Identifier index for the vault (0..255) |
  | `max_balance` | `u64` | 8 bytes (little-endian) | User cap in micro-USDC (`0 < max_balance <= 100_000_000`) |
- **Total Instruction Data Size**: 17 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | User wallet paying for PDA and ATA rent |
| 1 | `mint` | No | No | USDC Mint (`decimals == 6`, owner == classic Token program) |
| 2 | `vault_config` | No | **Yes** | PDA `[b"vault", owner, &[vault_id]]` |
| 3 | `vault_token_account` | No | **Yes** | ATA for (`vault_config`, `mint`) |
| 4 | `system_program` | No | No | `11111111111111111111111111111111` |
| 5 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |
| 6 | `associated_token_program` | No | No | `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL` |

---

### 2. `deposit`
Transfers USDC from the owner's token account into the vault ATA. Enforces `vault_balance + amount <= max_balance`.

- **8-byte Discriminator (hex)**: `f223c68952e1f2b6` (`sha256("global:deposit")[..8]`)
- **Borsh Arguments**:
  | Field | Type | Size | Description |
  | :--- | :--- | :--- | :--- |
  | `amount` | `u64` | 8 bytes (little-endian) | Micro-USDC to deposit (`> 0`) |
- **Total Instruction Data Size**: 16 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | Owner wallet signing deposit and fee payer |
| 1 | `vault_config` | No | No | PDA `[b"vault", owner, &[vault_id]]` |
| 2 | `mint` | No | No | USDC Mint (must match `vault_config.mint`) |
| 3 | `vault_token_account` | No | **Yes** | Vault ATA receiving funds |
| 4 | `owner_token_account` | No | **Yes** | Owner's USDC token account (source) |
| 5 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |

---

### 3. `withdraw`
Owner-callable instruction allowing partial or full withdrawal of accumulated funds into the owner's token account.

- **8-byte Discriminator (hex)**: `b712469c946da122` (`sha256("global:withdraw")[..8]`)
- **Borsh Arguments**:
  | Field | Type | Size | Description |
  | :--- | :--- | :--- | :--- |
  | `amount` | `u64` | 8 bytes (little-endian) | Micro-USDC to withdraw (`0 < amount <= balance`) |
- **Total Instruction Data Size**: 16 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | No | Vault owner wallet signer |
| 1 | `vault_config` | No | No | PDA `[b"vault", owner, &[vault_id]]` |
| 2 | `mint` | No | No | USDC Mint (must match `vault_config.mint`) |
| 3 | `vault_token_account` | No | **Yes** | Vault ATA debited via PDA-signed CPI |
| 4 | `destination_token_account`| No | **Yes** | Owner's USDC token account (must be owned by `owner`) |
| 5 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |

---

### 4. `close_vault`
Closes the vault token account via PDA-signed CPI and closes the `vault_config` PDA with `close = owner`, returning all rent SOL to the owner. The vault ATA balance must be zero.

- **8-byte Discriminator (hex)**: `8d67117e484b1d1d` (`sha256("global:close_vault")[..8]`)
- **Borsh Arguments**: None
- **Total Instruction Data Size**: 8 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | Vault owner wallet receiving rent SOL |
| 1 | `vault_config` | No | **Yes** | PDA `[b"vault", owner, &[vault_id]]` (closed) |
| 2 | `mint` | No | No | USDC Mint (must match `vault_config.mint`) |
| 3 | `vault_token_account` | No | **Yes** | Vault ATA (closed; balance must be 0) |
| 4 | `token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |

---

### 5. `mock_init` (Feature: `mock-swap` only)
Initializes a mock pool for a tokenized stock, creating `MockConfig`, the sink ATA for USDC, and the treasury ATA for stock tokens.

- **8-byte Discriminator (hex)**: `f458426f74792010` (`sha256("global:mock_init")[..8]`)
- **Borsh Arguments**:
  | Field | Type | Size | Description |
  | :--- | :--- | :--- | :--- |
  | `price` | `u64` | 8 bytes (little-endian) | Price in micro-USDC per whole stock token (`> 0`) |
- **Total Instruction Data Size**: 16 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `admin` | **Yes** | **Yes** | Admin wallet paying for account rent |
| 1 | `stock_mint` | No | No | Stock Mint (classic SPL Token or Token-2022) |
| 2 | `usdc_mint` | No | No | USDC Mint (classic SPL Token) |
| 3 | `mock_config` | No | **Yes** | PDA `[b"mock", stock_mint]` |
| 4 | `treasury_token_account` | No | **Yes** | ATA for (`mock_config`, `stock_mint`) |
| 5 | `sink_token_account` | No | **Yes** | ATA for (`mock_config`, `usdc_mint`) |
| 6 | `system_program` | No | No | `11111111111111111111111111111111` |
| 7 | `usdc_token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |
| 8 | `stock_token_program` | No | No | SPL Token or Token-2022 Program ID |
| 9 | `associated_token_program` | No | No | `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL` |

---

### 6. `mock_set_price` (Feature: `mock-swap` only)
Updates the price of stock tokens in micro-USDC. Admin signer only.

- **8-byte Discriminator (hex)**: `902a9f6e9bb8302c` (`sha256("global:mock_set_price")[..8]`)
- **Borsh Arguments**:
  | Field | Type | Size | Description |
  | :--- | :--- | :--- | :--- |
  | `new_price` | `u64` | 8 bytes (little-endian) | New price in micro-USDC per whole stock token (`> 0`) |
- **Total Instruction Data Size**: 16 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `admin` | **Yes** | No | Pool admin authority signer |
| 1 | `mock_config` | No | **Yes** | PDA `[b"mock", stock_mint]`, verified with `has_one = admin` |

---

### 7. `mock_swap` (Feature: `mock-swap` only)
Swaps USDC from user to sink ATA and transfers stock tokens from treasury ATA to owner stock ATA.

- **8-byte Discriminator (hex)**: `8476b98619386a0a` (`sha256("global:mock_swap")[..8]`)
- **Borsh Arguments**:
  | Field | Type | Size | Description |
  | :--- | :--- | :--- | :--- |
  | `usdc_in` | `u64` | 8 bytes (little-endian) | Micro-USDC input amount (`> 0`) |
  | `min_out` | `u64` | 8 bytes (little-endian) | Minimum stock token base units expected |
- **Total Instruction Data Size**: 24 bytes

#### Accounts:
| Index | Name | Signer | Writable | Description / Derivation |
| :---: | :--- | :---: | :---: | :--- |
| 0 | `owner` | **Yes** | **Yes** | Owner wallet signing swap |
| 1 | `mock_config` | No | No | PDA `[b"mock", stock_mint]` |
| 2 | `usdc_mint` | No | No | USDC Mint |
| 3 | `stock_mint` | No | No | Stock Mint |
| 4 | `sink_token_account` | No | **Yes** | Sink ATA receiving USDC |
| 5 | `treasury_token_account` | No | **Yes** | Treasury ATA supplying stock tokens |
| 6 | `owner_usdc_account` | No | **Yes** | Owner's USDC token account (source) |
| 7 | `owner_stock_account` | No | **Yes** | Owner's stock token account (destination) |
| 8 | `usdc_token_program` | No | No | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |
| 9 | `stock_token_program` | No | No | SPL Token or Token-2022 Program ID |

---

## Error Codes

| Code (Decimal) | Hex | Name | Message |
| :---: | :---: | :--- | :--- |
| 6000 | `0x1770` | `InvalidMaxBalance` | Max balance must be greater than zero and at most 100 USDC |
| 6001 | `0x1771` | `ZeroDepositAmount` | Deposit amount must be greater than zero |
| 6002 | `0x1772` | `DepositExceedsCap` | Deposit would exceed configured vault max balance cap |
| 6003 | `0x1773` | `ZeroWithdrawAmount` | Withdraw amount must be greater than zero |
| 6004 | `0x1774` | `InsufficientVaultBalance` | Withdraw amount exceeds vault balance |
| 6005 | `0x1775` | `VaultNotEmpty` | Vault token account balance must be zero to close vault |
| 6006 | `0x1776` | `InvalidDestinationOwner` | Destination token account owner must match vault owner |
| 6007 | `0x1777` | `InvalidMint` | Token mint does not match vault config mint |
| 6008 | `0x1778` | `InvalidDecimals` | Mint decimals must be 6 |
| 6009 | `0x1779` | `Token2022Rejected` | Token-2022 is rejected for vault token account (classic Token only) |
| 6010 | `0x177a` | `MathOverflow` | Math overflow occurred |
| 6011 | `0x177b` | `Unauthorized` | Signer is not authorized |
| 6012 | `0x177c` | `InvalidPrice` | Price must be greater than zero |
| 6013 | `0x177d` | `SlippageExceeded` | Output amount is below minimum expected amount |

---

## Events

### 1. `Deposited`
- **8-byte Discriminator (hex)**: `6f8d1a2da1236439`
- **Payload Layout**:
  | Offset | Field | Type | Size | Description |
  | :---: | :--- | :--- | :--- | :--- |
  | 0 | `owner` | `Pubkey` | 32 bytes | Depositor public key |
  | 32 | `amount` | `u64` | 8 bytes (little-endian) | Amount deposited in base units |
  | 40 | `vault_id` | `u8` | 1 byte | Vault ID |

### 2. `Withdrawn`
- **8-byte Discriminator (hex)**: `1459dfc6c27cdb0d`
- **Payload Layout**:
  | Offset | Field | Type | Size | Description |
  | :---: | :--- | :--- | :--- | :--- |
  | 0 | `owner` | `Pubkey` | 32 bytes | Withdrawer public key |
  | 32 | `amount` | `u64` | 8 bytes (little-endian) | Amount withdrawn in base units |
  | 40 | `vault_id` | `u8` | 1 byte | Vault ID |

### 3. `Closed`
- **8-byte Discriminator (hex)**: `321f579b87dcc3ef`
- **Payload Layout**:
  | Offset | Field | Type | Size | Description |
  | :---: | :--- | :--- | :--- | :--- |
  | 0 | `owner` | `Pubkey` | 32 bytes | Vault owner public key |
  | 32 | `amount` | `u64` | 8 bytes (little-endian) | Always 0 |
  | 40 | `vault_id` | `u8` | 1 byte | Vault ID |

---

## Cap Enforcement & Security Caveat

### Hard Cap Guarantees:
1. `HARD_MAX_BALANCE` is set at compile time to `100_000_000` (100 USDC, 6 decimals).
2. During `init_vault`, `max_balance` is required to satisfy `0 < max_balance <= HARD_MAX_BALANCE`.
3. During `deposit`, the program executes checked addition: `vault_balance + amount` and asserts `new_balance <= vault_config.max_balance`.

### Program-Path Only Caveat:
The vault token account is an SPL Associated Token Account whose authority is the `vault_config` PDA. Solana accounts can receive direct SPL Token transfers from third parties outside of this program (without invoking the `deposit` instruction). Therefore, direct transfers sent directly to the vault ATA can bypass the balance cap check.

However:
- The vault program strictly rejects any program `deposit` if the current balance plus deposit amount would exceed `max_balance`.
- Only the `owner` can withdraw accumulated funds via `withdraw`.
- The vault cannot be closed until the balance is completely 0 via `close_vault`.
