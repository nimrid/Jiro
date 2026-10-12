package com.jiro.app.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.jiro.app.components.AdaptiveContainer
import com.jiro.app.components.Monogram
import com.jiro.app.components.StockLogo
import com.jiro.app.components.rememberAdaptiveSpec
import com.jiro.app.theme.JiroColors
import com.jiro.app.theme.JiroTypography

enum class OnboardingStep {
    CONNECT_WALLET,
    PICK_STYLE,
    SET_RULES,
    CREATE_JAR
}

data class InvestmentStyle(
    val id: String,
    val title: String,
    val description: String,
    val tickers: List<String>
)

val PRESET_STYLES = listOf(
    InvestmentStyle(
        id = "tech",
        title = "Tech Giants",
        description = "High-growth technology leaders",
        tickers = listOf("AAPL", "MSFT", "NVDA")
    ),
    InvestmentStyle(
        id = "index",
        title = "Index Core",
        description = "Broad market index trackers",
        tickers = listOf("VOO", "QQQ", "SPY")
    ),
    InvestmentStyle(
        id = "balanced",
        title = "Balanced 500",
        description = "Diversified index & blue chips",
        tickers = listOf("VOO", "AAPL", "MSFT", "GOOGL")
    )
)

@Composable
fun OnboardingScreen(
    currentStep: OnboardingStep,
    onStepChange: (OnboardingStep) -> Unit,
    onConnectWallet: () -> Unit,
    onTryDemo: () -> Unit,
    onCreateJar: (style: InvestmentStyle, stepUsd: Double, boostMultiplier: Int, thresholdUsd: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStyle by remember { mutableStateOf(PRESET_STYLES[0]) }
    var selectedStepUsd by remember { mutableStateOf(1.0) }
    var selectedBoost by remember { mutableStateOf(1) }
    var selectedThresholdUsd by remember { mutableStateOf(5.0) }
    val spec = rememberAdaptiveSpec()

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
                .padding(horizontal = spec.horizontalPadding, vertical = spec.spacerMedium)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App header
            Text(
                text = stringResource(R.string.app_name),
                style = JiroTypography.HeadlineMedium,
                color = JiroColors.Amber
            )
            Text(
                text = stringResource(R.string.onboarding_title),
                style = JiroTypography.BodyMedium,
                color = JiroColors.Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = spec.spacerMedium)
            )

        // Step progression indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnboardingStep.values().forEachIndexed { index, step ->
                val isActive = step == currentStep
                val isCompleted = step.ordinal < currentStep.ordinal
                val color = when {
                    isActive -> JiroColors.Amber
                    isCompleted -> JiroColors.Mint
                    else -> JiroColors.Line
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                        .semantics { contentDescription = "Step ${index + 1}: ${step.name}" }
                )
                if (index < OnboardingStep.values().size - 1) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (isCompleted) JiroColors.Mint else JiroColors.Line)
                    )
                }
            }
        }

        when (currentStep) {
            OnboardingStep.CONNECT_WALLET -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JiroColors.Line, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Welcome to Jiro",
                                style = JiroTypography.TitleMedium,
                                color = JiroColors.Text
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Every time you spend USDC, your payment is rounded up into a personal on-chain jar that pours automatically into tokenized stocks.",
                                style = JiroTypography.BodyMedium,
                                color = JiroColors.Muted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onConnectWallet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_connect_wallet),
                            color = JiroColors.Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onTryDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JiroColors.Sky),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_try_demo),
                            color = JiroColors.Sky,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            OnboardingStep.PICK_STYLE -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.onboarding_pick_style),
                        style = JiroTypography.TitleMedium,
                        color = JiroColors.Text
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    PRESET_STYLES.forEach { style ->
                        val isSelected = style == selectedStyle
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedStyle = style }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) JiroColors.Amber else JiroColors.Line,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) JiroColors.SurfaceElevated else JiroColors.Surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = style.title,
                                        style = JiroTypography.TitleMedium,
                                        color = JiroColors.Text
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "Selected",
                                            color = JiroColors.Amber,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = style.description,
                                    style = JiroTypography.BodyMedium,
                                    color = JiroColors.Muted,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    style.tickers.forEach { ticker ->
                                        StockLogo(ticker = ticker, size = 32.dp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onStepChange(OnboardingStep.SET_RULES) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Next: Set Rules",
                            color = JiroColors.Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            OnboardingStep.SET_RULES -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.onboarding_rules_title),
                        style = JiroTypography.TitleMedium,
                        color = JiroColors.Text
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Step
                    Text(
                        text = stringResource(R.string.onboarding_step_label),
                        style = JiroTypography.BodyMedium,
                        color = JiroColors.Muted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1.0, 2.0, 5.0).forEach { stepVal ->
                            val isSel = selectedStepUsd == stepVal
                            OutlinedButton(
                                onClick = { selectedStepUsd = stepVal },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) JiroColors.Amber.copy(alpha = 0.2f) else JiroColors.Surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) JiroColors.Amber else JiroColors.Line
                                )
                            ) {
                                Text(
                                    text = "$${stepVal.toInt()}.00",
                                    color = if (isSel) JiroColors.Amber else JiroColors.Text,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Multiplier
                    Text(
                        text = stringResource(R.string.onboarding_boost_label),
                        style = JiroTypography.BodyMedium,
                        color = JiroColors.Muted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { boostVal ->
                            val isSel = selectedBoost == boostVal
                            OutlinedButton(
                                onClick = { selectedBoost = boostVal },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) JiroColors.Amber.copy(alpha = 0.2f) else JiroColors.Surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) JiroColors.Amber else JiroColors.Line
                                )
                            ) {
                                Text(
                                    text = "${boostVal}x",
                                    color = if (isSel) JiroColors.Amber else JiroColors.Text,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Threshold
                    Text(
                        text = stringResource(R.string.onboarding_cap_label),
                        style = JiroTypography.BodyMedium,
                        color = JiroColors.Muted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5.0, 10.0, 25.0).forEach { capVal ->
                            val isSel = selectedThresholdUsd == capVal
                            OutlinedButton(
                                onClick = { selectedThresholdUsd = capVal },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) JiroColors.Amber.copy(alpha = 0.2f) else JiroColors.Surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) JiroColors.Amber else JiroColors.Line
                                )
                            ) {
                                Text(
                                    text = "$${capVal.toInt()}",
                                    color = if (isSel) JiroColors.Amber else JiroColors.Text,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onStepChange(OnboardingStep.CREATE_JAR) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Amber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Next: Create Jar",
                            color = JiroColors.Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            OnboardingStep.CREATE_JAR -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JiroColors.Line, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = JiroColors.Surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Setup Summary",
                                style = JiroTypography.TitleMedium,
                                color = JiroColors.Text
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Style: ${selectedStyle.title} (${selectedStyle.tickers.joinToString(", ")})",
                                style = JiroTypography.BodyMedium,
                                color = JiroColors.Text
                            )
                            Text(
                                text = "Round-up step: $${selectedStepUsd.toInt()}.00 (${selectedBoost}x boost)",
                                style = JiroTypography.BodyMedium,
                                color = JiroColors.Text
                            )
                            Text(
                                text = "Pour threshold: $${selectedThresholdUsd.toInt()}.00",
                                style = JiroTypography.BodyMedium,
                                color = JiroColors.Text
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.onboarding_deposit_notice),
                                style = JiroTypography.LabelSmall,
                                color = JiroColors.Sky
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            onCreateJar(selectedStyle, selectedStepUsd, selectedBoost, selectedThresholdUsd)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JiroColors.Mint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_create_jar),
                            color = JiroColors.Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
}
