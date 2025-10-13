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
            println("🔍 [DEBUG] Current user UID from ViewModel is: $currentUid") // <--- 新增這一行

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

    fun createOrganization(orgName: String, location: String = "") {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch

            // 生成唯一組織代碼
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
                existingUser.copy(
                    role = "org_admin",
                    email = existingUser.email.ifEmpty { currentUser.email ?: "" }
                )
            } else {
                User(
                    id = currentUser.uid,
                    email = currentUser.email ?: "",
                    name = currentUser.displayName ?: "管理員",
                    role = "org_admin",
                    employeeId = "",
                    joinedAt = Date()
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

// ==================== 完整使用流程範例 ====================

/**
 * 流程 A: QR Code 加入
 *
 * 1. 管理員進入「邀請管理」→ 建立 QR Code 邀請
 * 2. 新用戶開啟 APP → 點擊「加入組織」→ 選擇「掃描 QR Code」
 * 3. 掃描後自動填入邀請碼並顯示組織資訊
 * 4. 用戶確認並送出申請
 * 5. 管理員在「審核申請」中核准或拒絕
 * 6. 核准後用戶自動加入組織 (及指定群組)
 */

/**
 * 流程 B: 邀請碼加入
 *
 * 1. 管理員進入「邀請管理」→ 建立一般邀請碼
 * 2. 管理員透過任何方式 (Line、Email等) 分享邀請碼
 * 3. 新用戶開啟 APP → 點擊「加入組織」→ 輸入邀請碼
 * 4. 系統顯示組織資訊供確認
 * 5. 其餘流程同上
 */

/**
 * 流程 C: Email 邀請 (未來功能)
 *
 * 1. 管理員進入「邀請管理」→ 建立 Email 邀請
 * 2. 輸入受邀者 Email 列表
 * 3. 系統發送邀請信 (含 Deep Link)
 * 4. 受邀者點擊 Email 中的連結
 * 5. APP 開啟並自動導向加入頁面
 * 6. 其餘流程同上
 */

/**
 * Deep Link 設定範例:
 *
 * AndroidManifest.xml:
 * <intent-filter>
 *     <action android:name="android.intent.action.VIEW" />
 *     <category android:name="android.intent.category.DEFAULT" />
 *     <category android:name="android.intent.category.BROWSABLE" />
 *     <data
 *         android:scheme="scheduler"
 *         android:host="join"
 *         android:pathPrefix="/org" />
 * </intent-filter>
 *
 * Deep Link 格式: scheduler://join/org?code=ABC12345
 */