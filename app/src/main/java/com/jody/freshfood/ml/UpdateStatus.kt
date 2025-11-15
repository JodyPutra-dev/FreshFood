package com.jody.freshfood.ml

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Downloading(val modelName: String, val progress: Int) : UpdateStatus()
    data class Success(val updatedModels: List<String>) : UpdateStatus()
    data class Error(val message: String, val cause: Throwable? = null) : UpdateStatus()

    fun isTerminal(): Boolean = this is Success || this is Error
}
