package com.jody.freshfood.ui.scan

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jody.freshfood.data.model.ScanResult
import com.jody.freshfood.ml.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    fun processScanImage(imagePath: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _predictionResult.postValue(PredictionState.Processing)

                val predictor = ModelManager.getPredictor(context)

                val ripeness = predictor.predictRipeness(imagePath, "")

                // Extract parsed components from insights
                val ripenessState = ripeness.insights["ripenessState"] ?: ripeness.label

                Log.d("ScanViewModel", "Predicted: $ripenessState (confidence: ${ripeness.confidence})")

                val scan = ScanResult(
                    freshnessLabel = ripenessState,
                    confidence = ripeness.confidence,
                    imagePath = imagePath,
                    insights = ripeness.insights["analysis"] ?: "Analyzed using TFLite model",
                    advice = "Handle with care. Store in a cool, dry place for optimal freshness.",
                    daysLeft = 0
                )

                _predictionResult.postValue(PredictionState.Success(scan))
            } catch (ex: Exception) {
                Log.e("ScanViewModel", "Model inference failed: ${ex.message}", ex)
                _predictionResult.postValue(PredictionState.Error("Model inference failed: ${ex.message}"))
            }
        }
    }
}
