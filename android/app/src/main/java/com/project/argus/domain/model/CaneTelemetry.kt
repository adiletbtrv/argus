package com.project.argus.domain.model

enum class CaneDirection {
    CENTER,
    LEFT,
    RIGHT;

    companion object {
        fun fromRaw(value: String?): CaneDirection = when (value?.trim()?.uppercase()) {
            "LEFT" -> LEFT
            "RIGHT" -> RIGHT
            else -> CENTER
        }
    }
}

data class CaneTelemetry(
    val distanceCm: Int,
    val direction: CaneDirection,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isCriticalDistance: Boolean
        get() = distanceCm in 1 until CRITICAL_DISTANCE_THRESHOLD_CM

    companion object {
        const val CRITICAL_DISTANCE_THRESHOLD_CM = 35

        private val TELEMETRY_REGEX = Regex("""DIST:(-?\d+),DIR:(\w+)""")

        /**
         * Parses telemetry line in format "DIST:<cm>,DIR:<DIR>".
         * Returns CaneTelemetry if parsed successfully, null otherwise.
         */
        fun parse(line: String): CaneTelemetry? {
            val match = TELEMETRY_REGEX.find(line.trim()) ?: return null
            val dist = match.groupValues[1].toIntOrNull() ?: return null
            val dir = CaneDirection.fromRaw(match.groupValues[2])
            return CaneTelemetry(
                distanceCm = dist,
                direction = dir
            )
        }
    }
}
