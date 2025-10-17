// scheduler/presentation/members/MemberListViewModel.kt
package stevedaydream.scheduler.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
data class MemberWithGroupInfo(
    val user: User,
    val groupName: String
)

data class MemberListUiState(
    val isLoading: Boolean = true,
    val membersInfo: List<MemberWithGroupInfo> = emptyList(), // 改用新的資料類別
    val currentUser: User? = null,
    val error: String? = null,
    val updateResult: Result<Unit>? = null
)
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

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
        _uiState.update { it.copy(isLoading = true) }

        // 協程一：監聽當前登入使用者以判斷權限
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
            }
        }

        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
        // 協程二：合併使用者與群組資料流
        viewModelScope.launch {
            val usersFlow = repository.observeUsers(orgId)
            val groupsFlow = repository.observeGroups(orgId)

            combine(usersFlow, groupsFlow) { users, groups ->
                val groupMap = groups.associateBy { it.id }
                users.map { user ->
                    // 找出使用者所在的群組名稱
                    val groupName = groups.find { it.memberIds.contains(user.id) }?.groupName ?: "未分配群組"
                    MemberWithGroupInfo(user, groupName)
                }
            }.collect { membersInfo ->
                _uiState.update {
                    it.copy(
                        membersInfo = membersInfo,
                        isLoading = false
                    )
                }
            }
        }
        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
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