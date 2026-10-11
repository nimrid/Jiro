# Workstream D: Android UI (Compose)
Read CLAUDE.md and docs/DESIGN.md fully. Build against FakeCatalogRepository + MockSwapProvider + fake wallet so UI never
blocks on other work. MVVM, StateFlow, coroutines, single activity, strings externalized.

## Build
- Theme from DESIGN.md tokens; bundle Space Grotesk, Inter (tabular numerals), JetBrains Mono with license files.
- JarView (Canvas): liquid level animation, surface wave, drop animation into the jar, haptic tick; reduced-motion fallback.
- TreemapView (Canvas/Compose layout): size by weight, dashed target outline, pulsing next-buy tile, tap to detail.
- SplitBar (live pay/round-up split), StackedWeightBar (basket editor with validation), Monogram (ticker avatar, deterministic
  color from ticker), StatusChip, LiquidityBadge, MarketBadge, QuoteCountdown.
- Screens: Onboarding (4 steps + demo data), Home, Pay (+QR scan), Invest sheet, Portfolio, Pick (all verified stocks, filters,
  detail sheet with disclosures), Activity, Settings/Safety.
- Every state in DESIGN.md has a preview and a UI test: loading skeletons, catalog failed with retry and snapshot age, jar full,
  insufficient funds, declined, expired quote, thin liquidity (buy blocked but stock visible), market closed, token changed,
  offline, pending/confirmed/failed, owed vs in-jar.
- "Why this stock?" renders the AllocationEngine Reason object, including skipped assets and reasons.
- Accessibility: TalkBack labels, 48dp targets, font scale 200% screenshots, AA contrast check.
- Screenshot tests for every screen and state (verify Paparazzi/Roborazzi fit the toolchain).

## Rules
No business logic in composables. No projected returns, no logos, no hidden fees. Never show an empty stock list.

## Done when
Demo mode walks through the whole flow with no network; all state previews exist; accessibility checks pass; final report lists
anything you could not verify on a real Seeker.
