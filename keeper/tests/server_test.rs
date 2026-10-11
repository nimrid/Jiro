use axum::{
    body::Body,
    http::{Request, StatusCode},
};
use keeper::client::BackpackClient;
use keeper::server::{create_router, AppState};
use std::sync::Arc;
use tower::ServiceExt;

fn test_state() -> AppState {
    AppState {
        backpack: Arc::new(BackpackClient::new(None, None)),
        jupiter_api_key: None,
        solana_rpc_url: "https://api.devnet.solana.com".to_string(),
        minimum_deposit_usdc: 5_000_000,
        sweep_threshold_usdc: 5_000_000,
        catalog_path: None,
        http_client: reqwest::Client::new(),
    }
}

#[tokio::test]
async fn test_healthz_endpoint() {
    let app = create_router(test_state());

    let response = app
        .oneshot(
            Request::builder()
                .uri("/healthz")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);

    let body = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

    assert_eq!(json["status"], "ok");
    assert_eq!(json["service"], "jiro-keeper");
}

#[tokio::test]
async fn test_status_endpoint_matches_android_keeper_api() {
    let app = create_router(test_state());

    let response = app
        .oneshot(
            Request::builder()
                .uri("/status")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);

    let body = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

    assert_eq!(json["healthy"], true);
    assert_eq!(json["minimumDepositUsdc"], 5000000);
    assert_eq!(json["sweepThresholdUsdc"], 5000000);
}
