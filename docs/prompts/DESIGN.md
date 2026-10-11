# Design brief: "The Jar"
Feel: a calm ledger crossed with a coin jar. Dark-first (Seeker OLED). Not a
trading terminal, not a bank. Numbers are the hero.

## Tokens
Ink #0E1013 (bg), Surface #161A1F, Line #232932, Text #F2EFE8 (warm off-white),
Muted #8A93A0, Amber #FFB84D (waiting/pending), Mint #5CE0B0 (owned/up),
Coral #FF6B5B (error), Sky #7FB5FF (verified/links). One accent per screen.
Type: Space Grotesk (headings), Inter with tabular numerals (body/numbers),
JetBrains Mono (addresses). All OFL. 8dp grid, 16dp radii, 48dp touch targets.
Motion: drop falls 300ms; jar level eases 500ms; pour 600ms. Haptic tick on
every drop. Respect "reduce motion" (fade instead). WCAG AA contrast.

## Navigation
Bottom bar: Home, Portfolio, Activity. Centered Pay button. Settings via avatar.

## Screens
1. Onboarding (4 steps, first value < 60s): connect wallet (MWA) -> pick a style
   (3 cards, each previews its basket) -> set rules (round-up step, boost, jar
   cap; defaults pre-selected) -> create jar (one signature, shows refundable
   setup deposit in SOL). "Try demo data" link for judges.
2. Home: a Canvas-drawn jar filling toward the threshold, with amber liquid.
   Under it: "$3.50 of $5.00 -> next: VOO" (or whichever stock is next).
   Last 3 drops as tiny receipts. Primary button morphs: Pay -> "Invest $5.20".
   Persistent chip: "Withdraw anytime".
3. Pay: amount entry or Solana Pay QR scan. A live split bar:
   [ Pay $33.50 | +$0.50 to jar ] with the fee line beneath. Confirm -> MWA.
   Success: the drop animates into the jar.
4. Invest sheet ("the pour"): chosen stock with monogram, estimated tokens,
   price impact, total cost, quote countdown, market-open/closed badge,
   "Why this stock?" expander (shows the underweight math). Confirm -> MWA.
5. Portfolio: a treemap of holdings. Tile size = current weight, dashed outline =
   target weight, the next-buy tile pulses amber. Tap a tile for position,
   units, value, contributions, mint, explorer link.
6. Pick (Explore): searchable catalog of eligible stocks (monogram, ticker, price,
   24h change, liquidity badge Deep/OK/Thin, market status). Add to basket with a
   weight stepper; a live stacked bar shows weights and validation (sum 100%,
   <= 5, >= 10%). Ineligible stocks are greyed with the reason.
7. Activity: one feed (round-up, deposit, invest, withdraw), each row opens
   the explorer.
8. Settings/Safety: round-up step, multiplier, cap, withdraw all, cluster,
   disclosures, "unaudited" notice.

## States (design every one)
First run; jar empty; jar full (cap); insufficient USDC; insufficient SOL;
wallet declined; quote expired; thin liquidity (blocked); market closed (warn);
offline (snapshot catalog + banner); transaction pending/confirmed/failed.

## Do not
Show projected returns. Use stock logos. Hide fees. Use purple gradients or
stock-photo style imagery. Use a spinner where a progress state is possible.