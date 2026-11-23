package app.focuslite.engine.accessibility

import android.content.Context
import android.util.Log
import app.focuslite.engine.detection.InvalidRulesException
import app.focuslite.engine.detection.RulesFile
import app.focuslite.engine.detection.RulesParser
import java.io.File

/**
 * Loads the detection rules. The copy bundled in the app is always valid; a newer override file
 * dropped in the app's files directory (later: downloaded) wins only if it parses and has a higher version.
 */
object RulesLoader {
    const val ASSET_NAME = "platform-rules.json"
    private const val TAG = "FocusLite"

    fun load(context: Context): RulesFile {
        val bundled = context.assets.open(ASSET_NAME).bufferedReader().use { RulesParser.parse(it.readText()) }
        val override = File(context.filesDir, ASSET_NAME)
        if (!override.isFile) return bundled
        return try {
            val candidate = RulesParser.parse(override.readText())
            if (candidate.version > bundled.version) {
                Log.i(TAG, "using override rules v${candidate.version} (bundled v${bundled.version})")
                candidate
            } else {
                bundled
            }
        } catch (e: InvalidRulesException) {
            Log.w(TAG, "ignoring invalid override rules: ${e.message}")
            bundled
        }
    }
}
