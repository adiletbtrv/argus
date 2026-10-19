package com.project.argus.domain.model

import java.util.Locale

/**
 * Translates COCO obstacle labels and key navigation terms to Russian.
 */
object ObstacleTranslator {

    private val translations = mapOf(
        "person" to "человек",
        "chair" to "стул",
        "dining table" to "стол",
        "table" to "стол",
        "bench" to "скамейка",
        "backpack" to "рюкзак",
        "suitcase" to "чемодан",
        "potted plant" to "растение",
        "plant" to "растение",
        "car" to "автомобиль",
        "bicycle" to "велосипед",
        "dog" to "собака",
        "armrest" to "подлокотник"
    )

    fun translate(label: String): String {
        val clean = label.trim().lowercase(Locale.ROOT)
        return translations[clean] ?: clean
    }
}
