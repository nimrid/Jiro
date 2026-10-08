# Workstream C: Anchor vault program

Read CLAUDE.md first. Then check `anchor --version`, `solana --version`,
`rustc --version` and match the scaffold to them. Do not upgrade toolchains.

## Goal
A minimal per-user vault that accumulates USDC round-ups, lets ONLY a keeper
sweep them to ONE pre-registered destination once a threshold is met, and lets
the owner withdraw at any time. This program will hold real USDC. Smaller and
stricter beats clever.

## State: VaultConfig PDA, seeds [b"vault", owner]
owner: Pubkey, keeper: Pubkey, mint: Pubkey, destination: Pubkey (a token
account address, stored as-is and validated at sweep time), threshold: u64,
max_balance: u64, bump: u8.
Vault token account: the ATA of (mint, VaultConfig PDA). The PDA is its authority.
Use classic SPL Token only (USDC). Reject Token-2022.
Compile-time const HARD_MAX_BALANCE = 100_000_000 (100 USDC, 6 decimals).

## Instructions
1. init_vault(threshold, max_balance)
   Owner signs and pays. Creates config and the vault ATA.
   Require: threshold > 0, max_balance >= threshold, max_balance <= HARD_MAX_BALANCE.
   Config is immutable after init (no update instruction).
2. deposit(amount)
   Owner signs. Transfers from the owner's token account to the vault token
   account using transfer_checked.
   Require: amount > 0, vault_balance + amount <= max_balance (checked math).
3. sweep()
   Keeper signs (has_one = keeper). Require vault_balance >= threshold.
   Transfers the ENTIRE balance to the account whose address equals
   config.destination (constraint on address, not on the passed account's owner).
   Destination must be a token account of config.mint. Sign with PDA seeds.
   Emit Swept { owner, amount }.
4. withdraw(amount)
   Owner signs (has_one = owner). Require 0 < amount <= vault_balance.
   Destination must be a token account owned by the owner, of config.mint.

## Security checklist (verify each and say how)
- Every account has explicit owner/mint/authority/seed constraints.
- Vault token account authority == config PDA and mint == config.mint.
- Signer checks on every privileged path. No remaining_accounts. No CPI to
  arbitrary programs. token_program is the classic Token program.
- Checked arithmetic everywhere. Bump stored, not recomputed.
- A different user's config cannot be paired with my vault token account.

## Tests (negative tests FIRST, then happy paths)
init: threshold 0 fails; cap < threshold fails; cap > HARD_MAX fails; re-init fails.
deposit: zero fails; exactly-at-cap succeeds; cap+1 fails; wrong mint fails;
  non-owner fails.
sweep: below threshold fails; exactly threshold succeeds and empties the vault;
  non-keeper fails (including the owner, when owner != keeper); wrong destination
  address fails; destination with wrong mint fails.
withdraw: partial and full succeed; non-owner fails; amount > balance fails;
  destination owned by someone else fails.
Also: u64::MAX amounts do not overflow or panic.

## Deliverables
1. Program + passing tests, deployed to DEVNET (report program ID).
2. docs/PROGRAM_INTERFACE.md: program ID, PDA seeds, per-instruction account
   list in exact order with signer/writable flags, 8-byte discriminators (hex),
   Borsh argument layouts, error codes, event layouts. The Kotlin app will
   hand-build instructions from this file, so it must be exact.
3. docs/fixtures/vault_vectors.json: for a fixed test owner pubkey, the expected
   vault PDA, bump, vault ATA, and the exact instruction-data hex for each
   instruction with sample args. Android tests will assert against this.
4. scripts/: a small CLI for init/deposit/sweep/withdraw against devnet.
5. IDL committed. Final report: what you verified, what is untested.

## Process
First read CLAUDE.md, write a plan in under 15 lines, list assumptions, then
wait for my go-ahead. If you hit an Anchor/Solana version problem, tell me the
exact error and your proposed fix before changing dependencies.# Workstream C: Anchor vault program

Read CLAUDE.md first. Then check `anchor --version`, `solana --version`,
`rustc --version` and match the scaffold to them. Do not upgrade toolchains.

## Goal
A minimal per-user vault that accumulates USDC round-ups, lets ONLY a keeper
sweep them to ONE pre-registered destination once a threshold is met, and lets
the owner withdraw at any time. This program will hold real USDC. Smaller and
stricter beats clever.

## State: VaultConfig PDA, seeds [b"vault", owner]
owner: Pubkey, keeper: Pubkey, mint: Pubkey, destination: Pubkey (a token
account address, stored as-is and validated at sweep time), threshold: u64,
max_balance: u64, bump: u8.
Vault token account: the ATA of (mint, VaultConfig PDA). The PDA is its authority.
Use classic SPL Token only (USDC). Reject Token-2022.
Compile-time const HARD_MAX_BALANCE = 100_000_000 (100 USDC, 6 decimals).

## Instructions
1. init_vault(threshold, max_balance)
   Owner signs and pays. Creates config and the vault ATA.
   Require: threshold > 0, max_balance >= threshold, max_balance <= HARD_MAX_BALANCE.
   Config is immutable after init (no update instruction).
2. deposit(amount)
   Owner signs. Transfers from the owner's token account to the vault token
   account using transfer_checked.
   Require: amount > 0, vault_balance + amount <= max_balance (checked math).
3. sweep()
   Keeper signs (has_one = keeper). Require vault_balance >= threshold.
   Transfers the ENTIRE balance to the account whose address equals
   config.destination (constraint on address, not on the passed account's owner).
   Destination must be a token account of config.mint. Sign with PDA seeds.
   Emit Swept { owner, amount }.
4. withdraw(amount)
   Owner signs (has_one = owner). Require 0 < amount <= vault_balance.
   Destination must be a token account owned by the owner, of config.mint.

## Security checklist (verify each and say how)
- Every account has explicit owner/mint/authority/seed constraints.
- Vault token account authority == config PDA and mint == config.mint.
- Signer checks on every privileged path. No remaining_accounts. No CPI to
  arbitrary programs. token_program is the classic Token program.
- Checked arithmetic everywhere. Bump stored, not recomputed.
- A different user's config cannot be paired with my vault token account.

## Tests (negative tests FIRST, then happy paths)
init: threshold 0 fails; cap < threshold fails; cap > HARD_MAX fails; re-init fails.
deposit: zero fails; exactly-at-cap succeeds; cap+1 fails; wrong mint fails;
  non-owner fails.
sweep: below threshold fails; exactly threshold succeeds and empties the vault;
  non-keeper fails (including the owner, when owner != keeper); wrong destination
  address fails; destination with wrong mint fails.
withdraw: partial and full succeed; non-owner fails; amount > balance fails;
  destination owned by someone else fails.
Also: u64::MAX amounts do not overflow or panic.

## Deliverables
1. Program + passing tests, deployed to DEVNET (report program ID).
2. docs/PROGRAM_INTERFACE.md: program ID, PDA seeds, per-instruction account
   list in exact order with signer/writable flags, 8-byte discriminators (hex),
   Borsh argument layouts, error codes, event layouts. The Kotlin app will
   hand-build instructions from this file, so it must be exact.
3. docs/fixtures/vault_vectors.json: for a fixed test owner pubkey, the expected
   vault PDA, bump, vault ATA, and the exact instruction-data hex for each
   instruction with sample args. Android tests will assert against this.
4. scripts/: a small CLI for init/deposit/sweep/withdraw against devnet.
5. IDL committed. Final report: what you verified, what is untested.

## Process
First read CLAUDE.md, write a plan in under 15 lines, list assumptions, then
wait for my go-ahead. If you hit an Anchor/Solana version problem, tell me the
exact error and your proposed fix before changing dependencies.