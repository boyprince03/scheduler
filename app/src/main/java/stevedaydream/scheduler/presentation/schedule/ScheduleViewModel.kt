package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject
import java.util.Date // ✅ 新增這個 import

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()

    private var currentOrgId: String = ""
    private var currentGroupId: String = ""

    val isScheduler: StateFlow<Boolean> = _group.map { group ->
        group?.schedulerId == auth.currentUser?.uid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canSchedule: StateFlow<Boolean> = _group.map { group ->
        group?.isSchedulerActive() == false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadGroup(orgId: String, groupId: String) {
        currentOrgId = orgId
        currentGroupId = groupId

        viewModelScope.launch {
            repository.observeGroup(groupId).collect { group ->
                _group.value = group

                // 自動續約邏輯
                // ✅ 修復:兩處都使用安全調用
                if (group?.schedulerId == auth.currentUser?.uid && group?.isSchedulerActive() == true) {
                    renewLease()
                }
            }
        }
    }

    fun claimScheduler() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch

            repository.claimScheduler(
                orgId = currentOrgId,
                groupId = currentGroupId,
                userId = currentUser.uid,
                userName = currentUser.displayName ?: currentUser.email ?: "未命名使用者"
            ).onSuccess { success ->
                if (!success) {
                    // 認領失敗,可能已有其他人認領
                    // TODO: 顯示錯誤訊息
                }
            }
        }
    }

    fun releaseScheduler() {
        viewModelScope.launch {
            repository.releaseScheduler(currentOrgId, currentGroupId)
        }
    }

    private fun renewLease() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch

            val expiresAt = _group.value?.schedulerLeaseExpiresAt
            // ⬇️ 修正 #3: 修改這裡的比較邏輯
            if (expiresAt != null && Date().before(expiresAt)) {
                repository.renewSchedulerLease(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    userId = currentUser.uid
                )
            }
        }
    }
}