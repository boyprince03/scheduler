package stevedaydream.scheduler.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.util.Date
import javax.inject.Inject

data class BasicInfoUiState(
    val name: String = "",
    val employeeId: String = "",
    val isLoading: Boolean = false,
    val isSaveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BasicInfoViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasicInfoUiState())
    val uiState = _uiState.asStateFlow()

    init {
        auth.currentUser?.displayName?.let {
            _uiState.update { state -> state.copy(name = it) }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onEmployeeIdChange(employeeId: String) {
        _uiState.update { it.copy(employeeId = employeeId) }
    }

    fun saveUserInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _uiState.update { it.copy(isLoading = false, error = "使用者未登入") }
                return@launch
            }

            // ✅ 修正: 確保包含所有必要欄位
            val result = repository.updateUser(
                userId = currentUser.uid,
                updates = mapOf(
                    "name" to _uiState.value.name.trim(),
                    "employeeId" to _uiState.value.employeeId.trim(),
                    // ✅ 新增: 確保 email 也被儲存
                    "email" to (currentUser.email ?: ""),
                    // ✅ 新增: 設定預設角色
                    "role" to "member",
                    // ✅ 新增: 記錄加入時間
                    "joinedAt" to Date()
                )
            )

            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaveSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "儲存失敗") }
            }
        }
    }

}