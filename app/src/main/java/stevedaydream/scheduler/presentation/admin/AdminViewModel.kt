// scheduler/presentation/admin/AdminViewModel.kt
package stevedaydream.scheduler.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject


data class OrganizationAdminInfo(
    val organization: Organization,
    val memberCount: Int
)

data class AdminUiState(
    val organizationsInfo: List<OrganizationAdminInfo> = emptyList(), // 改用新的 Info class
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _generationState = MutableSharedFlow<Result<Unit>>()
    val generationState = _generationState.asSharedFlow()

    private val _deleteState = MutableSharedFlow<Result<Unit>>()
    val deleteState = _deleteState.asSharedFlow()

    init {
        loadOrganizations()
    }

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    private fun loadOrganizations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.observeAllOrganizations()
                .flatMapLatest { orgs ->
                    if (orgs.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        // 為每個組織建立一個 Flow，該 Flow 會觀察其成員數
                        val memberCountFlows = orgs.map { org ->
                            repository.observeUsers(org.id).map { users ->
                                OrganizationAdminInfo(org, users.size)
                            }
                        }
                        // 將所有 Flow 合併成一個列表 Flow
                        combine(memberCountFlows) { it.toList() }
                    }
                }
                .catch { e ->
                    _uiState.update { it.copy(error = e.localizedMessage, isLoading = false) }
                }
                .collect { orgsInfo ->
                    _uiState.update {
                        it.copy(
                            organizationsInfo = orgsInfo.sortedBy { it.organization.orgName },
                            isLoading = false
                        )
                    }
                }
        }
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲


    fun createTestData(orgName: String, testMemberEmail: String) {
        viewModelScope.launch {
            val ownerId = auth.currentUser?.uid
            if (ownerId == null) {
                _generationState.emit(Result.failure(Exception("使用者未登入")))
                return@launch
            }

            if (orgName.isBlank()) {
                _generationState.emit(Result.failure(IllegalArgumentException("組織名稱不可為空")))
                return@launch
            }

            val result = repository.createTestData(orgName, ownerId, testMemberEmail.trim())
            _generationState.emit(result)
        }
    }

    fun deleteOrganization(orgId: String) {
        viewModelScope.launch {
            val result = repository.deleteOrganization(orgId)
            _deleteState.emit(result)
        }
    }
}