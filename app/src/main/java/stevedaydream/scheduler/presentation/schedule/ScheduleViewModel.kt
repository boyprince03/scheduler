// scheduler/presentation/schedule/ScheduleViewModel.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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

    private fun loadGroupData() {
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

        viewModelScope.launch {
            repository.observeGroup(currentGroupId).collect { group ->
                group?.let {
                    repository.observeUsers(currentOrgId).collect { allUsers ->
                        _users.value = allUsers.filter { user -> user.id in it.memberIds }
                    }
                }
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

    // ✅ ===== 修正點 =====
    fun generateSmartSchedule(month: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                // 從 repository 取得該月份的人力規劃資料
                val manpowerPlan = repository.observeManpowerPlan(currentOrgId, currentGroupId, month).firstOrNull()

                // 1. ScheduleGenerator 產生 Schedule 和 Assignments 物件 (此部分不變)
                val result = scheduleGenerator.generateSchedule(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    month = month,
                    users = _users.value,
                    shiftTypes = _shiftTypes.value,
                    requests = _requests.value,
                    rules = _rules.value.filter { it.isEnabled },
                    manpowerPlan = manpowerPlan
                )

                // 2. 使用新的原子操作函式，一次性寫入所有資料
                repository.createScheduleAndAssignments(
                    orgId = currentOrgId,
                    schedule = result.schedule,
                    assignments = result.assignments
                ).getOrThrow() // 如果批次寫入失敗，直接拋出例外

                _generateSuccess.emit(Unit)
            } catch (e: Exception) {
                println("❌ [ScheduleVM] 智慧排班生成或儲存失敗: ${e.message}")
                // 未來可以在此處更新 UI State 來向使用者顯示錯誤訊息
            } finally {
                _isGenerating.value = false
            }
        }
    }
}