package com.jarvis.assistant.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.data.model.PaymentSubmission
import com.jarvis.assistant.data.repository.MembershipRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MembershipRepository(application)

    private val _pendingSubmissions = MutableStateFlow<List<PaymentSubmission>>(emptyList())
    val pendingSubmissions: StateFlow<List<PaymentSubmission>> = _pendingSubmissions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        observeSubmissions()
    }

    private fun observeSubmissions() {
        viewModelScope.launch {
            repository.observePendingSubmissions().collect { _pendingSubmissions.value = it }
        }
    }

    fun approve(submission: PaymentSubmission) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.approveSubmission(submission)
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reject(submission: PaymentSubmission, reason: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.rejectSubmission(submission, reason)
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
