// 修改開始
package stevedaydream.scheduler.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.data.model.OrganizationJoinRequest
import stevedaydream.scheduler.domain.repository.SchedulerRepository

import javax.inject.Inject

// ==================== JoinOrganizationViewModel ====================

data class JoinOrganizationUiState(
    val isLoading: Boolean = false,
    val foundOrganization: Organization? = null,
    val pendingRequests: List<OrganizationJoinRequest> = emptyList(),
    val joinResult: Result<Unit>? = null
)

@HiltViewModel
class JoinOrganizationViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinOrganizationUiState())
    val uiState: StateFlow<JoinOrganizationUiState> = _uiState.asStateFlow()

    init {
        loadPendingRequests()
    }

    private fun loadPendingRequests() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.observeUserJoinRequests(userId).collect { requests ->
                _uiState.update { it.copy(pendingRequests = requests) }
            }
        }
    }

    fun searchByInviteCode(inviteCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = repository.getOrganizationByInviteCode(inviteCode)

            result.onSuccess { org ->
                _uiState.update {
                    it.copy(
                        foundOrganization = org,
                        isLoading = false
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        foundOrganization = null,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun submitJoinRequest(inviteCode: String, message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val currentUser = auth.currentUser
            val org = _uiState.value.foundOrganization

            if (currentUser == null || org == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        joinResult = Result.failure(Exception("資料不完整"))
                    )
                }
                return@launch
            }

            // 先驗證並使用邀請碼
            val inviteResult = repository.validateAndUseInviteCode(inviteCode)

            inviteResult.onSuccess { invite ->
                // ✅ 修正點：將 displayName 存為本地變數以啟用智慧轉型 (Smart Cast)
                val displayName = currentUser.displayName
                val userName = if (displayName.isNullOrBlank()) {
                    currentUser.email ?: "未命名使用者"
                } else {
                    displayName // 現在編譯器知道 displayName 在此處不為 null
                }

                val request = OrganizationJoinRequest(
                    orgId = org.id,
                    orgName = org.displayName.ifEmpty { org.orgName },
                    userId = currentUser.uid,
                    userName = userName,
                    userEmail = currentUser.email ?: "",
                    inviteCode = inviteCode,
                    joinMethod = when (invite.inviteType) {
                        "qrcode" -> "qrcode"
                        "email" -> "email"
                        else -> "manual"
                    },
                    message = message,
                    targetGroupId = invite.targetGroupId
                )

                val result = repository.createOrganizationJoinRequest(request)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        joinResult = result.map { }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        joinResult = Result.failure(error)
                    )
                }
            }
        }
    }

    fun openQRScanner() {
        // 導航到 QR Scanner (由 UI 層處理)
    }
}
// 修改結束