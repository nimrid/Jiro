use anchor_lang::prelude::*;
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token::{self, Mint, Token, TokenAccount, TransferChecked};

#[cfg(feature = "mock-swap")]
use anchor_spl::token_interface::{self, Mint as MintInterface, TokenAccount as TokenAccountInterface, TokenInterface};

#[cfg(all(feature = "mainnet", feature = "mock-swap"))]
compile_error!("Feature 'mock-swap' cannot be compiled with 'mainnet' feature!");

declare_id!("AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc");

pub const HARD_MAX_BALANCE: u64 = 100_000_000; // 100 USDC (6 decimals)

#[program]
pub mod vault {
    use super::*;

    pub fn init_vault(
        ctx: Context<InitVault>,
        vault_id: u8,
        max_balance: u64,
    ) -> Result<()> {
        require!(
            max_balance > 0 && max_balance <= HARD_MAX_BALANCE,
            VaultError::InvalidMaxBalance
        );

        let vault_config = &mut ctx.accounts.vault_config;
        vault_config.owner = ctx.accounts.owner.key();
        vault_config.mint = ctx.accounts.mint.key();
        vault_config.vault_id = vault_id;
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

        emit!(Deposited {
            owner: ctx.accounts.owner.key(),
            amount,
            vault_id: ctx.accounts.vault_config.vault_id,
        });

        Ok(())
    }

    pub fn withdraw(ctx: Context<Withdraw>, amount: u64) -> Result<()> {
        require!(amount > 0, VaultError::ZeroWithdrawAmount);
        require!(
            amount <= ctx.accounts.vault_token_account.amount,
            VaultError::InsufficientVaultBalance
        );

        let owner_key = ctx.accounts.owner.key();
        let vault_id_bytes = [ctx.accounts.vault_config.vault_id];
        let bump = ctx.accounts.vault_config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[
            b"vault",
            owner_key.as_ref(),
            &vault_id_bytes,
            &[bump],
        ]];

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

        emit!(Withdrawn {
            owner: ctx.accounts.owner.key(),
            amount,
            vault_id: ctx.accounts.vault_config.vault_id,
        });

        Ok(())
    }

    pub fn close_vault(ctx: Context<CloseVault>) -> Result<()> {
        require!(
            ctx.accounts.vault_token_account.amount == 0,
            VaultError::VaultNotEmpty
        );

        let owner_key = ctx.accounts.owner.key();
        let vault_id_bytes = [ctx.accounts.vault_config.vault_id];
        let bump = ctx.accounts.vault_config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[
            b"vault",
            owner_key.as_ref(),
            &vault_id_bytes,
            &[bump],
        ]];

        let close_accounts = token::CloseAccount {
            account: ctx.accounts.vault_token_account.to_account_info(),
            destination: ctx.accounts.owner.to_account_info(),
            authority: ctx.accounts.vault_config.to_account_info(),
        };
        let cpi_ctx = CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            close_accounts,
            signer_seeds,
        );
        token::close_account(cpi_ctx)?;

        emit!(Closed {
            owner: ctx.accounts.owner.key(),
            amount: 0,
            vault_id: ctx.accounts.vault_config.vault_id,
        });

        Ok(())
    }

    #[cfg(feature = "mock-swap")]
    pub fn mock_init(ctx: Context<MockInit>, price: u64) -> Result<()> {
        require!(price > 0, VaultError::InvalidPrice);

        let mock_config = &mut ctx.accounts.mock_config;
        mock_config.admin = ctx.accounts.admin.key();
        mock_config.stock_mint = ctx.accounts.stock_mint.key();
        mock_config.price = price;
        mock_config.bump = ctx.bumps.mock_config;

        Ok(())
    }

    #[cfg(feature = "mock-swap")]
    pub fn mock_set_price(ctx: Context<MockSetPrice>, new_price: u64) -> Result<()> {
        require!(new_price > 0, VaultError::InvalidPrice);
        ctx.accounts.mock_config.price = new_price;
        Ok(())
    }

    #[cfg(feature = "mock-swap")]
    pub fn mock_swap(ctx: Context<MockSwap>, usdc_in: u64, min_out: u64) -> Result<()> {
        require!(usdc_in > 0, VaultError::ZeroDepositAmount);
        let price = ctx.accounts.mock_config.price;
        require!(price > 0, VaultError::InvalidPrice);

        // 1. Transfer USDC from user to sink ATA
        let usdc_cpi_accounts = TransferChecked {
            from: ctx.accounts.owner_usdc_account.to_account_info(),
            mint: ctx.accounts.usdc_mint.to_account_info(),
            to: ctx.accounts.sink_token_account.to_account_info(),
            authority: ctx.accounts.owner.to_account_info(),
        };
        let usdc_cpi_ctx = CpiContext::new(
            ctx.accounts.usdc_token_program.key(),
            usdc_cpi_accounts,
        );
        token::transfer_checked(usdc_cpi_ctx, usdc_in, ctx.accounts.usdc_mint.decimals)?;

        // 2. Compute stock output amount:
        // price is micro-USDC per whole stock token (10^stock_decimals raw units)
        let stock_decimals = ctx.accounts.stock_mint.decimals;
        let factor = 10_u128.pow(stock_decimals as u32);
        let numerator = (usdc_in as u128)
            .checked_mul(factor)
            .ok_or(VaultError::MathOverflow)?;
        let stock_out = (numerator / (price as u128)) as u64;

        require!(stock_out >= min_out, VaultError::SlippageExceeded);

        // 3. Transfer stock tokens from treasury ATA to owner stock account
        let stock_mint_key = ctx.accounts.stock_mint.key();
        let bump = ctx.accounts.mock_config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[b"mock", stock_mint_key.as_ref(), &[bump]]];

        let stock_cpi_accounts = token_interface::TransferChecked {
            from: ctx.accounts.treasury_token_account.to_account_info(),
            mint: ctx.accounts.stock_mint.to_account_info(),
            to: ctx.accounts.owner_stock_account.to_account_info(),
            authority: ctx.accounts.mock_config.to_account_info(),
        };
        let stock_cpi_ctx = CpiContext::new_with_signer(
            ctx.accounts.stock_token_program.key(),
            stock_cpi_accounts,
            signer_seeds,
        );
        token_interface::transfer_checked(stock_cpi_ctx, stock_out, stock_decimals)?;

        Ok(())
    }
}

#[account]
#[derive(InitSpace)]
pub struct VaultConfig {
    pub owner: Pubkey,
    pub mint: Pubkey,
    pub vault_id: u8,
    pub max_balance: u64,
    pub bump: u8,
}

#[derive(Accounts)]
#[instruction(vault_id: u8)]
pub struct InitVault<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        constraint = mint.to_account_info().owner == &anchor_spl::token::ID @ VaultError::Token2022Rejected,
        constraint = mint.decimals == 6 @ VaultError::InvalidDecimals,
    )]
    pub mint: Account<'info, Mint>,

    #[account(
        init,
        payer = owner,
        space = 8 + VaultConfig::INIT_SPACE,
        seeds = [b"vault", owner.key().as_ref(), &[vault_id]],
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
        seeds = [b"vault", owner.key().as_ref(), &[vault_config.vault_id]],
        bump = vault_config.bump,
        has_one = owner @ VaultError::Unauthorized,
        has_one = mint @ VaultError::InvalidMint,
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
        constraint = owner_token_account.owner == owner.key() @ VaultError::Unauthorized,
        constraint = owner_token_account.mint == mint.key() @ VaultError::InvalidMint,
    )]
    pub owner_token_account: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct Withdraw<'info> {
    pub owner: Signer<'info>,

    #[account(
        seeds = [b"vault", owner.key().as_ref(), &[vault_config.vault_id]],
        bump = vault_config.bump,
        has_one = owner @ VaultError::Unauthorized,
        has_one = mint @ VaultError::InvalidMint,
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
        constraint = destination_token_account.owner == owner.key() @ VaultError::InvalidDestinationOwner,
        constraint = destination_token_account.mint == vault_config.mint @ VaultError::InvalidMint,
    )]
    pub destination_token_account: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct CloseVault<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        mut,
        seeds = [b"vault", owner.key().as_ref(), &[vault_config.vault_id]],
        bump = vault_config.bump,
        has_one = owner @ VaultError::Unauthorized,
        has_one = mint @ VaultError::InvalidMint,
        close = owner
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

    pub token_program: Program<'info, Token>,
}

#[cfg(feature = "mock-swap")]
#[account]
#[derive(InitSpace)]
pub struct MockConfig {
    pub admin: Pubkey,
    pub stock_mint: Pubkey,
    pub price: u64,
    pub bump: u8,
}

#[cfg(feature = "mock-swap")]
#[derive(Accounts)]
pub struct MockInit<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    pub stock_mint: InterfaceAccount<'info, MintInterface>,
    pub usdc_mint: Account<'info, Mint>,

    #[account(
        init,
        payer = admin,
        space = 8 + MockConfig::INIT_SPACE,
        seeds = [b"mock", stock_mint.key().as_ref()],
        bump
    )]
    pub mock_config: Account<'info, MockConfig>,

    #[account(
        init,
        payer = admin,
        associated_token::mint = stock_mint,
        associated_token::authority = mock_config,
        associated_token::token_program = stock_token_program
    )]
    pub treasury_token_account: InterfaceAccount<'info, TokenAccountInterface>,

    #[account(
        init,
        payer = admin,
        associated_token::mint = usdc_mint,
        associated_token::authority = mock_config,
        associated_token::token_program = usdc_token_program
    )]
    pub sink_token_account: Account<'info, TokenAccount>,

    pub system_program: Program<'info, System>,
    pub usdc_token_program: Program<'info, Token>,
    pub stock_token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

#[cfg(feature = "mock-swap")]
#[derive(Accounts)]
pub struct MockSetPrice<'info> {
    pub admin: Signer<'info>,

    #[account(
        mut,
        seeds = [b"mock", mock_config.stock_mint.as_ref()],
        bump = mock_config.bump,
        has_one = admin @ VaultError::Unauthorized,
    )]
    pub mock_config: Account<'info, MockConfig>,
}

#[cfg(feature = "mock-swap")]
#[derive(Accounts)]
pub struct MockSwap<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        seeds = [b"mock", stock_mint.key().as_ref()],
        bump = mock_config.bump,
        has_one = stock_mint @ VaultError::InvalidMint,
    )]
    pub mock_config: Account<'info, MockConfig>,

    pub usdc_mint: Account<'info, Mint>,
    pub stock_mint: InterfaceAccount<'info, MintInterface>,

    #[account(
        mut,
        associated_token::mint = usdc_mint,
        associated_token::authority = mock_config,
        associated_token::token_program = usdc_token_program
    )]
    pub sink_token_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        associated_token::mint = stock_mint,
        associated_token::authority = mock_config,
        associated_token::token_program = stock_token_program
    )]
    pub treasury_token_account: InterfaceAccount<'info, TokenAccountInterface>,

    #[account(
        mut,
        constraint = owner_usdc_account.owner == owner.key() @ VaultError::Unauthorized,
        constraint = owner_usdc_account.mint == usdc_mint.key() @ VaultError::InvalidMint,
    )]
    pub owner_usdc_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        constraint = owner_stock_account.owner == owner.key() @ VaultError::Unauthorized,
        constraint = owner_stock_account.mint == stock_mint.key() @ VaultError::InvalidMint,
    )]
    pub owner_stock_account: InterfaceAccount<'info, TokenAccountInterface>,

    pub usdc_token_program: Program<'info, Token>,
    pub stock_token_program: Interface<'info, TokenInterface>,
}

#[event]
pub struct Deposited {
    pub owner: Pubkey,
    pub amount: u64,
    pub vault_id: u8,
}

#[event]
pub struct Withdrawn {
    pub owner: Pubkey,
    pub amount: u64,
    pub vault_id: u8,
}

#[event]
pub struct Closed {
    pub owner: Pubkey,
    pub amount: u64,
    pub vault_id: u8,
}

#[error_code]
pub enum VaultError {
    #[msg("Max balance must be greater than zero and at most 100 USDC")]
    InvalidMaxBalance,

    #[msg("Deposit amount must be greater than zero")]
    ZeroDepositAmount,

    #[msg("Deposit would exceed configured vault max balance cap")]
    DepositExceedsCap,

    #[msg("Withdraw amount must be greater than zero")]
    ZeroWithdrawAmount,

    #[msg("Withdraw amount exceeds vault balance")]
    InsufficientVaultBalance,

    #[msg("Vault token account balance must be zero to close vault")]
    VaultNotEmpty,

    #[msg("Destination token account owner must match vault owner")]
    InvalidDestinationOwner,

    #[msg("Token mint does not match vault config mint")]
    InvalidMint,

    #[msg("Mint decimals must be 6")]
    InvalidDecimals,

    #[msg("Token-2022 is rejected for vault token account (classic Token only)")]
    Token2022Rejected,

    #[msg("Math overflow occurred")]
    MathOverflow,

    #[msg("Signer is not authorized")]
    Unauthorized,

    #[msg("Price must be greater than zero")]
    InvalidPrice,

    #[msg("Output amount is below minimum expected amount")]
    SlippageExceeded,
}
