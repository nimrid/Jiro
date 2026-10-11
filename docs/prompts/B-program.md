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
