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

  const programId = new PublicKey(idl.address);
  const program = new Program(idl as any, provider);

  const owner = Keypair.generate();
  const keeper = Keypair.generate();
  const attacker = Keypair.generate();
  const otherUser = Keypair.generate();

  let mint: PublicKey;
  let wrongMint: PublicKey;
  let ownerAta: PublicKey;
  let attackerAta: PublicKey;
  let destinationAta: PublicKey;
  let wrongMintDestinationAta: PublicKey;
  let attackerOwnedAta: PublicKey;

  let vaultPda: PublicKey;
  let vaultBump: number;
  let vaultAta: PublicKey;

  const HARD_MAX_BALANCE = new BN(100_000_000); // 100 USDC (6 decimals)
  const THRESHOLD = new BN(5_000_000); // 5 USDC
  const MAX_BALANCE = new BN(50_000_000); // 50 USDC

  before(async () => {
    // Fund test keypairs
    const airdropPromises = [owner, keeper, attacker, otherUser].map(async (kp) => {
      const sig = await provider.connection.requestAirdrop(
        kp.publicKey,
        5 * LAMPORTS_PER_SOL
      );
      await provider.connection.confirmTransaction(sig, "confirmed");
    });
    await Promise.all(airdropPromises);

    // Create classic SPL Token mints (6 decimals)
    mint = await createMint(
      provider.connection,
      owner,
      owner.publicKey,
      null,
      6
    );

    wrongMint = await createMint(
      provider.connection,
      owner,
      owner.publicKey,
      null,
      6
    );

    // Create token accounts
    ownerAta = await createAssociatedTokenAccount(
      provider.connection,
      owner,
      mint,
      owner.publicKey
    );

    attackerAta = await createAssociatedTokenAccount(
      provider.connection,
      attacker,
      mint,
      attacker.publicKey
    );

    // Destination ATA (pre-registered Backpack sweep destination)
    destinationAta = await createAssociatedTokenAccount(
      provider.connection,
      keeper,
      mint,
      keeper.publicKey
    );

    wrongMintDestinationAta = await createAssociatedTokenAccount(
      provider.connection,
      keeper,
      wrongMint,
      keeper.publicKey
    );

    attackerOwnedAta = attackerAta;

    // Mint USDC tokens to owner and attacker
    await mintTo(
      provider.connection,
      owner,
      mint,
      ownerAta,
      owner,
      1_000_000_000 // 1,000 USDC
    );

    await mintTo(
      provider.connection,
      owner,
      mint,
      attackerAta,
      owner,
      1_000_000_000
    );

    // Derive Vault PDA & ATA
    [vaultPda, vaultBump] = PublicKey.findProgramAddressSync(
      [Buffer.from("vault"), owner.publicKey.toBuffer()],
      programId
    );

    vaultAta = getAssociatedTokenAddressSync(
      mint,
      vaultPda,
      true,
      TOKEN_PROGRAM_ID,
      ASSOCIATED_TOKEN_PROGRAM_ID
    );
  });

  describe("1. init_vault", () => {
    it("fails when threshold is zero", async () => {
      try {
        await program.methods
          .initVault(new BN(0), MAX_BALANCE)
          .accountsPartial({
            owner: owner.publicKey,
            keeper: keeper.publicKey,
            mint,
            destination: destinationAta,
            vaultConfig: vaultPda,
            vaultTokenAccount: vaultAta,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with InvalidThreshold");
      } catch (err: any) {
        expect(err.toString()).to.include("InvalidThreshold");
      }
    });

    it("fails when max_balance < threshold", async () => {
      try {
        await program.methods
          .initVault(new BN(10_000_000), new BN(5_000_000))
          .accountsPartial({
            owner: owner.publicKey,
            keeper: keeper.publicKey,
            mint,
            destination: destinationAta,
            vaultConfig: vaultPda,
            vaultTokenAccount: vaultAta,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with MaxBalanceBelowThreshold");
      } catch (err: any) {
        expect(err.toString()).to.include("MaxBalanceBelowThreshold");
      }
    });

    it("fails when max_balance > HARD_MAX_BALANCE (100 USDC)", async () => {
      try {
        await program.methods
          .initVault(THRESHOLD, HARD_MAX_BALANCE.add(new BN(1)))
          .accountsPartial({
            owner: owner.publicKey,
            keeper: keeper.publicKey,
            mint,
            destination: destinationAta,
            vaultConfig: vaultPda,
            vaultTokenAccount: vaultAta,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Should have failed with MaxBalanceExceedsHardCap");
      } catch (err: any) {
        expect(err.toString()).to.include("MaxBalanceExceedsHardCap");
      }
    });

    it("succeeds with valid parameters and initializes vault ATA", async () => {
      await program.methods
        .initVault(THRESHOLD, MAX_BALANCE)
        .accountsPartial({
          owner: owner.publicKey,
          keeper: keeper.publicKey,
          mint,
          destination: destinationAta,
          vaultConfig: vaultPda,
          vaultTokenAccount: vaultAta,
          systemProgram: SystemProgram.programId,
          tokenProgram: TOKEN_PROGRAM_ID,
          associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const configAccount = await (program.account as any).vaultConfig.fetch(vaultPda);
      expect(configAccount.owner.equals(owner.publicKey)).to.be.true;
      expect(configAccount.keeper.equals(keeper.publicKey)).to.be.true;
      expect(configAccount.mint.equals(mint)).to.be.true;
      expect(configAccount.destination.equals(destinationAta)).to.be.true;
      expect(configAccount.threshold.toNumber()).to.equal(THRESHOLD.toNumber());
      expect(configAccount.maxBalance.toNumber()).to.equal(MAX_BALANCE.toNumber());
      expect(configAccount.bump).to.equal(vaultBump);

      const vaultTokenAcct = await getAccount(provider.connection, vaultAta);
      expect(vaultTokenAcct.amount.toString()).to.equal("0");
      expect(vaultTokenAcct.owner.equals(vaultPda)).to.be.true;
    });

    it("fails when re-initializing an existing vault", async () => {
      try {
        await program.methods
          .initVault(THRESHOLD, MAX_BALANCE)
          .accountsPartial({
            owner: owner.publicKey,
            keeper: keeper.publicKey,
            mint,
            destination: destinationAta,
            vaultConfig: vaultPda,
            vaultTokenAccount: vaultAta,
            systemProgram: SystemProgram.programId,
            tokenProgram: TOKEN_PROGRAM_ID,
            associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Re-init should have failed");
      } catch (err: any) {
        expect(err.toString()).to.include("already in use");
      }
    });
  });

  describe("2. deposit", () => {
    it("fails when deposit amount is zero", async () => {
      try {
        await program.methods
          .deposit(new BN(0))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Deposit of 0 must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("ZeroDepositAmount");
      }
    });

    it("fails when non-owner attempts to deposit into user vault", async () => {
      try {
        await program.methods
          .deposit(new BN(1_000_000))
          .accountsPartial({
            owner: attacker.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: attackerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([attacker])
          .rpc();
        expect.fail("Non-owner deposit must fail");
      } catch (err: any) {
        expect(err.toString()).to.satisfy((s: string) =>
          s.includes("ConstraintSeeds") || s.includes("has_one") || s.includes("2001") || s.includes("2006")
        );
      }
    });

    it("fails when wrong mint account is passed", async () => {
      try {
        await program.methods
          .deposit(new BN(1_000_000))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint: wrongMint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Wrong mint must fail");
      } catch (err: any) {
        expect(err.toString()).to.satisfy((s: string) =>
          s.includes("has_one") || s.includes("ConstraintRaw") || s.includes("InvalidDestinationMint") || s.includes("2001")
        );
      }
    });

    it("fails when deposit exceeds max_balance cap (cap + 1)", async () => {
      try {
        await program.methods
          .deposit(MAX_BALANCE.add(new BN(1)))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Deposit exceeding cap must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("DepositExceedsCap");
      }
    });

    it("succeeds when depositing exactly up to the cap", async () => {
      await program.methods
        .deposit(MAX_BALANCE)
        .accountsPartial({
          owner: owner.publicKey,
          vaultConfig: vaultPda,
          mint,
          vaultTokenAccount: vaultAta,
          ownerTokenAccount: ownerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const vaultTokenAcct = await getAccount(provider.connection, vaultAta);
      expect(vaultTokenAcct.amount.toString()).to.equal(MAX_BALANCE.toString());
    });

    it("fails when depositing even 1 more unit after reaching cap", async () => {
      try {
        await program.methods
          .deposit(new BN(1))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Deposit when full must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("DepositExceedsCap");
      }
    });

    it("fails cleanly when depositing u64::MAX without overflow or panic", async () => {
      try {
        const U64_MAX = new BN("18446744073709551615");
        await program.methods
          .deposit(U64_MAX)
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Deposit of u64::MAX must fail");
      } catch (err: any) {
        expect(err.toString()).to.satisfy((s: string) =>
          s.includes("DepositExceedsCap") || s.includes("MathOverflow") || s.includes("0x1774")
        );
      }
    });
  });

  describe("3. sweep", () => {
    it("fails when non-keeper attempts to sweep", async () => {
      try {
        await program.methods
          .sweep()
          .accountsPartial({
            keeper: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: destinationAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Non-keeper sweep must fail");
      } catch (err: any) {
        expect(err.toString()).to.satisfy((s: string) =>
          s.includes("has_one") || s.includes("ConstraintHasOne") || s.includes("2001")
        );
      }
    });

    it("fails when wrong destination address is passed", async () => {
      try {
        await program.methods
          .sweep()
          .accountsPartial({
            keeper: keeper.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: attackerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([keeper])
          .rpc();
        expect.fail("Wrong destination address must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("InvalidDestinationAddress");
      }
    });

    it("fails when destination has wrong mint", async () => {
      try {
        await program.methods
          .sweep()
          .accountsPartial({
            keeper: keeper.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: wrongMintDestinationAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([keeper])
          .rpc();
        expect.fail("Wrong mint destination must fail");
      } catch (err: any) {
        expect(err.toString()).to.satisfy((s: string) =>
          s.includes("InvalidDestinationMint") || s.includes("InvalidDestinationAddress")
        );
      }
    });

    it("succeeds when balance >= threshold and empties vault into fixed destination", async () => {
      const destBefore = await getAccount(provider.connection, destinationAta);

      let listenerId: number | null = null;
      const sweptPromise = new Promise<{ owner: PublicKey; amount: BN }>((resolve) => {
        listenerId = program.addEventListener("swept", (event: any) => {
          resolve(event);
        });
      });

      await program.methods
        .sweep()
        .accountsPartial({
          keeper: keeper.publicKey,
          vaultConfig: vaultPda,
          mint,
          vaultTokenAccount: vaultAta,
          destinationTokenAccount: destinationAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([keeper])
        .rpc();

      const sweptEvent = await sweptPromise;
      if (listenerId !== null) {
        await program.removeEventListener(listenerId);
      }

      expect(sweptEvent.owner.equals(owner.publicKey)).to.be.true;
      expect(sweptEvent.amount.toString()).to.equal(MAX_BALANCE.toString());

      const vaultAfter = await getAccount(provider.connection, vaultAta);
      expect(vaultAfter.amount.toString()).to.equal("0");

      const destAfter = await getAccount(provider.connection, destinationAta);
      expect(
        new BN(destAfter.amount.toString()).sub(new BN(destBefore.amount.toString())).toString()
      ).to.equal(MAX_BALANCE.toString());
    });

    it("fails to sweep when balance is below threshold (empty vault)", async () => {
      try {
        await program.methods
          .sweep()
          .accountsPartial({
            keeper: keeper.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: destinationAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([keeper])
          .rpc();
        expect.fail("Sweep below threshold must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("BelowSweepThreshold");
      }
    });

    it("succeeds when balance is exactly at threshold", async () => {
      // Deposit exactly THRESHOLD ($5)
      await program.methods
        .deposit(THRESHOLD)
        .accountsPartial({
          owner: owner.publicKey,
          vaultConfig: vaultPda,
          mint,
          vaultTokenAccount: vaultAta,
          ownerTokenAccount: ownerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      // Sweep exactly THRESHOLD
      await program.methods
        .sweep()
        .accountsPartial({
          keeper: keeper.publicKey,
          vaultConfig: vaultPda,
          mint,
          vaultTokenAccount: vaultAta,
          destinationTokenAccount: destinationAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([keeper])
        .rpc();

      const vaultAfter = await getAccount(provider.connection, vaultAta);
      expect(vaultAfter.amount.toString()).to.equal("0");
    });
  });

  describe("4. withdraw", () => {
    beforeEach(async () => {
      // Ensure vault has 10 USDC for withdraw tests
      const currentVault = await getAccount(provider.connection, vaultAta);
      if (currentVault.amount.toString() === "0") {
        await program.methods
          .deposit(new BN(10_000_000))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            ownerTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
      }
    });

    it("fails when withdraw amount is zero", async () => {
      try {
        await program.methods
          .withdraw(new BN(0))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Withdraw of 0 must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("ZeroWithdrawAmount");
      }
    });

    it("fails when non-owner attempts to withdraw", async () => {
      try {
        await program.methods
          .withdraw(new BN(1_000_000))
          .accountsPartial({
            owner: attacker.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: attackerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([attacker])
          .rpc();
        expect.fail("Non-owner withdraw must fail");
      } catch (err: any) {
        expect(err.toString()).to.satisfy((s: string) =>
          s.includes("has_one") || s.includes("ConstraintSeeds") || s.includes("2001")
        );
      }
    });

    it("fails when amount > balance", async () => {
      try {
        await program.methods
          .withdraw(new BN(100_000_000))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: ownerAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Withdraw exceeding balance must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("InsufficientVaultBalance");
      }
    });

    it("fails when destination token account is owned by someone else", async () => {
      try {
        await program.methods
          .withdraw(new BN(1_000_000))
          .accountsPartial({
            owner: owner.publicKey,
            vaultConfig: vaultPda,
            mint,
            vaultTokenAccount: vaultAta,
            destinationTokenAccount: attackerOwnedAta,
            tokenProgram: TOKEN_PROGRAM_ID,
          })
          .signers([owner])
          .rpc();
        expect.fail("Withdraw to someone else's token account must fail");
      } catch (err: any) {
        expect(err.toString()).to.include("InvalidWithdrawOwner");
      }
    });

    it("succeeds with partial withdraw", async () => {
      const ownerBefore = await getAccount(provider.connection, ownerAta);

      await program.methods
        .withdraw(new BN(4_000_000)) // 4 USDC
        .accountsPartial({
          owner: owner.publicKey,
          vaultConfig: vaultPda,
          mint,
          vaultTokenAccount: vaultAta,
          destinationTokenAccount: ownerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const vaultAfter = await getAccount(provider.connection, vaultAta);
      expect(vaultAfter.amount.toString()).to.equal("6000000"); // 6 USDC remaining

      const ownerAfter = await getAccount(provider.connection, ownerAta);
      expect(
        new BN(ownerAfter.amount.toString()).sub(new BN(ownerBefore.amount.toString())).toString()
      ).to.equal("4000000");
    });

    it("succeeds with full withdraw and leaves vault at 0", async () => {
      await program.methods
        .withdraw(new BN(6_000_000)) // withdraw remaining 6 USDC
        .accountsPartial({
          owner: owner.publicKey,
          vaultConfig: vaultPda,
          mint,
          vaultTokenAccount: vaultAta,
          destinationTokenAccount: ownerAta,
          tokenProgram: TOKEN_PROGRAM_ID,
        })
        .signers([owner])
        .rpc();

      const vaultAfter = await getAccount(provider.connection, vaultAta);
      expect(vaultAfter.amount.toString()).to.equal("0");
    });
  });
});
