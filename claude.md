# Project: Round-Up Investing for Solana Seeker (hackathon, deadline Mon Oct 12 2026)

## Product
Acorns-style spare-change investing, crypto-native. When a user pays in USDC, the
app rounds the payment up to the next dollar and sets the difference aside in a
per-user on-chain vault. When the vault balance reaches a threshold, a keeper
sweeps it to Backpack Exchange and buys tokenized US stocks/ETFs (e.g. VOO.US,
QQQ.US). Target device: Solana Seeker (Android, Seed Vault, Mobile Wallet Adapter).

## Stack
- Android app: Kotlin, Jetpack Compose, Solana Mobile Wallet Adapter (MWA) client lib
- On-chain: Anchor (Rust) program, small and audited-by-eye, with hard balance caps
- Backend/keeper: Rust (axum, tokio), ED25519 signing, Backpack REST + WebSocket
- Network: Solana devnet for program dev; mainnet only for a tiny Backpack deposit test

## Architecture (do not redesign without asking)
1. PAYMENT TX: the app builds ONE Solana transaction containing (a) the payment
   SPL-USDC transfer to the merchant and (b) the round-up SPL-USDC transfer to
   the user's vault token account. User signs once via MWA signAndSendTransactions.
2. VAULT PROGRAM (Anchor), per-user PDA, three instructions only:
   - init_vault: stores owner, destination token account, keeper authority,
     threshold, max_balance cap.
   - sweep: callable only by the keeper; succeeds only if balance >= threshold;
     moves funds ONLY to the stored destination; never anywhere else.
   - withdraw: owner-signed; owner can pull their full balance anytime.
   Deposits need no instruction (plain SPL transfer into the vault token account).
3. KEEPER (Rust): watches vault balances, triggers sweep at threshold (or weekly
   if balance >= Backpack's minimum deposit), then runs the Backpack buy flow.
4. BACKPACK BUY FLOW: after the deposit credits, get an indicative quote, convert
   dollars to shares, submit an RFQ, accept the quote, track settlement.

Threshold rule: max($5, Backpack minimumDeposit for USDC on Solana, amount where
fixed costs are < ~1% of the sweep). Read minimumDeposit from GET /api/v1/assets.

## Backpack API facts (source: docs.backpack.exchange, OpenAPI spec in /docs/openapi.json)
The OpenAPI spec is the source of truth. Your training data is stale; the API
changed in the last week. Never invent endpoints, field names, or instruction
types. If something is not in the spec, ask me.
- Base: https://api.backpack.exchange, WS: wss://ws.backpack.exchange
- Signed requests need headers X-Timestamp (ms), X-Window (default 5000, max
  60000), X-API-Key (base64 ed25519 public key), X-Signature (base64).
- Signing string: take body (or query) keys, sort alphabetically, build a query
  string, prefix `instruction=<type>&`, append `&timestamp=<ts>&window=<w>`.
  The window is appended even when no X-Window header is sent (use 5000).
  Docs example: `instruction=orderCancel&orderId=28&symbol=BTC_USDT&timestamp=1614550000000&window=5000`.
  Batch order endpoints concatenate each order's string with `&`.
- Stocks: RFQ symbols look like AAPL.US_USDC_RFQ and are NOT in GET /markets.
  Discover via GET /api/v1/securities (includes minQuantity/stepSize per session).
- Stock RFQs take SHARE quantity. quoteQuantity (dollars) is NOT supported and
  will expire with no quotes. We must convert dollars to shares ourselves.
- GET /api/v1/stockIndicativeQuote needs no account or balance. Its price is a
  limit price (settles at that price or better); estimatedPrice is a display estimate.
- Use executionMode=Immediate with a limit price to auto-accept the first quote.
- Deferred settlement: accepting is binding and funds lock. Events on the
  account.rfqUpdate stream: rfqAccepted, rfqActive, rfqCandidate,
  rfqAcceptedBinding, rfqFilled. UI must show "pending" until rfqFilled.
- RFQ only works during stock market sessions (GET /api/v1/stockSessions,
  /stockHolidays). Outside sessions, listed stocks trade on a spot book (e.g.
  MU.US_USDC) via standard order endpoints.
- REMOVED: POST /api/v1/rfq/refresh. RENAMED: /market-sessions -> /stockSessions.
- No sandbox exists that we know of. Everything is real money. Cap exposure.

## Hard rules
- Money math: integer base units only (USDC has 6 decimals). Never use floats for amounts.
- Round-up = ceil to next whole dollar of the payment amount; if exactly whole, round up $1 or 0 (make it a config, default 0).
- Never commit secrets. API keys via env vars; .env in .gitignore; provide .env.example.
- Program holds real USDC: enforce max_balance cap and keep instructions minimal.
- No new dependencies without telling me why. Check installed Anchor/Solana/Rust versions first and match them.
- Write the failing test first for anything involving signing, round-up math, or vault rules.
- Small commits. After each task: what you did, what you verified, what is untested.
- If a requirement is ambiguous, ask one specific question instead of guessing.

## Repo layout
/program   Anchor program + tests
/keeper    Rust service (backpack client, signer, sweeper)
/android   Kotlin app
/docs      openapi.json, notes

## Task order (one workstream per agent session)
A. keeper/backpack: ED25519 signer + unit test reproducing the docs' orderCancel
   string exactly; public client (securities, stockSessions, stockIndicativeQuote);
   signed GET /account against a real key. DONE WHEN: signature test passes and
   /account returns 200.
B. keeper/backpack: print minQuantity/stepSize for VOO.US, AAPL.US, QQQ.US and
   USDC minimumDeposit. Then implement dollars->shares (floor to stepSize,
   reject below minQuantity) with tests. REPORT FINDINGS FIRST; if whole-share
   only, stop and ask me.
C. program: init_vault, sweep, withdraw with tests covering: sweep below
   threshold fails, sweep by non-keeper fails, sweep destination is fixed,
   withdraw by non-owner fails, deposits over cap rejected.
D. android: MWA connect on Seeker, build the bundled payment+round-up
   transaction, signAndSend, show vault balance and "$X of $5 to next buy".
E. keeper: sweep trigger -> Backpack deposit -> RFQ buy -> fill tracking -> status API for the app.

Start with workstream A. First, read /docs/openapi.json, summarize your plan in
under 15 lines, list assumptions, then wait for my go-ahead before writing code.