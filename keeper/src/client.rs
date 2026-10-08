use crate::signer::BackpackSigner;
use crate::types::{AccountSummary, IndicativeQuoteResponse, MarketAsset, MarketSession, Security};
use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use std::time::{SystemTime, UNIX_EPOCH};
use thiserror::Error;

pub const DEFAULT_BASE_URL: &str = "https://api.backpack.exchange";
pub const DEFAULT_WINDOW_MS: u64 = 5000;

#[derive(Error, Debug)]
pub enum ClientError {
    #[error("HTTP request failed: {0}")]
    Reqwest(#[from] reqwest::Error),
    #[error("Signer missing for authenticated request")]
    MissingSigner,
    #[error("API error (status {status}): {body}")]
    ApiError {
        status: reqwest::StatusCode,
        body: String,
    },
    #[error("Failed to parse JSON response: {0}")]
    Json(#[from] serde_json::Error),
}

pub struct BackpackClient {
    base_url: String,
    http_client: reqwest::Client,
    signer: Option<BackpackSigner>,
}

impl BackpackClient {
    pub fn new(base_url: Option<String>, signer: Option<BackpackSigner>) -> Self {
        Self {
            base_url: base_url.unwrap_or_else(|| DEFAULT_BASE_URL.to_string()),
            http_client: reqwest::Client::new(),
            signer,
        }
    }

    pub fn set_signer(&mut self, signer: BackpackSigner) {
        self.signer = Some(signer);
    }

    /// Build authentication headers for signed requests.
    fn build_auth_headers(
        &self,
        instruction: &str,
        params: &[(&str, &str)],
    ) -> Result<HeaderMap, ClientError> {
        let signer = self.signer.as_ref().ok_or(ClientError::MissingSigner)?;
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("Time went backwards")
            .as_millis() as u64;
        let window = DEFAULT_WINDOW_MS;

        let (_signing_string, signature) = signer.sign(instruction, params, timestamp, window);

        let mut headers = HeaderMap::new();
        headers.insert(
            HeaderName::from_static("x-api-key"),
            HeaderValue::from_str(&signer.verifying_key_base64()).expect("valid header value"),
        );
        headers.insert(
            HeaderName::from_static("x-signature"),
            HeaderValue::from_str(&signature).expect("valid header value"),
        );
        headers.insert(
            HeaderName::from_static("x-timestamp"),
            HeaderValue::from_str(&timestamp.to_string()).expect("valid header value"),
        );
        headers.insert(
            HeaderName::from_static("x-window"),
            HeaderValue::from_str(&window.to_string()).expect("valid header value"),
        );

        Ok(headers)
    }

    /// GET /api/v1/securities (Public)
    pub async fn get_securities(&self) -> Result<Vec<Security>, ClientError> {
        let url = format!("{}/api/v1/securities", self.base_url);
        let resp = self.http_client.get(&url).send().await?;
        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            return Err(ClientError::ApiError { status, body });
        }
        Ok(resp.json::<Vec<Security>>().await?)
    }

    /// GET /api/v1/stockSessions (Public)
    pub async fn get_stock_sessions(&self) -> Result<Vec<MarketSession>, ClientError> {
        let url = format!("{}/api/v1/stockSessions", self.base_url);
        let resp = self.http_client.get(&url).send().await?;
        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            return Err(ClientError::ApiError { status, body });
        }
        Ok(resp.json::<Vec<MarketSession>>().await?)
    }

    /// GET /api/v1/stockIndicativeQuote (Public)
    pub async fn get_stock_indicative_quote(
        &self,
        symbol: &str,
        side: &str,
        quantity: &str,
    ) -> Result<IndicativeQuoteResponse, ClientError> {
        let url = format!("{}/api/v1/stockIndicativeQuote", self.base_url);
        let resp = self
            .http_client
            .get(&url)
            .query(&[("symbol", symbol), ("side", side), ("quantity", quantity)])
            .send()
            .await?;
        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            return Err(ClientError::ApiError { status, body });
        }
        Ok(resp.json::<IndicativeQuoteResponse>().await?)
    }

    /// GET /api/v1/assets (Public)
    pub async fn get_assets(&self) -> Result<Vec<MarketAsset>, ClientError> {
        let url = format!("{}/api/v1/assets", self.base_url);
        let resp = self.http_client.get(&url).send().await?;
        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            return Err(ClientError::ApiError { status, body });
        }
        Ok(resp.json::<Vec<MarketAsset>>().await?)
    }

    /// GET /api/v1/account (Signed, instruction: accountQuery)
    pub async fn get_account(&self) -> Result<AccountSummary, ClientError> {
        let headers = self.build_auth_headers("accountQuery", &[])?;
        let url = format!("{}/api/v1/account", self.base_url);
        let resp = self
            .http_client
            .get(&url)
            .headers(headers)
            .send()
            .await?;
        if !resp.status().is_success() {
            let status = resp.status();
            let body = resp.text().await.unwrap_or_default();
            return Err(ClientError::ApiError { status, body });
        }
        Ok(resp.json::<AccountSummary>().await?)
    }
}
