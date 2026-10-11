use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashMap;

pub const TOKEN_PROGRAM_ID: &str = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
pub const TOKEN_2022_PROGRAM_ID: &str = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
pub const USDC_MINT: &str = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";

// Lamports per byte-year on Solana mainnet (rent exemption is 2 years of rent: 2 * 3480 lamports/byte + 128 bytes overhead)
// Rent exempt minimum for N bytes: (128 + N) * 6960 lamports (approx 890880 for 165 bytes)
pub const LAMPORTS_PER_BYTE_YEAR: u64 = 3480;
pub const ACCOUNT_STORAGE_OVERHEAD: u64 = 128;

pub fn calculate_rent_exempt_lamports(account_data_len: usize) -> u64 {
    let bytes = (account_data_len as u64) + ACCOUNT_STORAGE_OVERHEAD;
    bytes * LAMPORTS_PER_BYTE_YEAR * 2
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum PolicyAction {
    OK,
    WARN,
    BLOCK,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct AuthoritiesInfo {
    pub mint_authority: Option<String>,
    pub freeze_authority: Option<String>,
    pub metadata_update_authority: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ProvenanceInfo {
    pub listed_by_backpack: bool,
    pub issuer_match: bool,
    pub issuer_authority: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyResult {
    pub action: PolicyAction,
    pub reasons: Vec<String>,
    pub disclosures: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BuyableResult {
    pub buyable: bool,
    pub reasons: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LiquidityInfo {
    pub route_found: bool,
    pub impact_bps_at_5_usd: Option<u32>,
    pub impact_bps_at_25_usd: Option<u32>,
    pub error: Option<String>,
    pub as_of: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtensionDetail {
    pub extension: String,
    pub state: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CatalogEntry {
    pub symbol: String,
    pub name: String,
    pub mint: String,
    pub token_program: String,
    pub decimals: u8,
    pub extensions: Vec<String>,
    pub extension_details: Vec<ExtensionDetail>,
    pub authorities: AuthoritiesInfo,
    pub provenance: ProvenanceInfo,
    pub policy: PolicyResult,
    pub buyable: BuyableResult,
    pub liquidity: LiquidityInfo,
    pub market_status: String,
    pub ata_rent_lamports: u64,
    pub extension_fingerprint: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CatalogSnapshot {
    pub version: u32,
    pub generated_at: String,
    pub total_entries: usize,
    pub buyable_entries: usize,
    pub entries: Vec<CatalogEntry>,
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct PolicyConfig {
    #[serde(default)]
    pub extensions: HashMap<String, ExtensionRule>,
    #[serde(default)]
    pub defaults: PolicyDefaults,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ExtensionRule {
    pub action: String,
    pub description: Option<String>,
    pub disclosure: Option<String>,
    #[serde(default)]
    pub allowed_programs: Vec<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct PolicyDefaults {
    #[serde(default = "default_unknown_ext")]
    pub unknown_extension: String,
    #[serde(default = "default_max_impact_5")]
    pub max_impact_bps_at_5_usd: u32,
    #[serde(default = "default_max_impact_25")]
    pub max_impact_bps_at_25_usd: u32,
    #[serde(default = "default_true")]
    pub min_liquidity_route: bool,
}

impl Default for PolicyDefaults {
    fn default() -> Self {
        Self {
            unknown_extension: default_unknown_ext(),
            max_impact_bps_at_5_usd: default_max_impact_5(),
            max_impact_bps_at_25_usd: default_max_impact_25(),
            min_liquidity_route: default_true(),
        }
    }
}

fn default_unknown_ext() -> String { "BLOCK".to_string() }
fn default_max_impact_5() -> u32 { 100 }
fn default_max_impact_25() -> u32 { 250 }
fn default_true() -> bool { true }

pub fn to_snake_case(s: &str) -> String {
    let mut result = String::new();
    for (i, ch) in s.chars().enumerate() {
        if ch.is_uppercase() {
            if i > 0 {
                result.push('_');
            }
            result.push(ch.to_ascii_lowercase());
        } else if ch == '-' || ch == ' ' {
            result.push('_');
        } else {
            result.push(ch);
        }
    }
    result
}

impl PolicyConfig {
    pub fn find_rule(&self, ext_name: &str) -> Option<&ExtensionRule> {
        let snake = to_snake_case(ext_name);
        if let Some(r) = self.extensions.get(&snake) {
            return Some(r);
        }
        // Try trimming _config, _mint
        if let Some(trimmed) = snake.strip_suffix("_config") {
            if let Some(r) = self.extensions.get(trimmed) {
                return Some(r);
            }
        }
        if let Some(trimmed) = snake.strip_suffix("_mint") {
            if let Some(r) = self.extensions.get(trimmed) {
                return Some(r);
            }
        }
        None
    }

    pub fn evaluate_extensions(&self, ext_details: &[ExtensionDetail]) -> PolicyResult {
        let mut overall_action = PolicyAction::OK;
        let mut reasons = Vec::new();
        let mut disclosures = Vec::new();

        for ext in ext_details {
            let snake = to_snake_case(&ext.extension);

            // Special handling for default_account_state:
            if snake == "default_account_state" {
                if let Some(state_val) = ext.state.get("accountState").and_then(|v| v.as_str()) {
                    if state_val.eq_ignore_ascii_case("frozen") {
                        overall_action = PolicyAction::BLOCK;
                        reasons.push("Default account state is frozen (BLOCK)".to_string());
                        continue;
                    }
                }
            }

            // Special handling for transfer_hook:
            if snake == "transfer_hook" {
                if let Some(prog) = ext.state.get("programId").and_then(|v| v.as_str()) {
                    if let Some(rule) = self.find_rule(&ext.extension) {
                        if !rule.allowed_programs.is_empty() && !rule.allowed_programs.iter().any(|p| p == prog) {
                            overall_action = PolicyAction::BLOCK;
                            reasons.push(format!("Transfer hook program {} not in allowed list", prog));
                            continue;
                        }
                    }
                }
            }

            if let Some(rule) = self.find_rule(&ext.extension) {
                let action = match rule.action.to_uppercase().as_str() {
                    "BLOCK" => PolicyAction::BLOCK,
                    "WARN" => PolicyAction::WARN,
                    _ => PolicyAction::OK,
                };
                if action == PolicyAction::BLOCK {
                    overall_action = PolicyAction::BLOCK;
                    reasons.push(rule.description.clone().unwrap_or_else(|| format!("Extension {} blocked", ext.extension)));
                } else if action == PolicyAction::WARN && overall_action != PolicyAction::BLOCK {
                    overall_action = PolicyAction::WARN;
                    if let Some(desc) = &rule.description {
                        reasons.push(desc.clone());
                    }
                }
                if let Some(disc) = &rule.disclosure {
                    disclosures.push(disc.clone());
                }
            } else {
                if self.defaults.unknown_extension.to_uppercase() == "BLOCK" {
                    overall_action = PolicyAction::BLOCK;
                    reasons.push(format!("Unknown extension {} blocked by default policy", ext.extension));
                } else {
                    if overall_action != PolicyAction::BLOCK {
                        overall_action = PolicyAction::WARN;
                    }
                    reasons.push(format!("Unknown extension {}", ext.extension));
                }
            }
        }

        PolicyResult {
            action: overall_action,
            reasons,
            disclosures,
        }
    }

    pub fn is_buyable(
        &self,
        policy: &PolicyResult,
        liquidity: &LiquidityInfo,
        market_status: &str,
    ) -> BuyableResult {
        let mut buyable = true;
        let mut reasons = Vec::new();

        if policy.action == PolicyAction::BLOCK {
            buyable = false;
            reasons.extend(policy.reasons.clone());
        }

        if !liquidity.route_found {
            buyable = false;
            let err = liquidity.error.clone().unwrap_or_else(|| "No Jupiter swap route found for $5 USDC".to_string());
            reasons.push(format!("No swap route found: {}", err));
        } else if let Some(impact) = liquidity.impact_bps_at_5_usd {
            if impact > self.defaults.max_impact_bps_at_5_usd {
                buyable = false;
                reasons.push(format!(
                    "Price impact {} bps exceeds threshold ({} bps)",
                    impact, self.defaults.max_impact_bps_at_5_usd
                ));
            }
        }

        if market_status.eq_ignore_ascii_case("closed") || market_status.eq_ignore_ascii_case("holiday") {
            reasons.push(format!("Market is currently {}", market_status));
        }

        BuyableResult { buyable, reasons }
    }
}

/// Compute stable extension fingerprint hash:
/// SHA-256 over canonical string `owner_program:decimals:[sorted_extensions_joined_by_comma]`
pub fn compute_extension_fingerprint(
    owner_program: &str,
    decimals: u8,
    extensions: &[String],
) -> String {
    let mut sorted_exts = extensions.to_vec();
    sorted_exts.sort();
    let canonical = format!(
        "{}:{}:{}",
        owner_program,
        decimals,
        sorted_exts.join(",")
    );
    let mut hasher = Sha256::new();
    hasher.update(canonical.as_bytes());
    hex::encode(hasher.finalize())
}

/// Calculate scaled UI amount: value = (raw * multiplier) / 10^decimals
pub fn calculate_scaled_ui_amount(raw_amount: u64, multiplier: f64, decimals: u8) -> f64 {
    let divisor = 10_f64.powi(decimals as i32);
    (raw_amount as f64) * multiplier / divisor
}

/// Calculate transfer fee: fee = min(max_fee, ceil(amount * fee_bps / 10000))
pub fn calculate_transfer_fee(amount: u64, fee_bps: u16, max_fee: u64) -> u64 {
    if fee_bps == 0 {
        return 0;
    }
    let numerator = (amount as u128) * (fee_bps as u128) + 9999;
    let computed_fee = (numerator / 10000) as u64;
    computed_fee.min(max_fee)
}

#[derive(Debug, Clone, Deserialize)]
pub struct ParsedMintAccount {
    pub decimals: u8,
    #[serde(rename = "isInitialized", default)]
    pub is_initialized: bool,
    #[serde(rename = "mintAuthority")]
    pub mint_authority: Option<String>,
    #[serde(rename = "freezeAuthority")]
    pub freeze_authority: Option<String>,
    #[serde(default)]
    pub supply: String,
    #[serde(default)]
    pub extensions: Vec<ExtensionDetail>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_to_snake_case() {
        assert_eq!(to_snake_case("metadataPointer"), "metadata_pointer");
        assert_eq!(to_snake_case("permanentDelegate"), "permanent_delegate");
        assert_eq!(to_snake_case("scaledUiAmountConfig"), "scaled_ui_amount_config");
        assert_eq!(to_snake_case("pausableConfig"), "pausable_config");
        assert_eq!(to_snake_case("transfer-fee"), "transfer_fee");
    }

    #[test]
    fn test_extension_fingerprint_deterministic() {
        let p = TOKEN_2022_PROGRAM_ID;
        let dec = 6;
        let exts1 = vec!["transfer_fee".to_string(), "permanent_delegate".to_string()];
        let exts2 = vec!["permanent_delegate".to_string(), "transfer_fee".to_string()];

        let fp1 = compute_extension_fingerprint(p, dec, &exts1);
        let fp2 = compute_extension_fingerprint(p, dec, &exts2);

        assert_eq!(fp1, fp2);
        assert_eq!(fp1.len(), 64);
    }

    #[test]
    fn test_fingerprint_test_vectors() {
        // Known vector 1: Standard Token program, 6 decimals, no extensions
        let fp_token = compute_extension_fingerprint(TOKEN_PROGRAM_ID, 6, &[]);
        let expected_canonical_token = format!("{}:6:", TOKEN_PROGRAM_ID);
        let mut hasher = Sha256::new();
        hasher.update(expected_canonical_token.as_bytes());
        let expected_token = hex::encode(hasher.finalize());
        assert_eq!(fp_token, expected_token);

        // Known vector 2: Token-2022 with permanent_delegate
        let fp_t22 = compute_extension_fingerprint(TOKEN_2022_PROGRAM_ID, 6, &["permanent_delegate".to_string()]);
        let expected_canonical_t22 = format!("{}:6:permanent_delegate", TOKEN_2022_PROGRAM_ID);
        let mut hasher2 = Sha256::new();
        hasher2.update(expected_canonical_t22.as_bytes());
        let expected_t22 = hex::encode(hasher2.finalize());
        assert_eq!(fp_t22, expected_t22);
    }

    #[test]
    fn test_policy_evaluation() {
        let toml_content = r#"
        [extensions.permanent_delegate]
        action = "WARN"
        description = "Issuer holds permanent delegate"
        disclosure = "Issuer can transfer tokens"

        [extensions.non_transferable]
        action = "BLOCK"
        description = "Non-transferable tokens"

        [defaults]
        unknown_extension = "BLOCK"
        "#;

        let policy: PolicyConfig = toml::from_str(toml_content).unwrap();

        let ext_warn = vec![ExtensionDetail {
            extension: "permanentDelegate".to_string(),
            state: serde_json::json!({}),
        }];
        let res_warn = policy.evaluate_extensions(&ext_warn);
        assert_eq!(res_warn.action, PolicyAction::WARN);
        assert_eq!(res_warn.disclosures.len(), 1);

        let ext_block = vec![ExtensionDetail {
            extension: "nonTransferable".to_string(),
            state: serde_json::json!({}),
        }];
        let res_block = policy.evaluate_extensions(&ext_block);
        assert_eq!(res_block.action, PolicyAction::BLOCK);

        let ext_unknown = vec![ExtensionDetail {
            extension: "mysteriousExtension".to_string(),
            state: serde_json::json!({}),
        }];
        let res_unknown = policy.evaluate_extensions(&ext_unknown);
        assert_eq!(res_unknown.action, PolicyAction::BLOCK);
    }

    #[test]
    fn test_extension_parsing() {
        let raw_json = r#"{
            "decimals": 6,
            "isInitialized": true,
            "mintAuthority": "J72EWDEiYzfxedF1kxXjkkJBULcoDFjKFeY6KUo17hDN",
            "freezeAuthority": "2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a",
            "supply": "1000000",
            "extensions": [
                {
                    "extension": "permanentDelegate",
                    "state": { "delegate": "2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a" }
                },
                {
                    "extension": "scaledUiAmountConfig",
                    "state": { "multiplier": "1", "newMultiplier": "1" }
                }
            ]
        }"#;

        let parsed: ParsedMintAccount = serde_json::from_str(raw_json).expect("failed to parse mint");
        assert_eq!(parsed.decimals, 6);
        assert_eq!(parsed.extensions.len(), 2);
        assert_eq!(parsed.extensions[0].extension, "permanentDelegate");
    }

    #[test]
    fn test_transfer_fee_math() {
        let fee = calculate_transfer_fee(100_000_000, 25, 1_000_000);
        assert_eq!(fee, 250_000);

        let fee_capped = calculate_transfer_fee(1_000_000_000, 50, 100_000);
        assert_eq!(fee_capped, 100_000);

        let fee_round = calculate_transfer_fee(1, 1, 100);
        assert_eq!(fee_round, 1);
    }

    #[test]
    fn test_scaled_ui_math() {
        let val = calculate_scaled_ui_amount(1_000_000, 2.5, 6);
        assert!((val - 2.5).abs() < 1e-6);
    }

    #[test]
    fn test_snapshot_schema_round_trip() {
        let entry = CatalogEntry {
            symbol: "VOO.US".to_string(),
            name: "Vanguard S&P 500 ETF".to_string(),
            mint: "Vv7EU4aoHNY6wFsFRUyXhGm2syKXBA2T6v2MZUZZo7E".to_string(),
            token_program: TOKEN_2022_PROGRAM_ID.to_string(),
            decimals: 6,
            extensions: vec!["permanentDelegate".to_string()],
            extension_details: vec![ExtensionDetail {
                extension: "permanentDelegate".to_string(),
                state: serde_json::json!({ "delegate": "2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a" }),
            }],
            authorities: AuthoritiesInfo {
                mint_authority: Some("J72EWDEiYzfxedF1kxXjkkJBULcoDFjKFeY6KUo17hDN".to_string()),
                freeze_authority: Some("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a".to_string()),
                metadata_update_authority: Some("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a".to_string()),
            },
            provenance: ProvenanceInfo {
                listed_by_backpack: true,
                issuer_match: true,
                issuer_authority: Some("2cVYpagTt7ZGc3mmTXBa7fAznUtx5DUu6aCq8uVDaf4a".to_string()),
            },
            policy: PolicyResult {
                action: PolicyAction::WARN,
                reasons: vec!["Permanent delegate active".to_string()],
                disclosures: vec!["Issuer can transfer tokens".to_string()],
            },
            buyable: BuyableResult {
                buyable: false,
                reasons: vec!["No liquidity".to_string()],
            },
            liquidity: LiquidityInfo {
                route_found: false,
                impact_bps_at_5_usd: None,
                impact_bps_at_25_usd: None,
                error: Some("TOKEN_NOT_TRADABLE".to_string()),
                as_of: "2026-10-09T12:00:00Z".to_string(),
            },
            market_status: "open".to_string(),
            ata_rent_lamports: 2039280,
            extension_fingerprint: "abc123hash".to_string(),
        };

        let snapshot = CatalogSnapshot {
            version: 1,
            generated_at: "2026-10-09T12:00:00Z".to_string(),
            total_entries: 1,
            buyable_entries: 0,
            entries: vec![entry],
        };

        let json_str = serde_json::to_string_pretty(&snapshot).expect("serialize failed");
        let deserialized: CatalogSnapshot = serde_json::from_str(&json_str).expect("deserialize failed");
        assert_eq!(deserialized.version, 1);
        assert_eq!(deserialized.entries.len(), 1);
        assert_eq!(deserialized.entries[0].symbol, "VOO.US");
    }

    #[test]
    fn test_rent_exempt_calculation() {
        // Standard SPL Token account: 165 bytes -> (165 + 128) * 3480 * 2 = 293 * 6960 = 2,039,280 lamports
        let lamports_165 = calculate_rent_exempt_lamports(165);
        assert_eq!(lamports_165, 2039280);
    }
}
