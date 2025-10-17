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
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import stevedaydream.scheduler.domain.scheduling.RuleEngine
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.util.DateUtils
import java.util.Date
import javax.inject.Inject

data class ReservationConflict(
    val date: String,
    val message: String
)

data class ReservationSaveSummary(
    val manpowerViolations: List<String>,
    val ruleViolations: List<String>,
    val usersToCoordinate: Set<String>
)

data class ShiftReservationUiState(
    val isLoading: Boolean = true,
    val month: String,
    val users: List<User> = emptyList(),
    val shiftTypes: List<ShiftType> = emptyList(),
    val manpowerPlan: ManpowerPlan? = null,
    val allReservations: List<Reservation> = emptyList(),
    val myReservation: Reservation? = null,
    val isSaving: Boolean = false,
    val instantConflict: ReservationConflict? = null,
    val saveSummary: ReservationSaveSummary? = null
)

@HiltViewModel
class ShiftReservationViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orgId: String = savedStateHandle.get<String>("orgId")!!
    private val groupId: String = savedStateHandle.get<String>("groupId")!!
    private val month: String = savedStateHandle.get<String>("month")!!
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    val currentUserId: String? = auth.currentUser?.uid
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    private val _uiState = MutableStateFlow(ShiftReservationUiState(month = month))
    val uiState: StateFlow<ShiftReservationUiState> = _uiState.asStateFlow()

    private val ruleEngine = RuleEngine(
        listOf(MaxConsecutiveWorkDaysRule(), MinRestBetweenShiftsRule(), NightShiftFollowupRule())
    )

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 監聽所有需要的資料流
            combine(
                repository.observeGroup(groupId).filterNotNull(),
                repository.observeShiftTypes(orgId, groupId),
                repository.observeReservations(orgId, groupId, month),
                repository.getManpowerPlanOnce(orgId, groupId, month).let { flowOf(it) }
            ) { group, shiftTypes, reservations, plan ->
                // 在 combine 內處理使用者列表的載入
                val userIds = group.memberIds
                val users = repository.observeUsers(orgId).first().filter { it.id in userIds }
                Triple(Triple(group, shiftTypes, users), reservations, plan)
            }.collect { (groupData, reservations, plan) ->
                val (_, shiftTypes, users) = groupData
                val currentUser = users.find { it.id == auth.currentUser?.uid }
                val myReservation = reservations.find { it.userId == auth.currentUser?.uid }
                    ?: Reservation(
                        orgId = orgId,
                        groupId = groupId,
                        month = month,
                        userId = currentUser?.id ?: "",
                        userName = currentUser?.name ?: ""
                    )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        users = users,
                        shiftTypes = shiftTypes,
                        manpowerPlan = plan,
                        allReservations = reservations,
                        myReservation = myReservation
                    )
                }
            }
        }
    }

    fun onCellClicked(day: String, shiftId: String) {
        val currentMyReservation = _uiState.value.myReservation ?: return
        val updatedShifts = currentMyReservation.dailyShifts.toMutableMap()

        // 如果點擊相同班別，則取消預約
        if (updatedShifts[day] == shiftId) {
            updatedShifts.remove(day)
        } else {
            updatedShifts[day] = shiftId
        }

        val updatedReservation = currentMyReservation.copy(dailyShifts = updatedShifts)
        _uiState.update { it.copy(myReservation = updatedReservation) }

        // 進行即時衝突檢查
        checkForInstantConflict(day, shiftId)
    }

    private fun checkForInstantConflict(day: String, shiftId: String) {
        val plan = _uiState.value.manpowerPlan ?: return
        val allReservations = _uiState.value.allReservations
        val shiftTypes = _uiState.value.shiftTypes

        // 1. 檢查人力配置
        val requiredCount = plan.dailyRequirements[day]?.requirements?.get(shiftId) ?: 0
        val reservedCount = allReservations.count { it.dailyShifts[day] == shiftId }
        val myCurrentShift = _uiState.value.myReservation?.dailyShifts?.get(day)

        // 如果我正要預約這個班，已預約人數就 +1
        val finalReservedCount = if (myCurrentShift == shiftId) reservedCount + 1 else reservedCount

        if (finalReservedCount > requiredCount) {
            val shiftName = shiftTypes.find { it.id == shiftId }?.name ?: "該班別"
            _uiState.update {
                it.copy(instantConflict = ReservationConflict(day, "提醒：${shiftName}預約人數已達 ${finalReservedCount} 人，超過人力規劃的 ${requiredCount} 人。"))
            }
            return
        }

        // 2. (可選) 檢查是否有人已選相同班別
        // 這個邏輯已包含在上面的人力配置檢查中

        _uiState.update { it.copy(instantConflict = null) }
    }

    fun dismissInstantConflict() {
        _uiState.update { it.copy(instantConflict = null) }
    }

    fun saveReservation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val reservationToSave = _uiState.value.myReservation?.copy(updatedAt = Date()) ?: return@launch

            // 先儲存至 Firebase
            repository.saveReservation(orgId, reservationToSave).onSuccess {
                // 儲存成功後，進行最終的衝突分析
                performFinalConflictAnalysis()
            }.onFailure {
                // 處理儲存失敗
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun performFinalConflictAnalysis() {
        // 重新獲取最新的預約資料
        val latestReservations = repository.observeReservations(orgId, groupId, month).first()
        val plan = _uiState.value.manpowerPlan
        val rules = repository.observeSchedulingRules(orgId, groupId).first().filter { it.isEnabled }
        val shiftTypes = _uiState.value.shiftTypes
        val users = _uiState.value.users

        val manpowerViolations = mutableListOf<String>()
        val ruleViolations = mutableListOf<String>()
        val usersToCoordinate = mutableSetOf<String>()

        // 1. 檢查整月人力配置衝突
        if (plan != null) {
            plan.dailyRequirements.forEach { (day, dailyReq) ->
                dailyReq.requirements.forEach { (shiftId, requiredCount) ->
                    val reservedCount = latestReservations.count { it.dailyShifts[day] == shiftId }
                    if (reservedCount > requiredCount) {
                        val shiftName = shiftTypes.find { it.id == shiftId }?.name ?: ""
                        val conflictingUsers = latestReservations
                            .filter { it.dailyShifts[day] == shiftId }
                            .map { it.userName }

                        manpowerViolations.add("${month}-${day} 的 ${shiftName} 超出 ${reservedCount - requiredCount} 人力。")
                        usersToCoordinate.addAll(conflictingUsers)
                    }
                }
            }
        }

        // 2. 檢查個人排班規則衝突
        latestReservations.forEach { reservation ->
            val user = users.find { it.id == reservation.userId }
            if (user != null) {
                val assignment = Assignment(dailyShifts = reservation.dailyShifts)
                val violations = ruleEngine.validate(user, assignment, shiftTypes, rules)
                if (violations.isNotEmpty()) {
                    ruleViolations.addAll(violations.map { it.message })
                    usersToCoordinate.add(user.name)
                }
            }
        }

        _uiState.update {
            it.copy(
                isSaving = false,
                saveSummary = ReservationSaveSummary(
                    manpowerViolations = manpowerViolations,
                    ruleViolations = ruleViolations,
                    usersToCoordinate = usersToCoordinate
                )
            )
        }
    }

    fun dismissSummaryDialog() {
        _uiState.update { it.copy(saveSummary = null) }
    }
}