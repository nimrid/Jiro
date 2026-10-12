package com.jiro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiro.app.R
import com.jiro.app.RoundUp
import com.jiro.app.components.AdaptiveContainer
import com.jiro.app.components.SplitBar
import com.jiro.app.components.WindowWidthClass
import com.jiro.app.components.rememberAdaptiveSpec
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography
import com.jiro.core.Money

@Composable
fun PayScreen(
    paymentInput: String,
    onPaymentInputChange: (String) -> Unit,
    merchantAddress: String,
    onMerchantAddressChange: (String) -> Unit,
    roundWholeDollarUp: Boolean,
    onRoundWholeDollarUpChange: (Boolean) -> Unit,
    userUsdcBalance: Long,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirmPay: () -> Unit,
    onScanQrClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paymentUnits = try {
        RoundUp.parseUsdcToRaw(paymentInput)
    } catch (e: Exception) {
        0L
    }
    val roundUpUnits = RoundUp.calculate(paymentUnits, roundWholeDollarUp)
    val totalRequired = paymentUnits + roundUpUnits
    val isInsufficientFunds = totalRequired > userUsdcBalance
    val spec = rememberAdaptiveSpec()

    val presets = listOf("2.50", "4.35", "12.00", "33.50")

    AdaptiveContainer(
        maxWidth = 520.dp,
        modifier = modifier
            .fillMaxSize()
            .background(JiroColors.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spec.horizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spec.spacerSmall))

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
                        .semantics { contentDescription = "Back to home" }
                ) {
                    Text(text = "←", fontSize = 24.sp, color = JiroColors.Text)
                }
                Text(
                    text = stringResource(R.string.pay_title),
                    style = JiroTypography.TitleMedium,
                    color = JiroColors.Text
                )
                // QR scan button
                IconButton(
                    onClick = onScanQrClick,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Scan Solana Pay QR code" }
                ) {
                    Text(text = "⛶", fontSize = 20.sp, color = JiroColors.Sky)
                }
            }

            Spacer(modifier = Modifier.height(spec.spacerMedium))

            // Amount input field
            Text(
                text = stringResource(R.string.pay_amount_label),
                style = JiroTypography.BodyMedium,
                color = JiroColors.Muted,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = paymentInput,
                onValueChange = onPaymentInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Payment amount input" },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = JiroColors.Text,
                    unfocusedTextColor = JiroColors.Text,
                    focusedBorderColor = JiroColors.Amber,
                    unfocusedBorderColor = JiroColors.Line,
                    focusedContainerColor = JiroColors.Surface,
                    unfocusedContainerColor = JiroColors.Surface
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                prefix = { Text(text = "$ ", color = JiroColors.Amber, fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                textStyle = JiroTypography.HeadlineMedium.copy(fontSize = if (spec.widthClass == WindowWidthClass.NARROW) 24.sp else 28.sp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Preset quick chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(JiroColors.SurfaceElevated)
                            .border(1.dp, JiroColors.Line, RoundedCornerShape(8.dp))
                            .clickable { onPaymentInputChange(preset) }
                            .semantics { contentDescription = "Preset amount $$preset" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$$preset",
                            color = JiroColors.Text,
                            fontSize = if (spec.widthClass == WindowWidthClass.NARROW) 11.sp else 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spec.spacerMedium))

            // Live Split Bar
            SplitBar(
                paymentAmount = Money(paymentUnits),
                roundUpAmount = Money(roundUpUnits),
                modifier = Modifier.fillMaxWidth()
            )

        Spacer(modifier = Modifier.height(8.dp))

        // Network fee notice
        Text(
            text = stringResource(R.string.pay_fee_notice),
            style = JiroTypography.LabelSmall,
            color = JiroColors.Muted,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Merchant Address
        Text(
            text = stringResource(R.string.pay_merchant_address),
            style = JiroTypography.BodyMedium,
            color = JiroColors.Muted,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = merchantAddress,
            onValueChange = onMerchantAddressChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Merchant address field" },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = JiroColors.Text,
                unfocusedTextColor = JiroColors.Muted,
                focusedBorderColor = JiroColors.Line,
                unfocusedBorderColor = JiroColors.Line,
                focusedContainerColor = JiroColors.Surface,
                unfocusedContainerColor = JiroColors.Surface
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            textStyle = JiroTypography.MonospaceMedium.copy(fontSize = 13.sp)
        )

        // Error message if any
        if (errorMessage != null || isInsufficientFunds) {
            val displayErr = errorMessage ?: "Insufficient USDC balance (${Money(userUsdcBalance).formatUsdc()} available)"
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JiroColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JiroColors.Coral.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = displayErr,
                    color = JiroColors.Coral,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Confirm Pay Button
        Button(
            onClick = onConfirmPay,
            enabled = !isLoading && paymentUnits > 0L && !isInsufficientFunds,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JiroColors.Amber,
                disabledContainerColor = JiroColors.SurfaceElevated
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = JiroColors.Ink,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.pay_confirm_button),
                    color = JiroColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
}
