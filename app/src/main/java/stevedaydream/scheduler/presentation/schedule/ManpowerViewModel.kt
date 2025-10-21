// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/schedule/ManpowerViewModel.kt
package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.* // ✅ 1. 確保匯入 SharedFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.util.DateUtils
import java.util.Date
import javax.inject.Inject

enum class ManpowerStep {
    DEFAULTS,
    DETAILS
}

data class ManpowerUiState(
    val isLoading: Boolean = true,
    val currentStep: ManpowerStep = ManpowerStep.DEFAULTS,
    val group: Group? = null,
    val shiftTypes: List<ShiftType> = emptyList(),
    val manpowerPlan: ManpowerPlan? = null,
    val holidays: Map<String, String> = emptyMap(),
    val showHolidayNameDialogFor: String? = null,
    val saveResult: Result<Unit>? = null // ✅ 2. 新增 saveResult 狀態來追蹤結果
)

@HiltViewModel
class ManpowerViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val orgId: String = savedStateHandle.get<String>("orgId")!!
    val groupId: String = savedStateHandle.get<String>("groupId")!!
    val month: String = savedStateHandle.get<String>("month")!!

    private val _uiState = MutableStateFlow(ManpowerUiState())
    val uiState: StateFlow<ManpowerUiState> = _uiState.asStateFlow()

    // ✅ 3. 新增 SharedFlow 用於單次事件通知
    private val _saveSuccessEvent = MutableSharedFlow<Unit>()
    val saveSuccessEvent = _saveSuccessEvent.asSharedFlow()


    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 並行載入所有需要的初始資料
            val groupData = repository.observeGroup(groupId).first()
            val shiftTypesData = repository.observeShiftTypes(orgId, groupId).first()
                .filter { s -> s.shortCode != "OFF" } // 過濾掉 OFF 班別

            // 嘗試從 DB 獲取計畫
            var planFromDb = repository.observeManpowerPlan(orgId, groupId, month).firstOrNull()

            // 如果資料庫中沒有計畫，則建立一個新的並設定預設值
            if (planFromDb == null) {
                // 找出 N 和 D 班的 ID
                val nShiftId = shiftTypesData.find { it.name == "值班(夜)" }?.id
                val dShiftId = shiftTypesData.find { it.name == "值班(日)" }?.id

                // 建立預設需求 Map，包含 N=1, D=1 (如果找得到 ID)
                val defaultRequirements = mutableMapOf<String, Int>()
                nShiftId?.let { defaultRequirements[it] = 1 }
                dShiftId?.let { defaultRequirements[it] = 1 }

                // 建立新的 ManpowerPlan，並將預設值填入所有範本
                planFromDb = ManpowerPlan(
                    id = "${orgId}_${groupId}_${month}",
                    orgId = orgId,
                    groupId = groupId,
                    month = month,
                    // 將預設值套用到 weekday, saturday, sunday, holiday
                    requirementDefaults = RequirementDefaults(
                        weekday = defaultRequirements.toMap(),
                        saturday = defaultRequirements.toMap(),
                        sunday = defaultRequirements.toMap(),
                        holiday = defaultRequirements.toMap()
                    )
                    // dailyRequirements 保持空，會在 applyDefaultsAndProceed 中生成
                )
            }

            // 從 API 獲取假日 (保持不變)
            val holidaysFromApi = fetchHolidaysFromApi(month)

            _uiState.update {
                it.copy(
                    group = groupData,
                    shiftTypes = shiftTypesData, // 已在前面過濾 OFF
                    manpowerPlan = planFromDb,
                    holidays = holidaysFromApi,
                    isLoading = false
                )
            }
        }
    }

    private fun fetchHolidaysFromApi(month: String): Map<String, String> {
        // ... (保持不變) ...
        val allHolidays2025 = mapOf(
            "2025-01-01" to "元旦", "2025-01-27" to "彈性放假", "2025-01-28" to "除夕",
            "2025-01-29" to "春節", "2025-01-30" to "春節", "2025-01-31" to "春節",
            "2025-02-28" to "和平紀念日", "2025-04-03" to "補假", "2025-04-04" to "兒童節",
            "2025-05-01" to "勞動節", "2025-05-30" to "補假", "2025-09-29" to "補假",
            "2025-10-06" to "中秋節", "2025-10-10" to "國慶日", "2025-10-24" to "補假",
        )
        return allHolidays2025.filterKeys { it.startsWith(month) }
    }

    fun onDateClicked(date: String) {
        // ... (保持不變) ...
        val currentHolidays = _uiState.value.holidays
        if (currentHolidays.containsKey(date)) {
            removeHoliday(date)
        } else {
            _uiState.update { it.copy(showHolidayNameDialogFor = date) }
        }
    }

    fun addHoliday(date: String, name: String) {
        // ... (保持不變) ...
        val updatedHolidays = _uiState.value.holidays.toMutableMap()
        updatedHolidays[date] = name.ifBlank { "特殊日" }
        _uiState.update { it.copy(holidays = updatedHolidays, showHolidayNameDialogFor = null) }
    }

    fun removeHoliday(date: String) {
        // ... (保持不變) ...
        val updatedHolidays = _uiState.value.holidays.toMutableMap()
        updatedHolidays.remove(date)
        _uiState.update { it.copy(holidays = updatedHolidays) }
    }

    fun dismissHolidayNameDialog() {
        // ... (保持不變) ...
        _uiState.update { it.copy(showHolidayNameDialogFor = null) }
    }

    fun updateDefaultRequirement(dayType: String, shiftTypeId: String, count: Int) {
        // ... (保持不變) ...
        val currentPlan = _uiState.value.manpowerPlan ?: return
        val currentDefaults = currentPlan.requirementDefaults
        val updatedMap = when(dayType) {
            "weekday" -> currentDefaults.weekday.toMutableMap()
            "saturday" -> currentDefaults.saturday.toMutableMap()
            "sunday" -> currentDefaults.sunday.toMutableMap()
            "holiday" -> currentDefaults.holiday.toMutableMap()
            else -> return
        }
        if (count > 0) updatedMap[shiftTypeId] = count else updatedMap.remove(shiftTypeId)
        val newDefaults = when(dayType) {
            "weekday" -> currentDefaults.copy(weekday = updatedMap)
            "saturday" -> currentDefaults.copy(saturday = updatedMap)
            "sunday" -> currentDefaults.copy(sunday = updatedMap)
            "holiday" -> currentDefaults.copy(holiday = updatedMap)
            else -> currentDefaults
        }
        _uiState.update { it.copy(manpowerPlan = currentPlan.copy(requirementDefaults = newDefaults)) }
    }

    fun applyDefaultsAndProceed() {
        // ... (保持不變) ...
        val currentPlan = _uiState.value.manpowerPlan ?: return
        val defaults = currentPlan.requirementDefaults
        val datesInMonth = DateUtils.getDatesInMonth(month)
        val holidays = _uiState.value.holidays // ✅ 使用 ViewModel 中的假日資料

        val dailyRequirements = datesInMonth.associate { date ->
            val day = date.split("-").last()
            val dayOfWeek = DateUtils.getDayOfWeek(date)

            val requirementsTemplate = when {
                holidays.containsKey(date) -> defaults.holiday
                dayOfWeek == 6 -> defaults.saturday
                dayOfWeek == 0 -> defaults.sunday
                else -> defaults.weekday
            }

            day to DailyRequirement(
                date = date,
                isHoliday = holidays.containsKey(date),
                holidayName = holidays[date],
                requirements = requirementsTemplate
            )
        }
        _uiState.update {
            it.copy(
                manpowerPlan = currentPlan.copy(dailyRequirements = dailyRequirements),
                currentStep = ManpowerStep.DETAILS
            )
        }
    }

    fun returnToDefaults() {
        // ... (保持不變) ...
        _uiState.update { it.copy(currentStep = ManpowerStep.DEFAULTS) }
    }

    fun updateRequirement(day: String, shiftTypeId: String, count: Int) {
        // ... (保持不變) ...
        val currentPlan = _uiState.value.manpowerPlan ?: return
        val updatedDailyReqs = currentPlan.dailyRequirements.toMutableMap()
        val currentDaily = updatedDailyReqs[day] ?: DailyRequirement()
        val updatedReqs = currentDaily.requirements.toMutableMap()
        if (count > 0) updatedReqs[shiftTypeId] = count else updatedReqs.remove(shiftTypeId)
        updatedDailyReqs[day] = currentDaily.copy(requirements = updatedReqs)
        _uiState.update { it.copy(manpowerPlan = currentPlan.copy(dailyRequirements = updatedDailyReqs)) }
    }

    // ✅ 4. 修改 savePlan 函式
    fun savePlan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveResult = null) } // 開始儲存時顯示 Loading 並清除舊結果
            try {
                _uiState.value.manpowerPlan?.let {
                    val planToSave = it.copy(updatedAt = Date())
                    val result = repository.saveManpowerPlan(orgId, planToSave)
                    _uiState.update { state -> state.copy(isLoading = false, saveResult = result) } // 更新儲存結果
                    if (result.isSuccess) {
                        _saveSuccessEvent.emit(Unit) // ✅ 5. 發送成功事件
                    }
                } ?: run {
                    // 如果 manpowerPlan 為 null，更新為失敗狀態
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            saveResult = Result.failure(IllegalStateException("Manpower plan is null"))
                        )
                    }
                }
            } catch (e: Exception) {
                // 處理可能的協程異常
                _uiState.update { state ->
                    state.copy(isLoading = false, saveResult = Result.failure(e))
                }
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲