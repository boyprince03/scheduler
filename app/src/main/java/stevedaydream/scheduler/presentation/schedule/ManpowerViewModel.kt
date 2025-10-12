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
    DEFAULTS, // 步驟一：設定範本
    DETAILS   // 步驟二：微調細節
}

data class ManpowerUiState(
    val isLoading: Boolean = true,
    val currentStep: ManpowerStep = ManpowerStep.DEFAULTS,
    val group: Group? = null,
    val shiftTypes: List<ShiftType> = emptyList(),
    val manpowerPlan: ManpowerPlan? = null,
    val holidays: Map<String, String> = emptyMap()
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
        // 先載入群組、班別、假日等靜態資料
        viewModelScope.launch {
            repository.observeGroup(groupId).collect { group ->
                _uiState.update { it.copy(group = group) }
            }
        }
        viewModelScope.launch {
            repository.observeShiftTypes(orgId, groupId).collect { shiftTypes ->
                _uiState.update { it.copy(shiftTypes = shiftTypes.filter { s -> s.shortCode != "OFF" }) }
            }
        }
        viewModelScope.launch {
            val holidays = mapOf("2025-10-10" to "國慶日", "2025-10-25" to "台灣光復節")
            _uiState.update { it.copy(holidays = holidays) }
        }

        // ✅ ===== 修正點 =====
        // 核心邏輯：處理 ManpowerPlan 的初始化
        viewModelScope.launch {
            // 持續監聽資料庫的變化
            repository.observeManpowerPlan(orgId, groupId, month).collect { planFromDb ->
                val currentState = _uiState.value

                if (currentState.isLoading) {
                    // 這是第一次載入
                    val planToSet = planFromDb ?: ManpowerPlan(
                        id = "${orgId}_${groupId}_${month}",
                        orgId = orgId,
                        groupId = groupId,
                        month = month
                    )
                    _uiState.update { it.copy(manpowerPlan = planToSet, isLoading = false) }
                } else {
                    // 這不是第一次載入，代表是儲存後 Firestore 的更新回饋
                    // 我們只在 planFromDb 不是 null 的情況下更新畫面，避免意外清除
                    if (planFromDb != null) {
                        _uiState.update { it.copy(manpowerPlan = planFromDb) }
                    }
                }
            }
        }
    }

    fun updateDefaultRequirement(dayType: String, shiftTypeId: String, count: Int) {
        // 在更新前，確保 manpowerPlan 物件已存在
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
        val holidays = _uiState.value.holidays

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