import * as anchor from "@coral-xyz/anchor";
import { Program, BN } from "@coral-xyz/anchor";
import {
  Keypair,
  PublicKey,
  SystemProgram,
  LAMPORTS_PER_SOL,
} from "@solana/web3.js";
import {
  TOKEN_PROGRAM_ID,
  TOKEN_2022_PROGRAM_ID,
  ASSOCIATED_TOKEN_PROGRAM_ID,
  createMint,
  createAssociatedTokenAccount,
  mintTo,
  getAccount,
  getAssociatedTokenAddressSync,
} from "@solana/spl-token";
import { expect } from "chai";
import idl from "../target/idl/vault.json";

describe("Vault Program Test Suite", () => {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);

  const program = new Program(idl as any, provider);

  const owner = Keypair.generate();
  const attacker = Keypair.generate();
  const mockAdmin = Keypair.generate();

  let usdcMint: PublicKey;
  let wrongDecimalsMint: PublicKey;
  let wrongMint: PublicKey;
  let token2022Mint: PublicKey;
  let token2022StockMint: PublicKey;

  let ownerUsdcAta: PublicKey;
  let attackerUsdcAta: PublicKey;
  let wrongMintAta: PublicKey;

  const HARD_MAX_BALANCE = new BN(100_000_000); // 100 USDC (6 decimals)
  const MAX_BALANCE = new BN(50_000_000); // 50 USDC
  const VAULT_ID_0 = 0;
  const VAULT_ID_1 = 1;

  function getVaultPda(ownerKey: PublicKey, vaultId: number): [PublicKey, number] {
    return PublicKey.findProgramAddressSync(
      [Buffer.from("vault"), ownerKey.toBuffer(), Buffer.from([vaultId])],
      program.programId
    );
  }

  function getMockPda(stockMintKey: PublicKey): [PublicKey, number] {
    return PublicKey.findProgramAddressSync(
      [Buffer.from("mock"), stockMintKey.toBuffer()],
      program.programId
    );
  }

  before(async () => {
    // Fund test keypairs
    for (const kp of [owner, attacker, mockAdmin]) {
      const sig = await provider.connection.requestAirdrop(
        kp.publicKey,
        5 * LAMPORTS_PER_SOL
      );
      await provider.connection.confirmTransaction(sig, "confirmed");
    }

    // 1. Classic USDC Mint (6 decimals)
    usdcMint = await createMint(
      provider.connection,
      owner,
      owner.publicKey,
      null,
      6,
      undefined,
      undefined,
      TOKEN_PROGRAM_ID
    );

    // 2. Mint with wrong decimals (9 decimals)
    wrongDecimalsMint = await createMint(
      provider.connection,
      owner,
      owner.publicKey,
      null,
      9,
      undefined,
      undefined,
      TOKEN_PROGRAM_ID
    );

    // 3. Wrong Mint (6 decimals)
    wrongMint = await createMint(
      provider.connection,
      owner,
      owner.publicKey,
      null,
      6,
      undefined,
      undefined,
      TOKEN_PROGRAM_ID
    );

    // 4. Token-2022 Mint (6 decimals)
    token2022Mint = await createMint(
      provider.connection,
      owner,
      owner.publicKey,
      null,
      6,
      undefined,
      undefined,
      TOKEN_2022_PROGRAM_ID
    );

    // 5. Token-2022 Stock Mint for mock swap (6 decimals)
    token2022StockMint = await createMint(
      provider.connection,
      mockAdmin,
      mockAdmin.publicKey,
      null,
      6,
      undefined,
      undefined,
      TOKEN_2022_PROGRAM_ID
    );

    // Setup Owner ATAs
    ownerUsdcAta = await createAssociatedTokenAccount(
      provider.connection,
      owner,
      usdcMint,
      owner.publicKey,
      undefined,
      TOKEN_PROGRAM_ID
    );

    wrongMintAta = await createAssociatedTokenAccount(
      provider.connection,
      owner,
      wrongMint,
      owner.publicKey,
      undefined,
      TOKEN_PROGRAM_ID
    );

    // Setup Attacker ATA
    attackerUsdcAta = await createAssociatedTokenAccount(
      provider.connection,
      attacker,
      usdcMint,
      attacker.publicKey,
      undefined,
      TOKEN_PROGRAM_ID
    );

    // Mint USDC to owner and attacker
    await mintTo(
      provider.connection,
      owner,
      usdcMint,
      ownerUsdcAta,
      owner.publicKey,
      500_000_000 // 500 USDC
    );

    await mintTo(
      provider.connection,
      owner,
      wrongMint,
      wrongMintAta,
      owner.publicKey,
      500_000_000
    );

    await mintTo(
      provider.connection,
      owner,
      usdcMint,
      attackerUsdcAta,
      owner.publicKey,
      500_000_000
    );
  });

  describe("1. init_vault (Negative First)", () => {
    it("fails with zero cap (max_balance = 0)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .initVault(VAULT_ID_0, new BN(0))
          .accounts({
            owner: owner.publicKey,
            mint: usdcMint,
            vaultConfig,
            vaultTokenAccount,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidMaxBalance");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InvalidMaxBalance/);
      }
    });

    it("fails with cap over hard max (100_000_001)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .initVault(VAULT_ID_0, new BN(100_000_001))
          .accounts({
            owner: owner.publicKey,
            mint: usdcMint,
            vaultConfig,
            vaultTokenAccount,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidMaxBalance");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InvalidMaxBalance/);
      }
    });

    it("fails with wrong decimals (9 decimals)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        wrongDecimalsMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .initVault(VAULT_ID_0, MAX_BALANCE)
          .accounts({
            owner: owner.publicKey,
            mint: wrongDecimalsMint,
            vaultConfig,
            vaultTokenAccount,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidDecimals");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InvalidDecimals/);
      }
    });

    it("fails with Token-2022 mint (Token2022Rejected)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        token2022Mint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .initVault(VAULT_ID_0, MAX_BALANCE)
          .accounts({
            owner: owner.publicKey,
            mint: token2022Mint,
            vaultConfig,
            vaultTokenAccount,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with Token2022Rejected");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/Token2022Rejected|AccountOwnedByWrongProgram/);
      }
    });

    it("successfully initializes vault_id = 0", async () => {
      const [vaultConfig, bump] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      await program.methods
        .initVault(VAULT_ID_0, MAX_BALANCE)
        .accounts({
          owner: owner.publicKey,
          mint: usdcMint,
          vaultConfig,
          vaultTokenAccount,
          systemProgram: SystemProgram.programId,
          tokenProgram: TOKEN_PROGRAM_ID,
          associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const configAccount = await (program.account as any).vaultConfig.fetch(vaultConfig);
      expect(configAccount.owner.toBase58()).to.equal(owner.publicKey.toBase58());
      expect(configAccount.mint.toBase58()).to.equal(usdcMint.toBase58());
      expect(configAccount.vaultId).to.equal(VAULT_ID_0);
      expect(configAccount.maxBalance.toString()).to.equal(MAX_BALANCE.toString());
      expect(configAccount.bump).to.equal(bump);

      const ata = await getAccount(provider.connection, vaultTokenAccount);
      expect(ata.owner.toBase58()).to.equal(vaultConfig.toBase58());
      expect(ata.amount.toString()).to.equal("0");
    });

    it("fails on re-initialization of vault_id = 0", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .initVault(VAULT_ID_0, MAX_BALANCE)
          .accounts({
            owner: owner.publicKey,
            mint: usdcMint,
            vaultConfig,
            vaultTokenAccount,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed to re-init");
      } catch (err: any) {
        expect(err.message).to.match(/already in use|custom program error/);
      }
    });

    it("successfully initializes independent vault_id = 1", async () => {
      const [vaultConfig, bump] = getVaultPda(owner.publicKey, VAULT_ID_1);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      await program.methods
        .initVault(VAULT_ID_1, HARD_MAX_BALANCE)
        .accounts({
          owner: owner.publicKey,
          mint: usdcMint,
          vaultConfig,
          vaultTokenAccount,
          systemProgram: SystemProgram.programId,
          tokenProgram: TOKEN_PROGRAM_ID,
          associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const configAccount = await (program.account as any).vaultConfig.fetch(vaultConfig);
      expect(configAccount.vaultId).to.equal(VAULT_ID_1);
      expect(configAccount.maxBalance.toString()).to.equal(HARD_MAX_BALANCE.toString());
    });
  });

  describe("2. deposit (Negative First)", () => {
    it("fails with zero deposit amount", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .deposit(new BN(0))
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            ownerTokenAccount: ownerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with ZeroDepositAmount");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/ZeroDepositAmount/);
      }
    });

    it("fails when deposit exceeds cap (cap + 1)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const excessiveAmount = MAX_BALANCE.add(new BN(1));

      try {
        await program.methods
          .deposit(excessiveAmount)
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            ownerTokenAccount: ownerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with DepositExceedsCap");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/DepositExceedsCap/);
      }
    });

    it("fails when depositing from wrong mint token account", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .deposit(new BN(1_000_000))
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            ownerTokenAccount: wrongMintAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidMint");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InvalidMint/);
      }
    });

    it("fails when non-owner tries to deposit into vault", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .deposit(new BN(1_000_000))
          .accounts({
            owner: attacker.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            ownerTokenAccount: attackerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([attacker])
          .rpc();
        expect.fail("Should have failed with Unauthorized");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/Unauthorized|ConstraintSeeds/);
      }
    });

    it("successfully deposits partial amount (20 USDC)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const depositAmount = new BN(20_000_000); // 20 USDC

      await program.methods
        .deposit(depositAmount)
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          ownerTokenAccount: ownerUsdcAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const ata = await getAccount(provider.connection, vaultTokenAccount);
      expect(ata.amount.toString()).to.equal(depositAmount.toString());
    });

    it("successfully deposits up to exactly cap (remaining 30 USDC)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const depositAmount = new BN(30_000_000); // 30 USDC, totals 50 USDC (cap)

      await program.methods
        .deposit(depositAmount)
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          ownerTokenAccount: ownerUsdcAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const ata = await getAccount(provider.connection, vaultTokenAccount);
      expect(ata.amount.toString()).to.equal(MAX_BALANCE.toString());
    });

    it("fails when adding 1 micro-USDC when already at cap", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .deposit(new BN(1))
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            ownerTokenAccount: ownerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with DepositExceedsCap");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/DepositExceedsCap/);
      }
    });
  });

  describe("3. withdraw (Negative First)", () => {
    it("fails with zero withdraw amount", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .withdraw(new BN(0))
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            destinationTokenAccount: ownerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with ZeroWithdrawAmount");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/ZeroWithdrawAmount/);
      }
    });

    it("fails when non-owner tries to withdraw", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .withdraw(new BN(5_000_000))
          .accounts({
            owner: attacker.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            destinationTokenAccount: attackerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([attacker])
          .rpc();
        expect.fail("Should have failed with Unauthorized");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/Unauthorized|ConstraintSeeds/);
      }
    });

    it("fails when withdrawing over balance", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const excessiveAmount = MAX_BALANCE.add(new BN(1_000_000));

      try {
        await program.methods
          .withdraw(excessiveAmount)
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            destinationTokenAccount: ownerUsdcAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InsufficientVaultBalance");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InsufficientVaultBalance/);
      }
    });

    it("fails when destination token account is owned by someone else", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .withdraw(new BN(5_000_000))
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            destinationTokenAccount: attackerUsdcAta, // Attacker's ATA
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidDestinationOwner");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InvalidDestinationOwner/);
      }
    });

    it("fails when destination token account has wrong mint", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .withdraw(new BN(5_000_000))
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            destinationTokenAccount: wrongMintAta, // Wrong mint
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidMint");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/InvalidMint/);
      }
    });

    it("successfully performs partial withdrawal (15 USDC)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const withdrawAmount = new BN(15_000_000);

      await program.methods
        .withdraw(withdrawAmount)
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          destinationTokenAccount: ownerUsdcAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const ata = await getAccount(provider.connection, vaultTokenAccount);
      expect(ata.amount.toString()).to.equal(new BN(35_000_000).toString());
    });

    it("successfully performs full withdrawal (remaining 35 USDC)", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const remainingAmount = new BN(35_000_000);

      await program.methods
        .withdraw(remainingAmount)
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          destinationTokenAccount: ownerUsdcAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const ata = await getAccount(provider.connection, vaultTokenAccount);
      expect(ata.amount.toString()).to.equal("0");
    });
  });

  describe("4. close_vault (Negative First)", () => {
    it("fails when vault has non-zero balance", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      // Deposit 1 USDC
      await program.methods
        .deposit(new BN(1_000_000))
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          ownerTokenAccount: ownerUsdcAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      try {
        await program.methods
          .closeVault()
          .accounts({
            owner: owner.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with VaultNotEmpty");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/VaultNotEmpty/);
      }

      // Withdraw it back so balance is 0 again
      await program.methods
        .withdraw(new BN(1_000_000))
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          destinationTokenAccount: ownerUsdcAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();
    });

    it("fails when non-owner tries to close vault", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      try {
        await program.methods
          .closeVault()
          .accounts({
            owner: attacker.publicKey,
            vaultConfig,
            mint: usdcMint,
            vaultTokenAccount,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([attacker])
          .rpc();
        expect.fail("Should have failed with Unauthorized");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/Unauthorized|ConstraintSeeds/);
      }
    });

    it("successfully closes vault and reclaims rent", async () => {
      const [vaultConfig] = getVaultPda(owner.publicKey, VAULT_ID_0);
      const vaultTokenAccount = getAssociatedTokenAddressSync(
        usdcMint,
        vaultConfig,
        true,
        TOKEN_PROGRAM_ID
      );

      const preBalance = await provider.connection.getBalance(owner.publicKey);

      await program.methods
        .closeVault()
        .accounts({
          owner: owner.publicKey,
          vaultConfig,
          mint: usdcMint,
          vaultTokenAccount,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const postBalance = await provider.connection.getBalance(owner.publicKey);
      // Rent reclaimed should exceed transaction fee
      expect(postBalance).to.be.greaterThan(preBalance);

      // Verify accounts no longer exist
      const configInfo = await provider.connection.getAccountInfo(vaultConfig);
      expect(configInfo).to.be.null;

      const ataInfo = await provider.connection.getAccountInfo(vaultTokenAccount);
      expect(ataInfo).to.be.null;
    });
  });

  describe("5. mock-swap feature (devnet/localnet)", () => {
    let mockConfigPda: PublicKey;
    let treasuryAta: PublicKey;
    let sinkAta: PublicKey;
    let ownerStockAta: PublicKey;

    const INITIAL_PRICE = new BN(500_000_000); // 500 micro-USDC per whole token ($500/share)

    before(async () => {
      [mockConfigPda] = getMockPda(token2022StockMint);

      treasuryAta = getAssociatedTokenAddressSync(
        token2022StockMint,
        mockConfigPda,
        true,
        TOKEN_2022_PROGRAM_ID
      );

      sinkAta = getAssociatedTokenAddressSync(
        usdcMint,
        mockConfigPda,
        true,
        TOKEN_PROGRAM_ID
      );

      ownerStockAta = await createAssociatedTokenAccount(
        provider.connection,
        owner,
        token2022StockMint,
        owner.publicKey,
        undefined,
        TOKEN_2022_PROGRAM_ID
      );
    });

    it("initializes mock pool for Token-2022 stock mint", async () => {
      await program.methods
        .mockInit(INITIAL_PRICE)
        .accounts({
          admin: mockAdmin.publicKey,
          stockMint: token2022StockMint,
          usdcMint,
          mockConfig: mockConfigPda,
          treasuryTokenAccount: treasuryAta,
          sinkTokenAccount: sinkAta,
          systemProgram: SystemProgram.programId,
          usdcTokenProgram: TOKEN_PROGRAM_ID,
          stockTokenProgram: TOKEN_2022_PROGRAM_ID,
          associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        })
        .signers([mockAdmin])
        .rpc();

      const config = await (program.account as any).mockConfig.fetch(mockConfigPda);
      expect(config.admin.toBase58()).to.equal(mockAdmin.publicKey.toBase58());
      expect(config.price.toString()).to.equal(INITIAL_PRICE.toString());

      // Fund treasury with 100 stock tokens (100 * 10^6)
      await mintTo(
        provider.connection,
        mockAdmin,
        token2022StockMint,
        treasuryAta,
        mockAdmin.publicKey,
        100_000_000,
        [],
        undefined,
        TOKEN_2022_PROGRAM_ID
      );
    });

    it("fails when non-admin tries to change price", async () => {
      try {
        await program.methods
          .mockSetPrice(new BN(600_000_000))
          .accounts({
            admin: attacker.publicKey,
            mockConfig: mockConfigPda,
          })
          .signers([attacker])
          .rpc();
        expect.fail("Should have failed with Unauthorized");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/Unauthorized/);
      }
    });

    it("admin successfully updates price", async () => {
      const newPrice = new BN(525_000_000); // $525
      await program.methods
        .mockSetPrice(newPrice)
        .accounts({
          admin: mockAdmin.publicKey,
          mockConfig: mockConfigPda,
        })
        .signers([mockAdmin])
        .rpc();

      const config = await (program.account as any).mockConfig.fetch(mockConfigPda);
      expect(config.price.toString()).to.equal(newPrice.toString());

      // Set back to 500 for clean math
      await program.methods
        .mockSetPrice(INITIAL_PRICE)
        .accounts({
          admin: mockAdmin.publicKey,
          mockConfig: mockConfigPda,
        })
        .signers([mockAdmin])
        .rpc();
    });

    it("fails when output stock amount is below min_out (slippage)", async () => {
      // 5 USDC ($5) at $500/share = 0.01 shares (10,000 raw units)
      const usdcIn = new BN(5_000_000);
      const unreasonableMinOut = new BN(20_000); // Expects 0.02 shares

      try {
        await program.methods
          .mockSwap(usdcIn, unreasonableMinOut)
          .accounts({
            owner: owner.publicKey,
            mockConfig: mockConfigPda,
            usdcMint,
            stockMint: token2022StockMint,
            sinkTokenAccount: sinkAta,
            treasuryTokenAccount: treasuryAta,
            ownerUsdcAccount: ownerUsdcAta,
            ownerStockAccount: ownerStockAta,
            usdcTokenProgram: TOKEN_PROGRAM_ID,
            stockTokenProgram: TOKEN_2022_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with SlippageExceeded");
      } catch (err: any) {
        expect(err.error?.errorCode?.code || err.message).to.match(/SlippageExceeded/);
      }
    });

    it("successfully executes mock swap (5 USDC -> 0.01 Token-2022 stock shares)", async () => {
      const usdcIn = new BN(5_000_000);
      const expectedStockOut = 10_000; // 0.01 shares

      await program.methods
        .mockSwap(usdcIn, new BN(expectedStockOut))
        .accounts({
          owner: owner.publicKey,
          mockConfig: mockConfigPda,
          usdcMint,
          stockMint: token2022StockMint,
          sinkTokenAccount: sinkAta,
          treasuryTokenAccount: treasuryAta,
          ownerUsdcAccount: ownerUsdcAta,
          ownerStockAccount: ownerStockAta,
          usdcTokenProgram: TOKEN_PROGRAM_ID,
          stockTokenProgram: TOKEN_2022_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const userStockAccount = await getAccount(
        provider.connection,
        ownerStockAta,
        undefined,
        TOKEN_2022_PROGRAM_ID
      );
      expect(userStockAccount.amount.toString()).to.equal(expectedStockOut.toString());
    });
  });
});
