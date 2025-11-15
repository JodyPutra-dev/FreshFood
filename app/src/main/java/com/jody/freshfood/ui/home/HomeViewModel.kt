package com.jody.freshfood.ui.home

import androidx.lifecycle.*
import com.jody.freshfood.data.local.entity.ScanResultEntity
import com.jody.freshfood.data.repository.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ScanRepository) : ViewModel() {

    private val _scanHistory = MutableLiveData<List<ScanResultEntity>>(emptyList())
    val scanHistory: LiveData<List<ScanResultEntity>> = _scanHistory

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getAllScanResults()
            _scanHistory.postValue(list)
        }
    }

    fun deleteScanResult(scanResult: ScanResultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteScanResult(scanResult)
            refreshHistory()
        }
    }

    fun insertScanResult(scanResult: ScanResultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertScanResult(scanResult)
            refreshHistory()
        }
    }

    class Factory(private val repository: ScanRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
