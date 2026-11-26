package com.project.argus.domain.model

import java.util.Locale

enum class ObstacleGroup {
    PERSON,
    ANIMAL,
    VEHICLE,
    FURNITURE,
    INFRASTRUCTURE,
    ROUND_OBJECT,
    EVERYDAY
}

// Metadata for a real object class detectable by the on-device COCO detector.
data class ObstacleSpec(
    val cocoLabel: String,
    val russianName: String,
    val group: ObstacleGroup,
    val dangerPriority: Int,
    val isCompact: Boolean = false,
    val minConfidence: Float = 0.45f
)

// Single source of truth for supported COCO-80 object classes and ML Kit scene terms.
object ObstacleCatalog {

    val specs: List<ObstacleSpec> = listOf(
        // People
        ObstacleSpec("person", "человек", ObstacleGroup.PERSON, dangerPriority = 3, minConfidence = 0.42f),

        // Animals (COCO-80)
        ObstacleSpec("dog", "собака", ObstacleGroup.ANIMAL, dangerPriority = 3, minConfidence = 0.48f),
        ObstacleSpec("cat", "кошка", ObstacleGroup.ANIMAL, dangerPriority = 2, isCompact = true, minConfidence = 0.48f),
        ObstacleSpec("bird", "птица", ObstacleGroup.ANIMAL, dangerPriority = 2, isCompact = true, minConfidence = 0.48f),
        ObstacleSpec("horse", "лошадь", ObstacleGroup.ANIMAL, dangerPriority = 4, minConfidence = 0.48f),
        ObstacleSpec("sheep", "овца", ObstacleGroup.ANIMAL, dangerPriority = 3, minConfidence = 0.48f),
        ObstacleSpec("cow", "корова", ObstacleGroup.ANIMAL, dangerPriority = 4, minConfidence = 0.48f),
        ObstacleSpec("bear", "медведь", ObstacleGroup.ANIMAL, dangerPriority = 4, minConfidence = 0.50f),

        // Vehicles (high collision risk)
        ObstacleSpec("car", "автомобиль", ObstacleGroup.VEHICLE, dangerPriority = 4, minConfidence = 0.44f),
        ObstacleSpec("bus", "автобус", ObstacleGroup.VEHICLE, dangerPriority = 4, minConfidence = 0.44f),
        ObstacleSpec("truck", "грузовик", ObstacleGroup.VEHICLE, dangerPriority = 4, minConfidence = 0.44f),
        ObstacleSpec("motorcycle", "мотоцикл", ObstacleGroup.VEHICLE, dangerPriority = 4, minConfidence = 0.44f),
        ObstacleSpec("bicycle", "велосипед", ObstacleGroup.VEHICLE, dangerPriority = 4, minConfidence = 0.42f),
        ObstacleSpec("train", "поезд", ObstacleGroup.VEHICLE, dangerPriority = 4, minConfidence = 0.46f),
        ObstacleSpec("boat", "лодка", ObstacleGroup.VEHICLE, dangerPriority = 3, minConfidence = 0.46f),

        // Street and infrastructure
        ObstacleSpec("traffic light", "светофор", ObstacleGroup.INFRASTRUCTURE, dangerPriority = 3, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("stop sign", "дорожный знак", ObstacleGroup.INFRASTRUCTURE, dangerPriority = 3, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("fire hydrant", "пожарный гидрант", ObstacleGroup.INFRASTRUCTURE, dangerPriority = 3, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("parking meter", "паркомат", ObstacleGroup.INFRASTRUCTURE, dangerPriority = 2, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("bench", "скамейка", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.43f),

        // Furniture
        ObstacleSpec("chair", "стул", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.42f),
        ObstacleSpec("couch", "диван", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.42f),
        ObstacleSpec("dining table", "стол", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.42f),
        ObstacleSpec("bed", "кровать", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.44f),
        ObstacleSpec("toilet", "унитаз", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.45f),
        ObstacleSpec("potted plant", "растение", ObstacleGroup.FURNITURE, dangerPriority = 2, minConfidence = 0.42f),

        // Round and compact objects (lower score threshold and compact area scale)
        ObstacleSpec("sports ball", "мяч", ObstacleGroup.ROUND_OBJECT, dangerPriority = 2, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("frisbee", "летающий диск", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("cup", "чашка", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("bowl", "миска", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("wine glass", "бокал", ObstacleGroup.ROUND_OBJECT, dangerPriority = 2, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("clock", "часы", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("apple", "яблоко", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("orange", "апельсин", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("donut", "пончик", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("cake", "торт", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("pizza", "пицца", ObstacleGroup.ROUND_OBJECT, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("vase", "ваза", ObstacleGroup.ROUND_OBJECT, dangerPriority = 2, isCompact = true, minConfidence = 0.40f),

        // Everyday obstacles and items
        ObstacleSpec("bottle", "бутылка", ObstacleGroup.EVERYDAY, dangerPriority = 2, isCompact = true, minConfidence = 0.38f),
        ObstacleSpec("fork", "вилка", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("knife", "нож", ObstacleGroup.EVERYDAY, dangerPriority = 3, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("spoon", "ложка", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("cell phone", "телефон", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("laptop", "ноутбук", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("keyboard", "клавиатура", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("tv", "монитор", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.44f),
        ObstacleSpec("mouse", "компьютерная мышь", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("remote", "пульт", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.40f),
        ObstacleSpec("book", "книга", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("backpack", "рюкзак", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.42f),
        ObstacleSpec("handbag", "сумка", ObstacleGroup.EVERYDAY, dangerPriority = 2, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("suitcase", "чемодан", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.42f),
        ObstacleSpec("umbrella", "зонт", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.42f),
        ObstacleSpec("skateboard", "скейтборд", ObstacleGroup.EVERYDAY, dangerPriority = 3, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("scissors", "ножницы", ObstacleGroup.EVERYDAY, dangerPriority = 2, isCompact = true, minConfidence = 0.42f),
        ObstacleSpec("microwave", "микроволновка", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.45f),
        ObstacleSpec("oven", "духовка", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.45f),
        ObstacleSpec("sink", "раковина", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.45f),
        ObstacleSpec("refrigerator", "холодильник", ObstacleGroup.EVERYDAY, dangerPriority = 2, minConfidence = 0.45f),
        ObstacleSpec("teddy bear", "игрушка", ObstacleGroup.EVERYDAY, dangerPriority = 1, isCompact = true, minConfidence = 0.44f)
    )

    private val specsByLabel: Map<String, ObstacleSpec> = specs.associateBy { it.cocoLabel }

    val allowedCocoClasses: Set<String> = specsByLabel.keys

    // Extra Russian mappings for formatted display aliases and verified ML Kit labels.
    private val extraTranslations = mapOf(
        "table" to "стол",
        "sofa" to "диван",
        "loveseat" to "кресло",
        "armchair" to "кресло",
        "stool" to "табурет",
        "cabinetry" to "шкаф",
        "drawer" to "тумба",
        "shelf" to "полка",
        "desk" to "стол",
        "plant" to "растение",
        "ball" to "мяч",
        "wheel" to "колесо",
        "tire" to "колесо",
        "saucer" to "тарелка",
        "handrail" to "перила",
        "branch" to "ветка",
        "rock" to "камень",
        "computer mouse" to "компьютерная мышь",
        "remote control" to "пульт",
        "furniture" to "мебель"
    )

    fun findSpec(rawLabel: String): ObstacleSpec? {
        val clean = rawLabel.trim().lowercase(Locale.ROOT)
        return specsByLabel[clean]
    }

    fun isAllowedCocoClass(rawLabel: String): Boolean {
        val clean = rawLabel.trim().lowercase(Locale.ROOT)
        return clean in specsByLabel
    }

    fun isAnimalClass(rawLabel: String): Boolean {
        return findSpec(rawLabel)?.group == ObstacleGroup.ANIMAL
    }

    fun isCompactClass(rawLabel: String): Boolean {
        return findSpec(rawLabel)?.isCompact ?: false
    }

    fun translate(rawLabel: String): String {
        val clean = rawLabel.trim().lowercase(Locale.ROOT)
        return specsByLabel[clean]?.russianName ?: extraTranslations[clean] ?: clean
    }
}
