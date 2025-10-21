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
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule // 保持 import
import stevedaydream.scheduler.domain.scheduling.RuleEngine
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule // 保持 import
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule // 保持 import
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
    // myReservation 的 dailyShifts 現在是 Map<String, List<String>>
    val myReservation: Reservation? = null,
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

            combine(
                repository.observeGroup(groupId).filterNotNull(),
                repository.observeShiftTypes(orgId, groupId),
                repository.observeReservations(orgId, groupId, month), // Reservation 現在有 List<String>
                repository.getManpowerPlanOnce(orgId, groupId, month).let { flowOf(it) },
                repository.observeRotationSchedule(orgId, groupId, month)
            ) { group, shiftTypes, reservations, plan, rotationSched ->
                val userIds = group.memberIds
                val users = repository.observeUsers(orgId).first().filter { it.id in userIds }
                Triple(Triple(group, shiftTypes, users), reservations, Pair(plan, rotationSched))
            }.collect { (groupData, reservations, planAndRotationData) ->
                val (_, shiftTypes, users) = groupData
                val (plan, rotationSched) = planAndRotationData
                val currentUser = users.find { it.id == auth.currentUser?.uid }
                // 處理 myReservation，確保 dailyShifts 是 Map<String, List<String>>
                val myReservation = reservations.find { it.userId == auth.currentUser?.uid }
                    ?: Reservation( // 新建時 dailyShifts 也是 Map<String, List<String>>
                        orgId = orgId,
                        groupId = groupId,
                        month = month,
                        userId = currentUser?.id ?: "",
                        userName = currentUser?.name ?: "",
                        dailyShifts = emptyMap() // 初始為空 Map
                    )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        users = users,
                        shiftTypes = shiftTypes,
                        manpowerPlan = plan,
                        allReservations = reservations,
                        myReservation = myReservation,
                        rotationSchedule = rotationSched
                    )
                }
            }
        }
    }

    /**
     * 修改 onCellClicked 邏輯以處理偏好列表 (簡化版)
     */
    fun onCellClicked(day: String, shiftId: String) {
        val currentMyReservation = _uiState.value.myReservation ?: return
        val rotationShift = _uiState.value.rotationSchedule[currentMyReservation.userId]?.get(day)

        // 如果點擊的格子是系統預排的輪班，則不允許修改
        if (rotationShift != null) {
            return
        }

        val offShift = _uiState.value.shiftTypes.find { it.shortCode == "OFF" }
        val updatedShifts = currentMyReservation.dailyShifts.toMutableMap()
        val currentPreferences = updatedShifts[day] ?: emptyList()

        // 簡化邏輯：點擊不同班別則替換，點擊相同班別則取消
        if (currentPreferences.firstOrNull() == shiftId) {
            // 如果點擊的是目前唯一的偏好，則移除偏好
            updatedShifts.remove(day)
        } else {
            // 否則，將偏好列表設為只包含點擊的班別
            updatedShifts[day] = listOf(shiftId)
        }

        val updatedReservation = currentMyReservation.copy(dailyShifts = updatedShifts)
        _uiState.update { it.copy(myReservation = updatedReservation) }

        // 即時衝突檢查仍然基於單一班別的可能性
        checkForInstantConflict(day, shiftId)
    }


    // checkForInstantConflict: 邏輯不變，檢查的是單一班別的人力
    private fun checkForInstantConflict(day: String, shiftIdToCheck: String) {
        val plan = _uiState.value.manpowerPlan ?: return
        // 注意：allReservations 裡面的 dailyShifts 也是 Map<String, List<String>>
        // 但檢查人力時，我們只關心這個班別是否出現在偏好中 (假設只預約一種)
        val allReservations = _uiState.value.allReservations
        val shiftTypes = _uiState.value.shiftTypes

        // 檢查人力配置
        val requiredCount = plan.dailyRequirements[day]?.requirements?.get(shiftIdToCheck) ?: 0
        // 計算有多少人的偏好列表包含 shiftIdToCheck (簡化：只看第一個)
        val reservedCount = allReservations.count {
            it.dailyShifts[day]?.firstOrNull() == shiftIdToCheck
        }

        val myCurrentPref = _uiState.value.myReservation?.dailyShifts?.get(day)?.firstOrNull()
        val finalReservedCount = when {
            myCurrentPref != shiftIdToCheck && updatedShifts[day]?.firstOrNull() == shiftIdToCheck -> reservedCount + 1 // 原本不是，改成是
            myCurrentPref == shiftIdToCheck && updatedShifts[day]?.firstOrNull() != shiftIdToCheck -> reservedCount - 1 // 原本是，改成不是
            else -> reservedCount // 沒變或原本就不是
        }

        if (finalReservedCount > requiredCount) {
            val shiftName = shiftTypes.find { it.id == shiftIdToCheck }?.name ?: "該班別"
            _uiState.update {
                it.copy(instantConflict = ReservationConflict(day, "提醒：${shiftName}預約人數可能達到 ${finalReservedCount} 人，超過人力規劃的 ${requiredCount} 人。"))
            }
            return
        }
        _uiState.update { it.copy(instantConflict = null) }
    }


    // dismissInstantConflict 保持不變
    fun dismissInstantConflict() {
        _uiState.update { it.copy(instantConflict = null) }
    }

    // saveReservation: 不需修改，Firestore 會處理 List<String>
    fun saveReservation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val reservationToSave = _uiState.value.myReservation?.copy(updatedAt = Date()) ?: return@launch
            repository.saveReservation(orgId, reservationToSave).onSuccess {
                performFinalConflictAnalysis()
            }.onFailure {
                _uiState.update { it.copy(isSaving = false) } // 儲存失敗也要結束 Saving 狀態
            }
        }
    }

    // performFinalConflictAnalysis: 分析的是最終 Assignment (Map<String, String>)，邏輯不變
    private suspend fun performFinalConflictAnalysis() {
        // ... (這部分邏輯不變，因為分析的是 Assignment 而不是 Reservation) ...
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
                    // 這裡仍然需要根據 reservation 的數據來預估衝突
                    // (假設使用者預約的第一個班別就是他最想要的)
                    val reservedCount = latestReservations.count { it.dailyShifts[day]?.firstOrNull() == shiftId }
                    if (reservedCount > requiredCount) {
                        val shiftName = shiftTypes.find { it.id == shiftId }?.name ?: ""
                        val conflictingUsers = latestReservations
                            .filter { it.dailyShifts[day]?.firstOrNull() == shiftId }
                            .map { it.userName }
                        manpowerViolations.add("${month}-${day} 的 ${shiftName} 超出 ${reservedCount - requiredCount} 人力。")
                        usersToCoordinate.addAll(conflictingUsers)
                    }
                }
            }
        }

        // 規則衝突分析也應該基於可能的排班結果，預約只是輸入之一
        // 這裡的簡易分析可能不完全準確，因為最終排班還會考慮其他因素
        latestReservations.forEach { reservation ->
            val user = users.find { it.id == reservation.userId }
            if (user != null) {
                // 創建一個基於預約第一偏好的假 Assignment 進行檢查
                val tempDailyShifts = reservation.dailyShifts.mapValues { it.value.firstOrNull() ?: "" }.filterValues { it.isNotEmpty() }
                if (tempDailyShifts.isNotEmpty()) {
                    val assignment = Assignment(dailyShifts = tempDailyShifts)
                    val violations = ruleEngine.validate(user, assignment, shiftTypes, rules)
                    if (violations.isNotEmpty()) {
                        ruleViolations.addAll(violations.map { it.message })
                        usersToCoordinate.add(user.name)
                    }
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

    // Helper: Get updated shifts map (used internally by checkForInstantConflict)
    private val updatedShifts: Map<String, List<String>>
        get() = _uiState.value.myReservation?.dailyShifts ?: emptyMap()

}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲