package app.focuslite.engine.detection

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class InvalidRulesException(message: String, cause: Throwable? = null) : Exception(message, cause)

object RulesParser {
    private val json = Json {
        ignoreUnknownKeys = true // newer rules files may add fields
        isLenient = false
    }

    /** Parses and validates a rules file. Throws [InvalidRulesException] with a readable reason. */
    fun parse(text: String): RulesFile {
        val rules = try {
            json.decodeFromString<RulesFile>(text)
        } catch (e: SerializationException) {
            throw InvalidRulesException("Rules file is not valid: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidRulesException("Rules file is not valid: ${e.message}", e)
        }
        validate(rules)
        return rules
    }

    private fun validate(rules: RulesFile) {
        if (rules.version <= 0) throw InvalidRulesException("version must be positive, was ${rules.version}")
        val seen = mutableSetOf<String>()
        for (app in rules.apps) {
            if (app.packages.isEmpty()) throw InvalidRulesException("an app entry has no packages")
            for (pkg in app.packages) {
                if (!seen.add(pkg)) throw InvalidRulesException("package $pkg appears in more than one entry")
            }
            if (app.surfaces.isEmpty()) {
                throw InvalidRulesException("${app.packages.first()} has no surface rules")
            }
        }
    }
}
