package com.jiro.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JiroTheme {
                val state by viewModel.uiState.collectAsState()
                JiroAppScreen(
                    state = state,
                    onConnect = { viewModel.connectWallet(this) },
                    onDisconnect = { viewModel.disconnectWallet() },
                    onRefresh = { viewModel.refreshBalances() },
                    onPaymentInputChanged = { viewModel.updatePaymentInput(it) },
                    onMerchantAddressChanged = { viewModel.updateMerchantAddress(it) },
                    onRoundWholeDollarChanged = { viewModel.setRoundWholeDollarUp(it) },
                    onPayAndInvest = { viewModel.executeBundledPayment(this) },
                    onInitVault = { viewModel.initializeVault(this) },
                    onWithdraw = { viewModel.withdrawVault(this) },
                    onViewExplorer = { tx ->
                        val url = "https://solscan.io/tx/$tx?cluster=devnet"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onClearMessages = { viewModel.clearMessages() }
                )
            }
        }
    }
}

@Composable
fun JiroTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF10B981),       // Vibrant Emerald
        onPrimary = Color(0xFF022C22),
        primaryContainer = Color(0xFF064E3B),
        onPrimaryContainer = Color(0xFFA7F3D0),
        secondary = Color(0xFF6366F1),     // Indigo
        onSecondary = Color.White,
        background = Color(0xFF0F172A),    // Slate 900
        surface = Color(0xFF1E293B),       // Slate 800
        surfaceVariant = Color(0xFF334155),
        onSurface = Color(0xFFF8FAFC),
        onSurfaceVariant = Color(0xFF94A3B8)
    )

    MaterialTheme(
        colorScheme = darkColors,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JiroAppScreen(
    state: AppUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onPaymentInputChanged: (String) -> Unit,
    onMerchantAddressChanged: (String) -> Unit,
    onRoundWholeDollarChanged: (Boolean) -> Unit,
    onPayAndInvest: () -> Unit,
    onInitVault: () -> Unit,
    onWithdraw: () -> Unit,
    onViewExplorer: (String) -> Unit,
    onClearMessages: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF10B981), Color(0xFF6366F1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "J",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("JIRO", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "DEVNET",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (state.isConnected) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status and Alert Banners
            state.errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, color = Color(0xFFFCA5A5), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearMessages) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                        }
                    }
                }
            }

            state.statusMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            state.lastTxSignature?.let { sig ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF6366F1)))),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Recent Transaction Confirmed", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            sig,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onViewExplorer(sig) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View on Solscan (Devnet)")
                        }
                    }
                }
            }

            // Section 1: Wallet Connection
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Solana Wallet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (state.isConnected) {
                            TextButton(onClick = onDisconnect) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!state.isConnected) {
                        Text(
                            "Connect your real Android wallet (Phantom, Solflare, or Seeker Seed Vault) via Mobile Wallet Adapter 2.0.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !state.isLoading
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect via MWA", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val pubkeyStr = state.publicKey?.toBase58() ?: ""
                        val truncated = if (pubkeyStr.length > 10) "${pubkeyStr.take(4)}...${pubkeyStr.takeLast(4)}" else pubkeyStr

                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Address:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text(truncated, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("USDC Balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${RoundUp.formatUsdc(state.ownerUsdcBalance)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SOL Fee Balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val sol = state.ownerSolBalance.toDouble() / 1_000_000_000.0
                                Text(String.format(java.util.Locale.US, "%.4f SOL", sol), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Section 2: Vault Progress ($5.00 sweep milestone)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Spare-Change Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val statusText = if (state.isVaultInitialized) "Active" else "Not Initialized"
                        val statusColor = if (state.isVaultInitialized) Color(0xFF10B981) else Color(0xFFF59E0B)
                        Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val vaultDollars = RoundUp.formatUsdc(state.vaultUsdcBalance)
                    val thresholdDollars = RoundUp.formatUsdc(state.vaultThreshold)

                    Text(
                        "$$vaultDollars",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress towards $5.00 Backpack stock buy
                    val progress = if (state.vaultThreshold > 0) {
                        (state.vaultUsdcBalance.toFloat() / state.vaultThreshold.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF10B981),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "$$vaultDollars of $$thresholdDollars to next Backpack stock buy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.isVaultInitialized && state.isConnected) {
                            Button(
                                onClick = onInitVault,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !state.isLoading
                            ) {
                                Text("Initialize Vault", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (state.vaultUsdcBalance > 0 && state.isConnected) {
                            OutlinedButton(
                                onClick = onWithdraw,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !state.isLoading
                            ) {
                                Text("Withdraw ($$$vaultDollars)")
                            }
                        }
                    }
                }
            }

            // Section 3: Bundled Payment + Round-Up (Single Signature)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pay Merchant + Round Up", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Single signature: [SPL USDC payment] + [vault.deposit(roundup)]",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = state.paymentInput,
                        onValueChange = onPaymentInputChanged,
                        label = { Text("Payment Amount (USDC)") },
                        leadingIcon = { Text("$", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live calculation breakdown
                    var paymentRaw = 0L
                    var roundUpRaw = 0L
                    var parseOk = false
                    try {
                        if (state.paymentInput.isNotBlank()) {
                            paymentRaw = RoundUp.parseUsdcToRaw(state.paymentInput)
                            roundUpRaw = RoundUp.calculate(paymentRaw, state.roundWholeDollarUp)
                            parseOk = true
                        }
                    } catch (ignored: Exception) {}

                    if (parseOk) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Merchant Payment:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$$${RoundUp.formatUsdc(paymentRaw)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Spare-Change Round-Up:", fontSize = 13.sp, color = Color(0xFF10B981))
                                    Text("+ $$${RoundUp.formatUsdc(roundUpRaw)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Charged (1 Signature):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("$$${RoundUp.formatUsdc(paymentRaw + roundUpRaw)}", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.merchantAddress,
                        onValueChange = onMerchantAddressChanged,
                        label = { Text("Merchant Recipient Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onPayAndInvest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = state.isConnected && parseOk && !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pay & Invest Round-Up",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
