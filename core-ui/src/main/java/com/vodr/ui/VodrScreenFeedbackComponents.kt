package com.vodr.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.vodr.ui.theme.VodrSurfaceStyles
import com.vodr.ui.theme.VodrUiTheme

enum class VodrMessageTone {
    DEFAULT,
    ERROR,
}

@Composable
fun VodrScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            VodrScreenTopBar(
                title = title,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun VodrScreenColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    fillMaxSize: Boolean = false,
    scrollable: Boolean = false,
    horizontalPadding: Dp? = null,
    verticalSpacing: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = VodrUiTheme.spacing
    val resolvedHorizontalPadding = horizontalPadding ?: spacing.xl
    val resolvedVerticalSpacing = verticalSpacing ?: spacing.md
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Surface(modifier = modifier.padding(contentPadding)) {
        Column(
            modifier = if (fillMaxSize) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = resolvedHorizontalPadding)
                    .then(scrollModifier)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = resolvedHorizontalPadding)
                    .then(scrollModifier)
            },
            verticalArrangement = Arrangement.spacedBy(resolvedVerticalSpacing),
            content = content,
        )
    }
}

@Composable
fun VodrMessageText(
    text: String,
    modifier: Modifier = Modifier,
    tone: VodrMessageTone = VodrMessageTone.DEFAULT,
) {
    val color = when (tone) {
        VodrMessageTone.DEFAULT -> Color.Unspecified
        VodrMessageTone.ERROR -> androidx.compose.material3.MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@Composable
fun VodrEmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = VodrUiTheme.spacing
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = VodrSurfaceStyles.subtleCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            VodrSectionHeader(
                title = title,
                subtitle = message,
            )
            if (actionLabel != null && onAction != null) {
                VodrInlineAction(
                    label = actionLabel,
                    onClick = onAction,
                )
            }
        }
    }
}
