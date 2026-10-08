use base64::prelude::*;
use ed25519_dalek::{Signer, SigningKey, VerifyingKey};
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SignerError {
    #[error("Base64 decode error: {0}")]
    Base64(#[from] base64::DecodeError),
    #[error("Invalid key length: expected 32 bytes, got {0}")]
    InvalidKeyLength(usize),
    #[error("Missing environment variable: {0}")]
    EnvVar(#[from] std::env::VarError),
    #[error("Public key mismatch with BACKPACK_API_KEY")]
    PublicKeyMismatch,
}

pub struct BackpackSigner {
    signing_key: SigningKey,
    verifying_key: VerifyingKey,
}

impl BackpackSigner {
    /// Construct signer from a 32-byte secret seed.
    pub fn from_seed(seed: &[u8; 32]) -> Self {
        let signing_key = SigningKey::from_bytes(seed);
        let verifying_key = signing_key.verifying_key();
        Self {
            signing_key,
            verifying_key,
        }
    }

    /// Construct signer from base64-encoded 32-byte secret seed.
    pub fn from_base64_secret(secret_base64: &str) -> Result<Self, SignerError> {
        let bytes = BASE64_STANDARD.decode(secret_base64.trim())?;
        if bytes.len() != 32 {
            return Err(SignerError::InvalidKeyLength(bytes.len()));
        }
        let mut seed = [0u8; 32];
        seed.copy_from_slice(&bytes);
        Ok(Self::from_seed(&seed))
    }

    /// Construct signer from environment variables (BACKPACK_API_SECRET, optional BACKPACK_API_KEY).
    pub fn from_env() -> Result<Self, SignerError> {
        let secret = std::env::var("BACKPACK_API_SECRET")?;
        let signer = Self::from_base64_secret(&secret)?;

        if let Ok(expected_pubkey) = std::env::var("BACKPACK_API_KEY") {
            if signer.verifying_key_base64() != expected_pubkey.trim() {
                return Err(SignerError::PublicKeyMismatch);
            }
        }

        Ok(signer)
    }

    /// Base64 encoded public key for X-API-Key header.
    pub fn verifying_key_base64(&self) -> String {
        BASE64_STANDARD.encode(self.verifying_key.as_bytes())
    }

    /// Build canonical signing string according to Backpack API specification:
    /// 1. Parameters sorted alphabetically by key.
    /// 2. Format: instruction=<instruction>[&key1=val1&key2=val2...]&timestamp=<ts>&window=<w>
    pub fn build_signing_string(
        instruction: &str,
        params: &[(&str, &str)],
        timestamp: u64,
        window: u64,
    ) -> String {
        let mut sorted_params = params.to_vec();
        sorted_params.sort_by(|a, b| a.0.cmp(b.0));

        let mut parts = Vec::with_capacity(sorted_params.len() + 3);
        parts.push(format!("instruction={}", instruction));

        for (k, v) in sorted_params {
            parts.push(format!("{}={}", k, v));
        }

        parts.push(format!("timestamp={}", timestamp));
        parts.push(format!("window={}", window));

        parts.join("&")
    }

    /// Sign canonical message and return (signing_string, base64_signature).
    pub fn sign(
        &self,
        instruction: &str,
        params: &[(&str, &str)],
        timestamp: u64,
        window: u64,
    ) -> (String, String) {
        let message = Self::build_signing_string(instruction, params, timestamp, window);
        let signature = self.signing_key.sign(message.as_bytes());
        let signature_base64 = BASE64_STANDARD.encode(signature.to_bytes());
        (message, signature_base64)
    }

    pub fn verifying_key(&self) -> &VerifyingKey {
        &self.verifying_key
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signature, Verifier};

    #[test]
    fn test_order_cancel_signing_string_matches_docs_example() {
        // Docs example from openapi.json:
        // Body: { "orderId": 28, "symbol": "BTC_USDT" }
        // Expected: "instruction=orderCancel&orderId=28&symbol=BTC_USDT&timestamp=1614550000000&window=5000"
        let params = vec![("symbol", "BTC_USDT"), ("orderId", "28")];
        let signing_string = BackpackSigner::build_signing_string(
            "orderCancel",
            &params,
            1614550000000,
            5000,
        );
        assert_eq!(
            signing_string,
            "instruction=orderCancel&orderId=28&symbol=BTC_USDT&timestamp=1614550000000&window=5000"
        );
    }

    #[test]
    fn test_account_query_signing_string_matches_format() {
        // Empty query/body params endpoint
        let signing_string = BackpackSigner::build_signing_string(
            "accountQuery",
            &[],
            1614550000000,
            5000,
        );
        assert_eq!(
            signing_string,
            "instruction=accountQuery&timestamp=1614550000000&window=5000"
        );
    }

    #[test]
    fn test_signature_generation_and_verification() {
        let seed = [42u8; 32];
        let signer = BackpackSigner::from_seed(&seed);

        let params = vec![("symbol", "BTC_USDT"), ("orderId", "28")];
        let (signing_string, sig_base64) = signer.sign("orderCancel", &params, 1614550000000, 5000);

        assert_eq!(
            signing_string,
            "instruction=orderCancel&orderId=28&symbol=BTC_USDT&timestamp=1614550000000&window=5000"
        );

        let sig_bytes = BASE64_STANDARD.decode(&sig_base64).expect("decode signature");
        let signature = Signature::from_slice(&sig_bytes).expect("valid signature bytes");

        signer
            .verifying_key()
            .verify(signing_string.as_bytes(), &signature)
            .expect("signature must be valid");
    }

    #[test]
    fn test_from_base64_secret_roundtrip() {
        let seed = [7u8; 32];
        let base64_secret = BASE64_STANDARD.encode(seed);
        let signer = BackpackSigner::from_base64_secret(&base64_secret).unwrap();
        assert_eq!(signer.verifying_key().as_bytes().len(), 32);
    }
}
