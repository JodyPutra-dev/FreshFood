package com.jody.freshfood.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class RealTFLitePredictor(private val context: Context) : TFLitePredictor {

    private val imageSize = 224
    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        interpreter = Interpreter(loadModel("fruit_ripeness_model_int8.tflite"))
        labels = loadLabels("labels.txt")
    }

    // Load model from assets
    private fun loadModel(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun loadLabels(fileName: String): List<String> {
        return context.assets.open(fileName).bufferedReader().readLines()
    }

    // ===============================
    // 1. Predict Fruit Type
    // ===============================
    override suspend fun predictFruitType(imagePath: String): TFLitePredictor.FruitTypeResult {
        val imageFile = File(imagePath)

        val guess = when {
            imagePath.contains("apple", true) -> "apple"
            imagePath.contains("banana", true) -> "banana"
            imagePath.contains("orange", true) -> "orange"
            else -> "unknown"
        }

        return TFLitePredictor.FruitTypeResult(
            type = guess,
            confidence = 0.80f
        )
    }

    // ===============================
    // 2. Predict Ripeness
    // ===============================
    override suspend fun predictRipeness(imagePath: String, fruitType: String): TFLitePredictor.RipenessResult {

        val bitmap = BitmapFactory.decodeFile(imagePath)
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)

        val input = bitmapToByteBuffer(resized)
        val output = Array(1) { FloatArray(labels.size) }

        interpreter.run(input, output)

        val probs = output[0]
        val maxIdx = probs.indices.maxBy { probs[it] }
        val label = labels[maxIdx]
        val confidence = probs[maxIdx]

        val insight = mapOf(
            "analysis" to "Analyzed using fruit_ripeness_model_int8.tflite",
            "peakConfidenceIndex" to maxIdx.toString()
        )

        return TFLitePredictor.RipenessResult(
            label = label,
            confidence = confidence,
            insights = insight
        )
    }

    // ===============================
    // Helper: Convert Bitmap → ByteBuffer (INT8)
    // ===============================
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(imageSize * imageSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val px = bitmap.getPixel(x, y)

                val r = (px shr 16 and 0xFF).toByte()
                val g = (px shr 8 and 0xFF).toByte()
                val b = (px and 0xFF).toByte()

                inputBuffer.put(r)
                inputBuffer.put(g)
                inputBuffer.put(b)
            }
        }

        return inputBuffer
    }
}
