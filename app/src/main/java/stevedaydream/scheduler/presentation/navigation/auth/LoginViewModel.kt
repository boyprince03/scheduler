package stevedaydream.scheduler.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoginSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun startGoogleSignIn() {
        _uiState.update { it.copy(isLoading = true, error = null) }
    }

    fun onLoginSuccess() {
        _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
    }

    fun onLoginError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signInAnonymously().await()
                onLoginSuccess()
            } catch (e: Exception) {
                onLoginError(e.localizedMessage ?: "Anonymous sign-in failed")
            }
        }
    }
}