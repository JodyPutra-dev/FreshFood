package com.jody.freshfood.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class RealTFLitePredictor(private val context: Context, private val modelPath: String? = null) : TFLitePredictor {

    private val imageSize = 224
    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseNNAPI(false)
        }
        interpreter = Interpreter(loadModel(modelPath), options)
        labels = loadLabels("labels.txt")
    }

    // Load model from file path or assets
    private fun loadModel(filePath: String?): MappedByteBuffer {
        return try {
            if (filePath != null && File(filePath).exists()) {
                Log.i("RealTFLitePredictor", "Loading model from file: $filePath")
                val file = File(filePath)
                FileInputStream(file).use { inputStream ->
                    inputStream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                }
            } else {
                Log.i("RealTFLitePredictor", "Loading model from assets: fruit_ripeness_model.tflite")
                val fileDescriptor = context.assets.openFd("fruit_ripeness_model.tflite")
                FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                    inputStream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fileDescriptor.startOffset,
                        fileDescriptor.declaredLength
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("RealTFLitePredictor", "Failed to load model from file, falling back to assets", e)
            val fileDescriptor = context.assets.openFd("fruit_ripeness_model.tflite")
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    private fun loadLabels(fileName: String): List<String> {
        return context.assets.open(fileName).bufferedReader().readLines()
    }

    // Parse label into fruit type and ripeness state
    private fun parseLabelToComponents(label: String): Pair<String, String> {
        val lowerLabel = label.lowercase().trim()
        
        val ripenessStates = listOf("fresh", "rotten", "unripe")
        val fruitNames = listOf("apple", "apples", "banana", "bananas", "orange", "oranges")
        
        var ripenessState = "unknown"
        var fruitType = "unknown"
        
        // Extract ripeness state
        for (state in ripenessStates) {
            if (lowerLabel.contains(state)) {
                ripenessState = state
                break
            }
        }
        
        // Extract fruit name
        for (fruit in fruitNames) {
            if (lowerLabel.contains(fruit)) {
                // Normalize to singular form
                fruitType = when {
                    fruit.startsWith("apple") -> "apple"
                    fruit.startsWith("banana") -> "banana"
                    fruit.startsWith("orange") -> "orange"
                    else -> fruit
                }
                break
            }
        }
        
        return Pair(fruitType, ripenessState)
    }

    // ===============================
    // 1. Predict Fruit Type
    // ===============================
    override suspend fun predictFruitType(imagePath: String): TFLitePredictor.FruitTypeResult {
        Log.d("RealTFLitePredictor", "━━━━━━━━━━ PREDICT FRUIT TYPE START ━━━━━━━━━━")
        Log.d("RealTFLitePredictor", "Input image path: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
        Log.d("RealTFLitePredictor", "Original bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
        
        // Log sample pixels from original bitmap (center pixel)
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val centerPixel = bitmap.getPixel(centerX, centerY)
        val cR = (centerPixel shr 16 and 0xFF)
        val cG = (centerPixel shr 8 and 0xFF)
        val cB = (centerPixel and 0xFF)
        Log.d("RealTFLitePredictor", "Center pixel RGB: ($cR, $cG, $cB)")
        
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        Log.d("RealTFLitePredictor", "Resized to: ${resized.width}x${resized.height}")

        val input = bitmapToByteBuffer(resized)
        Log.d("RealTFLitePredictor", "Input tensor created: ${input.capacity()} bytes")
        
        val output = Array(1) { FloatArray(labels.size) }

        Log.d("RealTFLitePredictor", "Running TFLite interpreter...")
        interpreter.run(input, output)
        Log.d("RealTFLitePredictor", "TFLite inference complete")

        val probs = output[0]
        val maxIdx = probs.indices.maxBy { probs[it] }
        val label = labels[maxIdx]
        val confidence = probs[maxIdx]
        
        Log.d("RealTFLitePredictor", "Raw output probabilities (all 9 classes):")
        probs.forEachIndexed { idx, prob ->
            Log.d("RealTFLitePredictor", "  [$idx] ${labels[idx]}: $prob")
        }
        Log.d("RealTFLitePredictor", "Predicted: index=$maxIdx, label='$label', confidence=$confidence")
        Log.d("RealTFLitePredictor", "━━━━━━━━━━ PREDICT FRUIT TYPE END ━━━━━━━━━━")

        val (fruitType, _) = parseLabelToComponents(label)

        return TFLitePredictor.FruitTypeResult(
            type = fruitType,
            confidence = confidence
        )
    }

    // ===============================
    // 2. Predict Ripeness
    // ===============================
    override suspend fun predictRipeness(imagePath: String, fruitType: String): TFLitePredictor.RipenessResult {
        Log.d("RealTFLitePredictor", "━━━━━━━━━━ PREDICT RIPENESS START ━━━━━━━━━━")
        Log.d("RealTFLitePredictor", "Input image path: $imagePath")
        Log.d("RealTFLitePredictor", "Fruit type hint: $fruitType")

        val bitmap = BitmapFactory.decodeFile(imagePath)
        Log.d("RealTFLitePredictor", "Original bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
        
        // Log sample pixels from original bitmap (top-left, center, bottom-right)
        val tlPixel = bitmap.getPixel(0, 0)
        val tlR = (tlPixel shr 16 and 0xFF)
        val tlG = (tlPixel shr 8 and 0xFF)
        val tlB = (tlPixel and 0xFF)
        
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val cPixel = bitmap.getPixel(centerX, centerY)
        val cR = (cPixel shr 16 and 0xFF)
        val cG = (cPixel shr 8 and 0xFF)
        val cB = (cPixel and 0xFF)
        
        val brPixel = bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        val brR = (brPixel shr 16 and 0xFF)
        val brG = (brPixel shr 8 and 0xFF)
        val brB = (brPixel and 0xFF)
        
        Log.d("RealTFLitePredictor", "Sample pixels RGB:")
        Log.d("RealTFLitePredictor", "  Top-left: ($tlR, $tlG, $tlB)")
        Log.d("RealTFLitePredictor", "  Center: ($cR, $cG, $cB)")
        Log.d("RealTFLitePredictor", "  Bottom-right: ($brR, $brG, $brB)")
        
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        Log.d("RealTFLitePredictor", "Resized to: ${resized.width}x${resized.height}")

        val input = bitmapToByteBuffer(resized)
        Log.d("RealTFLitePredictor", "Input tensor created: ${input.capacity()} bytes (${imageSize * imageSize * 3} floats)")
        
        val output = Array(1) { FloatArray(labels.size) }

        Log.d("RealTFLitePredictor", "Running TFLite interpreter...")
        interpreter.run(input, output)
        Log.d("RealTFLitePredictor", "TFLite inference complete")

        val probs = output[0]
        val maxIdx = probs.indices.maxBy { probs[it] }
        val label = labels[maxIdx]
        val confidence = probs[maxIdx]
        
        Log.d("RealTFLitePredictor", "Raw output probabilities (all 9 classes):")
        probs.forEachIndexed { idx, prob ->
            Log.d("RealTFLitePredictor", "  [$idx] ${labels[idx]}: $prob")
        }
        Log.d("RealTFLitePredictor", "Predicted: index=$maxIdx, label='$label', confidence=$confidence")
        Log.d("RealTFLitePredictor", "━━━━━━━━━━ PREDICT RIPENESS END ━━━━━━━━━━")

        val (parsedFruitType, parsedRipenessState) = parseLabelToComponents(label)

        val insight = mapOf(
            "analysis" to "Analyzed using TFLite model",
            "peakConfidenceIndex" to maxIdx.toString(),
            "fruitType" to parsedFruitType,
            "ripenessState" to parsedRipenessState
        )

        return TFLitePredictor.RipenessResult(
            label = label,
            confidence = confidence,
            insights = insight
        )
    }

    // ===============================
    // Helper: Convert Bitmap → ByteBuffer (FLOAT32 with raw pixel values 0-255)
    // NOTE: Model has Rescaling(1./255) layer built-in, so we feed raw values!
    // ===============================
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3) // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())

        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        var sumVal = 0.0
        var count = 0
        
        // Track first 10 float values for logging
        val firstValues = mutableListOf<Float>()

        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val px = bitmap.getPixel(x, y)

                // Extract raw RGB values [0-255] - model will rescale internally
                val r = (px shr 16 and 0xFF).toFloat()
                val g = (px shr 8 and 0xFF).toFloat()
                val b = (px and 0xFF).toFloat()

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
                
                // Track statistics
                listOf(r, g, b).forEach { value ->
                    if (count < 10) firstValues.add(value)
                    minVal = minOf(minVal, value)
                    maxVal = maxOf(maxVal, value)
                    sumVal += value
                    count++
                }
            }
        }
        
        val meanVal = sumVal / count
        Log.d("RealTFLitePredictor", "Tensor statistics (RAW 0-255): min=$minVal, max=$maxVal, mean=$meanVal")
        Log.d("RealTFLitePredictor", "First 10 tensor values: ${firstValues.joinToString()}")

        return inputBuffer
    }
}
