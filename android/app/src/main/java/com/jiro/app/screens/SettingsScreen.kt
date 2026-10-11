package com.jiro.app.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.R
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography

@Composable
fun SettingsScreen(
    roundUpStepUsd: Double,
    onRoundUpStepChange: (Double) -> Unit,
    multiplierBoost: Int,
    onMultiplierBoostChange: (Int) -> Unit,
    thresholdUsd: Double,
    onThresholdChange: (Double) -> Unit,
    isDemoMode: Boolean,
    onDemoModeToggle: (Boolean) -> Unit,
    onWithdrawAll: () -> Unit,
    onCloseJar: () -> Unit,
    onDisconnectWallet: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWithdrawConfirm by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Back from settings" }
            ) {
                Text(text = "←", fontSize = 24.sp, color = JiroColors.Text)
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = JiroTypography.TitleMedium,
                color = JiroColors.Text
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Unaudited notice banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JiroColors.Amber.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JiroColors.Amber.copy(alpha = 0.10f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "⚠ " + stringResource(R.string.unaudited_notice),
                    color = JiroColors.Amber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jiro smart contracts are currently in Devnet release testing. Balances are strictly capped at $100.00 max.",
                    color = JiroColors.Muted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Offline Demo Mode Switch
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JiroColors.Line, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_demo_mode),
                        style = JiroTypography.TitleMedium,
                        fontSize = 15.sp,
                        color = JiroColors.Text
                    )
                    Text(
                        text = "Walk through all features with simulated wallet and offline data",
                        style = JiroTypography.BodyMedium,
                        fontSize = 12.sp,
                        color = JiroColors.Muted
                    )
                }
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = onDemoModeToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JiroColors.Amber,
                        checkedTrackColor = JiroColors.Amber.copy(alpha = 0.3f),
                        uncheckedThumbColor = JiroColors.Muted,
                        uncheckedTrackColor = JiroColors.Line
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Rules Section
        Text(
            text = "Rules & Multipliers",
            style = JiroTypography.TitleMedium,
            color = JiroColors.Text,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Round up step
        Text(text = "Round-up step", color = JiroColors.Muted, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1.0, 2.0, 5.0).forEach { s ->
                val isSel = roundUpStepUsd == s
                OutlinedButton(
                    onClick = { onRoundUpStepChange(s) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSel) JiroColors.Amber.copy(alpha = 0.2f) else JiroColors.Surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) JiroColors.Amber else JiroColors.Line)
                ) {
                    Text(text = "$${s.toInt()}.00", color = if (isSel) JiroColors.Amber else JiroColors.Text)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Boost
        Text(text = "Multiplier boost", color = JiroColors.Muted, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 3).forEach { b ->
                val isSel = multiplierBoost == b
                OutlinedButton(
                    onClick = { onMultiplierBoostChange(b) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSel) JiroColors.Amber.copy(alpha = 0.2f) else JiroColors.Surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) JiroColors.Amber else JiroColors.Line)
                ) {
                    Text(text = "${b}x", color = if (isSel) JiroColors.Amber else JiroColors.Text)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Threshold
        Text(text = "Investment threshold cap", color = JiroColors.Muted, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5.0, 10.0, 25.0).forEach { t ->
                val isSel = thresholdUsd == t
                OutlinedButton(
                    onClick = { onThresholdChange(t) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSel) JiroColors.Amber.copy(alpha = 0.2f) else JiroColors.Surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) JiroColors.Amber else JiroColors.Line)
                ) {
                    Text(text = "$${t.toInt()}.00", color = if (isSel) JiroColors.Amber else JiroColors.Text)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Safety & Vault Actions
        Text(
            text = "Safety & Jar Management",
            style = JiroTypography.TitleMedium,
            color = JiroColors.Text,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Button(
            onClick = { showWithdrawConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JiroColors.SurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, JiroColors.Line),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = stringResource(R.string.settings_withdraw_all), color = JiroColors.Text, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { showCloseConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Coral.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, JiroColors.Coral.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = stringResource(R.string.settings_close_jar), color = JiroColors.Coral, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onDisconnectWallet,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = JiroColors.Coral
            ),
            border = BorderStroke(1.dp, JiroColors.Coral.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isDemoMode) "Exit Demo & Disconnect" else "Disconnect Wallet",
                color = JiroColors.Coral,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Disclosures
        Text(
            text = stringResource(R.string.settings_disclosures_title),
            style = JiroTypography.TitleMedium,
            fontSize = 14.sp,
            color = JiroColors.Text,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = stringResource(R.string.disclosures_text),
            style = JiroTypography.BodyMedium,
            fontSize = 12.sp,
            color = JiroColors.Muted
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_cluster),
            style = JiroTypography.LabelSmall,
            color = JiroColors.Muted
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showWithdrawConfirm) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirm = false },
            containerColor = JiroColors.SurfaceElevated,
            title = { Text(text = "Withdraw all from jar?", color = JiroColors.Text) },
            text = { Text(text = "This will return all USDC in your jar back to your main Solana wallet balance.", color = JiroColors.Muted) },
            confirmButton = {
                Button(
                    onClick = {
                        showWithdrawConfirm = false
                        onWithdrawAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber)
                ) {
                    Text(text = "Confirm Withdraw", color = JiroColors.Ink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirm = false }) {
                    Text(text = "Cancel", color = JiroColors.Muted)
                }
            }
        )
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            containerColor = JiroColors.SurfaceElevated,
            title = { Text(text = "Close jar account?", color = JiroColors.Coral) },
            text = { Text(text = "Your jar will be closed and the refundable SOL rent deposit will be returned to your wallet.", color = JiroColors.Muted) },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseConfirm = false
                        onCloseJar()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Coral)
                ) {
                    Text(text = "Close Jar", color = JiroColors.Text, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(text = "Cancel", color = JiroColors.Muted)
                }
            }
        )
    }
}
