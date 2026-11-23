package app.focuslite.engine.detection

import app.focuslite.core.model.ScreenSnapshot
import app.focuslite.core.model.Surface

/** Turns a [ScreenSnapshot] into a [Surface] using the rules file. Pure and side-effect free. */
class Detector(rules: RulesFile) {
    private val byPackage: Map<String, AppRules> =
        rules.apps.flatMap { app -> app.packages.map { it to app } }.toMap()

    /** True when the rules say anything about [packageName]. The service can skip other apps early. */
    fun watches(packageName: String): Boolean = packageName in byPackage

    /** Every package the rules mention; the service subscribes to events from exactly these. */
    fun packages(): Set<String> = byPackage.keys

    /** Ids of the views whose scrolling means "next video" in [packageName]. */
    fun counterIds(packageName: String): Set<String> =
        byPackage[packageName]?.surfaces?.flatMap { it.counterIds }?.toSet().orEmpty()

    /** True when [packageName] can be locked as a whole (focus, sleep): a social app, not a browser. */
    fun lockable(packageName: String): Boolean {
        val app = byPackage[packageName] ?: return false
        return app.urlBarId == null
    }

    /** Short id of the address bar when [packageName] is a browser entry. */
    fun urlBarId(packageName: String): String? = byPackage[packageName]?.urlBarId

    /** The tab to open after blocking in [packageName], if the rules define one. */
    fun escapeTab(packageName: String): String? = byPackage[packageName]?.escapeTab

    fun detect(snapshot: ScreenSnapshot): Surface {
        val app = byPackage[snapshot.packageName] ?: return Surface.OTHER
        return app.surfaces.firstOrNull { matches(it, snapshot) }?.surface ?: Surface.OTHER
    }

    private fun matches(rule: SurfaceRule, snapshot: ScreenSnapshot): Boolean {
        val ids = snapshot.viewIds
        if (!rule.allOf.all { it in ids }) return false
        if (rule.anyOf.isNotEmpty() && rule.anyOf.none { it in ids }) return false
        if (rule.noneOf.any { it in ids }) return false
        if (rule.urlContains.isNotEmpty()) {
            val url = snapshot.urlBarText?.lowercase() ?: return false
            if (rule.urlContains.none { url.contains(it.lowercase()) }) return false
        }
        return true
    }
}
