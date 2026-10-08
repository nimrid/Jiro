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
  createAssociatedTokenAccountInstruction,
  getAccount,
} from "@solana/spl-token";
import * as fs from "fs";
import * as path from "path";

const PROGRAM_ID = new PublicKey("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc");
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

export function getVaultPda(owner: PublicKey): [PublicKey, number] {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("vault"), owner.toBuffer()],
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

async function initVault(
  ownerKeypairPath: string,
  keeperPubkeyStr: string,
  destinationAtaStr: string,
  thresholdBaseUnits: string,
  maxBalanceBaseUnits: string,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const keeper = new PublicKey(keeperPubkeyStr);
  const destination = new PublicKey(destinationAtaStr);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;

  const [vaultPda] = getVaultPda(owner.publicKey);
  const vaultAta = getVaultAta(mint, vaultPda);

  const disc = Buffer.from("4d4f559621d9346a", "hex");
  const data = Buffer.concat([
    disc,
    u64ToLeBytes(BigInt(thresholdBaseUnits)),
    u64ToLeBytes(BigInt(maxBalanceBaseUnits)),
  ]);

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: owner.publicKey, isSigner: true, isWritable: true },
      { pubkey: keeper, isSigner: false, isWritable: false },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: destination, isSigner: false, isWritable: false },
      { pubkey: vaultPda, isSigner: false, isWritable: true },
      { pubkey: vaultAta, isSigner: false, isWritable: true },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: ASSOCIATED_TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data,
  });

  const tx = new Transaction().add(ix);
  console.log(`Sending init_vault tx for owner: ${owner.publicKey.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Init successful! Tx: ${sig}`);
  console.log(`Vault PDA: ${vaultPda.toBase58()}`);
  console.log(`Vault ATA: ${vaultAta.toBase58()}`);
}

async function deposit(
  ownerKeypairPath: string,
  amountBaseUnits: string,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner.publicKey);
  const vaultAta = getVaultAta(mint, vaultPda);
  const ownerAta = getAssociatedTokenAddressSync(mint, owner.publicKey);

  const disc = Buffer.from("f223c68952e1f2b6", "hex");
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
  console.log(`Depositing ${amountBaseUnits} base units into vault ${vaultPda.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Deposit successful! Tx: ${sig}`);
}

async function sweep(
  keeperKeypairPath: string,
  ownerPubkeyStr: string,
  destinationAtaStr: string,
  mintStr?: string
) {
  const keeper = loadKeypair(keeperKeypairPath);
  const owner = new PublicKey(ownerPubkeyStr);
  const destination = new PublicKey(destinationAtaStr);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;

  const [vaultPda] = getVaultPda(owner);
  const vaultAta = getVaultAta(mint, vaultPda);

  const disc = Buffer.from("2817eaaf0e3d9ab1", "hex");

  const ix = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: keeper.publicKey, isSigner: true, isWritable: false },
      { pubkey: vaultPda, isSigner: false, isWritable: false },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: vaultAta, isSigner: false, isWritable: true },
      { pubkey: destination, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data: disc,
  });

  const tx = new Transaction().add(ix);
  console.log(`Sweeping vault for owner ${owner.toBase58()}...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [keeper]);
  console.log(`Sweep successful! Tx: ${sig}`);
}

async function withdraw(
  ownerKeypairPath: string,
  amountBaseUnits: string,
  mintStr?: string
) {
  const owner = loadKeypair(ownerKeypairPath);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner.publicKey);
  const vaultAta = getVaultAta(mint, vaultPda);
  const ownerAta = getAssociatedTokenAddressSync(mint, owner.publicKey);

  const disc = Buffer.from("b712469c946da122", "hex");
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
  console.log(`Withdrawing ${amountBaseUnits} base units from vault...`);
  const sig = await sendAndConfirmTransaction(connection, tx, [owner]);
  console.log(`Withdraw successful! Tx: ${sig}`);
}

async function status(ownerPubkeyStr: string, mintStr?: string) {
  const owner = new PublicKey(ownerPubkeyStr);
  const mint = mintStr ? new PublicKey(mintStr) : DEFAULT_USDC_MINT;
  const [vaultPda] = getVaultPda(owner);
  const vaultAta = getVaultAta(mint, vaultPda);

  console.log(`=== Vault Status ===`);
  console.log(`Owner:     ${owner.toBase58()}`);
  console.log(`Vault PDA: ${vaultPda.toBase58()}`);
  console.log(`Vault ATA: ${vaultAta.toBase58()}`);

  try {
    const tokenAcct = await getAccount(connection, vaultAta);
    console.log(`Balance:   ${tokenAcct.amount.toString()} base units (${Number(tokenAcct.amount) / 1e6} USDC)`);
  } catch (e: any) {
    console.log(`Vault token account not found or not initialized yet.`);
  }
}

async function main() {
  const [command, ...args] = process.argv.slice(2);

  switch (command) {
    case "init":
      if (args.length < 5) {
        console.error("Usage: ts-node vault-cli.ts init <ownerKeypair> <keeperPubkey> <destAta> <threshold> <maxBalance> [mint]");
        process.exit(1);
      }
      await initVault(args[0], args[1], args[2], args[3], args[4], args[5]);
      break;
    case "deposit":
      if (args.length < 2) {
        console.error("Usage: ts-node vault-cli.ts deposit <ownerKeypair> <amount> [mint]");
        process.exit(1);
      }
      await deposit(args[0], args[1], args[2]);
      break;
    case "sweep":
      if (args.length < 3) {
        console.error("Usage: ts-node vault-cli.ts sweep <keeperKeypair> <ownerPubkey> <destAta> [mint]");
        process.exit(1);
      }
      await sweep(args[0], args[1], args[2], args[3]);
      break;
    case "withdraw":
      if (args.length < 2) {
        console.error("Usage: ts-node vault-cli.ts withdraw <ownerKeypair> <amount> [mint]");
        process.exit(1);
      }
      await withdraw(args[0], args[1], args[2]);
      break;
    case "status":
      if (args.length < 1) {
        console.error("Usage: ts-node vault-cli.ts status <ownerPubkey> [mint]");
        process.exit(1);
      }
      await status(args[0], args[1]);
      break;
    default:
      console.log("Commands: init, deposit, sweep, withdraw, status");
      break;
  }
}

main().catch((err) => {
  console.error("Error:", err);
  process.exit(1);
});
