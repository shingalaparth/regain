package app.focuslite.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.focuslite.SystemStatus
import app.focuslite.core.model.Surface
import app.focuslite.core.model.SurfaceMode
import app.focuslite.core.model.SurfaceSetting
import app.focuslite.engine.usage.Goals
import app.focuslite.engine.usage.formatDuration
import app.focuslite.usage.UsageData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    env: AppEnv,
    status: SystemStatus,
    usage: UsageData,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onOpenTime: () -> Unit,
    onOpenRoutines: () -> Unit,
) {
    val context = LocalContext.current
    var blocksToday by remember { mutableStateOf(env.stats.forDay()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { blocksToday = env.stats.forDay() }

    // Only the apps that are really installed on this phone get a tile.
    val installed = Platform.entries.filter { p ->
        val pkgs = env.platformPackages.getValue(p)
        rememberAppIcon(pkgs) != null
    }.ifEmpty { Platform.entries }
    val pager = rememberPagerState { installed.size }
    val current = installed[pager.currentPage.coerceIn(0, installed.lastIndex)]
    var sheetFor by remember { mutableStateOf<Platform?>(null) }

    val today = LocalDate.now()
    val allPackages = env.platformPackages.values.flatten().toSet()
    val goalMs = env.state.goalMinutes * 60_000L
    val todayMs = usage.msOn(allPackages, today)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Regain", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            StatusPill(on = status.blocking)
        }

        Spacer(28.dp)

        // The selected app: real time on screen today, then how many blocks happened
        val blockedHere = blocksToday.filterKeys { it in current.surfaces }.values.sum()
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("${current.label} today")
            when {
                !usage.loaded -> Text("…", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                usage.hasAccess -> {
                    val ms = usage.msOn(env.platformPackages.getValue(current), today)
                    Text(
                        formatDuration(ms),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { contentDescription = "${formatDuration(ms)} on ${current.label} today" },
                    )
                }
                else -> Text(
                    "Allow usage access",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .padding(vertical = 6.dp),
                )
            }
            val reelsHere = current.surfaces.sumOf { env.state.reels(it) }
            Text(
                "$blockedHere blocked  ·  $reelsHere reels watched  ›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenTime).padding(8.dp),
            )
        }

        Spacer(8.dp)

        // Carousel of real app icons: the centre one is large, its neighbours peek in
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 110.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val p = installed[page]
            val offset = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .scale(1f - 0.3f * offset)
                        .shadow(if (offset < 0.5f) 18.dp else 0.dp, RoundedCornerShape(34.dp), clip = false)
                        .pressScale { sheetFor = p }
                        .semantics { contentDescription = "${p.label} settings" },
                ) {
                    PlatformIcon(p, env.platformPackages.getValue(p), size = 132.dp)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            installed.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(3.dp)
                        .size(if (i == pager.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Text(
            "tap the icon to choose what to block",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(24.dp)

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Warnings first: they decide whether anything works at all
            if (!status.blocking) {
                Notice("Protection is off", "Switch the accessibility service on in Settings, otherwise nothing is blocked.", emphasis = true)
            } else if (!status.batteryUnrestricted) {
                Notice("Battery is restricted", "Your phone may switch protection off in the background. Fix it in Settings.", emphasis = true)
            }
            lastGapNotice(env, nowMs)?.let { Notice("Protection was off", it) }

            // Daily goal + streak: real time against a goal you set
            if (usage.hasAccess) GoalCard(env, usage, todayMs, goalMs, today, allPackages, onOpenTime)

            // Live state of Post Mode and Focus
            val st = env.state
            if (st.focusActive(nowMs)) {
                Notice(
                    "Focus session · ${countdownText(st.focusUntil - nowMs)}",
                    if (st.focusStrict) "Strict: it can't be ended early." else "Your social apps are locked until it ends.",
                    emphasis = true, onClick = onOpenRoutines,
                )
            } else if (st.postModeUntil > nowMs) {
                Notice("Post Mode · ${countdownText(st.postModeUntil - nowMs)} left", "Short-video rules are paused so you can post.", onClick = onOpenRoutines)
            } else {
                val phase = st.sleepSchedule().phaseAt(java.time.LocalTime.now().let { it.hour * 60 + it.minute })
                if (phase != app.focuslite.engine.policy.SleepPhase.NONE) {
                    Notice("Sleep mode", when (phase) {
                        app.focuslite.engine.policy.SleepPhase.WIND_DOWN -> "Winding down: no short video."
                        app.focuslite.engine.policy.SleepPhase.OVERNIGHT -> "Your social apps are locked until morning."
                        else -> "Easing into the day: your social apps stay locked a little longer."
                    }, onClick = onOpenRoutines)
                }
            }
        }
    }

    sheetFor?.let { platform ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            PlatformSheet(platform, env) { sheetFor = null }
        }
    }
}

private fun lastGapNotice(env: AppEnv, nowMs: Long): String? {
    val gap = env.tamper.recent(1).firstOrNull() ?: return null
    if (nowMs - gap.toMs > 24 * 3_600_000L) return null // only today's news
    val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    val mins = ((gap.toMs - gap.fromMs) / 60_000).coerceAtLeast(1)
    return "From ${fmt.format(Instant.ofEpochMilli(gap.fromMs))} for $mins min. Nothing was blocked in that time."
}

@Composable
private fun GoalCard(env: AppEnv, usage: UsageData, todayMs: Long, goalMs: Long, today: LocalDate, packages: Set<String>, onOpenTime: () -> Unit) {
    val fraction by animateFloatAsState((todayMs.toFloat() / goalMs).coerceIn(0f, 1f), tween(700), label = "goal")
    val totals = (0..6).associate { back -> today.minusDays(back.toLong()) to usage.msOn(packages, today.minusDays(back.toLong())) }
    val streak = Goals.streak(totals, goalMs, today, oldest = today.minusDays(6))
    val over = todayMs > goalMs
    Card(Modifier.clickable(onClick = onOpenTime)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Today on social apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (streak > 0) "$streak-day streak" else "No streak yet",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (streak > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${formatDuration(todayMs)} of ${formatDuration(goalMs)}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(on: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error))
        Spacer(8.dp)
        Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Notice(title: String, body: String, emphasis: Boolean = false, onClick: (() -> Unit)? = null) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (emphasis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            BodyMuted(body)
        }
    }
}

/** Per-platform sheet: for each surface choose Block, Pause first, a daily Limit, or Allow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformSheet(platform: Platform, env: AppEnv, onDone: () -> Unit) {
    val settings = remember(platform) {
        mutableStateMapOf<Surface, SurfaceSetting>().apply { platform.toggles.forEach { put(it.surface, env.prefs.setting(it.surface)) } }
    }
    fun update(surface: Surface, s: SurfaceSetting) { settings[surface] = s; env.prefs.setSetting(surface, s) }

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlatformIcon(platform, env.platformPackages.getValue(platform), size = 40.dp)
            Spacer(12.dp)
            Text(platform.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done") }
        }
        platform.toggles.forEach { t ->
            val s = settings.getValue(t.surface)
            CardColumn(spacing = 10.dp) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                t.hint?.let { BodyMuted(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SurfaceMode.BLOCK to "Block",
                        SurfaceMode.PAUSE to "Pause first",
                        SurfaceMode.LIMIT to "Daily limit",
                        SurfaceMode.ALLOW to "Allow",
                    ).forEach { (mode, label) ->
                        FilterChip(selected = s.mode == mode, onClick = { update(t.surface, s.copy(mode = mode)) }, label = { Text(label, maxLines = 1, fontSize = 12.sp) })
                    }
                }
                when (s.mode) {
                    SurfaceMode.LIMIT -> Stepper(
                        "Minutes per day", "${s.limitMinutes}",
                        onMinus = { update(t.surface, s.copy(limitMinutes = (s.limitMinutes - 5).coerceAtLeast(5))) },
                        onPlus = { update(t.surface, s.copy(limitMinutes = (s.limitMinutes + 5).coerceAtMost(240))) },
                    )
                    SurfaceMode.PAUSE -> BodyMuted("A breathing pause, then you give a reason. Each unlock lasts 5 minutes and the next wait is longer.")
                    SurfaceMode.BLOCK -> BodyMuted("Stepped out of immediately.")
                    SurfaceMode.ALLOW -> BodyMuted("Left alone.")
                }
            }
        }
    }
}
