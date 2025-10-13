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

    // 修改開始
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
                // 持續監聽 User 物件的變化
                repository.observeUser(userId)
                    // 使用 distinctUntilChanged 避免不必要的重複觸發
                    // 只有當 user 物件的內容真的改變時，下游才會收到通知
                    .distinctUntilChanged()
                    .collect { user ->
                        _uiState.update {
                            it.copy(
                                currentUser = user,
                                // 如果不是在編輯模式，就同步更新輸入框
                                nameInput = if (it.isEditing) it.nameInput else user?.name ?: "",
                                employeeIdInput = if (it.isEditing) it.employeeIdInput else user?.employeeId ?: ""
                            )
                        }

                        // 當 user 物件存在且 currentOrgId 不為空時，載入組織與群組資訊
                        // distinctUntilChanged 會確保即使 user 物件的其他欄位變動，
                        // 只要 currentOrgId 沒變，這段邏輯也不會一直重複執行。
                        if (user != null && user.currentOrgId.isNotEmpty()) {
                            loadOrganizationAndGroups(user)
                        } else {
                            // 如果使用者沒有組織，確保清除舊資料並停止載入動畫
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    organization = null,
                                    currentGroup = null,
                                    allGroups = emptyList()
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    // 修改結束

    private fun loadOrganizationAndGroups(user: User) {
        // 使用 currentOrgId 來載入組織
        viewModelScope.launch {
            try {
                repository.observeOrganization(user.currentOrgId).collect { org ->
                    _uiState.update { it.copy(organization = org) }
                }
            } catch (e: Exception) {
                println("❌ [UserProfile] 載入組織失敗: ${e.message}")
            }
        }

        // 使用 currentOrgId 來載入組別
        viewModelScope.launch {
            try {
                repository.observeGroups(user.currentOrgId).collect { groups ->
                    val current = groups.find { it.memberIds.contains(user.id) }
                    _uiState.update {
                        it.copy(
                            allGroups = groups,
                            currentGroup = current,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
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
                        // 直接更新 currentUser，避免等待 Firestore 回饋的延遲
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
        // ✅ 確保 currentOrgId 存在
        if (currentUser.currentOrgId.isEmpty()) return

        println("📨 [UserProfile] 發送組別加入申請: ${targetGroup.groupName}")

        viewModelScope.launch {
            val request = GroupJoinRequest(
                id = UUID.randomUUID().toString(),
                orgId = currentUser.currentOrgId, // ✅ 使用 currentOrgId
                userId = currentUser.id,
                userName = currentUser.name,
                targetGroupId = targetGroup.id,
                targetGroupName = targetGroup.groupName,
                status = "pending",
                requestedAt = Date()
            )

            val result = repository.createGroupJoinRequest(currentUser.currentOrgId, request) // ✅ 使用 currentOrgId
            _uiState.update { it.copy(requestResult = result.map { }) }
        }
    }

    fun clearRequestResult() {
        _uiState.update { it.copy(requestResult = null) }
    }
}