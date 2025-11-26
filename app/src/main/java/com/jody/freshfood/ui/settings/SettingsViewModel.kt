package com.jody.freshfood.ui.settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jody.freshfood.data.model.SupportedFood
import com.jody.freshfood.ml.ModelManager
import com.jody.freshfood.ml.ModelUpdateManager
import com.jody.freshfood.ml.ModelMetadata
import com.jody.freshfood.ml.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _updateStatus = MutableLiveData<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: LiveData<UpdateStatus> = _updateStatus

    private val _modelMetadata = MutableLiveData<List<ModelMetadata>>(emptyList())
    val modelMetadata: LiveData<List<ModelMetadata>> = _modelMetadata

    private val _supportedFoods = MutableLiveData<List<SupportedFood>>(emptyList())
    val supportedFoods: LiveData<List<SupportedFood>> = _supportedFoods

    fun loadModelMetadata(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = ModelManager.getAllModelMetadata(context)
                _modelMetadata.postValue(list)
            } catch (ex: Exception) {
                // on error, post empty list
                _modelMetadata.postValue(emptyList())
            }
        }
    }

    fun loadSupportedFoods(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read labels.txt from assets
                val labels = context.assets.open("labels.txt").bufferedReader().readLines()
                
                // Parse labels and group by fruit name
                val foodMap = mutableMapOf<String, MutableSet<String>>()
                
                labels.forEach { label ->
                    val trimmedLabel = label.trim().lowercase()
                    if (trimmedLabel.isNotEmpty()) {
                        // Extract ripeness state and fruit name
                        val (ripeness, fruit) = parseLabel(trimmedLabel)
                        if (fruit.isNotEmpty() && ripeness.isNotEmpty()) {
                            foodMap.getOrPut(fruit) { mutableSetOf() }.add(ripeness)
                        }
                    }
                }
                
                // Create SupportedFood objects with sorted ripeness states
                val supportedFoodsList = foodMap.map { (fruit, states) ->
                    SupportedFood(
                        fruitName = fruit.replaceFirstChar { it.uppercase() },
                        ripenessStates = states.sorted()
                    )
                }.sortedBy { it.fruitName }
                
                _supportedFoods.postValue(supportedFoodsList)
            } catch (ex: Exception) {
                // On error, post empty list
                _supportedFoods.postValue(emptyList())
            }
        }
    }

    private fun parseLabel(label: String): Pair<String, String> {
        // Handle formats: "freshapples", "unripe apple"
        val ripenessStates = listOf("fresh", "rotten", "unripe")
        
        for (state in ripenessStates) {
            if (label.startsWith(state)) {
                // Extract fruit name after ripeness state
                val fruitPart = label.removePrefix(state).trim()
                
                // Normalize fruit name to plural form
                val normalizedFruit = when {
                    fruitPart.startsWith("apple") -> "apples"
                    fruitPart.startsWith("banana") -> "bananas"
                    fruitPart.startsWith("orange") -> "oranges"
                    else -> fruitPart
                }
                
                return Pair(state, normalizedFruit)
            }
        }
        
        return Pair("", "")
    }

    fun checkForUpdates(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ModelUpdateManager.checkForUpdates(context).collect { status ->
                    _updateStatus.postValue(status)
                    if (status is UpdateStatus.Success) {
                        // reload metadata after successful update
                        loadModelMetadata(context)
                    }
                }
            } catch (ex: Exception) {
                _updateStatus.postValue(UpdateStatus.Error("${ex.message}", ex))
            }
        }
    }
}
