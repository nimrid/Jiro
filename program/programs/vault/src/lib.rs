use anchor_lang::prelude::*;
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token::{self, Mint, Token, TokenAccount, TransferChecked};

declare_id!("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc");

pub const HARD_MAX_BALANCE: u64 = 100_000_000; // 100 USDC (6 decimals)

#[program]
pub mod vault {
    use super::*;

    pub fn init_vault(
        ctx: Context<InitVault>,
        threshold: u64,
        max_balance: u64,
    ) -> Result<()> {
        require!(threshold > 0, VaultError::InvalidThreshold);
        require!(max_balance >= threshold, VaultError::MaxBalanceBelowThreshold);
        require!(max_balance <= HARD_MAX_BALANCE, VaultError::MaxBalanceExceedsHardCap);

        let vault_config = &mut ctx.accounts.vault_config;
        vault_config.owner = ctx.accounts.owner.key();
        vault_config.keeper = ctx.accounts.keeper.key();
        vault_config.mint = ctx.accounts.mint.key();
        vault_config.destination = ctx.accounts.destination.key();
        vault_config.threshold = threshold;
        vault_config.max_balance = max_balance;
        vault_config.bump = ctx.bumps.vault_config;

        Ok(())
    }

    pub fn deposit(ctx: Context<Deposit>, amount: u64) -> Result<()> {
        require!(amount > 0, VaultError::ZeroDepositAmount);

        let vault_balance = ctx.accounts.vault_token_account.amount;
        let new_balance = vault_balance
            .checked_add(amount)
            .ok_or(VaultError::MathOverflow)?;

        require!(
            new_balance <= ctx.accounts.vault_config.max_balance,
            VaultError::DepositExceedsCap
        );

        let cpi_accounts = TransferChecked {
            from: ctx.accounts.owner_token_account.to_account_info(),
            mint: ctx.accounts.mint.to_account_info(),
            to: ctx.accounts.vault_token_account.to_account_info(),
            authority: ctx.accounts.owner.to_account_info(),
        };
        let cpi_ctx = CpiContext::new(ctx.accounts.token_program.key(), cpi_accounts);
        token::transfer_checked(cpi_ctx, amount, ctx.accounts.mint.decimals)?;

        Ok(())
    }

    pub fn sweep(ctx: Context<Sweep>) -> Result<()> {
        let vault_balance = ctx.accounts.vault_token_account.amount;
        require!(
            vault_balance >= ctx.accounts.vault_config.threshold,
            VaultError::BelowSweepThreshold
        );

        let owner_key = ctx.accounts.vault_config.owner;
        let bump = ctx.accounts.vault_config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[b"vault", owner_key.as_ref(), &[bump]]];

        let cpi_accounts = TransferChecked {
            from: ctx.accounts.vault_token_account.to_account_info(),
            mint: ctx.accounts.mint.to_account_info(),
            to: ctx.accounts.destination_token_account.to_account_info(),
            authority: ctx.accounts.vault_config.to_account_info(),
        };
        let cpi_ctx = CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            cpi_accounts,
            signer_seeds,
        );
        token::transfer_checked(cpi_ctx, vault_balance, ctx.accounts.mint.decimals)?;

        emit!(Swept {
            owner: owner_key,
            amount: vault_balance,
        });

        Ok(())
    }

    pub fn withdraw(ctx: Context<Withdraw>, amount: u64) -> Result<()> {
        require!(amount > 0, VaultError::ZeroWithdrawAmount);
        require!(
            amount <= ctx.accounts.vault_token_account.amount,
            VaultError::InsufficientVaultBalance
        );

        let owner_key = ctx.accounts.vault_config.owner;
        let bump = ctx.accounts.vault_config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[b"vault", owner_key.as_ref(), &[bump]]];

        let cpi_accounts = TransferChecked {
            from: ctx.accounts.vault_token_account.to_account_info(),
            mint: ctx.accounts.mint.to_account_info(),
            to: ctx.accounts.destination_token_account.to_account_info(),
            authority: ctx.accounts.vault_config.to_account_info(),
        };
        let cpi_ctx = CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            cpi_accounts,
            signer_seeds,
        );
        token::transfer_checked(cpi_ctx, amount, ctx.accounts.mint.decimals)?;

        Ok(())
    }
}

#[account]
#[derive(InitSpace)]
pub struct VaultConfig {
    pub owner: Pubkey,
    pub keeper: Pubkey,
    pub mint: Pubkey,
    pub destination: Pubkey,
    pub threshold: u64,
    pub max_balance: u64,
    pub bump: u8,
}

#[derive(Accounts)]
pub struct InitVault<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    /// CHECK: Validated by storing in config and matching in sweep instruction
    pub keeper: UncheckedAccount<'info>,

    pub mint: Account<'info, Mint>,

    /// CHECK: Registered sweep destination token account (validated during sweep)
    pub destination: UncheckedAccount<'info>,

    #[account(
        init,
        payer = owner,
        space = 8 + VaultConfig::INIT_SPACE,
        seeds = [b"vault", owner.key().as_ref()],
        bump
    )]
    pub vault_config: Account<'info, VaultConfig>,

    #[account(
        init,
        payer = owner,
        associated_token::mint = mint,
        associated_token::authority = vault_config,
        associated_token::token_program = token_program
    )]
    pub vault_token_account: Account<'info, TokenAccount>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

#[derive(Accounts)]
pub struct Deposit<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        has_one = owner,
        has_one = mint,
        seeds = [b"vault", owner.key().as_ref()],
        bump = vault_config.bump
    )]
    pub vault_config: Account<'info, VaultConfig>,

    pub mint: Account<'info, Mint>,

    #[account(
        mut,
        associated_token::mint = mint,
        associated_token::authority = vault_config,
        associated_token::token_program = token_program
    )]
    pub vault_token_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        constraint = owner_token_account.owner == owner.key() @ VaultError::InvalidWithdrawOwner,
        constraint = owner_token_account.mint == mint.key() @ VaultError::InvalidDestinationMint
    )]
    pub owner_token_account: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct Sweep<'info> {
    pub keeper: Signer<'info>,

    #[account(
        has_one = keeper,
        has_one = mint,
        seeds = [b"vault", vault_config.owner.as_ref()],
        bump = vault_config.bump
    )]
    pub vault_config: Account<'info, VaultConfig>,

    pub mint: Account<'info, Mint>,

    #[account(
        mut,
        associated_token::mint = mint,
        associated_token::authority = vault_config,
        associated_token::token_program = token_program
    )]
    pub vault_token_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        constraint = destination_token_account.key() == vault_config.destination @ VaultError::InvalidDestinationAddress,
        constraint = destination_token_account.mint == mint.key() @ VaultError::InvalidDestinationMint
    )]
    pub destination_token_account: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct Withdraw<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        has_one = owner,
        has_one = mint,
        seeds = [b"vault", owner.key().as_ref()],
        bump = vault_config.bump
    )]
    pub vault_config: Account<'info, VaultConfig>,

    pub mint: Account<'info, Mint>,

    #[account(
        mut,
        associated_token::mint = mint,
        associated_token::authority = vault_config,
        associated_token::token_program = token_program
    )]
    pub vault_token_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        constraint = destination_token_account.owner == owner.key() @ VaultError::InvalidWithdrawOwner,
        constraint = destination_token_account.mint == mint.key() @ VaultError::InvalidDestinationMint
    )]
    pub destination_token_account: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
}

#[event]
pub struct Swept {
    pub owner: Pubkey,
    pub amount: u64,
}

#[error_code]
pub enum VaultError {
    #[msg("Threshold must be greater than zero")]
    InvalidThreshold,
    #[msg("Max balance must be at least equal to threshold")]
    MaxBalanceBelowThreshold,
    #[msg("Max balance exceeds hard cap of 100 USDC")]
    MaxBalanceExceedsHardCap,
    #[msg("Deposit amount must be greater than zero")]
    ZeroDepositAmount,
    #[msg("Deposit would exceed max balance cap")]
    DepositExceedsCap,
    #[msg("Vault balance is below sweep threshold")]
    BelowSweepThreshold,
    #[msg("Destination account address does not match config")]
    InvalidDestinationAddress,
    #[msg("Destination account mint does not match config mint")]
    InvalidDestinationMint,
    #[msg("Destination account owner must match vault owner")]
    InvalidWithdrawOwner,
    #[msg("Withdraw amount must be greater than zero")]
    ZeroWithdrawAmount,
    #[msg("Withdraw amount exceeds vault balance")]
    InsufficientVaultBalance,
    #[msg("Arithmetic overflow")]
    MathOverflow,
}
