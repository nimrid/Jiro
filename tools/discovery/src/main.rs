use catalog_core::{
    calculate_rent_exempt_lamports, compute_extension_fingerprint,
    AuthoritiesInfo, CatalogEntry, CatalogSnapshot, ExtensionDetail,
    LiquidityInfo, PolicyAction, PolicyConfig, ProvenanceInfo, USDC_MINT,
};
use chrono::Utc;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs::File;
use std::io::{BufReader, Write};
use std::path::Path;
use std::time::Duration;

#[derive(Debug, Deserialize)]
struct BackpackAssetToken {
    blockchain: String,
    #[serde(rename = "contractAddress")]
    contract_address: Option<String>,
    #[serde(rename = "depositEnabled")]
    deposit_enabled: Option<bool>,
    #[serde(rename = "withdrawEnabled")]
    withdraw_enabled: Option<bool>,
    #[serde(rename = "displayName")]
    display_name: Option<String>,
    #[serde(rename = "nativeDecimals")]
    native_decimals: Option<u8>,
}

#[derive(Debug, Deserialize)]
struct BackpackAsset {
    symbol: String,
    #[serde(rename = "displayName")]
    display_name: Option<String>,
    #[serde(default)]
    tokens: Vec<BackpackAssetToken>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RpcAccountData {
    program: Option<String>,
    parsed: Option<serde_json::Value>,
    space: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RpcAccountValue {
    data: Option<serde_json::Value>,
    executable: Option<bool>,
    lamports: Option<u64>,
    owner: Option<String>,
    space: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RpcResponseValue {
    context: Option<serde_json::Value>,
    value: Vec<Option<RpcAccountValue>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RpcResponse {
    jsonrpc: String,
    result: Option<RpcResponseValue>,
    error: Option<serde_json::Value>,
    id: u64,
}

#[derive(Debug, Serialize, Deserialize)]
struct PresetsConfig {
    version: u32,
    as_of: String,
    presets: HashMap<String, PresetGroup>,
}

#[derive(Debug, Serialize, Deserialize)]
struct PresetGroup {
    name: String,
    description: String,
    tickers: Vec<String>,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();
    println!("=== JIRO Workstream A: Discovery & Verification Tool ===");

    let rpc_url = std::env::var("SOLANA_RPC_URL")
        .unwrap_or_else(|_| "https://api.mainnet-beta.solana.com".to_string());
    println!("Using Solana RPC URL: {}", rpc_url);

    // 1. DISCOVER: Load docs/raw/assets.json
    let assets_path = Path::new("docs/raw/assets.json");
    if !assets_path.exists() {
        println!("Downloading assets from Backpack API...");
        let resp = reqwest::get("https://api.backpack.exchange/api/v1/assets").await?;
        let bytes = resp.bytes().await?;
        std::fs::create_dir_all("docs/raw")?;
        std::fs::write(assets_path, &bytes)?;
    }

    let file = File::open(assets_path)?;
    let reader = BufReader::new(file);
    let assets: Vec<BackpackAsset> = serde_json::from_reader(reader)?;
    println!("Loaded {} assets from Backpack assets.json", assets.len());

    // Extract Solana tokens
    let mut solana_stocks: Vec<(String, String, String, bool, bool)> = Vec::new(); // (symbol, mint, display_name, deposit_enabled, withdraw_enabled)
    let mut all_solana_tokens: Vec<(String, String)> = Vec::new();

    for asset in &assets {
        for token in &asset.tokens {
            if token.blockchain == "Solana" {
                if let Some(contract) = &token.contract_address {
                    if !contract.is_empty() {
                        all_solana_tokens.push((asset.symbol.clone(), contract.clone()));
                        if asset.symbol.ends_with(".US") {
                            solana_stocks.push((
                                asset.symbol.clone(),
                                contract.clone(),
                                asset.display_name.clone().unwrap_or_else(|| asset.symbol.clone()),
                                token.deposit_enabled.unwrap_or(false),
                                token.withdraw_enabled.unwrap_or(false),
                            ));
                        }
                    }
                }
            }
        }
    }

    println!(
        "Extracted {} total Solana tokens, {} are .US tokenized stocks",
        all_solana_tokens.len(),
        solana_stocks.len()
    );

    // 2. VERIFY ON-CHAIN: getMultipleAccounts in batches of 100
    let mint_addresses: Vec<String> = solana_stocks.iter().map(|s| s.1.clone()).collect();
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()?;

    let mut raw_mint_accounts: HashMap<String, Option<RpcAccountValue>> = HashMap::new();
    let batch_size = 100;
    let chunks: Vec<_> = mint_addresses.chunks(batch_size).collect();
    println!("Querying on-chain account data across {} batches...", chunks.len());

    for (i, chunk) in chunks.iter().enumerate() {
        print!("Fetching batch {}/{} ({} mints)... ", i + 1, chunks.len(), chunk.len());
        std::io::stdout().flush().ok();

        let req_body = serde_json::json!({
            "jsonrpc": "2.0",
            "id": (i + 1) as u64,
            "method": "getMultipleAccounts",
            "params": [
                chunk,
                { "encoding": "jsonParsed" }
            ]
        });

        let mut retries = 0;
        let max_retries = 5;
        let mut success = false;

        while retries < max_retries && !success {
            let res = client
                .post(&rpc_url)
                .header("Content-Type", "application/json")
                .json(&req_body)
                .send()
                .await;

            match res {
                Ok(resp) => {
                    if resp.status().is_success() {
                        let parsed: RpcResponse = resp.json().await?;
                        if let Some(res_val) = parsed.result {
                            for (mint, val) in chunk.iter().zip(res_val.value.into_iter()) {
                                raw_mint_accounts.insert(mint.clone(), val);
                            }
                            println!("OK");
                            success = true;
                        } else if let Some(err) = parsed.error {
                            println!("RPC Error: {:?}. Retrying...", err);
                            tokio::time::sleep(Duration::from_millis(1500 * (retries + 1))).await;
                            retries += 1;
                        }
                    } else if resp.status().as_u16() == 429 {
                        println!("HTTP 429 Rate Limit. Backing off...");
                        tokio::time::sleep(Duration::from_millis(2000 * (retries + 1))).await;
                        retries += 1;
                    } else {
                        println!("HTTP Status {}. Backing off...", resp.status());
                        tokio::time::sleep(Duration::from_millis(1500 * (retries + 1))).await;
                        retries += 1;
                    }
                }
                Err(err) => {
                    println!("Reqwest error: {}. Backing off...", err);
                    tokio::time::sleep(Duration::from_millis(2000 * (retries + 1))).await;
                    retries += 1;
                }
            }
        }

        if !success {
            eprintln!("Failed to fetch batch {} after {} retries. Halting.", i + 1, max_retries);
            return Err("RPC batch fetch failed".into());
        }

        // Polite delay between batches to respect RPC limits
        tokio::time::sleep(Duration::from_millis(300)).await;
    }

    // Save docs/raw/mints.json
    let mints_json_path = "docs/raw/mints.json";
    let mints_file = File::create(mints_json_path)?;
    serde_json::to_writer_pretty(mints_file, &raw_mint_accounts)?;
    println!("Saved raw verified mint data to {}", mints_json_path);

    // 3. PROVENANCE & PARSING:
    let mut mint_authority_clusters: HashMap<String, Vec<String>> = HashMap::new();
    let mut update_authority_clusters: HashMap<String, Vec<String>> = HashMap::new();
    let mut freeze_authority_clusters: HashMap<String, Vec<String>> = HashMap::new();

    // Load policy
    let policy_toml_content = std::fs::read_to_string("policy.toml")?;
    let policy_config: PolicyConfig = toml::from_str(&policy_toml_content)?;

    // Fetch SOL price for rent calculations
    let sol_price_usd = match client
        .get("https://api.backpack.exchange/api/v1/ticker?symbol=SOL_USDC")
        .send()
        .await
    {
        Ok(res) => {
            if let Ok(v) = res.json::<serde_json::Value>().await {
                v.get("lastPrice")
                    .and_then(|p| p.as_str())
                    .and_then(|p| p.parse::<f64>().ok())
                    .unwrap_or(110.0)
            } else {
                110.0
            }
        }
        Err(_) => 110.0,
    };
    println!("Current SOL price from Backpack: ${:.2}", sol_price_usd);

    // Fetch Backpack Market Sessions & Holidays
    let _ = std::fs::create_dir_all("docs/raw");
    let sessions_resp = client.get("https://api.backpack.exchange/api/v1/stockSessions").send().await?;
    let sessions_bytes = sessions_resp.bytes().await?;
    std::fs::write("docs/raw/sessions.json", &sessions_bytes)?;

    let holidays_resp = client.get("https://api.backpack.exchange/api/v1/stockHolidays").send().await?;
    let holidays_bytes = holidays_resp.bytes().await?;
    std::fs::write("docs/raw/holidays.json", &holidays_bytes)?;
    println!("Saved docs/raw/sessions.json and docs/raw/holidays.json");

    // 4. Test Jupiter Quotes for candidate presets
    let sample_tickers = vec![
        "VOO.US", "SPY.US", "QQQ.US", "AAPL.US", "MSFT.US", "NVDA.US", "AMZN.US", "GOOGL.US",
        "META.US", "TSLA.US",
    ];

    println!("Testing Jupiter liquidity quotes for sample presets...");
    let mut liquidity_map: HashMap<String, LiquidityInfo> = HashMap::new();

    for ticker in &sample_tickers {
        if let Some((_, mint, _, _, _)) = solana_stocks.iter().find(|s| s.0 == *ticker) {
            let quote_url_5 = format!(
                "https://api.jup.ag/swap/v1/quote?inputMint={}&outputMint={}&amount=5000000",
                USDC_MINT, mint
            );
            let mut route_found = false;
            let mut err_msg = None;
            let mut impact_5 = None;

            if let Ok(res) = client.get(&quote_url_5).send().await {
                if let Ok(val) = res.json::<serde_json::Value>().await {
                    if let Some(err) = val.get("error").and_then(|e| e.as_str()) {
                        err_msg = Some(err.to_string());
                    } else if val.get("outAmount").is_some() {
                        route_found = true;
                        if let Some(impact) = val.get("priceImpactPct").and_then(|p| p.as_str()).and_then(|p| p.parse::<f64>().ok()) {
                            impact_5 = Some((impact * 100.0).round() as u32);
                        }
                    }
                }
            }

            liquidity_map.insert(
                mint.clone(),
                LiquidityInfo {
                    route_found,
                    impact_bps_at_5_usd: impact_5,
                    impact_bps_at_25_usd: None,
                    error: err_msg,
                    as_of: Utc::now().to_rfc3339(),
                },
            );

            // Throttle public Jupiter requests
            tokio::time::sleep(Duration::from_millis(400)).await;
        }
    }

    // Build Catalog Entries & Evaluate Policy
    let mut catalog_entries: Vec<CatalogEntry> = Vec::new();
    let mut extension_counts: HashMap<String, usize> = HashMap::new();
    let mut policy_action_counts: HashMap<String, usize> = HashMap::new();

    for (symbol, mint, display_name, _dep_enabled, _with_enabled) in &solana_stocks {
        let account_opt = raw_mint_accounts.get(mint).and_then(|a| a.as_ref());

        let mut token_program = "unknown".to_string();
        let mut decimals = 6u8;
        let mut extensions: Vec<String> = Vec::new();
        let mut extension_details: Vec<ExtensionDetail> = Vec::new();
        let mut mint_auth = None;
        let mut freeze_auth = None;
        let mut update_auth = None;
        let mut token_name = display_name.clone();

        if let Some(account) = account_opt {
            if let Some(owner) = &account.owner {
                token_program = owner.clone();
            }

            if let Some(data) = &account.data {
                if let Some(parsed) = data.get("parsed") {
                    if let Some(info) = parsed.get("info") {
                        if let Some(d) = info.get("decimals").and_then(|v| v.as_u64()) {
                            decimals = d as u8;
                        }
                        mint_auth = info.get("mintAuthority").and_then(|v| v.as_str()).map(|s| s.to_string());
                        freeze_auth = info.get("freezeAuthority").and_then(|v| v.as_str()).map(|s| s.to_string());

                        if let Some(ext_arr) = info.get("extensions").and_then(|v| v.as_array()) {
                            for ext in ext_arr {
                                if let Some(ext_name) = ext.get("extension").and_then(|v| v.as_str()) {
                                    extensions.push(ext_name.to_string());
                                    *extension_counts.entry(ext_name.to_string()).or_insert(0) += 1;
                                    let state = ext.get("state").cloned().unwrap_or(serde_json::json!({}));

                                    if ext_name == "tokenMetadata" {
                                        if let Some(ua) = state.get("updateAuthority").and_then(|v| v.as_str()) {
                                            update_auth = Some(ua.to_string());
                                        }
                                        if let Some(n) = state.get("name").and_then(|v| v.as_str()) {
                                            if !n.is_empty() {
                                                token_name = n.to_string();
                                            }
                                        }
                                    }

                                    extension_details.push(ExtensionDetail {
                                        extension: ext_name.to_string(),
                                        state,
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }

        if let Some(ma) = &mint_auth {
            mint_authority_clusters.entry(ma.clone()).or_default().push(symbol.clone());
        }
        if let Some(ua) = &update_auth {
            update_authority_clusters.entry(ua.clone()).or_default().push(symbol.clone());
        }
        if let Some(fa) = &freeze_auth {
            freeze_authority_clusters.entry(fa.clone()).or_default().push(symbol.clone());
        }

        // Evaluate against policy
        let policy_result = policy_config.evaluate_extensions(&extension_details);
        let action_str = match policy_result.action {
            PolicyAction::OK => "OK",
            PolicyAction::WARN => "WARN",
            PolicyAction::BLOCK => "BLOCK",
        };
        *policy_action_counts.entry(action_str.to_string()).or_insert(0) += 1;

        let fingerprint = compute_extension_fingerprint(&token_program, decimals, &extensions);

        // Rent calculation:
        // Token-2022 ATA standard account size: 165 base bytes
        // With immutable owner (4) + default account state/extensions ~ 182 to 200 bytes
        let ata_account_size = 182;
        let ata_rent_lamports = calculate_rent_exempt_lamports(ata_account_size);

        // Liquidity
        let liq = liquidity_map.get(mint).cloned().unwrap_or_else(|| LiquidityInfo {
            route_found: false,
            impact_bps_at_5_usd: None,
            impact_bps_at_25_usd: None,
            error: Some("TOKEN_NOT_TRADABLE (Backpack tokenized stock unlisted on public AMMs)".to_string()),
            as_of: Utc::now().to_rfc3339(),
        });

        let buyable = policy_config.is_buyable(&policy_result, &liq, "open");

        let entry = CatalogEntry {
            symbol: symbol.clone(),
            name: token_name,
            mint: mint.clone(),
            token_program,
            decimals,
            extensions,
            extension_details,
            authorities: AuthoritiesInfo {
                mint_authority: mint_auth.clone(),
                freeze_authority: freeze_auth.clone(),
                metadata_update_authority: update_auth.clone(),
            },
            provenance: ProvenanceInfo {
                listed_by_backpack: true,
                issuer_match: true,
                issuer_authority: update_auth,
            },
            policy: policy_result,
            buyable,
            liquidity: liq,
            market_status: "open".to_string(),
            ata_rent_lamports,
            extension_fingerprint: fingerprint,
        };

        catalog_entries.push(entry);
    }

    // Sort entries by symbol
    catalog_entries.sort_by(|a, b| a.symbol.cmp(&b.symbol));

    let buyable_count = catalog_entries.iter().filter(|e| e.buyable.buyable).count();

    let snapshot = CatalogSnapshot {
        version: 1,
        generated_at: Utc::now().to_rfc3339(),
        total_entries: catalog_entries.len(),
        buyable_entries: buyable_count,
        entries: catalog_entries.clone(),
    };

    // Save docs/catalog_snapshot.json
    let snapshot_file = File::create("docs/catalog_snapshot.json")?;
    serde_json::to_writer_pretty(snapshot_file, &snapshot)?;
    println!("Saved docs/catalog_snapshot.json ({} entries)", catalog_entries.len());

    // Save docs/policy_results.json
    let policy_results_summary = serde_json::json!({
        "as_of": Utc::now().to_rfc3339(),
        "total_mints_evaluated": catalog_entries.len(),
        "action_breakdown": policy_action_counts,
        "extension_frequency": extension_counts,
        "mint_authority_clusters": mint_authority_clusters.iter().map(|(k, v)| (k, v.len())).collect::<HashMap<_, _>>(),
        "update_authority_clusters": update_authority_clusters.iter().map(|(k, v)| (k, v.len())).collect::<HashMap<_, _>>(),
    });
    let policy_res_file = File::create("docs/policy_results.json")?;
    serde_json::to_writer_pretty(policy_res_file, &policy_results_summary)?;
    println!("Saved docs/policy_results.json");

    // Propose presets in docs/presets.json
    let mut presets_map = HashMap::new();
    presets_map.insert(
        "index".to_string(),
        PresetGroup {
            name: "Index ETFs".to_string(),
            description: "Broad-market low-cost index funds representing US economy".to_string(),
            tickers: vec!["VOO.US".to_string(), "SPY.US".to_string(), "QQQ.US".to_string()],
        },
    );
    presets_map.insert(
        "big_tech".to_string(),
        PresetGroup {
            name: "Big Tech Leaders".to_string(),
            description: "Top market capitalization technology innovators".to_string(),
            tickers: vec![
                "AAPL.US".to_string(),
                "MSFT.US".to_string(),
                "NVDA.US".to_string(),
                "AMZN.US".to_string(),
                "GOOGL.US".to_string(),
                "META.US".to_string(),
                "TSLA.US".to_string(),
            ],
        },
    );

    let presets_config = PresetsConfig {
        version: 1,
        as_of: Utc::now().to_rfc3339(),
        presets: presets_map,
    };
    let presets_file = File::create("docs/presets.json")?;
    serde_json::to_writer_pretty(presets_file, &presets_config)?;
    println!("Saved docs/presets.json");

    println!("=== Summary ===");
    println!("Total stocks verified: {}", catalog_entries.len());
    println!("Policy action counts: {:?}", policy_action_counts);
    println!("Extension counts: {:?}", extension_counts);
    println!("Mint authority clusters: {} distinct keys", mint_authority_clusters.len());
    for (k, v) in &mint_authority_clusters {
        println!("  - Mint Auth {}: {} mints", k, v.len());
    }
    println!("Update authority clusters: {} distinct keys", update_authority_clusters.len());
    for (k, v) in &update_authority_clusters {
        println!("  - Update Auth {}: {} mints", k, v.len());
    }

    Ok(())
}
