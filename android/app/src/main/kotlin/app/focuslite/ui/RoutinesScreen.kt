package app.focuslite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.focuslite.engine.accessibility.RegainState
import app.focuslite.engine.policy.SleepPhase
import app.focuslite.engine.policy.SleepSchedule
import app.focuslite.engine.usage.formatDuration
import java.time.LocalTime

/** Routines tab: Post Mode, Focus session, Sleep routine and the daily goal. All settings live on the phone. */
@Composable
fun RoutinesScreen(env: AppEnv, nowMs: Long, modifier: Modifier = Modifier) {
    val st = env.state
    // Bump to re-read prefs after a button press (prefs are not observable).
    var version by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") version

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Routines", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)

        val focusOn = st.focusActive(nowMs)
        PostModeCard(st, nowMs, locked = focusOn) { version++ }
        FocusCard(st, nowMs) { version++ }
        SleepCard(st) { version++ }
        GoalCard(st) { version++ }
    }
}

@Composable
private fun PostModeCard(st: RegainState, nowMs: Long, locked: Boolean, changed: () -> Unit) {
    val active = st.postModeUntil > nowMs
    CardColumn {
        Text("Post Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        BodyMuted("Pauses your short-video rules for a few minutes so you can post a story or reel, then everything locks again.")
        if (active) {
            Text(countdownText(st.postModeUntil - nowMs), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { st.endPostMode(); changed() }) { Text("End now") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15).forEach { m ->
                    OutlinedButton(enabled = !locked, onClick = { st.startPostMode(m); changed() }) { Text("$m min") }
                }
            }
            if (locked) BodyMuted("Not available during a focus session.")
        }
    }
}

@Composable
private fun FocusCard(st: RegainState, nowMs: Long, changed: () -> Unit) {
    val active = st.focusActive(nowMs)
    var minutes by remember { mutableIntStateOf(25) }
    var strict by remember { mutableStateOf(false) }
    CardColumn {
        Text("Focus session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        BodyMuted("Locks Instagram, YouTube, Snapchat and TikTok completely until the timer ends. Messages in other apps are untouched.")
        if (active) {
            Text(countdownText(st.focusUntil - nowMs), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            if (st.focusStrict) {
                BodyMuted("Strict session: it can't be ended early.")
            } else {
                OutlinedButton(onClick = { st.stopFocus(); changed() }) { Text("End session") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                }
            }
            SwitchRow("Strict", "No ending early", strict) { strict = it }
            Button(onClick = { st.startFocus(minutes, strict); changed() }) { Text("Start $minutes min") }
        }
    }
}

@Composable
private fun SleepCard(st: RegainState, changed: () -> Unit) {
    val s = st.sleepSchedule()
    fun save(n: SleepSchedule) { st.setSleepSchedule(n); changed() }
    val phase = s.phaseAt(LocalTime.now().let { it.hour * 60 + it.minute })
    CardColumn {
        SwitchRow(
            "Sleep mode",
            when {
                !s.enabled -> "Off"
                phase == SleepPhase.NONE -> "Next lock tonight at ${clockText(s.windDownStartMin)}"
                else -> "Now: ${phase.name.lowercase().replace('_', ' ')}"
            },
            s.enabled,
        ) { save(s.copy(enabled = it)) }
        BodyMuted("Wind-down: no short video. Bedtime to morning: your social apps are locked completely.")
        Stepper("Wind-down starts", clockText(s.windDownStartMin), { save(s.copy(windDownStartMin = s.windDownStartMin - 15)) }, { save(s.copy(windDownStartMin = s.windDownStartMin + 15)) })
        Stepper("Bedtime", clockText(s.bedtimeMin), { save(s.copy(bedtimeMin = s.bedtimeMin - 15)) }, { save(s.copy(bedtimeMin = s.bedtimeMin + 15)) })
        Stepper("Wake up", clockText(s.wakeMin), { save(s.copy(wakeMin = s.wakeMin - 15)) }, { save(s.copy(wakeMin = s.wakeMin + 15)) })
        Stepper(
            "Locked after waking", "${s.morningLockMin} min",
            { save(s.copy(morningLockMin = (s.morningLockMin - 15).coerceAtLeast(0))) },
            { save(s.copy(morningLockMin = (s.morningLockMin + 15).coerceAtMost(180))) },
        )
    }
}

@Composable
private fun GoalCard(st: RegainState, changed: () -> Unit) {
    CardColumn {
        Text("Daily goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        BodyMuted("The most time you want to spend on social apps in a day. It drives the Home progress bar and your streak.")
        Stepper(
            "Goal", formatDuration(st.goalMinutes * 60_000L),
            { st.goalMinutes -= 15; changed() },
            { st.goalMinutes += 15; changed() },
        )
    }
}
