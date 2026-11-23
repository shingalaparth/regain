package app.focuslite.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.focuslite.Exporter
import app.focuslite.SystemStatus
import app.focuslite.engine.accessibility.FocusAccessibilityService
import app.focuslite.usage.UsageStatsSource
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Settings tab: permissions, protection history, the daily export, and the screen-recording tool. */
@Composable
fun SettingsScreen(env: AppEnv, status: SystemStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)

        Label("Protection")

        StepCard(title = "Accessibility service", done = status.blocking) {
            // Prominent disclosure: shown before the user is sent to the system switch.
            BodyMuted(
                "Regain uses Android's Accessibility service to see which screen is open in Instagram, " +
                    "YouTube, Snapchat, TikTok and your browser. It only looks for short-video screens, " +
                    "then steps you out of them. It does not read messages, and nothing is stored or sent " +
                    "off your phone.",
            )
            // One filled button per screen: the first step that still needs doing. The rest are outlined.
            val openAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            if (!status.blocking) Button(onClick = openAccessibility) { Text("Open accessibility settings") }
            else OutlinedButton(onClick = openAccessibility) { Text("Open accessibility settings") }
        }

        StepCard(title = "Usage access", done = status.usageAccess) {
            BodyMuted(
                "Lets Regain read how long each app was on screen, so the Screen Time numbers are real. " +
                    "It reads only app names and on-screen time, and nothing leaves your phone.",
            )
            val openUsage = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            if (status.blocking && !status.usageAccess) Button(onClick = openUsage) { Text("Open usage access") }
            else OutlinedButton(onClick = openUsage) { Text("Open usage access") }
        }

        StepCard(title = "Battery: no restrictions", done = status.batteryUnrestricted) {
            BodyMuted(
                "Phones like the iQOO stop background services to save power, which silently turns " +
                    "protection off. Set Regain to unrestricted.",
            )
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open battery settings") }
        }

        StepCard(title = "Keep it alive (iQOO / Vivo)", done = null) {
            BodyMuted(
                "Settings → Apps → Regain → Battery: choose \"No restrictions\". Also allow Autostart, and " +
                    "lock the app in the recent-apps screen so cleaning doesn't close it.",
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Open app settings") }
        }

        Label("Display")
        CounterSwitchCard(env)

        Label("History")
        GapsCard(env)

        Label("Tools")
        ExportCard(env)
        RecordCard(status)
    }
}

/** Turns the live reel counter (the white pill on top of the video) on or off. */
@Composable
private fun CounterSwitchCard(env: AppEnv) {
    var on by remember { mutableStateOf(env.state.showCounterPill) }
    CardColumn {
        SwitchRow(
            "Reel counter on screen",
            "A small pill on top of the video that counts the reels you watch today. It also shows the time left on an unlock.",
            on,
        ) { on = it; env.state.showCounterPill = it }
    }
}

/** Times protection was really off, logged by the service itself. */
@Composable
private fun GapsCard(env: AppEnv) {
    var gaps by remember { mutableStateOf(env.tamper.recent(5).reversed()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { gaps = env.tamper.recent(5).reversed() }
    val fmt = DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault())
    StepCard(title = "Protection history", done = null) {
        if (gaps.isEmpty()) {
            BodyMuted("No gaps recorded. If the service is ever switched off or killed, the time is logged here.")
        } else {
            gaps.forEach { g ->
                val mins = ((g.toMs - g.fromMs) / 60_000).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt.format(Instant.ofEpochMilli(g.fromMs)), style = MaterialTheme.typography.bodyMedium)
                    Text("off for $mins min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Writes today's real numbers to a JSON file the brain vault can read. */
@Composable
private fun ExportCard(env: AppEnv) {
    val context = LocalContext.current
    StepCard(title = "Export today for your brain", done = null) {
        BodyMuted("Saves today's screen time, blocks and unlock reasons as a JSON file on the phone. Nothing is uploaded; copy it with adb or a file manager.")
        OutlinedButton(onClick = {
            val today = LocalDate.now()
            val source = UsageStatsSource(context)
            val all = env.platformPackages.values.flatten().toSet()
            val sessions = source.loadSessions(all, 1)
            val zone = ZoneId.systemDefault()
            val perPlatform = env.platformPackages.mapKeys { it.key.label }
                .mapValues { (_, pkgs) -> app.focuslite.engine.usage.DayStats.of(sessions, today, zone, pkgs) }
            val blocks = env.stats.forDay(today).mapKeys { it.key.name }
            val unlocks = env.unlocks.since(today.atStartOfDay(zone).toInstant().toEpochMilli()).map { it.surface.name to it.reason }
            val file = Exporter.exportDay(context, today, perPlatform, blocks, unlocks, env.state.reelsByDay(today).mapKeys { it.key.name }, env.state.goalMinutes)
            Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_LONG).show()
        }) { Text("Export today") }
    }
}

@Composable
private fun RecordCard(status: SystemStatus) {
    val context = LocalContext.current
    val dumpsDir = remember { File(context.getExternalFilesDir(null), "dumps") }
    var dumps by remember { mutableStateOf(listDumps(dumpsDir)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { dumps = listDumps(dumpsDir) }

    StepCard(title = "Record a screen", done = null) {
        BodyMuted(
            "Saves the structure of the screen open 5 seconds after you tap (view IDs only, never text or " +
                "messages). Use it to check or fix a detection rule: tap, switch to Instagram's Reels tab, wait.",
        )
        OutlinedButton(
            enabled = status.serviceRunning,
            onClick = {
                FocusAccessibilityService.instance?.dumpAfter(5_000)
                Toast.makeText(context, "Recording in 5 seconds…", Toast.LENGTH_SHORT).show()
            },
        ) { Text("Record in 5 seconds") }
        if (!status.serviceRunning) {
            Text("Switch the accessibility service on first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (dumps.isNotEmpty()) {
            BodyMuted("Recorded: ${dumps.size} (latest ${dumps.first().name})")
        }
    }
}

/** [done] null means "cannot be checked automatically". */
@Composable
private fun StepCard(title: String, done: Boolean?, content: @Composable () -> Unit) {
    CardColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (done) {
                true -> Icon(Icons.Filled.CheckCircle, "Done", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                false -> Icon(Icons.Filled.Warning, "Needs attention", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                null -> Unit
            }
            if (done != null) Spacer(12.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

private fun listDumps(dir: File): List<File> =
    dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }.orEmpty()
