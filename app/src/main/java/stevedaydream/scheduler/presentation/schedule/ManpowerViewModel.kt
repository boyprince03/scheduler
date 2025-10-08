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

data class ManpowerUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val shiftTypes: List<ShiftType> = emptyList(),
    val manpowerPlan: ManpowerPlan? = null,
    // 國定假日資訊 (Key: YYYY-MM-dd)
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
        viewModelScope.launch {
            // 平行載入所有需要的資料
            launch {
                repository.observeGroup(groupId).collect { group ->
                    _uiState.update { it.copy(group = group) }
                }
            }
            launch {
                repository.observeShiftTypes(orgId, groupId).collect { shiftTypes ->
                    _uiState.update { it.copy(shiftTypes = shiftTypes.filter { it.shortCode != "OFF" }) }
                }
            }
            launch {
                // TODO: 在此處呼叫 API 或從本地資料庫讀取國定假日
                // 暫時使用假資料
                val holidays = mapOf(
                    "2025-10-10" to "國慶日",
                    "2025-10-25" to "台灣光復節"
                )
                _uiState.update { it.copy(holidays = holidays) }
            }
            launch {
                repository.observeManpowerPlan(orgId, groupId, month).collect { plan ->
                    val finalPlan = plan ?: createInitialPlan()
                    _uiState.update { it.copy(manpowerPlan = finalPlan, isLoading = false) }
                }
            }
        }
    }

    private fun createInitialPlan(): ManpowerPlan {
        val datesInMonth = DateUtils.getDatesInMonth(month)
        val holidays = _uiState.value.holidays
        val dailyRequirements = datesInMonth.associate { date ->
            val day = date.split("-").last()
            day to DailyRequirement(
                date = date,
                isHoliday = holidays.containsKey(date),
                holidayName = holidays[date]
            )
        }
        return ManpowerPlan(
            id = "${orgId}_${groupId}_${month}",
            orgId = orgId,
            groupId = groupId,
            month = month,
            dailyRequirements = dailyRequirements
        )
    }

    fun updateRequirement(day: String, shiftTypeId: String, count: Int) {
        val currentPlan = _uiState.value.manpowerPlan ?: return
        val updatedDailyReqs = currentPlan.dailyRequirements.toMutableMap()
        val currentDaily = updatedDailyReqs[day] ?: DailyRequirement()
        val updatedReqs = currentDaily.requirements.toMutableMap()

        if (count > 0) {
            updatedReqs[shiftTypeId] = count
        } else {
            updatedReqs.remove(shiftTypeId)
        }

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