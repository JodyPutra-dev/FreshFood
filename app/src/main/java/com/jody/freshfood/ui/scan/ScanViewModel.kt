package com.jody.freshfood.ui.scan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jody.freshfood.data.model.ScanResult
import com.jody.freshfood.ml.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class PredictionState {
    object Idle : PredictionState()
    object Processing : PredictionState()
    data class Success(val scanResult: ScanResult) : PredictionState()
    data class Error(val message: String) : PredictionState()
}

class ScanViewModel : ViewModel() {

    private val _predictionResult = MutableLiveData<PredictionState>(PredictionState.Idle)
    val predictionResult: LiveData<PredictionState> = _predictionResult

    fun reset() {
        _predictionResult.postValue(PredictionState.Idle)
    }

    fun processScanImage(imagePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _predictionResult.postValue(PredictionState.Processing)

                val predictor = ModelManager.getPredictor()

                val fruit = predictor.predictFruitType(imagePath)
                val ripeness = predictor.predictRipeness(imagePath, fruit.type)

                val avgConfidence = ((fruit.confidence + ripeness.confidence) / 2.0f)

                // Dummy advice/daysLeft extraction
                val daysLeft = Random.nextInt(3, 8)
                val advice = "Store in a cool, dry place"

                val insights = buildString {
                    append("HSV Hue: ${Random.nextInt(0, 360)}°\n")
                    append("Saturation: ${Random.nextInt(40, 100)}%\n")
                    append("Spot Ratio: ${Random.nextInt(5, 30)}%\n")
                    append("Edge Density: ${Random.nextInt(10, 50)}%\n")
                }

                val scan = ScanResult(
                    fruitType = fruit.type,
                    freshnessLabel = ripeness.label,
                    confidence = avgConfidence,
                    imagePath = imagePath,
                    insights = insights,
                    advice = advice,
                    daysLeft = daysLeft
                )

                _predictionResult.postValue(PredictionState.Success(scan))
            } catch (ex: Exception) {
                _predictionResult.postValue(PredictionState.Error(ex.message ?: "${ex.javaClass.simpleName}"))
            }
        }
    }
}
