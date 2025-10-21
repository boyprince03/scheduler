// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/schedule/ShiftReservationViewModel.kt
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

// ... (ReservationConflict, ReservationSaveSummary data classes 保持不變) ...
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
    // 新增：儲存預排輪班資料 Map<UserId, Map<Day, ShiftId>>
    val rotationSchedule: Map<String, Map<String, String>> = emptyMap(),
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
    val currentUserId: String? = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(ShiftReservationUiState(month = month))
    val uiState: StateFlow<ShiftReservationUiState> = _uiState.asStateFlow()

    // RuleEngine 保持不變
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
                repository.getManpowerPlanOnce(orgId, groupId, month).let { flowOf(it) },
                repository.observeRotationSchedule(orgId, groupId, month) // <<-- 新增觀察 RotationSchedule
            ) { group, shiftTypes, reservations, plan, rotationSched -> // <<-- 加入 rotationSched
                // 在 combine 內處理使用者列表的載入
                val userIds = group.memberIds
                // 確保先取得 User 列表再來處理
                val users = repository.observeUsers(orgId).first().filter { it.id in userIds }
                // 將所有需要更新的資料包裝起來
                Triple(Triple(group, shiftTypes, users), reservations, Pair(plan, rotationSched))
            }.collect { (groupData, reservations, planAndRotationData) ->
                val (_, shiftTypes, users) = groupData
                val (plan, rotationSched) = planAndRotationData // <<-- 解開 plan 和 rotationSched
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
                        myReservation = myReservation,
                        rotationSchedule = rotationSched // <<-- 更新 rotationSchedule 狀態
                    )
                }
            }
        }
    }

    // onCellClicked 修改：檢查是否為預排班
    fun onCellClicked(day: String, shiftId: String) {
        val currentMyReservation = _uiState.value.myReservation ?: return
        val rotationShift = _uiState.value.rotationSchedule[currentMyReservation.userId]?.get(day)

        // 如果點擊的格子是系統預排的輪班，則不允許修改
        if (rotationShift != null) {
            // 可以考慮發出提示訊息，告知使用者此為預排班
            return
        }

        // --- 以下邏輯與之前相同 ---
        val updatedShifts = currentMyReservation.dailyShifts.toMutableMap()
        if (updatedShifts[day] == shiftId) {
            updatedShifts.remove(day)
        } else {
            updatedShifts[day] = shiftId
        }
        val updatedReservation = currentMyReservation.copy(dailyShifts = updatedShifts)
        _uiState.update { it.copy(myReservation = updatedReservation) }
        checkForInstantConflict(day, shiftId)
    }

    // checkForInstantConflict 保持不變
    private fun checkForInstantConflict(day: String, shiftId: String) {
        val plan = _uiState.value.manpowerPlan ?: return
        val allReservations = _uiState.value.allReservations // 只考慮使用者預約
        val shiftTypes = _uiState.value.shiftTypes

        // 檢查人力配置 (只計算使用者預約的部分)
        val requiredCount = plan.dailyRequirements[day]?.requirements?.get(shiftId) ?: 0
        val reservedCount = allReservations.count { it.dailyShifts[day] == shiftId }
        val myCurrentShift = _uiState.value.myReservation?.dailyShifts?.get(day)
        val finalReservedCount = if (myCurrentShift == shiftId) reservedCount + 1 else reservedCount // 計算如果我預約下去的總數

        if (finalReservedCount > requiredCount) {
            val shiftName = shiftTypes.find { it.id == shiftId }?.name ?: "該班別"
            _uiState.update {
                it.copy(instantConflict = ReservationConflict(day, "提醒：${shiftName}預約人數已達 ${finalReservedCount} 人，超過人力規劃的 ${requiredCount} 人。"))
            }
            return
        }
        _uiState.update { it.copy(instantConflict = null) }
    }


    // dismissInstantConflict 保持不變
    fun dismissInstantConflict() {
        _uiState.update { it.copy(instantConflict = null) }
    }

    // saveReservation 保持不變
    fun saveReservation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val reservationToSave = _uiState.value.myReservation?.copy(updatedAt = Date()) ?: return@launch
            repository.saveReservation(orgId, reservationToSave).onSuccess {
                performFinalConflictAnalysis()
            }.onFailure {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // performFinalConflictAnalysis 保持不變 (只分析使用者預約)
    private suspend fun performFinalConflictAnalysis() {
        val latestReservations = repository.observeReservations(orgId, groupId, month).first()
        val plan = _uiState.value.manpowerPlan
        val rules = repository.observeSchedulingRules(orgId, groupId).first().filter { it.isEnabled }
        val shiftTypes = _uiState.value.shiftTypes
        val users = _uiState.value.users

        val manpowerViolations = mutableListOf<String>()
        val ruleViolations = mutableListOf<String>()
        val usersToCoordinate = mutableSetOf<String>()

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

        latestReservations.forEach { reservation ->
            val user = users.find { it.id == reservation.userId }
            if (user != null) {
                // 注意：這裡只驗證了使用者自己預約的部分，沒有考慮 rotationSchedule
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

    // dismissSummaryDialog 保持不變
    fun dismissSummaryDialog() {
        _uiState.update { it.copy(saveSummary = null) }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲