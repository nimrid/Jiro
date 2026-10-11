# Workstream G: round-ups on outgoing transfers (ObservedTransfers)
Constraint: the app cannot add a deposit to someone else's transaction, so this is NOT atomic. The UI must say "owed", never
"in your jar", until settled.
- Detection (on-device, no server learns the wallet): periodic WorkManager polling plus foreground refresh of the user's USDC
  ATA signatures; getTransaction jsonParsed; count only `finalized` transactions.
- Qualify: SPL transfer/transferChecked where source is the user's USDC ATA and authority is the user, destination owner is a
  different wallet. Exclude: self transfers, transfers to the vault ATA, transactions involving Jupiter or our program, swaps, and
  anything older than the baseline timestamp chosen at opt-in.
- Ledger (Room): unique key = transaction signature (idempotent), amount, computed roundUp, status owed/settled. Cap owed total.
- Settle: one signature `vault.deposit(sum)` (batch to fit size); optionally fold into the next PAY or INVEST. Local notification
  when owed >= $1 or the jar is ready (Android 13+ POST_NOTIFICATIONS flow).
- Tests: parser fixtures from real devnet/mainnet transactions, idempotency, exclusion rules, reinstall behavior (baseline reset),
  cap handling.
Final report: verified / failed / untested.
