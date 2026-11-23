package app.focuslite

import android.content.Context
import app.focuslite.engine.usage.DayStats
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/**
 * Writes one day's real numbers to a JSON file in the app's folder, for Parth's brain vault.
 * Local file only: nothing is uploaded. Pull it with `adb pull`.
 */
object Exporter {
    fun exportDay(
        context: Context,
        date: LocalDate,
        perPlatformStats: Map<String, DayStats>,
        blocks: Map<String, Int>,
        unlocks: List<Pair<String, String>>,
        reelsWatched: Map<String, Int>,
        goalMinutes: Int,
    ): File {
        val platforms = JSONObject()
        perPlatformStats.forEach { (name, s) ->
            platforms.put(
                name,
                JSONObject()
                    .put("screenMinutes", s.totalMs / 60_000)
                    .put("opens", s.totalOpens)
                    .put("longestSessionMinutes", s.longestSessionMs / 60_000),
            )
        }
        val json = JSONObject()
            .put("date", date.toString())
            .put("zone", ZoneId.systemDefault().id)
            .put("goalMinutes", goalMinutes)
            .put("totalScreenMinutes", perPlatformStats.values.sumOf { it.totalMs } / 60_000)
            .put("platforms", platforms)
            .put("blocks", JSONObject(blocks.mapValues { it.value }))
            .put("reelsWatched", JSONObject(reelsWatched.mapValues { it.value }))
            .put("unlocks", org.json.JSONArray(unlocks.map { JSONObject().put("surface", it.first).put("reason", it.second) }))
        val dir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
        return File(dir, "regain-$date.json").apply { writeText(json.toString(2)) }
    }
}
