package stevedaydream.scheduler.presentation.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject
import java.util.Date // ✅ 新增這個 import


@HiltViewModel
class OrganizationListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 🔴 移除了整個 init { ... } 區塊

    // 🟡 將 private fun loadOrganizations() 改為 fun loadOrganizations()
    fun loadOrganizations() {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid
            println("🔍 Current user UID: $currentUid") // ✅ 加入此行

            currentUid?.let { ownerId ->
                println("🔍 Querying organizations for ownerId: $ownerId") // ✅ 加入此行
                repository.observeOrganizationsByOwner(ownerId).collect { orgList ->
                    println("🔍 Received ${orgList.size} organizations") // ✅ 加入此行
                    _organizations.value = orgList
                }
            } ?: run {
                println("❌ No user logged in") // ✅ 加入此行
            }
        }
    }
    fun refresh() {
        // 防止使用者在刷新過程中重複觸發
        if (isRefreshing.value) return

        viewModelScope.launch {
            _isRefreshing.value = true
            // 記錄開始執行的時間
            val startTime = System.currentTimeMillis()
            try {
                auth.currentUser?.uid?.let { ownerId ->
                    repository.refreshOrganizations(ownerId).onFailure { error ->
                        // 處理可能發生的錯誤
                        println("Refresh failed: ${error.localizedMessage}")
                    }
                }
            } finally {
                // 計算實際花費的時間
                val duration = System.currentTimeMillis() - startTime

                // 如果花費時間少於 500 毫秒，就等待剩餘的時間
                // 這能確保動畫至少會顯示半秒鐘
                if (duration < 500L) {
                    kotlinx.coroutines.delay(500L - duration)
                }

                // 最後，結束刷新動畫
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
                createdAt = Date(), // ⬅️ 修正 #1: 將 System.currentTimeMillis() 改為 Date()
                plan = "free"
            )

            // 準備創建者的使用者物件
            val adminUser = User(
                id = currentUser.uid,
                email = currentUser.email ?: "",
                name = currentUser.displayName ?: "管理員",
                role = "org_admin",
                joinedAt = Date() // ⬅️ 修正 #2: 將 System.currentTimeMillis() 改為 Date()
            )

            // 呼叫 repository 並處理回傳的 Result
            repository.createOrganization(newOrg, adminUser)
                .onSuccess { newOrgId ->
                    println("Successfully created organization with ID: $newOrgId")
                }
                .onFailure { error ->
                    println("Failed to create organization: ${error.localizedMessage}")
                }
        }
    }
    // ✅ 新增一個 SharedFlow 來處理單次事件，例如導航
    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent = _logoutEvent.asSharedFlow()
    // ✅ 新增登出函式
    fun logout() {
        viewModelScope.launch {
            // 1. 清除本地所有資料
            repository.clearAllLocalData()
            // 2. 從 Firebase 登出
            auth.signOut()
            // 3. 發送登出成功事件，通知 UI 進行導航
            _logoutEvent.emit(Unit)
        }
    }
}