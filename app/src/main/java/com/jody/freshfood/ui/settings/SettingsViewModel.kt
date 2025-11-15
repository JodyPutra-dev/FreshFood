package com.jody.freshfood.ui.settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
