// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/admin/AdminViewModel.kt
package stevedaydream.scheduler.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.data.model.Request
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import javax.inject.Inject
import kotlin.random.Random


data class OrganizationAdminInfo(
    val organization: Organization,
    val memberCount: Int,
    val groups: List<Group> = emptyList() // 新增群組列表
)

data class AdminUiState(
    val organizationsInfo: List<OrganizationAdminInfo> = emptyList(),
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
    private val _deleteAllState = MutableSharedFlow<Result<Int>>()
    val deleteAllState = _deleteAllState.asSharedFlow()
    // 新增：用於顯示單元測試結果的 Flow
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()


    init {
        loadOrganizations()
    }

    private fun loadOrganizations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.observeAllOrganizations()
                .flatMapLatest { orgs ->
                    if (orgs.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        // 為每個組織建立一個 Flow，觀察其成員和群組
                        val infoFlows = orgs.map { org ->
                            combine(
                                repository.observeUsers(org.id),
                                repository.observeGroups(org.id)
                            ) { users, groups ->
                                OrganizationAdminInfo(org, users.size, groups)
                            }
                        }
                        combine(infoFlows) { it.toList() }
                    }
                }
                .flowOn(Dispatchers.IO)
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
    fun deleteAllTestData() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.deleteAllTestData()
            _deleteAllState.emit(result)
        }
    }

    // --- 單元測試功能 ---

    fun createOrgAndGroup(orgName: String, groupName: String) {
        viewModelScope.launch {
            val owner = auth.currentUser ?: return@launch
            val orgCode = repository.generateUniqueOrgCode()

            // 1. 建立 Organization
            val newOrg = Organization(
                orgName = orgName,
                ownerId = owner.uid,
                orgCode = orgCode,
                displayName = orgName
            )
            // 2. 建立 User (擁有者)
            val adminUser = User(
                id = owner.uid,
                email = owner.email ?: "",
                name = owner.displayName ?: "Admin",
                role = "org_admin",
                orgIds = emptyList() // 會在 repository 中被更新
            )
            val orgResult = repository.createOrganization(newOrg, adminUser)

            orgResult.onSuccess { newOrgId ->
                // 3. 建立 Group
                val newGroup = Group(orgId = newOrgId, groupName = groupName)
                val groupResult = repository.createGroup(newOrgId, newGroup)
                if (groupResult.isSuccess) {
                    _toastMessage.emit("成功建立組織 '$orgName' 及群組 '$groupName'")
                } else {
                    _toastMessage.emit("建立群組失敗: ${groupResult.exceptionOrNull()?.message}")
                }
            }.onFailure {
                _toastMessage.emit("建立組織失敗: ${it.message}")
            }
        }
    }

    fun createUserAndAssign(orgId: String, groupId: String?, userName: String, email: String) {
        viewModelScope.launch {
            if (userName.isBlank() || email.isBlank()) {
                _toastMessage.emit("使用者名稱和 Email 為必填")
                return@launch
            }
            val newUser = User(
                id = UUID.randomUUID().toString(), // 在實際應用中，這應該是註冊流程產生的 UID
                name = userName,
                email = email,
                orgIds = listOf(orgId),
                currentOrgId = orgId
            )
            // 1. 建立使用者
            val userResult = repository.createUser(orgId, newUser)

            userResult.onSuccess { userId ->
                // 2. 如果有指定群組，則加入
                if (groupId != null) {
                    val assignResult = repository.updateUserGroup(orgId, userId, newGroupId = groupId, oldGroupId = null)
                    if (assignResult.isSuccess) {
                        _toastMessage.emit("成功建立使用者 '$userName' 並加入群組")
                    } else {
                        _toastMessage.emit("建立使用者成功，但加入群組失敗")
                    }
                } else {
                    _toastMessage.emit("成功建立使用者 '$userName'")
                }
            }.onFailure {
                _toastMessage.emit("建立使用者失敗: ${it.message}")
            }
        }
    }

    fun assignRandomLeave(orgId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val orgInfo = _uiState.value.organizationsInfo.find { it.organization.id == orgId }
            if (orgInfo == null) {
                _toastMessage.emit("找不到該組織")
                return@launch
            }

            val usersInGroups = repository.observeUsers(orgId).first()
                .filter { user -> orgInfo.groups.any { group -> group.memberIds.contains(user.id) } }

            if (usersInGroups.isEmpty()) {
                _toastMessage.emit("該組織的群組中尚無成員")
                return@launch
            }

            val dates = DateUtils.getDatesInMonth(DateUtils.getCurrentMonthString())
            var requestCount = 0

            usersInGroups.forEach { user ->
                val randomDates = dates.shuffled().take(5)
                randomDates.forEach { date ->
                    val request = Request(
                        orgId = orgId,
                        userId = user.id,
                        userName = user.name,
                        date = date,
                        type = "leave",
                        status = "approved"
                    )
                    repository.createRequest(orgId, request)
                    requestCount++
                }
            }
            _toastMessage.emit("已為 ${usersInGroups.size} 位成員建立共 $requestCount 筆預假")
        }
    }

}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲