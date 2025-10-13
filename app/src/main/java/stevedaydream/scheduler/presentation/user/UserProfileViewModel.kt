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

data class UserOrganizationInfo(
    val organization: Organization,
    val groups: List<Group> = emptyList(),
    val userStatus: String = "在職",
    val isMember: Boolean = true
)

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修正點 (確保此資料類別存在於檔案頂部) ▼▼▼▼▼▼▼▼▼▼▼▼
data class SuccessorSelectionState(
    val org: Organization,
    val candidates: List<User> = emptyList(),
    val isLoading: Boolean = true
)

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val currentUser: User? = null,
    val organizationsInfo: List<UserOrganizationInfo> = emptyList(),
    val pendingGroupRequests: List<GroupJoinRequest> = emptyList(),
    val updatingGroupRequests: Set<String> = emptySet(),
    val requestResult: Result<Unit>? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val nameInput: String = "",
    val employeeIdInput: String = "",
    val saveResult: Result<Unit>? = null,
    val leaveOrgResult: Result<Unit>? = null,
    val successorSelectionState: SuccessorSelectionState? = null
)
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修正結束 ▲▲▲▲▲▲▲▲▲▲▲▲

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

        viewModelScope.launch {
            repository.observeGroupJoinRequestsForUser(userId).collect { requests ->
                _uiState.update { it.copy(pendingGroupRequests = requests) }
            }
        }
    }


    fun sendGroupJoinRequest(targetOrgId: String, targetGroup: Group) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(updatingGroupRequests = it.updatingGroupRequests + targetGroup.id) }

            val request = GroupJoinRequest(
                orgId = targetOrgId,
                userId = currentUser.id,
                userName = currentUser.name,
                targetGroupId = targetGroup.id,
                targetGroupName = targetGroup.groupName,
                status = "pending",
                requestedAt = Date()
            )

            try {
                val result = repository.createGroupJoinRequest(targetOrgId, request)
                _uiState.update { it.copy(requestResult = result.map { }) }
            } finally {
                _uiState.update { it.copy(updatingGroupRequests = it.updatingGroupRequests - targetGroup.id) }
            }
        }
    }

    fun cancelGroupJoinRequest(request: GroupJoinRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(updatingGroupRequests = it.updatingGroupRequests + request.targetGroupId) }

            try {
                repository.cancelGroupJoinRequest(request.orgId, request.id)
            } catch (e: Exception) {
                // Handle error
            }
            finally {
                _uiState.update { it.copy(updatingGroupRequests = it.updatingGroupRequests - request.targetGroupId) }
            }
        }
    }

    fun onLeaveOrganizationClicked(org: Organization) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            if (userId == org.ownerId) {
                _uiState.update { it.copy(successorSelectionState = SuccessorSelectionState(org = org)) }

                val allUsers = repository.observeUsers(org.id).firstOrNull() ?: emptyList()
                val otherMembers = allUsers.filter { it.id != userId }

                if (otherMembers.isEmpty()) {
                    repository.scheduleOrganizationForDeletion(org.id)
                    repository.leaveOrganization(org.id, userId)
                    _uiState.update { it.copy(successorSelectionState = null, leaveOrgResult = Result.success(Unit)) }
                } else {
                    _uiState.update {
                        it.copy(
                            successorSelectionState = it.successorSelectionState?.copy(
                                candidates = otherMembers.sortedBy { m -> m.joinedAt },
                                isLoading = false
                            )
                        )
                    }
                }
            } else {
                leaveOrganization(org.id)
            }
        }
    }

    fun transferOwnershipAndLeave(orgId: String, newOwnerId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val transferResult = repository.transferOwnership(orgId, newOwnerId)
            if (transferResult.isSuccess) {
                val leaveResult = repository.leaveOrganization(orgId, userId)
                _uiState.update { it.copy(successorSelectionState = null, leaveOrgResult = leaveResult) }
            } else {
                _uiState.update { it.copy(leaveOrgResult = Result.failure(transferResult.exceptionOrNull() ?: Exception("權限轉移失敗"))) }
            }
        }
    }

    fun cancelOwnerLeaveFlow() {
        _uiState.update { it.copy(successorSelectionState = null) }
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