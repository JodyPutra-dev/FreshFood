package com.jody.freshfood.ml

/**
 * TFLite inference interface for fruit type identification and ripeness prediction.
 * Production implementation is RealTFLitePredictor which uses TensorFlow Lite models.
 */
interface TFLitePredictor {

    data class FruitTypeResult(val type: String, val confidence: Float)
    data class RipenessResult(val label: String, val confidence: Float, val insights: Map<String, String>)

    suspend fun predictFruitType(imagePath: String): FruitTypeResult
    suspend fun predictRipeness(imagePath: String, fruitType: String): RipenessResult
}
