// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * ✅ 新增：用於存放班表統計數據的資料類別
 */
data class ScheduleStatistics(
    val targetOffDays: Int = 0,
    val actualOffDays: Int = 0,
    val totalDutyDays: Int = 0,
    val averageDailyManpower: Float = 0f,
    val currentUserWorkHours: Float = 0f,
    val currentUserOffDays: Int = 0,
)

data class ScheduleDetailUiState(
    val isLoading: Boolean = true,
    val schedule: Schedule? = null,
    val assignments: List<Assignment> = emptyList(),
    val users: List<User> = emptyList(),
    val shiftTypes: List<ShiftType> = emptyList(),
    val manpowerPlan: ManpowerPlan? = null,
    val enabledRules: List<SchedulingRule> = emptyList(),
    val statistics: ScheduleStatistics = ScheduleStatistics() // ✅ 新增：統計物件
)

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth, // ✅ 注入 FirebaseAuth 以取得當前使用者 ID
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orgId: String = savedStateHandle.get<String>("orgId")!!
    private val groupId: String = savedStateHandle.get<String>("groupId")!!
    private val scheduleId: String = savedStateHandle.get<String>("scheduleId")!!

    private val _uiState = MutableStateFlow(ScheduleDetailUiState())
    val uiState: StateFlow<ScheduleDetailUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val schedule = repository.observeSchedule(scheduleId).firstOrNull()
                if (schedule == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                val month = schedule.month

                val manpowerPlan = repository.getManpowerPlanOnce(orgId, groupId, month)

                combine(
                    repository.observeAssignments(orgId, scheduleId),
                    repository.observeShiftTypes(orgId, groupId),
                    repository.observeGroup(groupId).filterNotNull().flatMapLatest { group ->
                        repository.observeUsers(orgId).map { allUsers ->
                            allUsers.filter { it.id in group.memberIds }
                        }
                    },
                    repository.observeSchedulingRules(orgId, groupId)
                ) { assignments, shiftTypes, users, allRules ->
                    // ✅ 當所有資料都載入後，執行統計計算
                    val stats = calculateStatistics(manpowerPlan, assignments, users, shiftTypes, month, auth.currentUser?.uid)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            schedule = schedule,
                            manpowerPlan = manpowerPlan,
                            assignments = assignments,
                            shiftTypes = shiftTypes,
                            users = users,
                            enabledRules = allRules.filter { rule -> rule.isEnabled },
                            statistics = stats // ✅ 更新統計數據
                        )
                    }
                }.collect()

            } catch (e: Exception) {
                Log.e("ScheduleDetailVM", "Error loading data", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * ✅ 新增：核心統計計算函式
     */
    private fun calculateStatistics(
        plan: ManpowerPlan?,
        assignments: List<Assignment>,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        month: String,
        currentUserId: String?
    ): ScheduleStatistics {
        if (plan == null || assignments.isEmpty() || users.isEmpty() || shiftTypes.isEmpty()) {
            return ScheduleStatistics()
        }

        val daysInMonth = DateUtils.getDaysInMonth(month)
        val totalManDays = users.size * daysInMonth
        val shiftTypeMap = shiftTypes.associateBy { it.id }
        val offShiftId = shiftTypes.find { it.shortCode == "OFF" }?.id
        val dutyShiftNames = setOf("值班(日)", "值班(夜)")

        // 計算總目標休假天數
        val totalRequiredManpower = plan.dailyRequirements.values.sumOf { dailyReq ->
            dailyReq.requirements.values.sum()
        }
        val targetOffDays = totalManDays - totalRequiredManpower

        // 計算實際班表數據
        var actualOffDays = 0
        var totalDutyDays = 0
        assignments.forEach { assignment ->
            assignment.dailyShifts.values.forEach { shiftId ->
                if (shiftId == offShiftId) {
                    actualOffDays++
                }
                shiftTypeMap[shiftId]?.let {
                    if (it.name in dutyShiftNames) {
                        totalDutyDays++
                    }
                }
            }
        }

        val averageDailyManpower = (totalManDays - actualOffDays).toFloat() / daysInMonth

        // 計算個人數據
        var currentUserWorkHours = 0f
        var currentUserOffDays = 0
        if (currentUserId != null) {
            assignments.find { it.userId == currentUserId }?.let { userAssignment ->
                userAssignment.dailyShifts.values.forEach { shiftId ->
                    if (shiftId == offShiftId) {
                        currentUserOffDays++
                    } else {
                        currentUserWorkHours += getShiftDuration(shiftTypeMap[shiftId])
                    }
                }
            }
        }

        return ScheduleStatistics(
            targetOffDays = targetOffDays,
            actualOffDays = actualOffDays,
            totalDutyDays = totalDutyDays,
            averageDailyManpower = averageDailyManpower,
            currentUserWorkHours = currentUserWorkHours,
            currentUserOffDays = currentUserOffDays
        )
    }

    /**
     * ✅ 新增：計算班別時長的輔助函式 (處理跨日)
     */
    private fun getShiftDuration(shiftType: ShiftType?): Float {
        if (shiftType == null) return 0f
        try {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startTime = format.parse(shiftType.startTime) ?: return 0f
            val endTime = format.parse(shiftType.endTime) ?: return 0f

            var diff = endTime.time - startTime.time
            if (diff < 0) { // 跨日班次
                diff += 24 * 60 * 60 * 1000
            }
            return diff / (60 * 60 * 1000).toFloat()
        } catch (e: Exception) {
            return 0f
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲