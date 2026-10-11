# Workstream F: catalog service (Rust, axum)
Reuse `crates/catalog-core`. Keys live here only: SOLANA_RPC_URL, JUPITER_API_KEY (if needed), CATALOG_SIGNING_KEY.
- Background jobs: assets fetch (hourly), on-chain verification, liquidity probes ($5/$25, throttled), market sessions (daily),
  prices (cached 10-30s).
- Endpoints (document in docs/service-openapi.yaml): GET /v1/catalog (signed JSON + version + generatedAt), GET /v1/prices,
  GET /v1/quote and POST /v1/swap-instructions (Jupiter proxy that hides the key; the app still validates everything),
  GET /healthz.
- Catalog signing: canonical JSON, ed25519, publish the public key for embedding in the app. Never serve unsigned data.
- Never drop entries: serve BLOCK and not-buyable entries with reasons. Serve last-good if a refresh fails and expose its age.
- Rate limiting, request size limits, timeouts, structured logs with secrets and wallet addresses redacted, no user tracking.
- Tests: policy engine reuse, signature round-trip, stale-serving behavior, proxy error mapping, contract tests with the Kotlin client
  fixtures. Dockerfile and .env.example.
Final report: verified / failed / untested.
