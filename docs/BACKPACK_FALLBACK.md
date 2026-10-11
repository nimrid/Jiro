# Backpack Exchange RFQ API (Fallback Path)

Backpack's exchange RFQ API is a FALLBACK path only. The primary path is Backpack Securities tokenized stocks on Solana swapped via Jupiter.

## Backpack API facts (source: docs.backpack.exchange, OpenAPI spec in /docs/openapi.json)
The OpenAPI spec is the source of truth.
- Base: https://api.backpack.exchange, WS: wss://ws.backpack.exchange
- Signed requests need headers X-Timestamp (ms), X-Window (default 5000, max 60000), X-API-Key (base64 ed25519 public key), X-Signature (base64).
- Signing string: take body (or query) keys, sort alphabetically, build a query string, prefix `instruction=<type>&`, append `&timestamp=<ts>&window=<w>`. The window is appended even when no X-Window header is sent (use 5000). Docs example: `instruction=orderCancel&orderId=28&symbol=BTC_USDT&timestamp=1614550000000&window=5000`. Batch order endpoints concatenate each order's string with `&`.
- Stocks: RFQ symbols look like AAPL.US_USDC_RFQ and are NOT in GET /markets. Discover via GET /api/v1/securities (includes minQuantity/stepSize per session).
- Stock RFQs take SHARE quantity. quoteQuantity (dollars) is NOT supported and will expire with no quotes. We must convert dollars to shares ourselves.
- GET /api/v1/stockIndicativeQuote needs no account or balance. Its price is a limit price (settles at that price or better); estimatedPrice is a display estimate.
- Use executionMode=Immediate with a limit price to auto-accept the first quote.
- Deferred settlement: accepting is binding and funds lock. Events on the account.rfqUpdate stream: rfqAccepted, rfqActive, rfqCandidate, rfqAcceptedBinding, rfqFilled. UI must show "pending" until rfqFilled.
- RFQ only works during stock market sessions (GET /api/v1/stockSessions, /stockHolidays). Outside sessions, listed stocks trade on a spot book (e.g. MU.US_USDC) via standard order endpoints.
- REMOVED: POST /api/v1/rfq/refresh. RENAMED: /market-sessions -> /stockSessions.
- No sandbox exists that we know of. Everything is real money. Cap exposure.
