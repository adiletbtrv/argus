package com.project.argus.data.vision

import java.util.Locale

class LabelFormatter {

    private val aliasMap = mapOf(
        "tv" to "TV",
        "mouse" to "computer mouse",
        "remote" to "remote control",
        "sports ball" to "ball",
        "hair drier" to "hair dryer"
    )

    /**
     * Formats a raw detection label into a clean display string.
     * Applies alias mappings and capitalizes the first character for UI presentation.
     */
    fun format(raw: String): String {
        val trimmed = raw.trim()
        val mapped = aliasMap[trimmed.lowercase(Locale.ROOT)] ?: trimmed
        return if (mapped.equals("tv", ignoreCase = true)) {
            "TV"
        } else {
            mapped.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
    }
}
