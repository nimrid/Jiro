# ==========================================
# Multi-stage Dockerfile for JIRO Keeper Service
# ==========================================

# --- Stage 1: Build binary ---
FROM rust:1.81-slim-bookworm AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config \
    libssl-dev \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy workspace manifests
COPY Cargo.toml Cargo.lock ./
COPY crates/ crates/
COPY tools/ tools/
COPY keeper/ keeper/
COPY docs/ docs/

# Build release binary for keeper service
RUN cargo build --release -p keeper

# --- Stage 2: Minimal runtime image ---
FROM debian:bookworm-slim AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libssl3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy compiled keeper binary from builder
COPY --from=builder /app/target/release/keeper /usr/local/bin/keeper

# Copy catalog snapshot for /v1/catalog endpoint
COPY --from=builder /app/docs/catalog_snapshot.json /app/docs/catalog_snapshot.json

ENV PORT=3000
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3000/healthz || exit 1

CMD ["keeper"]
