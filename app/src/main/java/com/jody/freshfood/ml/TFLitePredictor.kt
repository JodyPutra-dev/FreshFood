package com.jody.freshfood.ml

import kotlin.random.Random

interface TFLitePredictor {

    data class FruitTypeResult(val type: String, val confidence: Float)
    data class RipenessResult(val label: String, val confidence: Float, val insights: Map<String, String>)

    suspend fun predictFruitType(imagePath: String): FruitTypeResult
    suspend fun predictRipeness(imagePath: String, fruitType: String): RipenessResult
}

/**
 * Dummy implementation that returns heuristic/randomized results to allow end-to-end flow
 * without actual TFLite inference. Replace with a RealTFLitePredictor when models are ready.
 */
class DummyTFLitePredictor : TFLitePredictor {

    private val fruitTypes = listOf("apple", "avocado", "bread")

    override suspend fun predictFruitType(imagePath: String): TFLitePredictor.FruitTypeResult {
        // Simple heuristic: pick a random fruit with reasonable confidence
        val type = fruitTypes[Random.nextInt(fruitTypes.size)]
        val confidence = Random.nextDouble(0.70, 0.95).toFloat()
        return TFLitePredictor.FruitTypeResult(type, confidence)
    }

    override suspend fun predictRipeness(imagePath: String, fruitType: String): TFLitePredictor.RipenessResult {
        // Return ripeness labels depending on fruit type with randomized confidences
        return when (fruitType.lowercase()) {
            "apple" -> {
                val label = if (Random.nextDouble() > 0.9) "spoiled" else "fresh"
                val confidence = Random.nextDouble(0.75, 0.95).toFloat()
                val insights = mapOf("reason" to "skinColorSimulated", "note" to "Dummy heuristic")
                TFLitePredictor.RipenessResult(label, confidence, insights)
            }
            "avocado" -> {
                val options = listOf("fresh", "ripe", "overripe", "spoiled")
                val label = options[Random.nextInt(options.size)]
                val confidence = Random.nextDouble(0.65, 0.95).toFloat()
                val insights = mapOf("hsvScore" to "${Random.nextDouble(0.2,0.8)}")
                TFLitePredictor.RipenessResult(label, confidence, insights)
            }
            "bread" -> {
                val label = if (Random.nextDouble() > 0.85) "spoiled" else "fresh"
                val confidence = Random.nextDouble(0.7, 0.95).toFloat()
                val insights = mapOf("moldRatio" to "${Random.nextDouble(0.0,0.3)}")
                TFLitePredictor.RipenessResult(label, confidence, insights)
            }
            else -> {
                val label = "fresh"
                val confidence = 0.8f
                TFLitePredictor.RipenessResult(label, confidence, emptyMap())
            }
        }
    }
}
