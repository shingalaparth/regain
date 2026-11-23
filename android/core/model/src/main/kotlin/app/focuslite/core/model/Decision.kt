package app.focuslite.core.model

/** How a surface is treated. BLOCK stops it, PAUSE makes you wait and give a reason, LIMIT allows a daily budget. */
enum class SurfaceMode { ALLOW, BLOCK, PAUSE, LIMIT }

/** A mode plus its one number. [limitMinutes] only matters for [SurfaceMode.LIMIT]. */
data class SurfaceSetting(val mode: SurfaceMode, val limitMinutes: Int = 15)

/** Why something was blocked; shown to the user and logged. */
enum class BlockReason { SETTING, LIMIT_REACHED, FOCUS, SLEEP }

/** The policy engine's answer for one screen. */
sealed interface Decision {
    data object Allow : Decision

    /** Allowed for now; [remainingMs] is how long until the budget or unlock runs out. */
    data class AllowFor(val remainingMs: Long) : Decision

    /** Stop the user from staying on [surface]; the enforcer picks how (tab redirect, Back, Home). */
    data class Block(val surface: Surface, val reason: BlockReason = BlockReason.SETTING) : Decision

    /** Make the user wait [waitSeconds] and give a reason before the surface opens. */
    data class Gate(val surface: Surface, val waitSeconds: Int) : Decision
}
