use thiserror::Error;

const USDC_DECIMALS: u32 = 6;
const USDC_ONE_DOLLAR: u64 = 1_000_000;
const INTERNAL_SCALE: u32 = 8;
const INTERNAL_MULTIPLIER: u128 = 100_000_000; // 10^8

#[derive(Debug, Error, PartialEq, Eq)]
pub enum MathError {
    #[error("Parse error: {0}")]
    ParseError(String),
    #[error("Zero price")]
    ZeroPrice,
    #[error("Zero step size")]
    ZeroStepSize,
    #[error("Calculated quantity {quantity} is below minimum quantity {min_quantity}")]
    BelowMinQuantity {
        quantity: String,
        min_quantity: String,
    },
    #[error("Zero quantity calculated from amount {dollars_base_units}")]
    ZeroQuantity {
        dollars_base_units: u64,
    },
}

/// Parse a decimal string (e.g. "337.34", "0.01", "1") into an integer scaled by 10^scale.
/// Purely integer-based parsing, no floating-point arithmetic.
pub fn parse_decimal_to_scaled(s: &str, target_scale: u32) -> Result<u128, MathError> {
    let s = s.trim();
    if s.is_empty() {
        return Err(MathError::ParseError("Empty decimal string".to_string()));
    }

    let parts: Vec<&str> = s.split('.').collect();
    if parts.len() > 2 {
        return Err(MathError::ParseError(format!("Invalid decimal format: {}", s)));
    }

    let int_part: u128 = parts[0]
        .parse()
        .map_err(|_| MathError::ParseError(format!("Invalid integer part in {}", s)))?;

    let frac_part_str = if parts.len() == 2 { parts[1] } else { "" };

    if frac_part_str.len() > target_scale as usize {
        return Err(MathError::ParseError(format!(
            "Decimal {} exceeds precision limit of {}",
            s, target_scale
        )));
    }

    let frac_val: u128 = if frac_part_str.is_empty() {
        0
    } else {
        frac_part_str
            .parse()
            .map_err(|_| MathError::ParseError(format!("Invalid fractional part in {}", s)))?
    };

    let frac_scale = frac_part_str.len() as u32;
    let frac_scaled = frac_val * 10u128.pow(target_scale - frac_scale);
    let int_scaled = int_part * 10u128.pow(target_scale);

    Ok(int_scaled + frac_scaled)
}

/// Count number of decimal places in string (e.g. "0.00001" -> 5, "1" -> 0, "0.01" -> 2).
pub fn decimal_places(s: &str) -> u32 {
    let s = s.trim();
    if let Some(pos) = s.find('.') {
        (s.len() - pos - 1) as u32
    } else {
        0
    }
}

/// Format an internal scaled integer (10^INTERNAL_SCALE) to decimal string
/// with specified decimal places.
pub fn format_scaled_to_decimal(val: u128, decimals: u32) -> String {
    let divisor = 10u128.pow(INTERNAL_SCALE);
    let int_part = val / divisor;
    let frac_remainder = val % divisor;

    if decimals == 0 {
        return int_part.to_string();
    }

    // Scale remainder down to the target decimals
    let downscale = 10u128.pow(INTERNAL_SCALE - decimals);
    let target_frac = frac_remainder / downscale;

    format!("{}.{:0width$}", int_part, target_frac, width = decimals as usize)
}

/// Convert dollars (USDC base units, 6 decimals) to share quantity string.
/// - Integer math only (no floats).
/// - Floors to stepSize.
/// - Rejects if below minQuantity.
pub fn dollars_to_shares(
    dollars_base_units: u64,
    price_str: &str,
    min_quantity_str: &str,
    step_size_str: &str,
) -> Result<String, MathError> {
    if dollars_base_units == 0 {
        return Err(MathError::ZeroQuantity { dollars_base_units: 0 });
    }

    let price_scaled = parse_decimal_to_scaled(price_str, USDC_DECIMALS)?;
    if price_scaled == 0 {
        return Err(MathError::ZeroPrice);
    }

    let min_qty_scaled = parse_decimal_to_scaled(min_quantity_str, INTERNAL_SCALE)?;
    let step_size_scaled = parse_decimal_to_scaled(step_size_str, INTERNAL_SCALE)?;
    if step_size_scaled == 0 {
        return Err(MathError::ZeroStepSize);
    }

    // dollars_base_units is in 10^6 USDC.
    // price_scaled is in 10^6 USDC per share.
    // shares = dollars / price.
    // Scale up by INTERNAL_SCALE (10^8):
    // raw_shares_scaled = (dollars_base_units * INTERNAL_MULTIPLIER) / price_scaled
    let raw_shares_scaled = ((dollars_base_units as u128) * INTERNAL_MULTIPLIER) / price_scaled;

    // Floor to stepSize
    let floored_shares_scaled = (raw_shares_scaled / step_size_scaled) * step_size_scaled;

    let decimals = decimal_places(step_size_str);
    let formatted_quantity = format_scaled_to_decimal(floored_shares_scaled, decimals);

    if floored_shares_scaled < min_qty_scaled {
        let min_formatted = format_scaled_to_decimal(min_qty_scaled, decimal_places(min_quantity_str));
        return Err(MathError::BelowMinQuantity {
            quantity: formatted_quantity,
            min_quantity: min_formatted,
        });
    }

    Ok(formatted_quantity)
}

/// Round-up calculation:
/// Ceil to next whole dollar of payment amount (USDC 6 decimals).
/// If payment is exactly whole dollar:
/// - if round_up_if_exact is true, round up $1.00 (1_000_000)
/// - if round_up_if_exact is false, round up $0.00 (default)
pub fn calculate_round_up(payment_base_units: u64, round_up_if_exact: bool) -> u64 {
    let remainder = payment_base_units % USDC_ONE_DOLLAR;
    if remainder == 0 {
        if round_up_if_exact {
            USDC_ONE_DOLLAR
        } else {
            0
        }
    } else {
        USDC_ONE_DOLLAR - remainder
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_regular_session_fractional_shares() {
        // $5.00 USDC = 5_000_000 base units
        // AAPL price: $337.34
        // minQuantity: 0.01
        // stepSize: 0.00001
        // Expected shares: 5 / 337.34 = 0.0148218... -> floored to 0.00001 = 0.01482
        let shares = dollars_to_shares(5_000_000, "337.34", "0.01", "0.00001").unwrap();
        assert_eq!(shares, "0.01482");
    }

    #[test]
    fn test_rejects_below_min_quantity() {
        // $2.00 USDC = 2_000_000 base units
        // Price: $337.34
        // 2 / 337.34 = 0.005928...
        // minQuantity: 0.01
        // Must reject because 0.00592 < 0.01
        let err = dollars_to_shares(2_000_000, "337.34", "0.01", "0.00001").unwrap_err();
        assert_eq!(
            err,
            MathError::BelowMinQuantity {
                quantity: "0.00592".to_string(),
                min_quantity: "0.01".to_string(),
            }
        );
    }

    #[test]
    fn test_whole_share_session() {
        // $1000.00 USDC = 1_000_000_000 base units
        // Price: $337.34
        // 1000 / 337.34 = 2.964...
        // minQuantity: 1, stepSize: 1
        // Expected floored to whole share: "2"
        let shares = dollars_to_shares(1_000_000_000, "337.34", "1", "1").unwrap();
        assert_eq!(shares, "2");
    }

    #[test]
    fn test_whole_share_session_rejects_fractional_dollar_sweep() {
        // $5.00 sweep during whole-share session
        let err = dollars_to_shares(5_000_000, "337.34", "1", "1").unwrap_err();
        assert_eq!(
            err,
            MathError::BelowMinQuantity {
                quantity: "0".to_string(),
                min_quantity: "1".to_string(),
            }
        );
    }

    #[test]
    fn test_round_up_calculation() {
        // $4.25 payment -> round-up should be $0.75 (750_000)
        assert_eq!(calculate_round_up(4_250_000, false), 750_000);

        // $0.01 payment -> round-up should be $0.99 (990_000)
        assert_eq!(calculate_round_up(10_000, false), 990_000);

        // $3.99 payment -> round-up should be $0.01 (10_000)
        assert_eq!(calculate_round_up(3_990_000, false), 10_000);

        // Exact whole dollar $5.00: default config (round_up_if_exact = false) -> $0.00
        assert_eq!(calculate_round_up(5_000_000, false), 0);

        // Exact whole dollar $5.00: config round_up_if_exact = true -> $1.00 (1_000_000)
        assert_eq!(calculate_round_up(5_000_000, true), 1_000_000);
    }
}
