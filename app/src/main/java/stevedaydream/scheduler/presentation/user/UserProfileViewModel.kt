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

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val currentUser: User? = null,
    val organization: Organization? = null,
    val currentGroup: Group? = null,
    val allGroups: List<Group> = emptyList(),
    val requestResult: Result<Unit>? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val nameInput: String = "",
    val employeeIdInput: String = "",
    val saveResult: Result<Unit>? = null
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
        val userId = auth.currentUser?.uid
        if (userId == null) {
            println("❌ [UserProfile] 沒有登入用戶")
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        println("🔍 [UserProfile] 開始載入用戶資料: $userId")

        viewModelScope.launch {
            try {
                repository.observeUser(userId).collectLatest { user ->
                    println("📥 [UserProfile] 收到用戶資料: name=${user?.name}, email=${user?.email}, orgId=${user?.orgId}")

                    // ✅ 立即更新用戶資料和輸入框
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            nameInput = user?.name ?: "",
                            employeeIdInput = user?.employeeId ?: ""
                        )
                    }

                    if (user != null && user.orgId.isNotEmpty()) {
                        println("🏢 [UserProfile] 用戶有組織，開始載入組織資料")
                        loadOrganizationAndGroups(user)
                    } else {
                        println("⚠️ [UserProfile] 用戶沒有組織，結束載入")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                println("❌ [UserProfile] 載入失敗: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadOrganizationAndGroups(user: User) {
        // 載入組織
        viewModelScope.launch {
            try {
                repository.observeOrganization(user.orgId).collect { org ->
                    println("🏢 [UserProfile] 收到組織資料: ${org?.orgName}")
                    _uiState.update { it.copy(organization = org) }
                }
            } catch (e: Exception) {
                println("❌ [UserProfile] 載入組織失敗: ${e.message}")
            }
        }

        // 載入組別
        viewModelScope.launch {
            try {
                repository.observeGroups(user.orgId).collect { groups ->
                    println("👥 [UserProfile] 收到 ${groups.size} 個組別")
                    val current = groups.find { it.memberIds.contains(user.id) }
                    println("✅ [UserProfile] 當前組別: ${current?.groupName}")

                    _uiState.update {
                        it.copy(
                            allGroups = groups,
                            currentGroup = current,
                            isLoading = false // ✅ 確保設定為 false
                        )
                    }
                }
            } catch (e: Exception) {
                println("❌ [UserProfile] 載入組別失敗: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun enableEditMode() {
        println("✏️ [UserProfile] 進入編輯模式")
        _uiState.update {
            it.copy(
                isEditing = true,
                nameInput = it.currentUser?.name ?: "",
                employeeIdInput = it.currentUser?.employeeId ?: ""
            )
        }
    }

    fun cancelEditMode() {
        println("❌ [UserProfile] 取消編輯")
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
        println("💾 [UserProfile] 開始儲存: name=${_uiState.value.nameInput}, employeeId=${_uiState.value.employeeIdInput}")

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val updates = mapOf(
                "name" to _uiState.value.nameInput.trim(),
                "employeeId" to _uiState.value.employeeIdInput.trim()
            )

            val result = repository.updateUser(userId, updates)

            if (result.isSuccess) {
                println("✅ [UserProfile] 儲存成功")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditing = false,
                        currentUser = it.currentUser?.copy(
                            name = _uiState.value.nameInput.trim(),
                            employeeId = _uiState.value.employeeIdInput.trim()
                        ),
                        saveResult = result
                    )
                }
            } else {
                println("❌ [UserProfile] 儲存失敗: ${result.exceptionOrNull()?.message}")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditing = true,
                        saveResult = result
                    )
                }
            }
        }
    }

    fun clearSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }

    fun sendGroupJoinRequest(targetGroup: Group) {
        val currentUser = _uiState.value.currentUser ?: return
        println("📨 [UserProfile] 發送組別加入申請: ${targetGroup.groupName}")

        viewModelScope.launch {
            val request = GroupJoinRequest(
                id = UUID.randomUUID().toString(),
                orgId = currentUser.orgId,
                userId = currentUser.id,
                userName = currentUser.name,
                targetGroupId = targetGroup.id,
                targetGroupName = targetGroup.groupName,
                status = "pending",
                requestedAt = Date()
            )

            val result = repository.createGroupJoinRequest(currentUser.orgId, request)
            _uiState.update { it.copy(requestResult = result.map { }) }
        }
    }

    fun clearRequestResult() {
        _uiState.update { it.copy(requestResult = null) }
    }
}