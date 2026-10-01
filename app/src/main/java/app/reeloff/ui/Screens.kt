@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.reeloff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.reeloff.core.BlockEngine
import app.reeloff.core.BlockMode
import app.reeloff.core.BlockRule
import app.reeloff.core.PauseState
import app.reeloff.core.RuleCatalog
import app.reeloff.data.Settings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Onboarding: Google Play requires a prominent disclosure before asking for Accessibility access.
// ---------------------------------------------------------------------------------------------

@Composable
fun DisclosureScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("ReelOff", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Keep YouTube, Instagram and Facebook. Lose the Shorts, Reels and endless feeds.",
            style = MaterialTheme.typography.titleMedium,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How ReelOff uses Accessibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Bullet("ReelOff uses Android's Accessibility Service API to see which screen is open in the apps you choose to filter, such as the YouTube Shorts player or the Instagram Reels tab.")
                Bullet("When one of those screens appears, ReelOff presses Back or Home for you. That is the only action it takes.")
                Bullet("It reads screen structure (view ids, tab names, the browser address bar) - never your messages, passwords or posts.")
                Bullet("Nothing is collected or shared. ReelOff has no internet permission, so data cannot leave your phone. Only block counts are stored, on-device.")
            }
        }
        Text(
            "On the next screen, open \"Installed apps\" (or \"Downloaded apps\"), choose ReelOff and turn it on.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Agree and continue") }
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("No thanks") }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------------------------

/** A change that makes ReelOff less strict, held behind a countdown while protection is on. */
private data class Gate(val title: String, val onConfirm: () -> Unit)

@Composable
fun MainScreen(
    state: UiState,
    settings: Settings,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val paused = now < state.pausedUntil
    val protecting = state.serviceEnabled && !paused

    var gate by remember { mutableStateOf<Gate?>(null) }
    var showPauseChooser by remember { mutableStateOf(false) }

    /** Tightening applies at once; loosening waits out the commitment timer while protected. */
    fun loosen(title: String, action: () -> Unit) {
        if (protecting) gate = Gate(title, action) else action()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                    Text("ReelOff", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Use your apps. Skip the scroll.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                StatusCard(
                    state = state,
                    paused = paused,
                    now = now,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenAppInfo = onOpenAppInfo,
                    onPause = { showPauseChooser = true },
                    onResume = { settings.pauseState = settings.pauseState.resume() },
                )
            }
            item { StatsCard(state) }

            item { SectionHeader("Block short videos", "Each app keeps working - only the endless short-video part is closed.") }
            RuleCatalog.ALL.forEach { rule ->
                item(key = rule.id) {
                    RuleCard(
                        rule = rule,
                        state = state,
                        onToggle = { enabled ->
                            if (enabled) settings.setRuleEnabled(rule.id, true)
                            else loosen("Stop blocking ${rule.title}?") { settings.setRuleEnabled(rule.id, false) }
                        },
                        onAllowance = { minutes ->
                            if (minutes <= (state.allowanceMinutes[rule.id] ?: 0)) settings.setAllowanceMinutes(rule.id, minutes)
                            else loosen("Allow $minutes min of ${rule.title} a day?") { settings.setAllowanceMinutes(rule.id, minutes) }
                        },
                    )
                }
            }

            item { SectionHeader("Endless scroll limiter", "Feeds have no bottom. ReelOff adds one: after your swipe budget, the feed closes for a break.") }
            item {
                ScrollLimiterCard(
                    state = state,
                    onToggle = { enabled ->
                        if (enabled) settings.scrollLimitEnabled = true
                        else loosen("Turn off the scroll limiter?") { settings.scrollLimitEnabled = false }
                    },
                    onTogglePackage = { pkg, on ->
                        if (on) settings.scrollPackages = state.scrollPackages + pkg
                        else loosen("Stop limiting ${BlockEngine.appTitle(pkg)}?") { settings.scrollPackages = state.scrollPackages - pkg }
                    },
                    onSwipes = { swipes ->
                        if (swipes <= state.swipesPerSession) settings.swipesPerSession = swipes
                        else loosen("Raise the budget to $swipes swipes?") { settings.swipesPerSession = swipes }
                    },
                    onCooldown = { minutes ->
                        if (minutes >= state.scrollCooldownMinutes) settings.scrollCooldownMinutes = minutes
                        else loosen("Shorten breaks to $minutes min?") { settings.scrollCooldownMinutes = minutes }
                    },
                )
            }

            item { SectionHeader("Commitment", "Making ReelOff less strict means waiting first. Urges pass; this gives them time to.") }
            item {
                CommitmentCard(state.pauseWaitSeconds) { seconds ->
                    if (seconds >= state.pauseWaitSeconds) settings.pauseWaitSeconds = seconds
                    else loosen("Shorten the wait to ${formatWait(seconds)}?") { settings.pauseWaitSeconds = seconds }
                }
            }
            item { PrivacyFooter() }
        }
    }

    if (showPauseChooser) {
        PauseChooser(
            onDismiss = { showPauseChooser = false },
            onChoose = { minutes ->
                showPauseChooser = false
                gate = Gate("Pause ReelOff for $minutes minutes?") {
                    val start = System.currentTimeMillis()
                    settings.pauseState = PauseState().request(start).confirm(start, 0, minutes * 60_000L)
                }
            },
        )
    }

    gate?.let { g ->
        CountdownDialog(
            title = g.title,
            waitSeconds = state.pauseWaitSeconds,
            onCancel = { gate = null },
            onConfirm = {
                gate = null
                g.onConfirm()
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(
    state: UiState,
    paused: Boolean,
    now: Long,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, icon, title, body) = when {
        !state.serviceEnabled -> StatusLook(scheme.errorContainer, Icons.Filled.Warning, "Protection is off", "Turn on ReelOff in Accessibility settings to start blocking.")
        paused -> StatusLook(scheme.tertiaryContainer, Icons.Filled.Info, "Paused", "Back on automatically in ${formatDuration(state.pausedUntil - now)}.")
        else -> StatusLook(scheme.primaryContainer, Icons.Filled.CheckCircle, "You're protected", "${state.enabledRules.size} blockers on${if (state.scrollEnabled) " · scroll limiter on" else ""}.")
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            when {
                !state.serviceEnabled -> {
                    Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Turn on in Accessibility settings") }
                    Text(
                        "Toggle greyed out? On Android 13+ apps installed outside Google Play need one extra step: open App info, tap ⋮ (top right) and choose \"Allow restricted settings\", then try again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) { Text("Open App info") }
                }
                paused -> Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume protection now") }
                else -> OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pause protection")
                }
            }
        }
    }
}

private data class StatusLook(
    val container: androidx.compose.ui.graphics.Color,
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@Composable
private fun StatsCard(state: UiState) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("${state.blocksToday}", "blocked today")
                Stat("${state.blocksWeek.sum()}", "this week")
                Stat(formatMinutes(state.minutesSaved), "reclaimed*")
            }
            WeekBars(state.blocksWeek)
            Text(
                "*Estimate: ~${Settings.MINUTES_SAVED_PER_BLOCK} min per interruption, ${state.blocksTotal} interruptions so far.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekBars(counts: List<Int>) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = remember {
        val today = java.time.LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) }
    }
    Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, count ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((52f * count / max).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (i == counts.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[i], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val ALLOWANCE_CHOICES = listOf(0, 5, 10, 15, 30)

@Composable
private fun RuleCard(
    rule: BlockRule,
    state: UiState,
    onToggle: (Boolean) -> Unit,
    onAllowance: (Int) -> Unit,
) {
    val enabled = rule.id in state.enabledRules
    val allowance = state.allowanceMinutes[rule.id] ?: 0
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                HorizontalDivider()
                Text(
                    if (rule.mode == BlockMode.WHOLE_APP) "Daily allowance for the app" else "Daily allowance",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ALLOWANCE_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = allowance == minutes,
                            onClick = { onAllowance(minutes) },
                            label = { Text(if (minutes == 0) "Block always" else "$minutes min") },
                        )
                    }
                }
                if (allowance > 0) {
                    val used = ((state.allowanceUsedMillis[rule.id] ?: 0L) / 1000f / 60f)
                    LinearProgressIndicator(
                        progress = { (used / allowance).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    )
                    Text("${used.roundToInt()} of $allowance min used today", style = MaterialTheme.typography.labelSmall)
                }
                val count = state.blocksByRule[rule.id] ?: 0
                if (count > 0) Text("Closed $count times so far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val COOLDOWN_CHOICES = listOf(5, 15, 30, 60)

@Composable
private fun ScrollLimiterCard(
    state: UiState,
    onToggle: (Boolean) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit,
    onSwipes: (Int) -> Unit,
    onCooldown: (Int) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Limit endless scrolling", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "One swipe ≈ one screen of feed. Leave the feed alone for 10 minutes and the budget refills.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.scrollEnabled, onCheckedChange = onToggle)
            }
            if (state.scrollEnabled) {
                HorizontalDivider()
                Text("Apps", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockEngine.FEED_APPS.forEach { (pkg, name) ->
                        val on = pkg in state.scrollPackages
                        FilterChip(selected = on, onClick = { onTogglePackage(pkg, !on) }, label = { Text(name) })
                    }
                }
                // Slider moves fire continuously; commit only when the finger lifts.
                var swipes by remember(state.swipesPerSession) { mutableFloatStateOf(state.swipesPerSession.toFloat()) }
                Text("Swipe budget per sitting: ${swipes.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = swipes,
                    onValueChange = { swipes = it },
                    onValueChangeFinished = { onSwipes(swipes.roundToInt()) },
                    valueRange = 10f..100f,
                    steps = 17,
                )
                Text("Break length", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COOLDOWN_CHOICES.forEach { minutes ->
                        FilterChip(selected = state.scrollCooldownMinutes == minutes, onClick = { onCooldown(minutes) }, label = { Text("$minutes min") })
                    }
                }
            }
        }
    }
}

private val WAIT_CHOICES = listOf(10, 30, 60, 120, 300)

@Composable
private fun CommitmentCard(waitSeconds: Int, onWait: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wait before loosening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Applies to pausing, turning blockers off and raising limits. Making things stricter is always instant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WAIT_CHOICES.forEach { seconds ->
                    FilterChip(selected = waitSeconds == seconds, onClick = { onWait(seconds) }, label = { Text(formatWait(seconds)) })
                }
            }
        }
    }
}

@Composable
private fun PrivacyFooter() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "No internet permission. No accounts. No tracking.\nEverything ReelOff sees stays on this phone.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
private fun PauseChooser(onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause for how long?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Protection switches itself back on afterwards.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        FilterChip(selected = false, onClick = { onChoose(minutes) }, label = { Text("$minutes min") })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Never mind") } },
    )
}

private val NUDGES = listOf(
    "What were you about to do before this urge?",
    "Will you remember these videos tomorrow?",
    "Take three slow breaths while you wait.",
    "Is there a person you could message instead?",
    "Future you set this up for a reason.",
    "Stand up, stretch, drink some water.",
)

@Composable
private fun CountdownDialog(title: String, waitSeconds: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    var remaining by remember { mutableLongStateOf(waitSeconds.toLong()) }
    LaunchedEffect(Unit) {
        val end = System.currentTimeMillis() + waitSeconds * 1_000L
        while (remaining > 0) {
            remaining = ((end - System.currentTimeMillis() + 999) / 1_000).coerceAtLeast(0)
            delay(250)
        }
    }
    val nudge = NUDGES[((waitSeconds - remaining) / 8).toInt().mod(NUDGES.size)]
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { if (waitSeconds == 0) 1f else 1f - remaining.toFloat() / waitSeconds },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                )
                Text(
                    if (remaining > 0) "Available in ${formatDuration(remaining * 1_000)}" else "You can continue now.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(nudge, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = remaining <= 0) { Text("Continue") }
        },
        dismissButton = {
            Button(onClick = onCancel) { Text("Keep me focused") }
        },
    )
}

// ---------------------------------------------------------------------------------------------

private fun formatDuration(millis: Long): String {
    val total = (millis / 1_000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}m ${s.toString().padStart(2, '0')}s" else "${s}s"
}

private fun formatWait(seconds: Int): String = if (seconds < 60) "${seconds}s" else "${seconds / 60} min"

private fun formatMinutes(minutes: Int): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
