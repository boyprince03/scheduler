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

// UI 狀態，用來描述頁面需要的所有資料
data class UserProfileUiState(
    val isLoading: Boolean = true,
    val currentUser: User? = null,
    val organization: Organization? = null,
    val currentGroup: Group? = null,
    val allGroups: List<Group> = emptyList(),
    val requestResult: Result<Unit>? = null, // 用來通知 UI 申請結果

    // -- 新增編輯相關狀態 --
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

    // ✅ 修正點：將 MutableFlow 改為 MutableStateFlow
    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            // 1. 取得使用者資料，這會觸發後續的資料載入
            repository.observeUser(userId).collectLatest { user ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        // 當使用者資料載入時，同時更新輸入框的初始值
                        nameInput = user?.name ?: "",
                        employeeIdInput = user?.employeeId ?: ""
                    )
                }

                if (user != null && user.orgId.isNotEmpty()) {
                    // 2. 當取得 orgId 後，載入組織與組別資料
                    loadOrganizationAndGroups(user)
                } else {
                    // 如果使用者沒有組織，就結束載入
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun loadOrganizationAndGroups(user: User) {
        viewModelScope.launch {
            // 監聽組織資訊
            repository.observeOrganization(user.orgId).collect { org ->
                _uiState.update { it.copy(organization = org) }
            }
        }
        viewModelScope.launch {
            // 監聽該組織的所有組別
            repository.observeGroups(user.orgId).collect { groups ->
                // 從所有組別中，找出使用者目前所在的組別
                val current = groups.find { it.memberIds.contains(user.id) }
                _uiState.update {
                    it.copy(
                        allGroups = groups,
                        currentGroup = current,
                        isLoading = false // 所有資料都載入完畢
                    )
                }
            }
        }
    }

    /**
     * 啟用編輯模式
     */
    fun enableEditMode() {
        _uiState.update {
            it.copy(
                isEditing = true,
                // 確保編輯時是從目前的使用者資料開始
                nameInput = it.currentUser?.name ?: "",
                employeeIdInput = it.currentUser?.employeeId ?: ""
            )
        }
    }

    /**
     * 取消編輯模式
     */
    fun cancelEditMode() {
        _uiState.update { it.copy(isEditing = false) }
    }

    /**
     * 當姓名輸入框內容改變時呼叫
     */
    fun onNameChange(name: String) {
        _uiState.update { it.copy(nameInput = name) }
    }

    /**
     * 當員工編號輸入框內容改變時呼叫
     */
    fun onEmployeeIdChange(employeeId: String) {
        _uiState.update { it.copy(employeeIdInput = employeeId) }
    }

    /**
     * 儲存更新後的使用者個人資料
     */
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
                    isEditing = result.isFailure, // 如果失敗，停留在編輯模式
                    saveResult = result
                )
            }
        }
    }

    /**
     * 清除儲存結果，避免重複顯示 Toast
     */
    fun clearSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }

    /**
     * 發送加入組別的申請
     */
    fun sendGroupJoinRequest(targetGroup: Group) {
        val currentUser = _uiState.value.currentUser ?: return

        viewModelScope.launch {
            val request = GroupJoinRequest(
                id = UUID.randomUUID().toString(),
                orgId = currentUser.orgId,
                userId = currentUser.id,
                userName = _uiState.value.nameInput, // 使用更新過的名稱
                targetGroupId = targetGroup.id,
                targetGroupName = targetGroup.groupName,
                status = "pending",
                requestedAt = Date()
            )

            val result = repository.createGroupJoinRequest(currentUser.orgId, request)
            _uiState.update { it.copy(requestResult = result.map { }) }
        }
    }

    /**
     * 重設申請結果狀態，避免重複顯示 Toast
     */
    fun clearRequestResult() {
        _uiState.update { it.copy(requestResult = null) }
    }
}