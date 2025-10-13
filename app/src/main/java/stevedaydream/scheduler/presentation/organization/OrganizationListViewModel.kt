// scheduler/presentation/organization/OrganizationListViewModel.kt
package stevedaydream.scheduler.presentation.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.data.model.OrganizationJoinRequest
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.util.*
import javax.inject.Inject

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
data class OrganizationWithMemberCount(
    val organization: Organization,
    val memberCount: Int
)

@HiltViewModel
class OrganizationListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<OrganizationJoinRequest>>(emptyList())
    val pendingRequests: StateFlow<List<OrganizationJoinRequest>> = _pendingRequests.asStateFlow()

    private val ownedOrganizationsFlow = auth.currentUser?.uid?.let { ownerId ->
        repository.observeOrganizationsByOwner(ownerId)
    } ?: flowOf(emptyList())

    private val joinedOrganizationsFlow: Flow<List<Organization>> = _currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            val orgIds = user.orgIds.filter { it.isNotBlank() }
            if (orgIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                val orgFlows = orgIds.map { orgId ->
                    repository.observeOrganization(orgId)
                }
                combine(orgFlows) { organizations ->
                    organizations.filterNotNull()
                }
            }
        }

    val organizationsInfo: StateFlow<List<OrganizationWithMemberCount>> =
        combine(ownedOrganizationsFlow, joinedOrganizationsFlow) { owned, joined ->
            (owned + joined).distinctBy { it.id }
        }.flatMapLatest { orgs ->
            if (orgs.isEmpty()) {
                flowOf(emptyList())
            } else {
                val memberCountFlows = orgs.map { org ->
                    repository.observeUsers(org.id).map { users ->
                        OrganizationWithMemberCount(org, users.size)
                    }
                }
                combine(memberCountFlows) { it.toList() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        loadData()
    }

    private fun loadData() {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            println("❌ No user logged in")
            return
        }

        viewModelScope.launch {
            repository.observeUser(currentUid).collect { user ->
                _currentUser.value = user
            }
        }

        viewModelScope.launch {
            organizationsInfo
                .flatMapLatest { orgsInfo ->
                    val ownedOrgs = orgsInfo.filter { it.organization.ownerId == auth.currentUser?.uid }.map { it.organization }
                    if (ownedOrgs.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val requestFlows = ownedOrgs.map { org ->
                            repository.observeOrganizationJoinRequests(org.id)
                        }
                        combine(requestFlows) { arrays ->
                            arrays.flatMap { it.toList() }
                        }
                    }
                }
                .collect { requests ->
                    _pendingRequests.value = requests.filter { it.status == "pending" }
                }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val startTime = System.currentTimeMillis()
            try {
                auth.currentUser?.uid?.let { ownerId ->
                    repository.refreshOrganizations(ownerId).onFailure { error ->
                        println("Refresh failed: ${error.localizedMessage}")
                    }
                    repository.observeUser(ownerId).firstOrNull()?.let {
                        _currentUser.value = it
                    }
                }
            } finally {
                val duration = System.currentTimeMillis() - startTime
                if (duration < 500L) {
                    kotlinx.coroutines.delay(500L - duration)
                }
                _isRefreshing.value = false
            }
        }
    }

    fun createOrganization(orgName: String, location: String = "") {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch

            val orgCode = repository.generateUniqueOrgCode()

            val newOrg = Organization(
                orgName = orgName,
                ownerId = currentUser.uid,
                createdAt = Date(),
                plan = "free",
                orgCode = orgCode,
                displayName = if (location.isNotEmpty()) "$orgName ($location)" else orgName,
                location = location,
                requireApproval = true
            )

            val existingUser = repository.observeUser(currentUser.uid).firstOrNull()
            val adminUser = if (existingUser != null) {
                val updatedOrgIds = existingUser.orgIds + newOrg.id
                existingUser.copy(
                    role = "org_admin",
                    email = existingUser.email.ifEmpty { currentUser.email ?: "" },
                    orgIds = updatedOrgIds.distinct(),
                    currentOrgId = newOrg.id
                )
            } else {
                User(
                    id = currentUser.uid,
                    email = currentUser.email ?: "",
                    name = currentUser.displayName ?: "管理員",
                    role = "org_admin",
                    employeeId = "",
                    joinedAt = Date(),
                    orgIds = listOf(newOrg.id),
                    currentOrgId = newOrg.id
                )
            }

            repository.createOrganization(newOrg, adminUser)
                .onSuccess { newOrgId ->
                    println("✅ 組織建立成功,代碼: $orgCode")
                }
                .onFailure { error ->
                    println("❌ 組織建立失敗: ${error.localizedMessage}")
                }
        }
    }


    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent = _logoutEvent.asSharedFlow()

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearAllLocalData()
            }
            auth.signOut()
            _logoutEvent.emit(Unit)
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲