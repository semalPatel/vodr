package com.vodr.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.vodr.parser.DocumentArtworkLoader
import com.vodr.ui.theme.VodrUiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentArtworkCover(
    title: String,
    sourceUri: String?,
    mimeType: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    placeholderTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
    placeholderContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    placeholderContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val bitmap by rememberDocumentArtworkBitmap(
        title = title,
        sourceUri = sourceUri,
        mimeType = mimeType,
    )
    val artworkBitmap = bitmap
    if (artworkBitmap != null) {
        Image(
            bitmap = artworkBitmap.asImageBitmap(),
            contentDescription = "$title cover art",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .clip(shape)
                .background(placeholderContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(2).uppercase(),
                style = placeholderTextStyle,
                color = placeholderContentColor,
            )
        }
    }
}

@Composable
fun PlaybackActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sizes = VodrUiTheme.sizes
    val spacing = VodrUiTheme.spacing
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = sizes.actionMinHeight),
        enabled = enabled,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(sizes.actionIcon),
            )
            Text(
                text = label,
                modifier = Modifier.semantics { this.contentDescription = contentDescription },
            )
        }
    }
}

@Composable
fun CompactPlaybackIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sizes = VodrUiTheme.sizes
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(sizes.compactActionIcon),
        )
    }
}

@Composable
private fun rememberDocumentArtworkBitmap(
    title: String,
    sourceUri: String?,
    mimeType: String?,
): State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(
        initialValue = null,
        key1 = title,
        key2 = sourceUri,
        key3 = mimeType,
    ) {
        value = if (sourceUri == null || mimeType == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                DocumentArtworkLoader.load(
                    context = context,
                    sourceUri = sourceUri,
                    mimeType = mimeType,
                    title = title,
                )
            }
        }
    }
}
