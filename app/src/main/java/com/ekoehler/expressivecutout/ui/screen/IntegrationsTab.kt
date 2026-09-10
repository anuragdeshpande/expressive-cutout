package com.ekoehler.expressivecutout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekoehler.expressivecutout.R
import com.ekoehler.expressivecutout.ui.AppViewModel
import java.io.Serializable

/** Routes within the Integrations tab navigation stack. */
sealed interface IntegrationsRoute : Serializable {
    /** The main list of available integrations. */
    data object List : IntegrationsRoute

    /** The Notification Previews configuration screen. */
    data object NotificationPreviews : IntegrationsRoute

    /** Detail screen for configuring an app's preview rules and sub-filters. */
    data class AppRule(val packageName: String) : IntegrationsRoute
}

/** Parent route for back navigation within the Integrations tab. */
val IntegrationsRoute.parent: IntegrationsRoute
    get() = when (this) {
        is IntegrationsRoute.AppRule -> IntegrationsRoute.NotificationPreviews
        is IntegrationsRoute.NotificationPreviews -> IntegrationsRoute.List
        IntegrationsRoute.List -> IntegrationsRoute.List
    }

/**
 * Top-level "Integrations" destination: showcases intelligent integrations like Notification Previews.
 */
@Composable
fun IntegrationsTab(
    viewModel: AppViewModel,
    contentPadding: PaddingValues,
    onNavigate: (IntegrationsRoute) -> Unit,
) {
    val settings by viewModel.notificationPreviewSettings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Tab Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.integrations_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.integrations_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Notification Previews Integration Card
        SettingsToggleNavCard(
            shape = RoundedCornerShape(20.dp),
            title = stringResource(R.string.integration_notif_preview_title),
            description = stringResource(R.string.integration_notif_preview_desc),
            checked = settings.enabled,
            onCheckedChange = { viewModel.setNotificationPreviewEnabled(it) },
            onClick = {
                if (settings.enabled) {
                    onNavigate(IntegrationsRoute.NotificationPreviews)
                }
            },
            leading = {
                val iconColor = if (settings.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
                val badgeColor = if (settings.enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeColor,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
        )
    }
}
