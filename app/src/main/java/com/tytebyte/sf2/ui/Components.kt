package com.tytebyte.sf2.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale

/** Картинка из галереи (только чтение). [fit] = true — вписать целиком, без обрезки. */
@Composable
fun Thumb(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sizePx: Int = 384,
    fit: Boolean = false,
) {
    val ctx = LocalContext.current
    val request = ImageRequest.Builder(ctx)
        .data(uri)
        .size(sizePx)
        .apply { if (fit) scale(Scale.FIT) }
        .crossfade(true)
        .build()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}
