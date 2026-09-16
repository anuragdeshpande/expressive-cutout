package com.ekoehler.expressivecutout.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekoehler.expressivecutout.R
import com.ekoehler.expressivecutout.bridge.data.BridgePairingStore
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

    /** The Android Bridge QR pairing and connection status screen. */
    data object AndroidBridge : IntegrationsRoute
}

/** Parent route for back navigation within the Integrations tab. */
val IntegrationsRoute.parent: IntegrationsRoute
    get() = when (this) {
        is IntegrationsRoute.AppRule -> IntegrationsRoute.NotificationPreviews
        is IntegrationsRoute.NotificationPreviews -> IntegrationsRoute.List
        IntegrationsRoute.AndroidBridge -> IntegrationsRoute.List
        IntegrationsRoute.List -> IntegrationsRoute.List
    }

/**
 * Top-level "Integrations" destination: showcases intelligent integrations like Notification Previews
 * and hardware continuity bridges like Android Bridge.
 *
 * @param viewModel Application viewmodel managing global settings.
 * @param contentPadding Insets to prevent content collision with system scrims.
 * @param onNavigate Lambda invoked when selecting an integration route.
 */
@Composable
fun IntegrationsTab(
    viewModel: AppViewModel,
    contentPadding: PaddingValues,
    onNavigate: (IntegrationsRoute) -> Unit,
) {
    val settings by viewModel.notificationPreviewSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bridgeStore = remember { BridgePairingStore(context) }
    val pairingRecord by bridgeStore.pairingRecord.collectAsStateWithLifecycle(initialValue = null)

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

        // Android Bridge Integration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onNavigate(IntegrationsRoute.AndroidBridge) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bridge_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.bridge_card_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.size(4.dp))

                    // Status indicator pill
                    val statusText = if (pairingRecord != null) {
                        stringResource(R.string.bridge_status_paired_connected)
                    } else {
                        stringResource(R.string.bridge_unpaired_title)
                    }
                    val statusColor = if (pairingRecord != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
