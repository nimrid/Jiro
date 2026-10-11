package com.jiro.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.R
import com.jiro.app.StockDetailInfoProvider
import com.jiro.app.components.Monogram
import com.jiro.app.components.StockLogo
import com.jiro.app.components.TreemapTile
import com.jiro.app.components.TreemapView
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.Money

@Composable
fun PortfolioScreen(
    holdings: List<TreemapTile>,
    totalValueUsdc: Long,
    totalContributionsUsdc: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTile by remember { mutableStateOf<TreemapTile?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.portfolio_title),
            style = JiroTypography.HeadlineMedium,
            color = JiroColors.Text
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Portfolio Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, JiroColors.Line, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.portfolio_total_value),
                        style = JiroTypography.LabelSmall,
                        color = JiroColors.Muted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Money(totalValueUsdc).formatUsdc(),
                        style = JiroTypography.HeadlineMedium,
                        fontSize = 20.sp,
                        color = JiroColors.Text
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, JiroColors.Line, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.portfolio_contributions),
                        style = JiroTypography.LabelSmall,
                        color = JiroColors.Muted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Money(totalContributionsUsdc).formatUsdc(),
                        style = JiroTypography.HeadlineMedium,
                        fontSize = 20.sp,
                        color = JiroColors.Mint
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Treemap visualization
        TreemapView(
            holdings = holdings,
            onTileClick = { tile -> selectedTile = tile },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Asset List Table
        Text(
            text = "Holdings",
            style = JiroTypography.TitleMedium,
            color = JiroColors.Text,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (holdings.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No holdings yet. Round-ups will invest into your basket.",
                        style = JiroTypography.BodyMedium,
                        color = JiroColors.Muted
                    )
                }
            }
        } else {
            holdings.forEach { holding ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedTile = holding }
                        .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StockLogo(ticker = holding.ticker, size = 40.dp)

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = holding.ticker,
                                    color = JiroColors.Text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (holding.isNextBuy) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(JiroColors.Amber.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Next buy",
                                            color = JiroColors.Amber,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${holding.currentWeightBps / 100}% (target: ${holding.targetWeightBps / 100}%)",
                                color = JiroColors.Muted,
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("$%.2f", holding.valueUsdc),
                                color = JiroColors.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format("%.4f units", holding.units),
                                color = JiroColors.Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Holding Detail Bottom Sheet
    selectedTile?.let { tile ->
        AlertDialog(
            onDismissRequest = { selectedTile = null },
            containerColor = JiroColors.SurfaceElevated,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StockLogo(ticker = tile.ticker, size = 40.dp)
                    Column {
                        Text(
                            text = tile.ticker,
                            color = JiroColors.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = tile.name,
                            color = JiroColors.Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val stockInfo = remember(tile.ticker, StockDetailInfoProvider.version) {
                        StockDetailInfoProvider.getInfo(tile.ticker, tile.name)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Market Price", color = JiroColors.Muted, fontSize = 13.sp)
                        Text(
                            text = String.format("$%.2f / share", stockInfo.priceUsd),
                            color = JiroColors.Text,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Current Value", color = JiroColors.Muted, fontSize = 13.sp)
                        Text(text = String.format("$%.2f", tile.valueUsdc), color = JiroColors.Text, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Contributions", color = JiroColors.Muted, fontSize = 13.sp)
                        Text(text = String.format("$%.2f", tile.contributionsUsdc), color = JiroColors.Mint, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Token Units", color = JiroColors.Muted, fontSize = 13.sp)
                        Text(text = String.format("%.6f", tile.units), color = JiroColors.Text)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Weight", color = JiroColors.Muted, fontSize = 13.sp)
                        Text(text = "${tile.currentWeightBps / 100}% (target: ${tile.targetWeightBps / 100}%)", color = JiroColors.Text)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(text = "Token Mint", color = JiroColors.Muted, fontSize = 12.sp)
                    Text(
                        text = tile.mint,
                        color = JiroColors.Sky,
                        fontSize = 11.sp,
                        fontFamily = JiroTypography.MonoFamily
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val explorerUrl = "https://solscan.io/token/${tile.mint}?cluster=devnet"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl)))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Sky),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "View on Solana Explorer", color = JiroColors.Ink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTile = null }) {
                    Text(text = "Close", color = JiroColors.Muted)
                }
            }
        )
    }
}
