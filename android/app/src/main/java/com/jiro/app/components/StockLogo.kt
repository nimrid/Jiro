package com.jiro.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.jiro.app.theme.JiroColors

/**
 * Stock logo component that loads the official company SVG logo
 * from the Backpack Securities CDN, with a smooth Monogram fallback.
 */
@Composable
fun StockLogo(
    ticker: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    logoUrl: String? = null
) {
    val cleanTicker = ticker.removeSuffix(".US").uppercase()
    val url = logoUrl ?: "https://backpack.exchange/api/stock-logo/$cleanTicker"
    val context = LocalContext.current

    val imageRequest = ImageRequest.Builder(context)
        .data(url)
        .decoderFactory(SvgDecoder.Factory())
        .crossfade(true)
        .build()

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = "$cleanTicker stock logo",
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, JiroColors.Line, CircleShape)
            .padding(4.dp),
        contentScale = ContentScale.Fit,
        loading = {
            Monogram(ticker = cleanTicker, size = size)
        },
        error = {
            Monogram(ticker = cleanTicker, size = size)
        }
    )
}
