// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    val showHolidayNameDialogFor: String? = null
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

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 並行載入所有需要的初始資料
            val groupData = repository.observeGroup(groupId).first()
            val shiftTypesData = repository.observeShiftTypes(orgId, groupId).first()

            // ✅ 核心修改：使用 .firstOrNull() 來獲取一次性資料，避免後續的重複更新
            var planFromDb = repository.observeManpowerPlan(orgId, groupId, month).firstOrNull()

            // 如果資料庫中沒有計畫，則建立一個新的
            if (planFromDb == null) {
                planFromDb = ManpowerPlan(
                    id = "${orgId}_${groupId}_${month}",
                    orgId = orgId,
                    groupId = groupId,
                    month = month
                )
            }

            // 從 API 獲取假日
            val holidaysFromApi = fetchHolidaysFromApi(month)

            _uiState.update {
                it.copy(
                    group = groupData,
                    shiftTypes = shiftTypesData.filter { s -> s.shortCode != "OFF" },
                    manpowerPlan = planFromDb,
                    holidays = holidaysFromApi,
                    isLoading = false
                )
            }
        }
    }

    private fun fetchHolidaysFromApi(month: String): Map<String, String> {
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
        val currentHolidays = _uiState.value.holidays
        if (currentHolidays.containsKey(date)) {
            removeHoliday(date)
        } else {
            _uiState.update { it.copy(showHolidayNameDialogFor = date) }
        }
    }

    fun addHoliday(date: String, name: String) {
        val updatedHolidays = _uiState.value.holidays.toMutableMap()
        updatedHolidays[date] = name.ifBlank { "特殊日" }
        _uiState.update { it.copy(holidays = updatedHolidays, showHolidayNameDialogFor = null) }
    }

    fun removeHoliday(date: String) {
        val updatedHolidays = _uiState.value.holidays.toMutableMap()
        updatedHolidays.remove(date)
        _uiState.update { it.copy(holidays = updatedHolidays) }
    }

    fun dismissHolidayNameDialog() {
        _uiState.update { it.copy(showHolidayNameDialogFor = null) }
    }

    fun updateDefaultRequirement(dayType: String, shiftTypeId: String, count: Int) {
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
        _uiState.update { it.copy(currentStep = ManpowerStep.DEFAULTS) }
    }

    fun updateRequirement(day: String, shiftTypeId: String, count: Int) {
        val currentPlan = _uiState.value.manpowerPlan ?: return
        val updatedDailyReqs = currentPlan.dailyRequirements.toMutableMap()
        val currentDaily = updatedDailyReqs[day] ?: DailyRequirement()
        val updatedReqs = currentDaily.requirements.toMutableMap()
        if (count > 0) updatedReqs[shiftTypeId] = count else updatedReqs.remove(shiftTypeId)
        updatedDailyReqs[day] = currentDaily.copy(requirements = updatedReqs)
        _uiState.update { it.copy(manpowerPlan = currentPlan.copy(dailyRequirements = updatedDailyReqs)) }
    }

    fun savePlan() {
        viewModelScope.launch {
            _uiState.value.manpowerPlan?.let {
                val planToSave = it.copy(updatedAt = Date())
                repository.saveManpowerPlan(orgId, planToSave)
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲