package com.project.argus.domain.model

// Translates COCO obstacle labels and navigation terms to Russian via ObstacleCatalog.
object ObstacleTranslator {

    fun translate(label: String): String {
        return ObstacleCatalog.translate(label)
    }
}
