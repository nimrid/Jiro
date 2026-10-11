use crate::client::BackpackClient;
use crate::signer::BackpackSigner;
use axum::{
    extract::{Query, State},
    http::{HeaderMap, HeaderValue, StatusCode},
    response::IntoResponse,
    routing::get,
    Json, Router,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tracing::{info, warn};

#[derive(Clone)]
pub struct AppState {
    pub backpack: Arc<BackpackClient>,
    pub jupiter_api_key: Option<String>,
    pub solana_rpc_url: String,
    pub minimum_deposit_usdc: u64,
    pub sweep_threshold_usdc: u64,
    pub catalog_path: Option<String>,
    pub http_client: reqwest::Client,
}

#[derive(Serialize)]
pub struct HealthResponse {
    pub status: &'static str,
    pub service: &'static str,
    pub version: &'static str,
    pub timestamp: u64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct KeeperStatusResponse {
    pub healthy: bool,
    pub minimum_deposit_usdc: u64,
    pub sweep_threshold_usdc: u64,
    pub last_sweep_tx: Option<String>,
    pub last_buy_order: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterQuoteQuery {
    pub input_mint: String,
    pub output_mint: String,
    pub amount: u64,
    pub slippage_bps: Option<u16>,
    pub taker: Option<String>,
}

#[derive(Deserialize)]
pub struct BackpackQuoteQuery {
    pub symbol: String,
    pub side: String,
    pub quantity: String,
}

pub fn create_router(state: AppState) -> Router {
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    Router::new()
        .route("/healthz", get(healthz_handler))
        .route("/status", get(status_handler))
        .route("/v1/catalog", get(catalog_handler))
        .route("/v1/quote", get(jupiter_quote_handler))
        .route("/v1/build", get(jupiter_build_handler))
        .route("/v1/backpack/assets", get(backpack_assets_handler))
        .route("/v1/backpack/securities", get(backpack_securities_handler))
        .route("/v1/backpack/quote", get(backpack_quote_handler))
        .layer(cors)
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

/// GET /healthz - Render uptime & health probe
async fn healthz_handler() -> Json<HealthResponse> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();

    Json(HealthResponse {
        status: "ok",
        service: "jiro-keeper",
        version: env!("CARGO_PKG_VERSION"),
        timestamp: now,
    })
}

/// GET /status - Consumed by Android KeeperApi.kt
async fn status_handler(State(state): State<AppState>) -> Json<KeeperStatusResponse> {
    Json(KeeperStatusResponse {
        healthy: true,
        minimum_deposit_usdc: state.minimum_deposit_usdc,
        sweep_threshold_usdc: state.sweep_threshold_usdc,
        last_sweep_tx: None,
        last_buy_order: None,
    })
}

/// GET /v1/catalog - Serves verified stock catalog
async fn catalog_handler(State(state): State<AppState>) -> Result<impl IntoResponse, StatusCode> {
    let path = state
        .catalog_path
        .as_deref()
        .unwrap_or("docs/catalog_snapshot.json");

    match tokio::fs::read_to_string(path).await {
        Ok(content) => {
            let mut headers = HeaderMap::new();
            headers.insert("content-type", HeaderValue::from_static("application/json"));
            Ok((StatusCode::OK, headers, content))
        }
        Err(e) => {
            warn!("Could not read catalog at {}: {}", path, e);
            Err(StatusCode::NOT_FOUND)
        }
    }
}

/// GET /v1/quote - Proxies to Jupiter Swap API quote endpoint hiding server-side API key
async fn jupiter_quote_handler(
    State(state): State<AppState>,
    Query(params): Query<JupiterQuoteQuery>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let mut url = reqwest::Url::parse("https://api.jup.ag/swap/v2/quote")
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    {
        let mut q = url.query_pairs_mut();
        q.append_pair("inputMint", &params.input_mint);
        q.append_pair("outputMint", &params.output_mint);
        q.append_pair("amount", &params.amount.to_string());
        if let Some(bps) = params.slippage_bps {
            q.append_pair("slippageBps", &bps.to_string());
        }
    }

    let mut req = state.http_client.get(url);
    if let Some(key) = &state.jupiter_api_key {
        if !key.trim().is_empty() {
            req = req.header("x-api-key", key.trim());
        }
    }

    let resp = req
        .send()
        .await
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))?;

    let status = resp.status();
    let body = resp
        .text()
        .await
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))?;

    let mut headers = HeaderMap::new();
    headers.insert("content-type", HeaderValue::from_static("application/json"));
    Ok((status, headers, body))
}

/// GET /v1/build - Proxies to Jupiter Swap API V2 /build endpoint hiding server-side API key
async fn jupiter_build_handler(
    State(state): State<AppState>,
    Query(params): Query<JupiterQuoteQuery>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let mut url = reqwest::Url::parse("https://api.jup.ag/swap/v2/build")
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    let taker = params
        .taker
        .unwrap_or_else(|| "11111111111111111111111111111111".to_string());
    let slippage = params.slippage_bps.unwrap_or(100);

    {
        let mut q = url.query_pairs_mut();
        q.append_pair("inputMint", &params.input_mint);
        q.append_pair("outputMint", &params.output_mint);
        q.append_pair("amount", &params.amount.to_string());
        q.append_pair("taker", &taker);
        q.append_pair("slippageBps", &slippage.to_string());
        q.append_pair("maxAccounts", "64");
        q.append_pair("transactionVersion", "0");
    }

    let mut req = state.http_client.get(url);
    if let Some(key) = &state.jupiter_api_key {
        if !key.trim().is_empty() {
            req = req.header("x-api-key", key.trim());
        }
    }

    let resp = req
        .send()
        .await
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))?;

    let status = resp.status();
    let body = resp
        .text()
        .await
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))?;

    let mut headers = HeaderMap::new();
    headers.insert("content-type", HeaderValue::from_static("application/json"));
    Ok((status, headers, body))
}

/// GET /v1/backpack/assets
async fn backpack_assets_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    state
        .backpack
        .get_assets()
        .await
        .map(|assets| Json(serde_json::to_value(assets).unwrap_or_default()))
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))
}

/// GET /v1/backpack/securities
async fn backpack_securities_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    state
        .backpack
        .get_securities()
        .await
        .map(|sec| Json(serde_json::to_value(sec).unwrap_or_default()))
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))
}

/// GET /v1/backpack/quote
async fn backpack_quote_handler(
    State(state): State<AppState>,
    Query(params): Query<BackpackQuoteQuery>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    state
        .backpack
        .get_stock_indicative_quote(&params.symbol, &params.side, &params.quantity)
        .await
        .map(|q| Json(serde_json::to_value(q).unwrap_or_default()))
        .map_err(|e| (StatusCode::BAD_GATEWAY, e.to_string()))
}

pub async fn start_server() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();

    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "keeper=info,tower_http=info".into()),
        )
        .init();

    info!("Starting JIRO Keeper Backend Service...");

    // Signer from environment (if provided)
    let signer = match BackpackSigner::from_env() {
        Ok(s) => {
            info!("Backpack ED25519 signer initialized successfully.");
            Some(s)
        }
        Err(e) => {
            warn!("Running without Backpack signer: {}", e);
            None
        }
    };

    let backpack = Arc::new(BackpackClient::new(None, signer));
    let jupiter_api_key = std::env::var("JUPITER_API_KEY").ok();
    let solana_rpc_url = std::env::var("SOLANA_RPC_URL")
        .unwrap_or_else(|_| "https://api.devnet.solana.com".to_string());

    let catalog_path = if std::path::Path::new("docs/catalog_snapshot.json").exists() {
        Some("docs/catalog_snapshot.json".to_string())
    } else if std::path::Path::new("catalog_snapshot.json").exists() {
        Some("catalog_snapshot.json".to_string())
    } else {
        None
    };

    let state = AppState {
        backpack,
        jupiter_api_key,
        solana_rpc_url,
        minimum_deposit_usdc: 5_000_000, // 5.00 USDC
        sweep_threshold_usdc: 5_000_000, // 5.00 USDC
        catalog_path,
        http_client: reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(15))
            .build()?,
    };

    let app = create_router(state);

    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(3000);

    let listener = tokio::net::TcpListener::bind(format!("0.0.0.0:{}", port)).await?;
    info!("JIRO Keeper Service listening on http://0.0.0.0:{}", port);
    info!("  Health check: http://0.0.0.0:{}/healthz", port);
    info!("  Status:       http://0.0.0.0:{}/status", port);
    info!("  Catalog:      http://0.0.0.0:{}/v1/catalog", port);
    info!("  Quote Proxy:  http://0.0.0.0:{}/v1/quote", port);

    axum::serve(listener, app).await?;

    Ok(())
}
