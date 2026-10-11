# Workstream E: Jupiter integration and mainnet smoke test
1. READ current official Jupiter developer docs. Report: base URLs, auth/API key requirements, rate limits, quote and
   swap-instructions endpoints, parameter names, program ID (record the doc URL in a code comment). Wait for confirmation if a
   paid plan or key is required.
2. Implement JupiterSwapProvider: ExactIn USDC -> stock mint, slippage from config, retrieve instructions + ALT addresses, fetch ALT
   accounts, compose with vault.withdraw into one v0 transaction (InvestTxBuilder), respect compute-budget guidance from the API.
3. Quote policy: 30s expiry, refresh before signing, reject price impact over the cap, reject routes whose output mint program does
   not match the catalog, account for transfer-fee and scaled-UI extensions in expected output.
4. Run SwapTxValidator on every built transaction; add contract tests with recorded fixtures plus a live test behind a flag.
5. Failure handling: route disappears, blockhash expiry, size overflow -> two-transaction fallback, simulation mismatch -> abort
   with the reason, user decline, partial failure (withdraw succeeded, swap failed) -> funds are in the user's wallet; show a
   clear recovery state with a retry.
6. SMOKE TEST (mainnet): throwaway wallet funded <= $25. Pay with round-up -> deposit -> invest about $3-5 into one buyable stock
   with Deep liquidity. Record signatures, fees, price impact, and realized vs quoted output in docs/SMOKE_TEST.md.
Final report: verified / failed / untested.
