import {
  Connection,
  Keypair,
  PublicKey,
  Transaction,
  TransactionInstruction,
  sendAndConfirmTransaction,
  SystemProgram,
} from "@solana/web3.js";
import {
  TOKEN_PROGRAM_ID,
  ASSOCIATED_TOKEN_PROGRAM_ID,
  getAssociatedTokenAddressSync,
  getAccount,
} from "@solana/spl-token";
import * as fs from "fs";
import * as path from "path";

const PROGRAM_ID = new PublicKey(
  process.env.VAULT_PROGRAM_ID || "AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc"
);
// Devnet standard USDC mint or fallback
const DEFAULT_USDC_MINT = new PublicKey("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU");

const RPC_URL = process.env.SOLANA_RPC_URL || "https://api.devnet.solana.com";
const connection = new Connection(RPC_URL, "confirmed");

function loadKeypair(filePath: string): Keypair {
  const resolved = path.resolve(filePath.replace(/^~/, process.env.HOME || ""));
  const secret = JSON.parse(fs.readFileSync(resolved, "utf8"));
  return Keypair.fromSecretKey(new Uint8Array(secret));
}

function u64ToLeBytes(n: bigint): Buffer {
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64LE(n);
  return buf;
}

export function getVaultPda(owner: PublicKey, vaultId: number): [PublicKey, number] {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("vault"), owner.toBuffer(), Buffer.from([vaultId])],
    PROGRAM_ID
  );
}

export function getVaultAta(mint: PublicKey, vaultPda: PublicKey): PublicKey {
  return getAssociatedTokenAddressSync(
    mint,
    vaultPda,
    true,
    TOKEN_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID
  );
}

export function getMockPda(stockMint: PublicKey): [PublicKey, number] {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("mock"), stockMint.toBuffer()],
    PROGRAM_ID
  );
}

async function initVault(
  ownerKeypairPath: string,
  vaultId: number,
  maxBalanceBaseUnits: string,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;

  const [vaultPda] = getVaultPda(owner.publicKey, vaultId);
  const vaultAta = getVaultAta(mint, vaultPda);

  const disc = Buffer.from("4d4f559621d9346a", "hex"); // init_vault
  const data = Buffer.concat([
    disc,
    Buffer.from([vaultId]),
    u64ToLeBytes(BigInt(maxBalanceBaseUnits)),
  ]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: owner.publicKey, isSigner: true, isWritable: true },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: vaultPda, isSigner: false, isWritable: true },
      { pubkey: vaultAta, isSigner: false, isWritable: true },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: ASSOCIATED_TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Sending init_vault tx (vault_id=${vaultId}, cap=${maxBalanceBaseUnits}) for owner: ${owner.publicKey.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Init successful! Tx: ${sig}`);
  console.log(`Vault PDA: ${vaultPda.toBase58()}`);
  console.log(`Vault ATA: ${vaultAta.toBase58()}`);
}

async function deposit(
  ownerKeypairPath: string,
  vaultId: number,
  amountBaseUnits: string,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner.publicKey, vaultId);
  const vaultAta = getVaultAta(mint, vaultPda);
  const ownerAta = getAssociatedTokenAddressSync(mint, owner.publicKey);

  const disc = Buffer.from("f223c68952e1f2b6", "hex"); // deposit
  const data = Buffer.concat([disc, u64ToLeBytes(BigInt(amountBaseUnits))]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: owner.publicKey, isSigner: true, isWritable: true },
      { pubkey: vaultPda, isSigner: false, isWritable: false },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: vaultAta, isSigner: false, isWritable: true },
      { pubkey: ownerAta, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Depositing ${amountBaseUnits} base units into vault (id=${vaultId}) ${vaultPda.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Deposit successful! Tx: ${sig}`);
}

async function withdraw(
  ownerKeypairPath: string,
  vaultId: number,
  amountBaseUnits: string,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner.publicKey, vaultId);
  const vaultAta = getVaultAta(mint, vaultPda);
  const ownerAta = getAssociatedTokenAddressSync(mint, owner.publicKey);

  const disc = Buffer.from("b712469c946da122", "hex"); // withdraw
  const data = Buffer.concat([disc, u64ToLeBytes(BigInt(amountBaseUnits))]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: owner.publicKey, isSigner: true, isWritable: false },
      { pubkey: vaultPda, isSigner: false, isWritable: false },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: vaultAta, isSigner: false, isWritable: true },
      { pubkey: ownerAta, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Withdrawing ${amountBaseUnits} base units from vault (id=${vaultId})...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Withdraw successful! Tx: ${sig}`);
}

async function closeVault(
  ownerKeypairPath: string,
  vaultId: number,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner.publicKey, vaultId);
  const vaultAta = getVaultAta(mint, vaultPda);

  const disc = Buffer.from("8d67117e484b1d1d", "hex"); // close_vault

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: owner.publicKey, isSigner: true, isWritable: true },
      { pubkey: vaultPda, isSigner: false, isWritable: true },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: vaultAta, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data: disc,
  });

  const tx = new Transaction().add(ix);
  console.log(`Closing vault (id=${vaultId}) ${vaultPda.toBase58()} and reclaiming rent SOL...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Vault closed successfully! Tx: ${sig}`);
}

async function mockInit(
  adminKeypairPath: string,
  stockMintStr: string,
  priceBaseUnits: string,
  usdcMintStr?: string
) {
  const admin = loadKeypair(adminKeypairPath);
  const stockMint = new PublicKey(stockMintStr);
  const usdcMint = usdcMintStr ? new PublicKey(usdcMintStr) : DEFAULT_USDC_MINT;

  const [mockPda] = getMockPda(stockMint);
  const sinkAta = getAssociatedTokenAddressSync(usdcMint, mockPda, true);
  const treasuryAta = getAssociatedTokenAddressSync(stockMint, mockPda, true);

  const disc = Buffer.from("f458426f74792010", "hex"); // mock_init
  const data = Buffer.concat([disc, u64ToLeBytes(BigInt(priceBaseUnits))]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: admin.publicKey, isSigner: true, isWritable: true },
      { pubkey: stockMint, isSigner: false, isWritable: false },
      { pubkey: usdcMint, isSigner: false, isWritable: false },
      { pubkey: mockPda, isSigner: false, isWritable: true },
      { pubkey: treasuryAta, isSigner: false, isWritable: true },
      { pubkey: sinkAta, isSigner: false, isWritable: true },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: ASSOCIATED_TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Initializing mock pool for stock ${stockMint.toBase58()} at price ${priceBaseUnits}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [admin]);
  console.log(`Mock pool initialized! Tx: ${sig}`);
  console.log(`Mock PDA: ${mockPda.toBase58()}`);
  console.log(`Treasury ATA: ${treasuryAta.toBase58()}`);
  console.log(`Sink ATA: ${sinkAta.toBase58()}`);
}

async function mockSetPrice(
  adminKeypairPath: string,
  stockMintStr: string,
  newPriceBaseUnits: string
) {
  const admin = loadKeypair(adminKeypairPath);
  const stockMint = new PublicKey(stockMintStr);
  const [mockPda] = getMockPda(stockMint);

  const disc = Buffer.from("902a9f6e9bb8302c", "hex"); // mock_set_price
  const data = Buffer.concat([disc, u64ToLeBytes(BigInt(newPriceBaseUnits))]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: admin.publicKey, isSigner: true, isWritable: false },
      { pubkey: mockPda, isSigner: false, isWritable: true },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Setting mock price to ${newPriceBaseUnits} for stock ${stockMint.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [admin]);
  console.log(`Price updated! Tx: ${sig}`);
}

async function mockSwap(
  ownerKeypairPath: string,
  stockMintStr: string,
  usdcInBaseUnits: string,
  minOutBaseUnits: string,
  usdcMintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const stockMint = new PublicKey(stockMintStr);
  const usdcMint = usdcMintStr ? new PublicKey(usdcMintStr) : DEFAULT_USDC_MINT;

  const [mockPda] = getMockPda(stockMint);
  const sinkAta = getAssociatedTokenAddressSync(usdcMint, mockPda, true);
  const treasuryAta = getAssociatedTokenAddressSync(stockMint, mockPda, true);
  const ownerUsdcAta = getAssociatedTokenAddressSync(usdcMint, owner.publicKey);
  const ownerStockAta = getAssociatedTokenAddressSync(stockMint, owner.publicKey);

  const disc = Buffer.from("8476b98619386a0a", "hex"); // mock_swap
  const data = Buffer.concat([
    disc,
    u64ToLeBytes(BigInt(usdcInBaseUnits)),
    u64ToLeBytes(BigInt(minOutBaseUnits)),
  ]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: owner.publicKey, isSigner: true, isWritable: true },
      { pubkey: mockPda, isSigner: false, isWritable: false },
      { pubkey: usdcMint, isSigner: false, isWritable: false },
      { pubkey: stockMint, isSigner: false, isWritable: false },
      { pubkey: sinkAta, isSigner: false, isWritable: true },
      { pubkey: treasuryAta, isSigner: false, isWritable: true },
      { pubkey: ownerUsdcAta, isSigner: false, isWritable: true },
      { pubkey: ownerStockAta, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Executing mock_swap: ${usdcInBaseUnits} USDC for stock ${stockMint.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Swap successful! Tx: ${sig}`);
}

async function status(ownerPubkeyStr: string, vaultId: number, mintStr?: string) {
  const owner = new PublicKey(ownerPubkeyStr);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner, vaultId);
  const vaultAta = getVaultAta(mint, vaultPda);

  console.log(`=== Vault Status ===`);
  console.log(`Program ID: ${PROGRAM_ID.toBase58()}`);
  console.log(`Owner:      ${owner.toBase58()}`);
  console.log(`Vault ID:   ${vaultId}`);
  console.log(`Vault PDA:  ${vaultPda.toBase58()}`);
  console.log(`Vault ATA:  ${vaultAta.toBase58()}`);

  try {
    const tokenAcct = await getAccount(connection, vaultAta);
    console.log(`Balance:    ${tokenAcct.amount.toString()} base units (${Number(tokenAcct.amount) / 1e6} USDC)`);
  } catch (e: any) {
    console.log(`Vault token account not found or not initialized yet.`);
  }
}

async function main() {
  const [command, ...args] = process.argv.slice(2);

  switch (command) {
    case "init":
      if (args.length < 3) {
        console.error("Usage: ts-node vault-cli.ts init <ownerKeypair> <vaultId> <maxBalance> [mint]");
        process.exit(1);
      }
      await initVault(args[0], parseInt(args[1], 10), args[2], args[3]);
      break;
    case "deposit":
      if (args.length < 3) {
        console.error("Usage: ts-node vault-cli.ts deposit <ownerKeypair> <vaultId> <amount> [mint]");
        process.exit(1);
      }
      await deposit(args[0], parseInt(args[1], 10), args[2], args[3]);
      break;
    case "withdraw":
      if (args.length < 3) {
        console.error("Usage: ts-node vault-cli.ts withdraw <ownerKeypair> <vaultId> <amount> [mint]");
        process.exit(1);
      }
      await withdraw(args[0], parseInt(args[1], 10), args[2], args[3]);
      break;
    case "close":
      if (args.length < 2) {
        console.error("Usage: ts-node vault-cli.ts close <ownerKeypair> <vaultId> [mint]");
        process.exit(1);
      }
      await closeVault(args[0], parseInt(args[1], 10), args[2]);
      break;
    case "mock-init":
      if (args.length < 3) {
        console.error("Usage: ts-node vault-cli.ts mock-init <adminKeypair> <stockMint> <price> [usdcMint]");
        process.exit(1);
      }
      await mockInit(args[0], args[1], args[2], args[3]);
      break;
    case "mock-set-price":
      if (args.length < 3) {
        console.error("Usage: ts-node vault-cli.ts mock-set-price <adminKeypair> <stockMint> <newPrice>");
        process.exit(1);
      }
      await mockSetPrice(args[0], args[1], args[2]);
      break;
    case "mock-swap":
      if (args.length < 4) {
        console.error("Usage: ts-node vault-cli.ts mock-swap <ownerKeypair> <stockMint> <usdcIn> <minOut> [usdcMint]");
        process.exit(1);
      }
      await mockSwap(args[0], args[1], args[2], args[3], args[4]);
      break;
    case "status":
      if (args.length < 2) {
        console.error("Usage: ts-node vault-cli.ts status <ownerPubkey> <vaultId> [mint]");
        process.exit(1);
      }
      await status(args[0], parseInt(args[1], 10), args[2]);
      break;
    default:
      console.log("Commands: init, deposit, withdraw, close, mock-init, mock-set-price, mock-swap, status");
      break;
  }
}

main().catch((err) => {
  console.error("Error:", err);
  process.exit(1);
});
