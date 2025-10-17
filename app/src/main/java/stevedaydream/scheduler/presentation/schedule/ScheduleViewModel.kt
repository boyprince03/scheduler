// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/schedule/ScheduleViewModel.kt
package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.domain.scheduling.ScheduleGenerator
import javax.inject.Inject


@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth,
    private val scheduleGenerator: ScheduleGenerator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val currentOrgId: String = savedStateHandle.get<String>("orgId")!!
    val currentGroupId: String = savedStateHandle.get<String>("groupId")!!

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isScheduler: StateFlow<Boolean> = _group.map { group ->
        group?.schedulerId == auth.currentUser?.uid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canSchedule: StateFlow<Boolean> = _group.map { group ->
        group?.isSchedulerActive() == false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _users = MutableStateFlow<List<User>>(emptyList())
    private val _shiftTypes = MutableStateFlow<List<ShiftType>>(emptyList())
    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    private val _rules = MutableStateFlow<List<SchedulingRule>>(emptyList())
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generateSuccess = MutableSharedFlow<Unit>()
    val generateSuccess = _generateSuccess.asSharedFlow()

    init {
        loadGroupData()
    }

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 函式修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    private fun loadGroupData() {
        auth.currentUser?.uid?.let { userId ->
            viewModelScope.launch {
                repository.observeUser(userId).collect { user ->
                    _currentUser.value = user
                }
            }
        }

        viewModelScope.launch {
            repository.observeGroup(currentGroupId).collect { groupData ->
                _group.value = groupData
                if (groupData?.schedulerId == auth.currentUser?.uid && groupData?.isSchedulerActive() == true) {
                    renewLease()
                }
            }
        }

        viewModelScope.launch {
            repository.observeSchedules(currentOrgId, currentGroupId).collect { scheduleList ->
                _schedules.value = scheduleList
            }
        }

        // ✅ 核心修正：使用 flatMapLatest 建立反應式資料流
        // 當 group 的 memberIds 變更時，會自動重新取得並過濾 users 列表
        viewModelScope.launch {
            repository.observeGroup(currentGroupId)
                .filterNotNull()
                .flatMapLatest { group ->
                    repository.observeUsers(currentOrgId).map { allUsers ->
                        allUsers.filter { user -> user.id in group.memberIds }
                    }
                }
                .collect { groupMembers ->
                    _users.value = groupMembers
                }
        }

        viewModelScope.launch {
            repository.observeShiftTypes(currentOrgId, currentGroupId).collect { types ->
                _shiftTypes.value = types
            }
        }

        viewModelScope.launch {
            repository.observeRequests(currentOrgId).collect { reqs ->
                _requests.value = reqs
            }
        }

        viewModelScope.launch {
            repository.observeSchedulingRules(currentOrgId, currentGroupId).collect { ruleList ->
                _rules.value = ruleList
            }
        }
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 函式修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    fun toggleReservation(month: String, currentStatus: String) {
        viewModelScope.launch {
            val newStatus = when (currentStatus) {
                "inactive" -> "active"
                "active" -> "closed"
                "closed" -> "active"
                else -> "inactive"
            }
            repository.updateReservationStatus(currentOrgId, currentGroupId, month, newStatus)
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
            )
        }
    }

    /**
     * 將身為排班者的管理員加入到目前群組中
     */
    fun addSchedulerToGroup() {
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.addUserToGroupAndOrg(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    userId = userId
                )
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
            repository.renewSchedulerLease(
                orgId = currentOrgId,
                groupId = currentGroupId,
                userId = currentUser.uid
            )
        }
    }

    fun generateSmartSchedule(month: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val manpowerPlan = repository.getManpowerPlanOnce(currentOrgId, currentGroupId, month)
                // ✅ 新增：在排班前，獲取該月份的預約資料
                val reservations = repository.observeReservations(currentOrgId, currentGroupId, month).first()

                val result = scheduleGenerator.generateSchedule(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    month = month,
                    users = _users.value,
                    shiftTypes = _shiftTypes.value,
                    requests = _requests.value,
                    reservations = reservations, // ✅ 傳入預約資料
                    rules = _rules.value.filter { it.isEnabled },
                    manpowerPlan = manpowerPlan
                )

                repository.createScheduleAndAssignments(
                    orgId = currentOrgId,
                    schedule = result.schedule,
                    assignments = result.assignments
                ).getOrThrow()

                _generateSuccess.emit(Unit)
            } catch (e: Exception) {
                println("❌ [ScheduleVM] 智慧排班生成或儲存失敗: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }
    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            repository.deleteSchedule(currentOrgId, scheduleId)
                .onFailure {
                    println("❌ 刪除班表失敗: ${it.message}")
                }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲