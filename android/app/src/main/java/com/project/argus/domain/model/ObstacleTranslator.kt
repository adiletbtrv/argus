package com.project.argus.domain.model

/**
 * Translates navigation obstacle labels and aliases to Russian via [ObstacleCatalog].
 */
object ObstacleTranslator {

    fun translate(label: String): String {
        return ObstacleCatalog.translate(label)
    }
}
