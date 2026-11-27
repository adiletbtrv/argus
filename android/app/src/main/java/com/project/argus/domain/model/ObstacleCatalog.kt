package com.project.argus.domain.model

import java.util.Locale

data class ObstacleSpec(
    val cocoLabel: String,
    val russianName: String,
    val dangerPriority: Int,
    val isCompact: Boolean = false
)

/**
 * Curated navigation-focused catalog of 23 COCO-80 object classes.
 * Excludes household clutter, tableware, food, and non-COCO classes such as "door".
 */
object ObstacleCatalog {

    val specs: List<ObstacleSpec> = listOf(
        // High risk (moving / large vehicles and pedestrians): dangerPriority = 3
        ObstacleSpec(cocoLabel = "person", russianName = "человек", dangerPriority = 3),
        ObstacleSpec(cocoLabel = "car", russianName = "машина", dangerPriority = 3),
        ObstacleSpec(cocoLabel = "bus", russianName = "автобус", dangerPriority = 3),
        ObstacleSpec(cocoLabel = "truck", russianName = "грузовик", dangerPriority = 3),
        ObstacleSpec(cocoLabel = "motorcycle", russianName = "мотоцикл", dangerPriority = 3),
        ObstacleSpec(cocoLabel = "bicycle", russianName = "велосипед", dangerPriority = 3),

        // Physical obstacles (animals, furniture, luggage, street fixtures): dangerPriority = 2
        ObstacleSpec(cocoLabel = "dog", russianName = "собака", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "cat", russianName = "кошка", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "chair", russianName = "стул", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "couch", russianName = "диван", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "bench", russianName = "скамейка", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "dining table", russianName = "стол", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "bed", russianName = "кровать", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "potted plant", russianName = "растение", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "suitcase", russianName = "чемодан", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "backpack", russianName = "рюкзак", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "fire hydrant", russianName = "гидрант", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "stop sign", russianName = "дорожный знак", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "traffic light", russianName = "светофор", dangerPriority = 2),
        ObstacleSpec(cocoLabel = "parking meter", russianName = "столбик", dangerPriority = 2),

        // Compact / low-risk personal items (never trigger urgent Priority 3 or phone vibration): dangerPriority = 1
        ObstacleSpec(cocoLabel = "handbag", russianName = "сумка", dangerPriority = 1),
        ObstacleSpec(cocoLabel = "umbrella", russianName = "зонт", dangerPriority = 1),
        ObstacleSpec(cocoLabel = "sports ball", russianName = "мяч", dangerPriority = 1, isCompact = true)
    )

    private val specsByCocoLabel: Map<String, ObstacleSpec> = specs.associateBy { it.cocoLabel }

    val allowedCocoClasses: Set<String> = specsByCocoLabel.keys

    // Aliases for formatted UI display labels (LabelFormatter) and legacy domain terms.
    private val labelAliases: Map<String, String> = mapOf(
        "table" to "dining table",
        "plant" to "potted plant",
        "ball" to "sports ball",
        "sofa" to "couch"
    )

    fun findSpec(rawCocoLabel: String): ObstacleSpec? {
        val clean = rawCocoLabel.trim().lowercase(Locale.ROOT)
        return specsByCocoLabel[clean]
    }

    fun isAllowedCocoClass(rawCocoLabel: String): Boolean {
        return findSpec(rawCocoLabel) != null
    }

    fun dangerPriority(label: String): Int {
        return resolveSpec(label)?.dangerPriority ?: 0
    }

    fun isCompact(label: String): Boolean {
        return resolveSpec(label)?.isCompact ?: false
    }

    fun translate(label: String): String {
        val clean = label.trim().lowercase(Locale.ROOT)
        return resolveSpec(clean)?.russianName ?: clean
    }

    private fun resolveSpec(label: String): ObstacleSpec? {
        val clean = label.trim().lowercase(Locale.ROOT)
        val canonical = labelAliases[clean] ?: clean
        return specsByCocoLabel[canonical]
    }
}
