# Workstream D: Android app (Kotlin, Compose, MWA)

Read CLAUDE.md first. Target: Solana Seeker (Seed Vault) plus a Solana Mobile
fake wallet on an emulator for development. Look at the official Solana Mobile
docs and the Kotlin MWA sample apps in the solana-mobile GitHub org. Verify
every artifact's coordinates and current version on Maven Central before
adding it, and tell me what you added.

## Goal
A working vertical slice: connect wallet, pay USDC with a bundled round-up in
ONE approval, see the vault fill toward the next buy, withdraw.

## Hard rules
- No private keys and no Backpack API keys in the app, ever. Signing happens
  only through MWA. Keeper URL comes from BuildConfig.
- MWA 2.0: use signAndSendTransactions (not signTransactions). Set a real app
  identity (name, uri, icon). Cache the auth token encrypted (Keystore-backed)
  and use reauthorize to avoid re-prompting. Handle wallet-not-found, user
  declined, and timeout as distinct UI states.
- Amounts are Long base units. Never Double. USDC has 6 decimals.
- Cluster is a build config: devnet for dev, mainnet for demo.
- Do NOT use an Anchor client library. Hand-build instructions from
  docs/PROGRAM_INTERFACE.md: 8-byte discriminator + Borsh args (u64 little-endian).
- Use legacy transactions. Check serialized size is under 1232 bytes.

## Architecture
MVVM: ViewModel + StateFlow + coroutines, Compose UI. Put logic in pure Kotlin
classes so it is unit-testable without a device.

## Core pieces (build in this order, each with tests)
1. RoundUp.kt: roundUp(amountBaseUnits) = distance to the next whole dollar
   (1_000_000 base units). Exact whole-dollar behavior is a config flag, default 0.
   Tests: 33.50 -> 0.50, 33.01 -> 0.99, 33.00 -> 0, 0.01 -> 0.99.
2. Pda.kt: derive the vault PDA with seeds ["vault", owner] and the vault ATA
   with off-curve-owner allowed. Assert against docs/fixtures/vault_vectors.json.
3. Instructions.kt: builders for init_vault, deposit, withdraw, plus SPL transfer,
   create-ATA-idempotent. Golden tests: instruction data hex must equal the fixtures.
4. PaymentTxBuilder.kt: assembles one transaction:
   [create recipient ATA idempotent if missing] +
   [init_vault + create vault ATA if the vault does not exist] +
   [USDC transfer to recipient] + [vault.deposit(roundUp)].
   Fetch a recent blockhash via RPC with lastValidBlockHeight.
5. PreflightCheck: before asking the wallet to sign, check USDC balance >=
   amount + roundUp, SOL balance covers fees and any rent, and the vault cap
   will not be exceeded. Show a clear error instead of a failed transaction.
6. MwaRepository: connect, reauthorize, signAndSend, confirm via RPC
   (confirmed commitment, honor lastValidBlockHeight), expose results as Flow.
7. KeeperApi: interface plus a FakeKeeperApi returning this shape so UI work
   never blocks on workstream E:
   { "vaultBalance": "3500000", "threshold": "5000000", "pendingBuys":
     [{"symbol":"VOO.US","usd":"5000000","status":"pending|filled",
       "shares":"0.01"}], "holdings": [{"symbol":"VOO.US","shares":"0.01"}] }

## Screens
Connect; Home (vault balance, ring "$3.50 of $5.00 to your next buy",
recent round-ups, holdings); Pay (recipient + amount, preview line
"Pay $33.50 -> +$0.50 to vault", confirm); Withdraw (owner pulls from vault).
Stretch only after the above works: parse Solana Pay URIs
(solana:<recipient>?amount=&spl-token=) and scan them from a QR code.

## Done when
Unit tests green (round-up, PDA and instruction fixtures, size check). On
devnet with the deployed program ID, one MWA approval produces a transaction
containing the payment plus the vault deposit, and the Home screen shows the
vault balance rising. Final report: what you verified on a real wallet, what
you only verified against the fake wallet, what is untested.

## Process
Read CLAUDE.md, docs/PROGRAM_INTERFACE.md, and docs/fixtures. If the interface
doc or fixtures do not exist yet, build steps 1, 5, 6 and 7 against stubs and
tell me what you are blocked on. First reply with a plan under 15 lines and
your assumptions, then wait for my go-ahead.