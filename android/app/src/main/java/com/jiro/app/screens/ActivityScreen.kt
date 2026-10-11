package com.jiro.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.jiro.app.components.Monogram
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.ActivityItem
import com.jiro.core.ActivityType

@Composable
fun ActivityScreen(
    activities: List<ActivityItem>,
    owedRoundUpUsdc: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.activity_title),
            style = JiroTypography.HeadlineMedium,
            color = JiroColors.Text
        )

        // Owed vs In-jar banner if any round-up is owed
        if (owedRoundUpUsdc > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JiroColors.Amber.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = JiroColors.Amber.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "⏳", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "Owed round-ups: $${String.format("%.2f", owedRoundUpUsdc / 1_000_000.0)}",
                            color = JiroColors.Amber,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Awaiting bundled wallet signature. Not yet deposited into jar.",
                            color = JiroColors.Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.activity_empty),
                    color = JiroColors.Muted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activities) { item ->
                    ActivityRow(
                        item = item,
                        onClick = {
                            if (item.signature.isNotBlank() && !item.signature.startsWith("fake_")) {
                                val url = "https://solscan.io/tx/${item.signature}?cluster=devnet"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityRow(
    item: ActivityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (iconText, iconColor, title) = when (item.type) {
        ActivityType.ROUND_UP_DEPOSIT -> Triple("↓", JiroColors.Amber, "Round-up deposit")
        ActivityType.STOCK_INVESTMENT -> Triple(
            "✦",
            JiroColors.Mint,
            if (item.stockSymbol != null) "Poured into ${item.stockSymbol}" else "Stock investment"
        )
        ActivityType.WITHDRAWAL -> Triple("↑", JiroColors.Sky, "Withdrawn to wallet")
        ActivityType.VAULT_CLOSED -> Triple("✕", JiroColors.Coral, "Jar closed")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, JiroColors.Line, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "$title for ${item.amount.formatUsdc()}" },
        colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconText, color = iconColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = JiroColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shortSig = if (item.signature.length > 8) {
                        "${item.signature.take(4)}...${item.signature.takeLast(4)}"
                    } else item.signature
                    Text(
                        text = shortSig,
                        color = JiroColors.Sky,
                        fontSize = 11.sp,
                        fontFamily = JiroTypography.MonoFamily
                    )
                    Text(
                        text = "• ${item.status}",
                        color = if (item.status == "Failed") JiroColors.Coral else JiroColors.Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = item.amount.formatUsdc(),
                color = if (item.type == ActivityType.WITHDRAWAL) JiroColors.Text else iconColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
