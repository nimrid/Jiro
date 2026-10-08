use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Security {
    pub asset: String,
    pub name: String,
    #[serde(default)]
    pub cusip: Option<String>,
    #[serde(default)]
    pub sessions: Vec<SecuritySession>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SecuritySession {
    pub name: String,
    pub min_quantity: String,
    pub step_size: String,
    #[serde(default)]
    pub max_quantity: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MarketSession {
    pub name: String,
    #[serde(default)]
    pub description: Option<String>,
    pub start_time: String,
    pub end_time: String,
    pub timezone: String,
    pub start_weekday: i32,
    pub end_weekday: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IndicativeQuoteResponse {
    pub symbol: String,
    pub side: String,
    pub quantity: String,
    pub available: bool,
    #[serde(default)]
    pub reason: Option<String>,
    #[serde(default)]
    pub price: Option<String>,
    #[serde(default)]
    pub estimated_price: Option<String>,
    #[serde(default)]
    pub quote_quantity: Option<String>,
    #[serde(default)]
    pub session: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MarketAsset {
    pub symbol: String,
    pub display_name: String,
    #[serde(default)]
    pub coingecko_id: Option<String>,
    #[serde(default)]
    pub tokens: Vec<Token>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Token {
    pub display_name: String,
    pub blockchain: String,
    #[serde(default)]
    pub contract_address: Option<String>,
    pub deposit_enabled: bool,
    #[serde(default)]
    pub minimum_deposit: Option<String>,
    pub withdraw_enabled: bool,
    #[serde(default)]
    pub minimum_withdrawal: Option<String>,
    #[serde(default)]
    pub maximum_withdrawal: Option<String>,
    #[serde(default)]
    pub withdrawal_fee: Option<String>,
    #[serde(default)]
    pub native_decimals: Option<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountSummary {
    #[serde(default)]
    pub auto_borrow_settlements: bool,
    #[serde(default)]
    pub auto_lend: bool,
    #[serde(default)]
    pub auto_realize_pnl: bool,
    #[serde(default)]
    pub auto_repay_borrows: bool,
    #[serde(default)]
    pub borrow_limit: Option<String>,
    #[serde(default)]
    pub leverage_limit: Option<u32>,
    #[serde(default)]
    pub limit_orders: Option<u32>,
    #[serde(default)]
    pub position_limit: Option<String>,
    #[serde(flatten)]
    pub extra: HashMap<String, serde_json::Value>,
}
