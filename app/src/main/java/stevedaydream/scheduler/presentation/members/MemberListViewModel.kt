// scheduler/presentation/members/MemberListViewModel.kt
package stevedaydream.scheduler.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

data class MemberListUiState(
    val isLoading: Boolean = true,
    val members: List<User> = emptyList(),
    val currentUser: User? = null,
    val error: String? = null,
    val updateResult: Result<Unit>? = null
)

@HiltViewModel
class MemberListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemberListUiState())
    val uiState: StateFlow<MemberListUiState> = _uiState.asStateFlow()

    private var currentOrgId: String? = null

    fun loadData(orgId: String) {
        if (orgId == currentOrgId) return
        currentOrgId = orgId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 獲取當前登入使用者以判斷權限
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
            }

            // 獲取組織成員列表
            repository.observeUsers(orgId).collect { users ->
                _uiState.update { it.copy(members = users, isLoading = false) }
            }
        }
    }

    fun updateUserStatus(orgId: String, userId: String, newStatus: String) {
        viewModelScope.launch {
            val result = repository.updateEmploymentStatus(orgId, userId, newStatus)
            _uiState.update { it.copy(updateResult = result) }
        }
    }

    fun clearUpdateResult() {
        _uiState.update { it.copy(updateResult = null) }
    }
}