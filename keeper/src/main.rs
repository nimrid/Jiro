use keeper::client::BackpackClient;
use keeper::signer::BackpackSigner;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Load .env if present
    let _ = dotenvy::dotenv();

    println!("--- Testing Public Endpoints ---");
    let client = BackpackClient::new(None, None);

    println!("1. Fetching securities...");
    match client.get_securities().await {
        Ok(securities) => println!("   Successfully fetched {} securities.", securities.len()),
        Err(e) => eprintln!("   Failed to fetch securities: {}", e),
    }

    println!("2. Fetching stock sessions...");
    match client.get_stock_sessions().await {
        Ok(sessions) => println!("   Successfully fetched {} sessions.", sessions.len()),
        Err(e) => eprintln!("   Failed to fetch stock sessions: {}", e),
    }

    println!("3. Fetching indicative quote for AAPL.US...");
    match client.get_stock_indicative_quote("AAPL.US", "Bid", "1").await {
        Ok(quote) => println!("   Indicative quote: symbol={}, available={}, price={:?}", quote.symbol, quote.available, quote.price),
        Err(e) => eprintln!("   Failed to fetch indicative quote: {}", e),
    }

    println!("\n--- Testing Authenticated Endpoint (/account) ---");
    match BackpackSigner::from_env() {
        Ok(signer) => {
            println!("   Signer initialized from environment.");
            let authed_client = BackpackClient::new(None, Some(signer));
            match authed_client.get_account().await {
                Ok(account) => {
                    println!("   Successfully retrieved account! autoLend={}, autoBorrowSettlements={}", account.auto_lend, account.auto_borrow_settlements);
                }
                Err(e) => {
                    eprintln!("   Failed to fetch account: {}", e);
                }
            }
        }
        Err(e) => {
            println!("   Skipping /account live call: {}", e);
            println!("   Set BACKPACK_API_SECRET (and optional BACKPACK_API_KEY) in .env to run live account query.");
        }
    }

    Ok(())
}
