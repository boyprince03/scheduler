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
 * ✅ 修改：加入個人班別統計和日均夜班數
 */
data class ScheduleStatistics(
    val targetOffDays: Int = 0,
    val actualOffDays: Int = 0,
    val totalDutyDays: Int = 0,
    val averageDailyManpower: Float = 0f,
    val averageNightShiftsPerUser: Float = 0f, // 新增：日均夜班數
    val currentUserWorkHours: Float = 0f,
    val currentUserOffDays: Int = 0,
    val userShiftCounts: Map<String, Map<String, Int>> = emptyMap() // Map<UserId, Map<ShiftName, Count>>
)

data class ScheduleDetailUiState(
    val isLoading: Boolean = true,
    val schedule: Schedule? = null,
    val assignments: List<Assignment> = emptyList(),
    val users: List<User> = emptyList(),
    val shiftTypes: List<ShiftType> = emptyList(),
    val manpowerPlan: ManpowerPlan? = null,
    val enabledRules: List<SchedulingRule> = emptyList(),
    val statistics: ScheduleStatistics = ScheduleStatistics() // ✅ 統計物件
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
                            // 保持原始順序或按名稱排序，以便與 ScheduleDetailTable 一致
                            allUsers.filter { it.id in group.memberIds }
                                .sortedBy { it.name } // 確保與表格顯示順序一致
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
                            users = users, // 使用排序後的使用者列表
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
     * ✅ 修改：核心統計計算函式，加入個人班別統計和日均夜班數計算
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
        val shiftNameMap = shiftTypes.associate { it.id to it.name } // Map<ShiftId, ShiftName>
        val offShiftId = shiftTypes.find { it.shortCode == "OFF" }?.id
        val nightShiftId = shiftTypes.find { it.name == "值班(夜)" }?.id // 找到夜班 ID
        val dutyShiftNames = setOf("值班(日)", "值班(夜)")

        // 計算總目標休假天數
        val totalRequiredManpower = plan.dailyRequirements.values.sumOf { dailyReq ->
            dailyReq.requirements.values.sum()
        }
        val targetOffDays = totalManDays - totalRequiredManpower

        // 計算實際班表數據 & 個人班別統計
        var actualOffDays = 0
        var totalDutyDays = 0
        var totalNightShifts = 0 // 新增：計算總夜班數
        val userShiftCounts = mutableMapOf<String, MutableMap<String, Int>>() // Map<UserId, Map<ShiftName, Count>>
        users.forEach { userShiftCounts[it.id] = mutableMapOf() } // 初始化

        assignments.forEach { assignment ->
            assignment.dailyShifts.values.forEach { shiftId ->
                val shiftName = shiftNameMap[shiftId] ?: "未知"

                // 累加個人班別統計
                userShiftCounts[assignment.userId]?.let { counts ->
                    counts[shiftName] = (counts[shiftName] ?: 0) + 1
                }

                // 累加全體統計
                if (shiftId == offShiftId) {
                    actualOffDays++
                }
                if (shiftId == nightShiftId) { // 如果是夜班，累加總夜班數
                    totalNightShifts++
                }
                shiftTypeMap[shiftId]?.let {
                    if (it.name in dutyShiftNames) {
                        totalDutyDays++
                    }
                }
            }
        }

        val averageDailyManpower = (totalManDays - actualOffDays).toFloat() / daysInMonth
        // 計算日均夜班數 (總夜班數 / 人數)
        val averageNightShiftsPerUser = if (users.isNotEmpty()) totalNightShifts.toFloat() / users.size else 0f


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
            averageNightShiftsPerUser = averageNightShiftsPerUser, // 加入計算結果
            currentUserWorkHours = currentUserWorkHours,
            currentUserOffDays = currentUserOffDays,
            userShiftCounts = userShiftCounts // 加入個人統計結果
        )
    }

    /**
     * ✅ 計算班別時長的輔助函式 (處理跨日) - 保持不變
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