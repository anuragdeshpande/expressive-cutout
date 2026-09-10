package com.ekoehler.expressivecutout.ui.screen.integrations

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.PendingIntent
import android.content.Intent
import com.ekoehler.expressivecutout.R
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.core.IslandPreviewBus
import com.ekoehler.expressivecutout.data.NotificationMode
import com.ekoehler.expressivecutout.ui.AppViewModel
import com.ekoehler.expressivecutout.ui.components.ExpressiveSegmentedRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Simple data model for launchable apps displayed in the rule list. */
private data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

/** Preset scenario for the interactive cutout preview mockup. */
private enum class PreviewScenario(
    val title: String,
    val packageName: String,
    val appName: String,
    val smartTitle: String,
    val actionLabel: String,
    val actionIcon: ImageVector,
    val isSensitive: Boolean,
) {
    Chat(
        title = "Chat",
        packageName = "com.slack",
        appName = "Slack",
        smartTitle = "New message from Alex Vance",
        actionLabel = "Reply",
        actionIcon = Icons.AutoMirrored.Rounded.Send,
        isSensitive = true,
    ),
    TwoFactor(
        title = "2FA Auth",
        packageName = "com.duosecurity.duomobile",
        appName = "Duo Mobile",
        smartTitle = "Login request from Work SSO",
        actionLabel = "Approve",
        actionIcon = Icons.Rounded.Check,
        isSensitive = false,
    ),
    Community(
        title = "Community",
        packageName = "com.reddit.frontpage",
        appName = "Reddit",
        smartTitle = "New post in r/androiddev",
        actionLabel = "Open",
        actionIcon = Icons.AutoMirrored.Rounded.OpenInNew,
        isSensitive = false,
    ),
}

/** Converts a [PreviewScenario] into a simulated [CutoutSignal.Notification] for the live island overlay. */
private fun scenarioToSignal(context: Context, scenario: PreviewScenario, allowSensitive: Boolean): CutoutSignal.Notification {
    val isMasked = scenario.isSensitive && !allowSensitive
    val dummyIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent("com.ekoehler.expressivecutout.ACTION_PREVIEW_DUMMY"),
        PendingIntent.FLAG_IMMUTABLE,
    )
    val actions = listOf(
        CutoutSignal.Notification.Action(
            title = scenario.actionLabel,
            intent = dummyIntent,
        )
    )
    return CutoutSignal.Notification(
        packageName = scenario.packageName,
        title = scenario.smartTitle,
        text = null,
        appName = scenario.appName,
        postTimeMs = System.currentTimeMillis(),
        key = "preview_scenario_${scenario.name}",
        contentIntent = null,
        actions = actions,
        largeIcon = null,
        smallIcon = null,
        progressData = null,
        isSilent = false,
        mode = NotificationMode.PREVIEW,
        previewContextTag = scenario.appName,
        previewSummary = if (isMasked) context.getString(R.string.notif_preview_privacy_hidden_preview) else scenario.smartTitle,
        isContentMasked = isMasked,
        primaryAction = actions.firstOrNull(),
    )
}

/** Determines whether [packageName] or its detected actions belong to a 2FA authenticator app. */
private fun isTwoFactorApp(packageName: String, actions: List<String>): Boolean {
    val pkg = packageName.lowercase()
    if (pkg.contains("duo") || pkg.contains("authenticator") || pkg.contains("okta") ||
        pkg.contains("authy") || pkg.contains("bitwarden") || pkg.contains("onepassword") ||
        pkg.contains("1password") || pkg.contains("yubico") || pkg.contains("pingidentity")
    ) {
        return true
    }
    return actions.any { action ->
        val a = action.lowercase()
        a.contains("approve") || a.contains("deny")
    }
}

/**
 * Settings screen for the Notification Previews integration.
 */
@Composable
fun NotificationPreviewScreen(
    viewModel: AppViewModel,
    contentPadding: PaddingValues,
    onNavigateToAppRule: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by viewModel.notificationPreviewSettings.collectAsStateWithLifecycle()
    val detectedAppsAndActions by viewModel.detectedAppsAndActions.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedAppTab by rememberSaveable { mutableIntStateOf(0) }
    var simulatedStack by remember { mutableStateOf(listOf(PreviewScenario.Chat)) }

    // Stream simulated scenarios live to the physical device overlay while this screen is active
    DisposableEffect(Unit) {
        IslandPreviewBus.setActive(true)
        onDispose {
            IslandPreviewBus.setActive(false)
            IslandPreviewBus.setPreviewStack(emptyList())
        }
    }

    LaunchedEffect(simulatedStack, settings.allowSensitiveContentGlobally) {
        val signals = simulatedStack.map { scenarioToSignal(context, it, settings.allowSensitiveContentGlobally) }
        IslandPreviewBus.setPreviewStack(signals)
    }

    val coroutineScope = rememberCoroutineScope()
    val simulatorFlickAnim = remember { Animatable(0f) }
    var isFlickingSimulator by remember { mutableStateOf(false) }

    fun runSimulatorCycleAnim(onComplete: () -> Unit) {
        if (isFlickingSimulator || simulatedStack.size <= 1) return
        coroutineScope.launch {
            isFlickingSimulator = true
            simulatorFlickAnim.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            onComplete()
            simulatorFlickAnim.snapTo(0f)
            isFlickingSimulator = false
        }
    }

    fun triggerSimulatorCycle() {
        if (isFlickingSimulator || simulatedStack.size <= 1) return
        IslandPreviewBus.requestCycle()
        runSimulatorCycleAnim {
            simulatedStack = simulatedStack.drop(1) + simulatedStack.first()
        }
    }

    // Sync simulator rotation when user flicks cards directly on physical cutout overlay
    LaunchedEffect(Unit) {
        IslandPreviewBus.rotationCycle.collect { cycle ->
            if (cycle > 0 && simulatedStack.size > 1 && !isFlickingSimulator) {
                runSimulatorCycleAnim {
                    simulatedStack = simulatedStack.drop(1) + simulatedStack.first()
                }
            }
        }
    }

    val apps by produceState<List<InstalledAppInfo>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val resolves = pm.queryIntentActivities(mainIntent, 0)
            resolves.map {
                InstalledAppInfo(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                )
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Interactive Cutout Simulator
        item(key = "simulator") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.notif_preview_simulator_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Live Overlay Pinned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val activeScenario = simulatedStack.first()
                        val isContentMasked = activeScenario.isSensitive && !settings.allowSensitiveContentGlobally

                        // Stack Preview flush at top bezel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (simulatedStack.size > 2) 12.dp else if (simulatedStack.size > 1) 7.dp else 0.dp)
                                .pointerInput(simulatedStack.size) {
                                    if (simulatedStack.size <= 1) return@pointerInput
                                    val threshold = 10.dp.toPx()
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val startY = down.position.y
                                        var triggered = false
                                        val pointerId = down.id
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            val dragUpDistance = startY - change.position.y
                                            // Intercept upward gesture on initial pass so LazyColumn doesn't scroll
                                            if (dragUpDistance > 4f) {
                                                change.consume()
                                            }
                                            if (!isFlickingSimulator && !triggered && dragUpDistance >= threshold) {
                                                triggered = true
                                                change.consume()
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                triggerSimulatorCycle()
                                            }
                                            if (!change.pressed) {
                                                break
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            val fProgress = simulatorFlickAnim.value
                            val density = LocalDensity.current.density
                            val dynamicWidthPercent = remember(activeScenario, isContentMasked, simulatedStack.size, density) {
                                com.ekoehler.expressivecutout.overlay.previewCutoutWidthPercent(
                                    appName = activeScenario.appName,
                                    headerText = "${activeScenario.appName} • Now",
                                    summaryText = if (isContentMasked) "Content hidden for privacy" else activeScenario.smartTitle,
                                    actionLabel = activeScenario.actionLabel,
                                    stackCount = simulatedStack.size,
                                    isMasked = isContentMasked,
                                    displayWidthDp = 380,
                                    density = density,
                                    minWidthPercent = 65,
                                    maxWidthPercent = 98,
                                )
                            }
                            val animatedCardWidthPercent by animateFloatAsState(
                                targetValue = dynamicWidthPercent.toFloat(),
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "simCardWidth",
                            )
                            val baseFraction = (animatedCardWidthPercent / 100f).coerceIn(0.5f, 1f)

                            // Tucking card at the back of the deck
                            if (simulatedStack.size > 1 && fProgress > 0f) {
                                val tuckY = if (simulatedStack.size > 2) lerpDp(14.dp, 10.dp, fProgress) else lerpDp(8.dp, 5.dp, fProgress)
                                val tuckWidth = if (simulatedStack.size > 2) (0.85f + 0.05f * fProgress) else (0.90f + 0.05f * fProgress)
                                val tuckColor = if (simulatedStack.size > 2) Color(0xFF08090C) else Color(0xFF0A0B0E)
                                val tuckBorder = if (simulatedStack.size > 2) Color(0xFF1C1E23) else Color(0xFF24262C)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(baseFraction * tuckWidth)
                                        .height(56.dp)
                                        .offset(y = tuckY)
                                        .graphicsLayer { alpha = fProgress },
                                    shape = RoundedCornerShape(28.dp),
                                    color = tuckColor,
                                    border = BorderStroke(1.dp, tuckBorder),
                                ) {}
                            }

                            // Peek 3 card
                            if (simulatedStack.size > 2) {
                                val peek3Y = lerpDp(10.dp, 5.dp, fProgress)
                                val peek3Width = 0.90f + 0.05f * fProgress
                                val peek3Color = lerp(Color(0xFF08090C), Color(0xFF0A0B0E), fProgress)
                                val peek3Border = lerp(Color(0xFF1C1E23), Color(0xFF24262C), fProgress)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(baseFraction * peek3Width)
                                        .height(56.dp)
                                        .offset(y = peek3Y),
                                    shape = RoundedCornerShape(28.dp),
                                    color = peek3Color,
                                    border = BorderStroke(1.dp, peek3Border),
                                ) {}
                            }

                            // Peek 2 card
                            if (simulatedStack.size > 1) {
                                val peek2Y = lerpDp(5.dp, 0.dp, fProgress)
                                val peek2Width = 0.95f + 0.05f * fProgress
                                val peek2Color = lerp(Color(0xFF0A0B0E), Color(0xFF0C0D10), fProgress)
                                val peek2Border = lerp(Color(0xFF24262C), Color(0xFF2E3138), fProgress)
                                val secondScenario = simulatedStack.getOrNull(1)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(baseFraction * peek2Width)
                                        .height(56.dp)
                                        .offset(y = peek2Y),
                                    shape = RoundedCornerShape(28.dp),
                                    color = peek2Color,
                                    border = BorderStroke(1.dp, peek2Border),
                                ) {
                                    if (secondScenario != null && fProgress > 0f) {
                                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fProgress }) {
                                            SimulatorCardContent(
                                                scenario = secondScenario,
                                                isContentMasked = secondScenario.isSensitive && !settings.allowSensitiveContentGlobally,
                                                stackCount = simulatedStack.size,
                                                stackIndex = 2,
                                            )
                                        }
                                    }
                                }
                            }

                            // Active front card
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(baseFraction)
                                    .height(56.dp)
                                    .graphicsLayer {
                                        scaleX = 1f - fProgress * 0.04f
                                        scaleY = 1f - fProgress * 0.04f
                                        translationY = -fProgress * 28.dp.toPx()
                                        alpha = (1f - fProgress * 1.3f).coerceIn(0f, 1f)
                                    },
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xFF0C0D10),
                                border = BorderStroke(1.dp, Color(0xFF2E3138)),
                            ) {
                                SimulatorCardContent(
                                    scenario = activeScenario,
                                    isContentMasked = isContentMasked,
                                    stackCount = simulatedStack.size,
                                    stackIndex = 1,
                                )
                            }
                        }

                        // Scenario Switchers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            PreviewScenario.entries.forEach { scenario ->
                                val selected = simulatedStack.first() == scenario
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        simulatedStack = if (simulatedStack.size == 1) {
                                            listOf(scenario)
                                        } else {
                                            listOf(scenario) + simulatedStack.filterNot { it == scenario }
                                        }
                                    },
                                    label = { Text(scenario.title, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = Color(0xFF1B1D22),
                                        labelColor = Color.White.copy(alpha = 0.75f),
                                    ),
                                )
                            }
                        }

                        // Multi-card simulation buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (simulatedStack.size < settings.maxStackSize) {
                                        val candidates = PreviewScenario.entries.filterNot { it in simulatedStack }
                                        val next = candidates.firstOrNull() ?: PreviewScenario.entries[simulatedStack.size % PreviewScenario.entries.size]
                                        simulatedStack = simulatedStack + next
                                    }
                                },
                                enabled = simulatedStack.size < settings.maxStackSize,
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.notif_preview_simulator_add_stack), fontSize = 11.sp)
                            }

                            if (simulatedStack.size > 1) {
                                OutlinedButton(
                                    onClick = { triggerSimulatorCycle() },
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.notif_preview_simulator_cycle), fontSize = 11.sp)
                                }

                                IconButton(
                                    onClick = { simulatedStack = listOf(simulatedStack.first()) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.notif_preview_simulator_clear_stack),
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preview Stack Limit Slider Card
        item(key = "stack_limit") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.notif_preview_stack_limit_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.notif_preview_stack_count, settings.maxStackSize),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.notif_preview_stack_limit_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                    Slider(
                        value = settings.maxStackSize.toFloat(),
                        onValueChange = { viewModel.setMaxStackSize(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Detected 2FA Apps & Auto-Unfurl Card
        item(key = "2fa_apps_section") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.notif_preview_2fa_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.notif_preview_2fa_section_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                            )
                        }
                    }

                    val detected2Fa = apps?.filter {
                        isTwoFactorApp(it.packageName, detectedAppsAndActions[it.packageName].orEmpty())
                    }.orEmpty()

                    if (detected2Fa.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            detected2Fa.forEach { app ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Security,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = "Auto-Unfurl Ready",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.notif_preview_2fa_none_detected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Auto-unfurl toggle row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.notif_preview_2fa_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.notif_preview_2fa_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.autoUnfurlDynamicActions,
                            onCheckedChange = { viewModel.setAutoUnfurlDynamicActions(it) },
                        )
                    }
                }
            }
        }

        // App Rules Section Header & Segmented Tabs
        item(key = "apps_header") {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.notif_preview_apps_section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                ExpressiveSegmentedRow(
                    options = listOf(
                        stringResource(R.string.notif_preview_tab_detected),
                        stringResource(R.string.notif_preview_tab_all),
                    ),
                    selectedIndex = selectedAppTab,
                    onSelect = { selectedAppTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.notif_preview_search_hint)) },
                    singleLine = true,
                )
            }
        }

        val loadedApps = apps
        if (loadedApps == null) {
            item(key = "apps_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
        } else {
            val baseList = if (selectedAppTab == 0) {
                loadedApps.filter { it.packageName in detectedAppsAndActions.keys }
            } else {
                loadedApps
            }

            val filtered = baseList.filter {
                it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                item(key = "apps_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (selectedAppTab == 0) {
                                    stringResource(R.string.notif_preview_detected_empty)
                                } else {
                                    stringResource(R.string.notif_preview_search_empty)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.packageName }) { app ->
                    val isBlocked = app.packageName in settings.disabledContentPackages ||
                        (!settings.allowSensitiveContentGlobally && isLikelyMessagingApp(app.packageName))
                    AppRow(
                        app = app,
                        isContentAllowed = !isBlocked,
                        onClick = { onNavigateToAppRule(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledAppInfo,
    isContentAllowed: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val iconBitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(64, 64).asImageBitmap()
            }.getOrNull()
        }
    }.value

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                // Clean M3 Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isContentAllowed) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isContentAllowed) Icons.Rounded.Security else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isContentAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = if (isContentAllowed) stringResource(R.string.notif_preview_app_status_allowed) else stringResource(R.string.notif_preview_app_status_protected),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isContentAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun isLikelyMessagingApp(packageName: String): Boolean {
    val pkg = packageName.lowercase()
    return pkg.contains("teams") || pkg.contains("discord") || pkg.contains("whatsapp") ||
        pkg.contains("telegram") || pkg.contains("messaging") || pkg.contains("slack") ||
        pkg.contains("signal")
}

/**
 * Visual two-row content for a simulated notification preview card in the in-app interactive simulator.
 */
@Composable
private fun SimulatorCardContent(
    scenario: PreviewScenario,
    isContentMasked: Boolean,
    stackCount: Int,
    stackIndex: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // App icon badge
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (scenario == PreviewScenario.TwoFactor) Icons.Rounded.Security else Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Context and summary text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${scenario.appName} • Now",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (stackCount > 1) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "$stackIndex/$stackCount",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (isContentMasked) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = stringResource(R.string.notif_preview_privacy_hidden_preview),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = scenario.smartTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Action chip
        val isApprove = scenario.actionLabel == "Approve"
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isApprove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = scenario.actionIcon,
                    contentDescription = null,
                    tint = if (isApprove) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = scenario.actionLabel,
                    color = if (isApprove) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

