package app.focuslite.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.focuslite.SystemStatus
import app.focuslite.engine.accessibility.PolicyPrefs
import app.focuslite.engine.accessibility.RegainState
import app.focuslite.engine.accessibility.RulesLoader
import app.focuslite.engine.accessibility.TamperLog
import app.focuslite.engine.accessibility.UnlockLog
import app.focuslite.engine.enforcement.BlockStats
import app.focuslite.usage.UsageData
import app.focuslite.usage.UsageStatsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Everything the screens read. All of it lives on the phone; nothing here is made up. */
class AppEnv(
    val state: RegainState,
    val prefs: PolicyPrefs,
    val stats: BlockStats,
    val unlocks: UnlockLog,
    val tamper: TamperLog,
    val platformPackages: Map<Platform, Set<String>>,
)

private enum class Tab(val label: String) {
    Home("Home"),
    Time("Screen Time"),
    Routines("Routines"),
    Settings("Settings"),
}

@Composable
fun FocusLiteApp() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tab by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }

    val env = remember {
        val rules = RulesLoader.load(context)
        AppEnv(
            state = RegainState(context),
            prefs = PolicyPrefs(context),
            stats = BlockStats(context),
            unlocks = UnlockLog(context),
            tamper = TamperLog(context),
            platformPackages = Platform.entries.associateWith { it.packages(rules) },
        )
    }

    // Permissions change outside the app (system settings), so re-read them whenever we come back.
    var status by remember { mutableStateOf(SystemStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = SystemStatus.read(context) }

    // One shared clock so countdowns tick, and the service/permission state stays fresh while visible.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var n = 0
            while (true) {
                nowMs = System.currentTimeMillis()
                if (n++ % 2 == 0) status = SystemStatus.read(context)
                delay(1_000)
            }
        }
    }

    // Real on-screen time per app, from Android's usage log. Refreshed while the app is visible.
    var usage by remember { mutableStateOf(UsageData()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val source = UsageStatsSource(context)
            val all = env.platformPackages.values.flatten().toSet()
            while (true) {
                usage = withContext(Dispatchers.IO) {
                    UsageData(hasAccess = source.hasAccess(), loaded = true, sessions = source.loadSessions(all, 7))
                }
                delay(30_000)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry.ordinal,
                        onClick = { tab = entry.ordinal },
                        icon = {
                            when (entry) {
                                Tab.Home -> Icon(Icons.Filled.Home, contentDescription = null)
                                Tab.Time -> RingIcon(MaterialTheme.colorScheme.onSurfaceVariant)
                                Tab.Routines -> Icon(Icons.Filled.Favorite, contentDescription = null)
                                Tab.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(entry.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.Home -> HomeScreen(env, status, usage, nowMs, modifier, onOpenTime = { tab = Tab.Time.ordinal }, onOpenRoutines = { tab = Tab.Routines.ordinal })
            Tab.Time -> TimeScreen(env, usage, modifier)
            Tab.Routines -> RoutinesScreen(env, nowMs, modifier)
            Tab.Settings -> SettingsScreen(env, status, modifier)
        }
    }
}

/** A small ring, the Screen Time tab's icon. Drawn, not an emoji or an image. */
@Composable
private fun RingIcon(color: Color) {
    Box(Modifier.size(24.dp)) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.5.dp.toPx()
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
