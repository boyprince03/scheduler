// scheduler/presentation/user/UserProfileViewModel.kt

package stevedaydream.scheduler.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.util.*
import javax.inject.Inject

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼

data class UserOrganizationInfo(
    val organization: Organization,
    val groups: List<Group> = emptyList(),
    val userStatus: String = "在職",
    val isMember: Boolean = true
)

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val currentUser: User? = null,
    val organizationsInfo: List<UserOrganizationInfo> = emptyList(),
    val pendingGroupRequests: List<GroupJoinRequest> = emptyList(), // 新增：追蹤待審核的申請
    val requestResult: Result<Unit>? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val nameInput: String = "",
    val employeeIdInput: String = "",
    val saveResult: Result<Unit>? = null,
    val leaveOrgResult: Result<Unit>? = null
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        // 監聽使用者資料與其組織/群組資訊
        viewModelScope.launch {
            repository.observeUser(userId)
                .distinctUntilChanged()
                .flatMapLatest { user ->
                    _uiState.update { it.copy(currentUser = user) }
                    if (user == null || user.orgIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val validOrgIds = user.orgIds.filter { it.isNotBlank() }
                        if (validOrgIds.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            val orgFlows = validOrgIds.map { orgId ->
                                combine(
                                    repository.observeOrganization(orgId).filterNotNull(),
                                    repository.observeGroups(orgId)
                                ) { org, groups ->
                                    UserOrganizationInfo(
                                        organization = org,
                                        groups = groups,
                                        userStatus = user.employmentStatus[orgId] ?: "在職",
                                        isMember = true
                                    )
                                }
                            }
                            combine(orgFlows) { it.toList() }
                        }
                    }
                }
                .collect { organizationsInfo ->
                    _uiState.update {
                        it.copy(
                            organizationsInfo = organizationsInfo,
                            isLoading = false
                        )
                    }
                }
        }

        // 獨立監聽使用者的群組加入申請
        viewModelScope.launch {
            repository.observeGroupJoinRequestsForUser(userId).collect { requests ->
                _uiState.update { it.copy(pendingGroupRequests = requests) }
            }
        }
    }


    fun sendGroupJoinRequest(targetOrgId: String, targetGroup: Group) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            val request = GroupJoinRequest(
                id = UUID.randomUUID().toString(),
                orgId = targetOrgId,
                userId = currentUser.id,
                userName = currentUser.name,
                targetGroupId = targetGroup.id,
                targetGroupName = targetGroup.groupName,
                status = "pending",
                requestedAt = Date()
            )
            val result = repository.createGroupJoinRequest(targetOrgId, request)
            _uiState.update { it.copy(requestResult = result.map { }) }
        }
    }

    fun cancelGroupJoinRequest(request: GroupJoinRequest) {
        viewModelScope.launch {
            val result = repository.cancelGroupJoinRequest(request.orgId, request.id)
            // 可以在此處更新 UI 反應，例如顯示 Toast
        }
    }


    fun leaveOrganization(orgId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = repository.leaveOrganization(orgId, userId)
            _uiState.update { it.copy(leaveOrgResult = result) }
        }
    }

    fun clearLeaveOrgResult() {
        _uiState.update { it.copy(leaveOrgResult = null) }
    }

    // --- User Profile Edit ---
    fun enableEditMode() {
        _uiState.update {
            it.copy(
                isEditing = true,
                nameInput = it.currentUser?.name ?: "",
                employeeIdInput = it.currentUser?.employeeId ?: ""
            )
        }
    }

    fun cancelEditMode() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(nameInput = name) }
    }

    fun onEmployeeIdChange(employeeId: String) {
        _uiState.update { it.copy(employeeIdInput = employeeId) }
    }

    fun saveUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val updates = mapOf(
                "name" to _uiState.value.nameInput.trim(),
                "employeeId" to _uiState.value.employeeIdInput.trim()
            )
            val result = repository.updateUser(userId, updates)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isEditing = !result.isSuccess,
                    saveResult = result
                )
            }
        }
    }

    fun clearSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }

    fun clearRequestResult() {
        _uiState.update { it.copy(requestResult = null) }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲