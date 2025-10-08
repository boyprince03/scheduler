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
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OrganizationListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 🔽🔽🔽 新增這兩行 🔽🔽🔽
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    // 🔼🔼🔼 到此為止 🔼🔼🔼

    fun loadOrganizations() {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid
            println("🔍 Current user UID: $currentUid")

            currentUid?.let { ownerId ->
                // ✅ 取得使用者詳細資料
                viewModelScope.launch {
                    repository.observeUser(ownerId).collect { user ->
                        _currentUser.value = user
                    }
                }

                println("🔍 Querying organizations for ownerId: $ownerId")
                repository.observeOrganizationsByOwner(ownerId).collect { orgList ->
                    println("🔍 Received ${orgList.size} organizations")
                    _organizations.value = orgList
                }
            } ?: run {
                println("❌ No user logged in")
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

    fun createOrganization(orgName: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            println("🔍 Creating org with ownerId: ${currentUser.uid}")

            val newOrg = Organization(
                orgName = orgName,
                ownerId = currentUser.uid,
                createdAt = Date(),
                plan = "free"
            )

            val adminUser = User(
                id = currentUser.uid,
                email = currentUser.email ?: "",
                name = currentUser.displayName ?: "管理員",
                role = "org_admin",
                joinedAt = Date()
            )

            repository.createOrganization(newOrg, adminUser)
                .onSuccess { newOrgId ->
                    println("Successfully created organization with ID: $newOrgId")
                }
                .onFailure { error ->
                    println("Failed to create organization: ${error.localizedMessage}")
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