package app.focuslite.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.focuslite.core.model.Surface
import app.focuslite.ui.theme.extra
import app.focuslite.engine.usage.DayStats
import app.focuslite.engine.usage.Goals
import app.focuslite.engine.usage.formatDuration
import app.focuslite.usage.UsageData
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Screen Time" tab. Every number is read from the phone: time on screen from Android's usage log,
 * blocks and unlocks from Regain's own logs. Without usage access it says so instead of showing anything.
 */
@Composable
fun TimeScreen(env: AppEnv, usage: UsageData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var blocksToday by remember { mutableStateOf(env.stats.forDay()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { blocksToday = env.stats.forDay() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Screen Time", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)

        when {
            !usage.loaded -> BodyMuted("Loading…")
            !usage.hasAccess -> CardColumn {
                Text("Usage access needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                BodyMuted(
                    "To show how long you spend in each app, Android needs you to allow Regain under " +
                        "\"Usage access\". Until then Regain shows no time at all rather than guessing.",
                )
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Text("Open usage access")
                }
            }
            else -> TimeSections(env, usage)
        }

        BlocksCard(env, blocksToday)
        ReelsCard(env)
    }
}

@Composable
private fun TimeSections(env: AppEnv, usage: UsageData) {
    val today = LocalDate.now()
    val week = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    var selected by rememberSaveable { mutableIntStateOf(6) }
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = Platform.entries.firstOrNull { it.name == filterName }
    val allPackages = remember { env.platformPackages.values.flatten().toSet() }
    val packages = filter?.let { env.platformPackages.getValue(it) } ?: allPackages
    val goalMs = env.state.goalMinutes * 60_000L

    val stats = remember(usage, filterName) { week.map { usage.day(it, packages) } }
    val sel = stats[selected]
    val installed = Platform.entries.filter { p -> p in env.platformPackages && usage.sessions.any { it.packageName in env.platformPackages.getValue(p) } }

    // Filter chips: All + the apps you really used this week
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter == null, onClick = { filterName = null }, label = { Text("All apps") })
        installed.forEach { p ->
            FilterChip(selected = filter == p, onClick = { filterName = p.name }, label = { Text(p.label) })
        }
    }

    Hero(sel, goalMs, isToday = selected == 6, streak = streak(usage, allPackages, today, goalMs), date = week[selected])
    WeekBars(week, stats, goalMs, selected) { selected = it }
    DayFacts(sel, usage)
    Hourly(sel)
    ByApp(env, usage, week[selected], filter)
    WeekReport(env, week, stats)
}

private fun streak(usage: UsageData, packages: Set<String>, today: LocalDate, goalMs: Long): Int {
    val totals = (0..6).associate { back -> today.minusDays(back.toLong()) to usage.msOn(packages, today.minusDays(back.toLong())) }
    return Goals.streak(totals, goalMs, today, oldest = today.minusDays(6))
}

// ---- hero: animated ring against the daily goal ----
@Composable
private fun Hero(sel: DayStats, goalMs: Long, isToday: Boolean, streak: Int, date: LocalDate) {
    val fraction by animateFloatAsState((sel.totalMs.toFloat() / goalMs).coerceIn(0f, 1f), tween(800), label = "ring")
    val minutes by animateIntAsState((sel.totalMs / 60_000).toInt(), tween(800), label = "count")
    val over = sel.totalMs > goalMs
    val ringColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.outlineVariant
    CardColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(if (isToday) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())))
            Text(
                if (streak > 0) "$streak-day streak" else "No streak yet",
                style = MaterialTheme.typography.labelLarge,
                color = if (streak > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(132.dp).semantics {
                    contentDescription = "${formatDuration(sel.totalMs)} on social apps against a goal of ${formatDuration(goalMs)}"
                },
            ) {
                Canvas(Modifier.size(132.dp)) {
                    val stroke = 13.dp.toPx()
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                    drawArc(ringColor, -90f, 360f * fraction, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text(formatDuration(minutes * 60_000L), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("on social apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                BodyMuted(
                    if (over) "Over your ${formatDuration(goalMs)} goal by ${formatDuration(sel.totalMs - goalMs)}"
                    else "${formatDuration(goalMs - sel.totalMs)} left of your ${formatDuration(goalMs)} goal",
                )
            }
        }
    }
}

// ---- the week: tap a bar to inspect that day ----
@Composable
private fun WeekBars(week: List<LocalDate>, stats: List<DayStats>, goalMs: Long, selected: Int, onSelect: (Int) -> Unit) {
    val scaleMax = maxOf(goalMs, stats.maxOf { it.totalMs }, 1L) * 1.15
    val goalLine = MaterialTheme.colorScheme.onSurfaceVariant
    val chartHeight = 130.dp
    CardColumn {
        Label("Last 7 days · tap a day")
        Box(Modifier.fillMaxWidth().height(chartHeight + 44.dp)) {
            Canvas(Modifier.fillMaxWidth().height(chartHeight).padding(top = 18.dp)) {
                val y = size.height * (1f - (goalMs / scaleMax).toFloat())
                drawLine(
                    goalLine.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                )
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, date ->
                    val ms = stats[i].totalMs
                    val isSel = i == selected
                    Column(
                        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp)).clickable { onSelect(i) }
                            .semantics { contentDescription = "${formatDuration(ms)} on ${dayName(date)}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(if (ms > 0) formatDuration(ms) else "", fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            Modifier.fillMaxWidth(0.7f).height((chartHeight * (ms / scaleMax).toFloat()).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.extra.chartMuted),
                        )
                        Text(
                            dayName(date).take(1), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 18.dp, height = 2.dp).background(goalLine))
            Text("  your ${formatDuration(goalMs)} goal", style = MaterialTheme.typography.bodySmall, color = goalLine)
        }
    }
}

// ---- facts about the selected day ----
@Composable
private fun DayFacts(s: DayStats, usage: UsageData) {
    val time = DateTimeFormatter.ofPattern("h:mm a").withZone(usage.zone)
    fun at(ms: Long?) = ms?.let { time.format(Instant.ofEpochMilli(it)) } ?: "—"
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Fact("Opened", "${s.totalOpens}×", Modifier.weight(1f))
        Fact("Longest stretch", if (s.longestSessionMs > 0) formatDuration(s.longestSessionMs) else "—", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Fact("First use", at(s.firstUseMs), Modifier.weight(1f))
        Fact("Last use", at(s.lastUseMs), Modifier.weight(1f))
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(label)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- when in the day ----
@Composable
private fun Hourly(s: DayStats) {
    val peakHour = s.perHour.indices.maxByOrNull { s.perHour[it] } ?: 0
    val peak = s.perHour[peakHour]
    CardColumn {
        Label("By hour")
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            s.perHour.forEachIndexed { h, ms ->
                Box(
                    Modifier.weight(1f).height((80.dp * (ms / 3_600_000f)).coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp))
                        .background(if (h == peakHour && ms > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.extra.chartMuted),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM").forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        BodyMuted(
            if (peak > 0) "Busiest hour: ${clockText(peakHour * 60)} · ${formatDuration(peak)}" else "No use this day.",
        )
    }
}

// ---- by app, with real icons ----
@Composable
private fun ByApp(env: AppEnv, usage: UsageData, day: LocalDate, filter: Platform?) {
    val rows = env.platformPackages
        .filter { filter == null || it.key == filter }
        .map { (p, pkgs) -> Triple(p, usage.day(day, pkgs).totalMs, usage.day(day, pkgs).totalOpens) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    CardColumn(spacing = 14.dp) {
        Label("By app")
        if (rows.isEmpty()) BodyMuted("No time on these apps this day.")
        val top = rows.firstOrNull()?.second ?: 1L
        rows.forEach { (p, ms, opens) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformIcon(p, env.platformPackages.getValue(p), size = 38.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${p.label} · ${opens}×", style = MaterialTheme.typography.bodyLarge)
                        Text(formatDuration(ms), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Bar(ms.toFloat() / top)
                }
            }
        }
    }
}

// ---- the week in numbers ----
@Composable
private fun WeekReport(env: AppEnv, week: List<LocalDate>, stats: List<DayStats>) {
    val from = week.first().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val total = stats.sumOf { it.totalMs }
    val busiest = stats.indices.maxByOrNull { stats[it].totalMs }?.takeIf { stats[it].totalMs > 0 }
    val blocks = week.sumOf { env.stats.totalForDay(it) }
    val unlocks = env.unlocks.since(from)
    val topReason = unlocks.groupingBy { it.reason }.eachCount().maxByOrNull { it.value }
    val gaps = env.tamper.recent(50).count { it.fromMs >= from }
    CardColumn(spacing = 10.dp) {
        Label("Your week")
        ReportLine("Total on social apps", formatDuration(total))
        ReportLine("Daily average", formatDuration(total / 7))
        ReportLine("Busiest day", busiest?.let { "${dayName(week[it])} · ${formatDuration(stats[it].totalMs)}" } ?: "—")
        ReportLine("Reels watched", "${week.sumOf { env.state.reelsTotal(it) }}")
        ReportLine("Blocks", "$blocks")
        ReportLine("Pause-screen unlocks", "${unlocks.size}")
        if (topReason != null) ReportLine("Most common reason", "${topReason.key} (${topReason.value}×)")
        ReportLine("Times protection was off", "$gaps")
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Regain's own logs ----
@Composable
private fun BlocksCard(env: AppEnv, today: Map<Surface, Int>) {
    val allowedMs = env.state.shortVideoMs()
    CardColumn(spacing = 14.dp) {
        Label("Blocked today")
        Text("${today.values.sum()} blocked", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (today.isEmpty()) BodyMuted("No blocks recorded today.")
        val top = today.values.maxOrNull() ?: 1
        today.entries.sortedByDescending { it.value }.forEach { (surface, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label(surface), style = MaterialTheme.typography.bodyLarge)
                    Text(count.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Bar(count.toFloat() / top)
            }
        }
        if (allowedMs > 0) BodyMuted("Short video you allowed yourself today: ${formatDuration(allowedMs)}")
    }
}

/** Short videos actually watched, counted one per swipe. Blocked or paused reels are never counted. */
@Composable
private fun ReelsCard(env: AppEnv) {
    var today by remember { mutableStateOf(env.state.reelsByDay()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = env.state.reelsByDay() }
    val week = (0..6).map { env.state.reelsTotal(LocalDate.now().minusDays(it.toLong())) }.reversed()
    CardColumn(spacing = 14.dp) {
        Label("Reels watched")
        Text("${today.values.sum()} today", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (today.isEmpty()) BodyMuted("None counted today. Only reels you were allowed to watch are counted.")
        val top = today.values.maxOrNull() ?: 1
        today.entries.sortedByDescending { it.value }.forEach { (surface, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label(surface), style = MaterialTheme.typography.bodyLarge)
                    Text(count.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Bar(count.toFloat() / top)
            }
        }
        BodyMuted("Last 7 days: ${week.joinToString(" · ")}")
    }
}

@Composable
private fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
    }
}

private fun dayName(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun label(surface: Surface) = when (surface) {
    Surface.REELS_FEED -> "Instagram Reels"
    Surface.REEL_FROM_DM -> "Reels from friends"
    Surface.SHORTS -> "YouTube Shorts"
    Surface.SPOTLIGHT -> "Snapchat Spotlight"
    Surface.BROWSER_SHORT_URL -> "Short videos in browser"
    Surface.WHOLE_APP -> "Locked or blocked apps"
    else -> surface.name
}
