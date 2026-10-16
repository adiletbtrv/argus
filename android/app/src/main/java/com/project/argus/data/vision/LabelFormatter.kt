package com.project.argus.data.vision

import java.util.Locale


class LabelFormatter {

    /**
     * Formats a raw ML Kit label into a clean display string.
     * Maps known aliases (e.g. "Home good") to canonical names.
     */
    fun format(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.equals("Home good", ignoreCase = true)) return "Furniture"
        return trimmed.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
