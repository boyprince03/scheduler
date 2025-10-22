// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/schedule/InteractiveScheduleViewModel.kt
package stevedaydream.scheduler.presentation.schedule

import android.util.Log // 引入 Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.domain.scheduling.FillingStepResult
import stevedaydream.scheduler.domain.scheduling.InteractiveFiller // 引入 InteractiveFiller
import stevedaydream.scheduler.domain.scheduling.RotationRuleConfig
import stevedaydream.scheduler.domain.scheduling.RotationScheduler
import stevedaydream.scheduler.domain.scheduling.RuleEngine
import stevedaydream.scheduler.domain.scheduling.ScheduleInitializer
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData // Alias for clarity


// --- Data State ---
data class InteractiveScheduleUiState(
    val isLoading: Boolean = true,
    val currentStep: ScheduleStep = ScheduleStep.INITIALIZING,
    val month: String = "",
    // val initialAssignments: Map<String, Map<String, String>> = emptyMap(), // From Initializer - Can be removed if not needed directly
    val currentAssignments: Map<String, Map<String, String>> = emptyMap(), // Being reviewed/edited
    val remainingWorkQuotas: Map<String, Map<String, Int>> = emptyMap(),
    val remainingOffQuota: Map<String, Int> = emptyMap(),
    val users: List<User> = emptyList(), // Only users in the group
    val orderedUsers: List<User> = emptyList(), // For hospital strategy
    val shiftTypes: List<ShiftType> = emptyList(),
    val offShift: ShiftType? = null,
    val nShift: ShiftType? = null,
    val dShift: ShiftType? = null,
    val sShift: ShiftType? = null,
    val rules: List<SchedulingRuleData> = emptyList(), // All rules (enabled/disabled)
    val enabledHardRules: List<SchedulingRuleData> = emptyList(), // Pre-filtered for checks
    val enabledSoftRules: List<SchedulingRuleData> = emptyList(), // Pre-filtered for calculation
    val manpowerPlan: ManpowerPlan? = null,
    val requests: List<Request> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
    val preScheduledRotations: Map<String, Map<String, String>> = emptyMap(), // For hospital strategy
    val userShiftCounts: Map<String, Map<String, Int>> = emptyMap(), // Calculated stats
    val softRuleViolations: List<RuleViolation> = emptyList(), // Calculated violations
    val hardRuleViolationsInStep: List<String> = emptyList(), // Violations found during filling step
    val manualEditViolation: String? = null, // Temporary message for failed manual edit
    val errorMessage: String? = null, // For major errors (init, save)
    val saveResult: Result<Unit>? = null // For final save
)

// --- ViewModel ---
@HiltViewModel
class InteractiveScheduleViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth,
    private val scheduleInitializer: ScheduleInitializer,
    private val ruleEngine: RuleEngine,
    private val interactiveFiller: InteractiveFiller, // <-- 注入 InteractiveFiller
    private val rotationScheduler: RotationScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val TAG = "InteractiveScheduleVM" // For logging

    val orgId: String = savedStateHandle.get<String>("orgId")!!
    val groupId: String = savedStateHandle.get<String>("groupId")!!
    private val month: String = savedStateHandle.get<String>("month")!!

    private val _uiState = MutableStateFlow(InteractiveScheduleUiState(month = month))
    val uiState: StateFlow<InteractiveScheduleUiState> = _uiState.asStateFlow()

    // --- Initialization ---
    init {
        loadInitialData()
    }

    // 修改 loadInitialData 以處理 FillStepResult 並觸發下一步
    private fun loadInitialData() {
        viewModelScope.launch {
            // ... (設定 isLoading, 抓取 group, users, shiftTypes, rules, manpowerPlan, requests, reservations 等) ...
            try {
                // <<< 新增：在 Initializer 之前計算輪替 >>>
                val group = repository.observeGroup(groupId).filterNotNull().first()
                val allUsersInOrg = repository.observeUsers(orgId).first()
                val groupUsers = allUsersInOrg.filter { it.id in group.memberIds }
                val userMap = groupUsers.associateBy { it.id }
                val orderedGroupUsers = group.userOrder?.mapNotNull { userMap[it] } ?: groupUsers
                val shiftTypes = repository.observeShiftTypes(orgId, groupId).first()
                // 讀取輪替設定
                val rotationSettingsContainer = repository.observeRotationSettings(orgId, groupId).first()
                val rotationRulesConfig = rotationSettingsContainer?.rules?.mapValues { (shiftId, setting) ->
                    RotationRuleConfig(shiftTypeId = shiftId, daysOfWeek = setting.daysOfWeek)
                } ?: emptyMap()
                val initialRotationState = group.rotationState ?: emptyMap()

                // 計算輪替班表
                val rotationResult = rotationScheduler.calculateRotations(
                    month = month,
                    orderedUsers = orderedGroupUsers,
                    shiftTypes = shiftTypes,
                    rotationRules = rotationRulesConfig,
                    initialRotationState = initialRotationState
                )
                val calculatedRotations = rotationResult.preScheduledRotations
                Log.d(TAG, "Rotation calculation complete. Found ${calculatedRotations.values.sumOf { it.size }} rotation shifts.")
                // <<< 新增結束 >>>


                // ... (其他資料抓取) ...
                val offShift = shiftTypes.find { it.shortCode == "OFF" }
                val manpowerPlan = repository.getManpowerPlanOnce(orgId, groupId, month)
                val requests = repository.observeRequests(orgId).first()
                val reservations = repository.observeReservations(orgId, groupId, month).first()
                val rules = repository.observeSchedulingRules(orgId, groupId).first()
                val enabledHardRules = rules.filter { it.isEnabled && it.ruleType == "hard" }
                val enabledSoftRules = rules.filter { it.isEnabled && it.ruleType == "soft" }

                if (offShift == null || manpowerPlan == null) throw IllegalStateException("缺少 OFF 班別或人力規劃")
                if (groupUsers.isEmpty()) throw IllegalStateException("群組內沒有成員")


                Log.d(TAG, "Data fetched. Calling Initializer...")
                // ✅ 將計算好的輪替傳給 Initializer
                val initResult = scheduleInitializer.initializeSchedule(
                    allUsers = groupUsers,
                    orderedUsers = orderedGroupUsers,
                    shiftTypes = shiftTypes,
                    requests = requests,
                    reservations = reservations,
                    dbRules = enabledHardRules + enabledSoftRules,
                    manpowerPlan = manpowerPlan,
                    offShift = offShift,
                    dates = DateUtils.getDatesInMonth(month),
                    preScheduledRotations = calculatedRotations // <<< 傳入計算結果
                )
                Log.d(TAG, "Initializer completed. Initial hard violations: ${initResult.initialViolations.size}")

                _uiState.update {
                    it.copy(
                        // ... (儲存其他 fetched data) ...
                        users = groupUsers,
                        orderedUsers = orderedGroupUsers,
                        shiftTypes = shiftTypes,
                        offShift = offShift,
                        nShift = shiftTypes.find { s -> s.name == "值班(夜)" },
                        dShift = shiftTypes.find { s -> s.name == "值班(日)" },
                        sShift = shiftTypes.find { s -> s.name == "白班" },
                        rules = rules,
                        enabledHardRules = enabledHardRules,
                        enabledSoftRules = enabledSoftRules,
                        manpowerPlan = manpowerPlan,
                        requests = requests,
                        reservations = reservations,
                        preScheduledRotations = calculatedRotations, // <<< 在 State 中也儲存一份 (可選)

                        // Store results from Initializer
                        currentAssignments = initResult.initialAssignments.mapValues { e -> e.value.toMap() },
                        remainingWorkQuotas = initResult.remainingWorkQuotas.mapValues { e -> e.value.toMap() },
                        remainingOffQuota = initResult.remainingOffQuota.toMap(),
                        hardRuleViolationsInStep = initResult.initialViolations.toList(),
                        errorMessage = null
                    )
                }
                // Directly proceed to the first filling step AFTER state update
                proceedToNextStep()

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = ScheduleStep.ERROR,
                        errorMessage = "初始化失敗: ${e.message}"
                    )
                }
            }
        }
    }

    // --- Step Progression ---
    private fun proceedToNextStep() {
        val nextStep = when (_uiState.value.currentStep) {
            ScheduleStep.INITIALIZING -> ScheduleStep.FILLING_N
            ScheduleStep.REVIEWING_N -> ScheduleStep.FILLING_D
            ScheduleStep.REVIEWING_D -> ScheduleStep.FILLING_S
            ScheduleStep.REVIEWING_S -> ScheduleStep.FILLING_OFF
            ScheduleStep.REVIEWING_OFF -> ScheduleStep.FINALIZING // Trigger final save
            else -> null // Do nothing in other states
        }
        if (nextStep == ScheduleStep.FINALIZING) {
            finalizeSchedule()
        } else if (nextStep != null) {
            fillNextStep(nextStep)
        }
    }

    // 修改 fillNextStep 以調用 InteractiveFiller
    private fun fillNextStep(nextFillingStep: ScheduleStep) {
        _uiState.update { it.copy(isLoading = true, currentStep = nextFillingStep, hardRuleViolationsInStep = emptyList()) } // Clear previous step violations
        Log.d(TAG, "Starting step: $nextFillingStep")

        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val dates = DateUtils.getDatesInMonth(month)
                val result: FillingStepResult // 修正: 變數名稱 fillingStepResult -> result

                when (nextFillingStep) {
                    ScheduleStep.FILLING_N -> {
                        result = interactiveFiller.fillSpecificShift(
                            shiftToFill = currentState.nShift,
                            currentAssignments = currentState.currentAssignments,
                            currentWorkQuotas = currentState.remainingWorkQuotas,
                            currentOffQuota = currentState.remainingOffQuota,
                            dates = dates,
                            manpowerPlan = currentState.manpowerPlan!!, // Assume not null after init check
                            orderedUsers = currentState.orderedUsers, // Use ordered for N
                            allUsers = currentState.users,
                            shiftTypes = currentState.shiftTypes,
                            dbRules = currentState.enabledHardRules, // Only check hard rules during fill
                            offShiftForNFiller = currentState.offShift // Pass OFF for N-shift prefill
                        )
                    }
                    ScheduleStep.FILLING_D -> {
                        result = interactiveFiller.fillSpecificShift(
                            shiftToFill = currentState.dShift,
                            currentAssignments = currentState.currentAssignments,
                            currentWorkQuotas = currentState.remainingWorkQuotas,
                            currentOffQuota = currentState.remainingOffQuota,
                            dates = dates,
                            manpowerPlan = currentState.manpowerPlan!!,
                            orderedUsers = currentState.orderedUsers, // Use ordered for D
                            allUsers = currentState.users,
                            shiftTypes = currentState.shiftTypes,
                            dbRules = currentState.enabledHardRules
                        )
                    }
                    ScheduleStep.FILLING_S -> {
                        result = interactiveFiller.fillSpecificShift(
                            shiftToFill = currentState.sShift,
                            currentAssignments = currentState.currentAssignments,
                            currentWorkQuotas = currentState.remainingWorkQuotas,
                            currentOffQuota = currentState.remainingOffQuota,
                            dates = dates,
                            manpowerPlan = currentState.manpowerPlan!!,
                            orderedUsers = currentState.users, // Use non-ordered for S
                            allUsers = currentState.users,
                            shiftTypes = currentState.shiftTypes,
                            dbRules = currentState.enabledHardRules
                        )
                    }
                    ScheduleStep.FILLING_OFF -> {
                        result = interactiveFiller.fillRemainingWithOff(
                            currentAssignments = currentState.currentAssignments,
                            currentOffQuota = currentState.remainingOffQuota,
                            allUsers = currentState.users,
                            dates = dates,
                            offShift = currentState.offShift!!, // Assume not null
                            shiftTypes = currentState.shiftTypes,
                            dbRules = currentState.enabledHardRules
                        )
                    }
                    else -> {
                        Log.e(TAG, "Invalid filling step: $nextFillingStep")
                        throw IllegalStateException("Invalid filling step")
                    }
                }
                Log.d(TAG, "Step $nextFillingStep completed. Violations found: ${result.violations.size}")

                // Determine the corresponding review step
                val nextReviewStep = when (nextFillingStep) {
                    ScheduleStep.FILLING_N -> ScheduleStep.REVIEWING_N
                    ScheduleStep.FILLING_D -> ScheduleStep.REVIEWING_D
                    ScheduleStep.FILLING_S -> ScheduleStep.REVIEWING_S
                    ScheduleStep.FILLING_OFF -> ScheduleStep.REVIEWING_OFF
                    else -> ScheduleStep.ERROR // Should not happen
                }

                // Update state with results and move to review step
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = nextReviewStep,
                        currentAssignments = result.updatedAssignments,
                        remainingWorkQuotas = result.updatedWorkQuotas,
                        remainingOffQuota = result.updatedOffQuota,
                        hardRuleViolationsInStep = result.violations // Show violations from this step
                    )
                }
                // Calculate stats and SOFT violations for the review step
                calculateStatisticsAndViolations()

            } catch (e: Exception) {
                Log.e(TAG, "Error during step $nextFillingStep", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = ScheduleStep.ERROR,
                        errorMessage = "步驟 $nextFillingStep 失敗: ${e.message}"
                    )
                }
            }
        }
    }

    // --- Calculation ---
    // 實作 calculateStatisticsAndViolations
    private fun calculateStatisticsAndViolations() {
        viewModelScope.launch {
            val assignments = _uiState.value.currentAssignments
            val users = _uiState.value.users
            val shiftTypes = _uiState.value.shiftTypes
            val softRules = _uiState.value.enabledSoftRules // Use pre-filtered soft rules
            val shiftNameMap = shiftTypes.associate { it.id to it.name }

            if (users.isEmpty() || shiftTypes.isEmpty()) {
                Log.w(TAG, "Cannot calculate stats: users or shiftTypes empty.")
                return@launch
            }

            // Calculate shift counts
            val userCounts = mutableMapOf<String, MutableMap<String, Int>>()
            users.forEach { userCounts[it.id] = mutableMapOf() } // Initialize map for all users

            assignments.forEach { (userId, dailyShifts) -> // 修正: 不需使用 currentState
                dailyShifts.values.forEach { shiftId ->
                    val shiftName = shiftNameMap[shiftId] ?: "未知"
                    userCounts[userId]?.let { counts ->
                        counts[shiftName] = (counts[shiftName] ?: 0) + 1
                    }
                }
            }

            // Calculate soft rule violations
            val softViolationsResult = mutableListOf<RuleViolation>()
            users.forEach { user ->
                val userAssignment = assignments[user.id] ?: emptyMap() // 修正: 不需使用 currentState
                if(userAssignment.isNotEmpty()){
                    // Create a temporary Assignment object for validation
                    val tempAssignment = Assignment(userId = user.id, dailyShifts = userAssignment)
                    val violations = ruleEngine.validate(user, tempAssignment, shiftTypes, softRules)
                    softViolationsResult.addAll(violations)
                }
            }
            Log.d(TAG, "Calculated stats and ${softViolationsResult.size} soft violations.")

            _uiState.update {
                it.copy(
                    userShiftCounts = userCounts.mapValues { e -> e.value.toMap() }, // Make immutable
                    softRuleViolations = softViolationsResult
                )
            }
        }
    }


    // --- User Actions ---
    // 實作 updateAssignment，包含硬性規則檢查
    fun updateAssignment(userId: String, day: String, shiftId: String) { // 修正: 從 currentState 取得 userId
        _uiState.update { it.copy(manualEditViolation = null) } // Clear previous edit error
        val currentState = _uiState.value
        val user = currentState.users.find { it.id == userId } // 修正: 使用傳入的 userId
        if(user == null) {
            Log.e(TAG, "User not found for updateAssignment: $userId")
            return
        }

        // --- Hard Rule Check ---
        val isValidMove: Boolean
        if (shiftId.isBlank()) {
            // Removing a shift - generally allowed, but check if removing it breaks a rule *elsewhere* (complex, skip for now)
            // Let's assume removing is always valid for simplicity in this step-by-step process.
            isValidMove = true
        } else {
            // Adding/Changing a shift - check hard rules
            // 假設 interactiveFiller.checkAllHardRulesRealtime 已公開或內部可訪問
            isValidMove = interactiveFiller.checkAllHardRulesRealtime(
                user = user,
                day = day,
                shiftIdToAssign = shiftId,
                currentAssignments = currentState.currentAssignments,
                localShiftTypes = currentState.shiftTypes,
                dbRules = currentState.enabledHardRules // Check against enabled hard rules
            )
        }

        if (isValidMove) {
            // Update the assignment state
            val updatedAssignments = currentState.currentAssignments.toMutableMap()
            val userShifts = updatedAssignments[userId]?.toMutableMap() ?: mutableMapOf()

            if (shiftId.isBlank()) {
                userShifts.remove(day)
                Log.d(TAG, "User ${user.name} cleared assignment on day $day")
            } else {
                userShifts[day] = shiftId
                Log.d(TAG, "User ${user.name} updated day $day to ${currentState.shiftTypes.find{it.id==shiftId}?.shortCode}")
            }
            // If the map becomes empty after removal, remove the user entry? Optional.
            if (userShifts.isEmpty()) {
                updatedAssignments.remove(userId)
            } else {
                updatedAssignments[userId] = userShifts.toMap() // Make inner map immutable
            }

            _uiState.update { it.copy(currentAssignments = updatedAssignments.toMap()) } // Make outer map immutable

            // Recalculate stats and soft violations after successful update
            calculateStatisticsAndViolations()
        } else {
            // Handle invalid move - Don't update state, show temporary error
            val shiftName = currentState.shiftTypes.find{it.id == shiftId}?.name ?: "該班別"
            val message = "無法將 ${user.name} 在 $day 的班別設為 $shiftName (違反硬性規則)"
            Log.w(TAG, message)
            _uiState.update { it.copy(manualEditViolation = message) }
            // Optionally use a SharedFlow to show a Toast
        }
    }

    // 實作 saveStepAndProceed
    fun saveStepAndProceed() {
        // Ensure stats/violations are up-to-date before proceeding
        calculateStatisticsAndViolations()
        Log.d(TAG, "Saving step ${_uiState.value.currentStep} and proceeding.")
        // Proceed to trigger the next filling step
        proceedToNextStep()
    }

    // --- Finalization ---
    // 實作 saveFinalSchedule
    private fun finalizeSchedule() {
        _uiState.update { it.copy(isLoading = true, currentStep = ScheduleStep.FINALIZING, errorMessage = null, saveResult = null) }
        Log.d(TAG, "Finalizing schedule...")
        viewModelScope.launch {
            try {
                val finalState = _uiState.value
                val finalAssignmentsMap = finalState.currentAssignments

                // 1. Create Schedule Object
                // Recalculate final score based on soft rules violations
                val finalSoftViolations = mutableListOf<RuleViolation>()
                val finalUserMap = finalState.users.associateBy { it.id }
                var finalScore = 0
                finalState.users.forEach { user ->
                    val userAssignment = finalAssignmentsMap[user.id] ?: emptyMap()
                    if (userAssignment.isNotEmpty()) {
                        val tempAssignment = Assignment(userId = user.id, dailyShifts = userAssignment)
                        val violations = ruleEngine.validate(user, tempAssignment, finalState.shiftTypes, finalState.enabledSoftRules)
                        finalSoftViolations.addAll(violations)
                    }
                }
                finalSoftViolations.forEach{ violation ->
                    val ruleData = finalState.enabledSoftRules.find { it.ruleName == violation.ruleName }
                    finalScore += ruleData?.penaltyScore ?: 0
                }

                // Combine all hard violations encountered throughout the process
                // Note: We might need a better way to track violations across steps if needed for the final Schedule object
                val allViolationMessages = finalState.hardRuleViolationsInStep // Using last step's hard violations for now

                val schedule = Schedule(
                    // Generate new ID on client side for batch write
                    id = UUID.randomUUID().toString(),
                    orgId = orgId,
                    groupId = groupId,
                    month = month,
                    status = "draft", // Save as draft initially
                    generatedAt = Date(),
                    totalScore = finalScore,
                    violatedRules = allViolationMessages, // Combine hard+soft? Or just hard? Using hard step violations for now.
                    generationMethod = "interactive" // Mark as interactive
                )

                // 2. Create List<Assignment>
                val assignmentsList = finalAssignmentsMap.mapNotNull { (userId, dailyShifts) ->
                    finalUserMap[userId]?.let { user ->
                        Assignment(
                            // Generate new ID on client side
                            id = UUID.randomUUID().toString(),
                            scheduleId = schedule.id, // Use the same generated schedule ID
                            userId = userId,
                            userName = user.name,
                            dailyShifts = dailyShifts
                        )
                    }
                }

                if(assignmentsList.isEmpty()){
                    throw IllegalStateException("最終班表為空，無法儲存")
                }

                Log.d(TAG, "Calling repository to save schedule (${schedule.id}) with ${assignmentsList.size} assignments.")
                // 3. Save using Repository
                val result = repository.createScheduleAndAssignments(orgId, schedule, assignmentsList)
                result.getOrThrow() // Throw exception on failure

                Log.d(TAG, "Schedule saved successfully.")
                _uiState.update { it.copy(isLoading = false, currentStep = ScheduleStep.COMPLETE, saveResult = Result.success(Unit)) }

            } catch (e: Exception) {
                Log.e(TAG, "Finalizing schedule failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = ScheduleStep.ERROR,
                        errorMessage = "儲存最終班表失敗: ${e.message}",
                        saveResult = Result.failure(e)
                    )
                }
            }
        }
    }
}

// Helper extension (定義 firstNotNull 擴展函數)
fun <T> Flow<T?>.firstNotNull(): Flow<T> = filterNotNull()//.first() // 移除 .first()，因為 loadData 中已使用 .first()
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲