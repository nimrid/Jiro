use keeper::client::BackpackClient;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = BackpackClient::new(None, None);

    println!("=== WORKSTREAM B: MARKET & ASSET DISCOVERY ===\n");

    println!("--- 1. Fetching Securities (VOO.US, AAPL.US, QQQ.US) ---");
    let securities = client.get_securities().await?;
    let targets = ["VOO", "VOO.US", "AAPL", "AAPL.US", "QQQ", "QQQ.US"];

    let mut found_count = 0;
    for s in &securities {
        if targets.iter().any(|&t| s.asset == t || s.name.contains(t)) {
            found_count += 1;
            println!("Security: {} (Asset: {}, CUSIP: {:?})", s.name, s.asset, s.cusip);
            for session in &s.sessions {
                println!(
                    "  Session: '{}' -> minQuantity: {}, stepSize: {}, maxQuantity: {:?}",
                    session.name, session.min_quantity, session.step_size, session.max_quantity
                );
            }
        }
    }
    if found_count == 0 {
        println!("No exact matches found for target tickers. Listing first 10 stock assets:");
        for s in securities.iter().take(10) {
            println!("  Asset: {}, Name: {}", s.asset, s.name);
        }
    }

    println!("\n--- 2. Fetching USDC Minimum Deposit on Solana ---");
    let assets = client.get_assets().await?;
    let usdc = assets.iter().find(|a| a.symbol == "USDC");
    if let Some(usdc_asset) = usdc {
        println!("Found asset: {} ({})", usdc_asset.symbol, usdc_asset.display_name);
        for token in &usdc_asset.tokens {
            println!(
                "  Blockchain: '{}' | depositEnabled: {} | minimumDeposit: {:?} | decimals: {:?}",
                token.blockchain, token.deposit_enabled, token.minimum_deposit, token.native_decimals
            );
        }
    } else {
        println!("USDC asset not found in assets list.");
    }

    Ok(())
}
