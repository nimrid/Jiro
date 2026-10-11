# Design brief: "The Jar"

## First principles
At any moment the user asks: where is my money, what happens next, and can I get out?
1. Make the invisible mechanism visible. A round-up is tiny with a delayed payoff, so show the split live,
   let the jar fill, and let the pour into a stock be a moment.
2. Remove decisions, not control. A style chosen once, one automatic pick per buy, and "Why this stock?" one tap away.
3. Honest numbers. Contributions only, no projected returns. Fees and price impact before signing.
4. Verifiable. Every amount links to the chain. Withdraw is always visible.

## Feel
A calm ledger crossed with a coin jar. Dark-first (Seeker OLED). Not a trading terminal, not a bank. Numbers are the hero.

## Tokens
Ink #0E1013 (bg), Surface #161A1F, Line #232932, Text #F2EFE8, Muted #8A93A0, Amber #FFB84D (waiting/pending),
Mint #5CE0B0 (owned/up), Coral #FF6B5B (error), Sky #7FB5FF (verified/links). One accent per screen.
Type: Space Grotesk (headings), Inter with tabular numerals (body/numbers), JetBrains Mono (addresses). Bundle with
OFL license files. 8dp grid, 16dp radii, 48dp touch targets.
Motion: drop falls 300ms, jar level eases 500ms, pour 600ms, haptic tick on each drop. "Reduce motion" = fades only.
Contrast AA minimum. Every interactive element has a TalkBack label. Support large font scale without clipping.

## Navigation
Bottom bar: Home, Portfolio, Activity. Centered Pay button. Settings via avatar. Single-activity, Compose Navigation,
`solana:` Solana Pay deep link intent filter.

## Screens
1. Onboarding (first value under 60s): connect wallet -> pick a style (3 cards, each previews its basket) -> set rules
   (round-up step, boost, jar cap; defaults pre-selected) -> create jar (one signature; shows refundable setup deposit
   in SOL). "Try demo data" link.
2. Home: Canvas jar filling toward the threshold, amber liquid. Under it: "$3.50 of $5.00 -> next: VOO" (or the next pick).
   Last 3 drops as tiny receipts. Primary button morphs Pay -> "Invest $5.20". Persistent "Withdraw anytime" chip.
   If a larger-deficit stock was skipped: one line "Skipped TSLA: paused".
3. Pay: amount entry or Solana Pay QR scan. Live split bar [ Pay $33.50 | +$0.50 to jar ], fee line beneath.
   Success: the drop animates into the jar.
4. Invest ("the pour"): chosen stock monogram, estimated tokens, price impact, total cost, quote countdown, market
   open/closed badge, "Why this stock?" expander (shows the underweight math). Confirm -> MWA.
5. Portfolio: treemap. Tile size = current weight, dashed outline = target weight, next-buy tile pulses amber.
   Tap a tile: units, value, contributions, mint, explorer link.
6. Pick: searchable list of ALL verified stocks (monogram, ticker, price, 24h change, liquidity badge Deep/OK/Thin,
   market status). Filter chips: All, Buyable, Index, Tech. Status chip per row: Buyable, Thin liquidity, Paused,
   Needs review. Detail sheet: issuer, mint, token program, plain disclosures ("issuer can pause transfers",
   "issuer can move tokens"). Add-to-basket with weight stepper and live stacked bar (sum 100%, max 5, min 10%).
7. Activity: one feed (round-up, owed, deposit, invest, withdraw); each row opens the explorer.
8. Settings/Safety: round-up step, multiplier, cap, withdraw all, close jar, cluster, disclosures, "unaudited" notice.

## States (design and build every one)
First run; catalog loading (skeleton rows, never spinner-only); catalog failed (error + retry + snapshot age);
jar empty; jar full; insufficient USDC; insufficient SOL; wallet declined; quote expired; thin liquidity (warn/block
buy, stock stays visible); market closed (warn); token changed since verification (blocked); offline (snapshot banner);
transaction pending / confirmed / failed (with explorer link); owed vs in-jar (never claim money is set aside when
it is only owed).

## Microcopy rules
Plain words. "In your jar" not "vault balance". "Owed" for round-ups not yet moved. Always say "tokenized stock".
No exclamation marks, no promises about returns.

## Do not
Show projected returns. Use stock logos (ticker monograms only). Hide fees. Use purple gradients or stock-photo
imagery. Use a bare spinner where progress can be shown.
