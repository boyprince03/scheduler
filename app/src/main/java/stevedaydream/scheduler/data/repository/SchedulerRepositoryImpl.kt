// scheduler/data/repository/SchedulerRepositoryImpl.kt
package stevedaydream.scheduler.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.local.*
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.data.remote.FirebaseDataSource
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.util.TestDataGenerator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseDataSource,
    private val database: SchedulerDatabase,
    private val auth: FirebaseAuth, // <-- 新增 FirebaseAuth
    private val externalScope: CoroutineScope
) : SchedulerRepository {

    // ==================== 組織邀請管理 ====================
    override suspend fun createOrganizationInvite(
        orgId: String,
        invite: OrganizationInvite
    ): Result<String> {
        return remoteDataSource.createOrganizationInvite(orgId, invite)
    }
    override fun observeOrganizationInvites(orgId: String): Flow<List<OrganizationInvite>> {
        // 啟動背景同步監聽
        externalScope.launch {
            remoteDataSource.observeOrganizationInvites(orgId)
                .collect { remoteInvites ->
                    // 同步資料：先刪除舊的，再插入新的
                    database.organizationInviteDao().deleteInvitesByOrg(orgId)
                    database.organizationInviteDao().insertInvites(remoteInvites)
                }
        }
        // UI 層從本地資料庫讀取資料
        return database.organizationInviteDao().getInvitesByOrg(orgId)
    }
    override suspend fun getOrganizationByInviteCode(inviteCode: String): Result<Organization?> {
        return remoteDataSource.getOrganizationByInviteCode(inviteCode)
    }

    override suspend fun validateAndUseInviteCode(inviteCode: String): Result<OrganizationInvite> {
        return remoteDataSource.validateAndUseInviteCode(inviteCode)
    }

    override suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit> {
        return remoteDataSource.deactivateInvite(orgId, inviteId)
    }
    override suspend fun scheduleOrganizationForDeletion(orgId: String): Result<Unit> {
        return remoteDataSource.scheduleOrganizationForDeletion(orgId)
    }

    override suspend fun transferOwnership(orgId: String, newOwnerId: String): Result<Unit> {
        return remoteDataSource.transferOwnership(orgId, newOwnerId)
    }
    override suspend fun leaveOrganization(orgId: String, userId: String): Result<Unit> {
        return remoteDataSource.leaveOrganization(orgId, userId)
    }

    override suspend fun updateEmploymentStatus(orgId: String, userId: String, status: String): Result<Unit> {
        return remoteDataSource.updateEmploymentStatus(orgId, userId, status)
    }

    // ==================== 組織 ====================
    override suspend fun createOrganization(org: Organization, user: User): Result<String> {
        // 我們直接呼叫 FirebaseDataSource，讓它處理交易
        return remoteDataSource.createOrganizationAndFirstUser(org, user)
    }

    override fun observeAllOrganizations(): Flow<List<Organization>> {
        // 直接從遠端觀察，因為管理員需要看到即時的完整列表
        return remoteDataSource.observeAllOrganizations()
    }

    override suspend fun deleteOrganization(orgId: String): Result<Unit> {
        // 直接呼叫遠端資料來源進行刪除
        return remoteDataSource.deleteOrganizationAndSubcollections(orgId)
    }
    override fun observeOrganization(orgId: String): Flow<Organization?> {
        // 啟動同步監聽
        externalScope.launch {
            remoteDataSource.observeOrganization(orgId)
                .collect { org ->
                    org?.let { database.organizationDao().insertOrganization(it) }
                }
        }
        // 返回本地資料
        return database.organizationDao().getOrganization(orgId)
    }
    override fun observeOrganizationsByOwner(ownerId: String): Flow<List<Organization>> {
        externalScope.launch {
            remoteDataSource.observeOrganizationsByOwner(ownerId)
                .collect { remoteOrgs ->
                    // 為了同步，我們先刪除這位使用者擁有的舊資料
                    database.organizationDao().deleteOrganizationsByOwner(ownerId)
                    // 然後插入從 Firebase 拿到的最新資料
                    database.organizationDao().insertOrganizations(remoteOrgs)
                }
        }
        // UI 將會從本地資料庫讀取資料，確保資料流一致
        return database.organizationDao().getOrganizationsByOwner(ownerId)
    }
    override suspend fun refreshOrganizations(ownerId: String): Result<Unit> = runCatching {
        // 從遠端一次性獲取最新資料
        val remoteOrgs = remoteDataSource.getOrganizationsByOwner(ownerId)
        // 更新本地資料庫
        database.organizationDao().deleteOrganizationsByOwner(ownerId)
        database.organizationDao().insertOrganizations(remoteOrgs)
    }
// ==================== 組織加入申請 ====================

    override suspend fun createOrganizationJoinRequest(
        request: OrganizationJoinRequest
    ): Result<String> {
        return remoteDataSource.createOrganizationJoinRequest(request)
    }

    override fun observeOrganizationJoinRequests(orgId: String): Flow<List<OrganizationJoinRequest>> {
        // 啟動背景同步監聽
        externalScope.launch {
            remoteDataSource.observeOrganizationJoinRequests(orgId)
                .collect { remoteRequests ->
                    // 同步資料：先刪除舊的，再插入新的
                    database.organizationJoinRequestDao().deleteRequestsByOrg(orgId)
                    database.organizationJoinRequestDao().insertRequests(remoteRequests)
                }
        }
        // UI 層從本地資料庫讀取資料
        return database.organizationJoinRequestDao().getRequestsByOrg(orgId)
    }

    override fun observeUserJoinRequests(userId: String): Flow<List<OrganizationJoinRequest>> {
        // 啟動背景同步監聽
        externalScope.launch {
            remoteDataSource.observeUserJoinRequests(userId)
                .collect { remoteRequests ->
                    // 同步資料：先刪除用戶舊的申請，再插入新的
                    database.organizationJoinRequestDao().deleteRequestsByUser(userId)
                    database.organizationJoinRequestDao().insertRequests(remoteRequests)
                }
        }
        // UI 層從本地資料庫讀取資料
        return database.organizationJoinRequestDao().getRequestsByUser(userId)
    }

    override suspend fun processJoinRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String?
    ): Result<Unit> {
        return remoteDataSource.processJoinRequest(orgId, requestId, approve, processedBy, targetGroupId)
    }

    override suspend fun generateUniqueOrgCode(): String {
        return remoteDataSource.generateUniqueOrgCode()
    }

    override suspend fun getOrganizationByCode(orgCode: String): Result<Organization?> {
        return remoteDataSource.getOrganizationByCode(orgCode)
    }

    // ==================== 使用者 ====================
    override suspend fun createUser(orgId: String, user: User): Result<String> {
        return remoteDataSource.createUser(orgId, user)
    }
    override fun observeAllUsers(): Flow<List<User>> {
        return remoteDataSource.observeAllUsers()
    }

    override fun observeUsers(orgId: String): Flow<List<User>> {
        return remoteDataSource.observeUsers(orgId)
    }


    override fun observeUser(userId: String): Flow<User?> {
        // ✅ 新增：啟動 Firestore 同步
        externalScope.launch {
            try {
                // 從頂層 users 集合監聽
                remoteDataSource.observeUserFromTopLevel(userId)
                    .collect { remoteUser ->
                        remoteUser?.let {
                            println("🔄 [Repo] 從 Firestore 同步用戶資料: ${it.name}")
                            database.userDao().insertUser(it)
                        }
                    }
            } catch (e: Exception) {
                println("❌ [Repo] Firestore 同步失敗: ${e.message}")
            }
        }

        // 從本地資料庫讀取
        val localUserFlow = database.userDao().getUser(userId)
        val adminStatusFlow = observeAdminStatus(userId)

        return combine(localUserFlow, adminStatusFlow) { user, isSuperuser ->
            println("📊 [Repo] 本地用戶資料: name=${user?.name}, isSuperuser=$isSuperuser")
            if (isSuperuser && user != null) {
                user.copy(role = "superuser")
            } else {
                user
            }
        }
    }


    override suspend fun checkUserExists(userId: String): Boolean {
        return remoteDataSource.checkUserExists(userId)
    }


    override suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Unit> {
        return remoteDataSource.updateUser(userId, updates)
    }

    override fun observeAdminStatus(userId: String): Flow<Boolean> {
        // 直接從遠端監聽，這個狀態不需要快取
        return remoteDataSource.observeAdminStatus(userId)
    }


    // ==================== 群組 ====================
    override suspend fun createGroup(orgId: String, group: Group): Result<String> {
        return remoteDataSource.createGroup(orgId, group)
    }

    override suspend fun updateGroup(orgId: String, groupId: String, updates: Map<String, Any>): Result<Unit> {
        return remoteDataSource.updateGroup(orgId, groupId, updates)
    }

    override fun observeGroups(orgId: String): Flow<List<Group>> {
        // 啟動同步監聽
        externalScope.launch {
            remoteDataSource.observeGroups(orgId)
                .collect { groups ->
                    database.groupDao().deleteGroupsByOrg(orgId)
                    database.groupDao().insertGroups(groups)
                }
        }
        // 返回本地資料
        return database.groupDao().getGroupsByOrg(orgId)
    }

    override fun observeGroup(groupId: String): Flow<Group?> {
        return database.groupDao().getGroup(groupId)
    }
    // ==================== 組別加入申請 ====================
    override suspend fun createGroupJoinRequest(orgId: String, request: GroupJoinRequest): Result<String> {
        return remoteDataSource.createGroupJoinRequest(orgId, request)
    }

    override suspend fun cancelGroupJoinRequest(orgId: String, requestId: String): Result<Unit> {
        return remoteDataSource.cancelGroupJoinRequest(orgId, requestId)
    }

    override fun observeGroupJoinRequestsForUser(userId: String): Flow<List<GroupJoinRequest>> {
        // 直接從遠端觀察，確保即時性
        return remoteDataSource.observeGroupJoinRequestsForUser(userId)
    }

    override fun observeGroupJoinRequestsForOrg(orgId: String): Flow<List<GroupJoinRequest>> {
        // 直接從 remote 觀察，確保管理員看到的是即時資料
        return remoteDataSource.observeGroupJoinRequestsForOrg(orgId)
    }

    override suspend fun updateGroupJoinRequestStatus(orgId: String, requestId: String, updates: Map<String, Any>): Result<Unit> {
        return remoteDataSource.updateGroupJoinRequestStatus(orgId, requestId, updates)
    }

    override suspend fun updateUserGroup(
        orgId: String,
        userId: String,
        newGroupId: String,
        oldGroupId: String?
    ): Result<Unit> {
        return remoteDataSource.updateUserGroup(orgId, userId, newGroupId, oldGroupId)
    }

    // ==================== 排班者生命週期 ====================
    override suspend fun claimScheduler(
        orgId: String,
        groupId: String,
        userId: String,
        userName: String
    ): Result<Boolean> {
        return remoteDataSource.claimScheduler(orgId, groupId, userId, userName)
    }

    override suspend fun renewSchedulerLease(
        orgId: String,
        groupId: String,
        userId: String
    ): Result<Boolean> {
        return remoteDataSource.renewSchedulerLease(orgId, groupId, userId)
    }

    override suspend fun releaseScheduler(orgId: String, groupId: String): Result<Unit> {
        return remoteDataSource.releaseScheduler(orgId, groupId)
    }

    // ==================== 班別類型 ====================
    override fun observeShiftTypes(orgId: String, groupId: String): Flow<List<ShiftType>> {
        externalScope.launch {
            remoteDataSource.observeShiftTypes(orgId, groupId)
                .collect { types ->
                    // 改用更精確的刪除方式，只刪除預設和當前群組的快取
                    database.shiftTypeDao().deleteDefaultAndGroupShiftTypes(orgId, groupId)
                    database.shiftTypeDao().insertShiftTypes(types)
                }
        }
        return database.shiftTypeDao().getShiftTypesByOrgAndGroup(orgId, groupId)
    }

    override fun observeShiftTypeTemplates(): Flow<List<ShiftType>> {
        return remoteDataSource.observeShiftTypeTemplates()
    }

    override suspend fun addCustomShiftTypeForGroup(orgId: String, groupId: String, shiftType: ShiftType): Result<String> {
        return remoteDataSource.addCustomShiftTypeForGroup(orgId, groupId, shiftType)
    }

    override suspend fun updateShiftType(orgId: String, shiftTypeId: String, updates: Map<String, Any>): Result<Unit> {
        return remoteDataSource.updateShiftType(orgId, shiftTypeId, updates)
    }

    override suspend fun deleteShiftType(orgId: String, shiftTypeId: String): Result<Unit> {
        return remoteDataSource.deleteShiftType(orgId, shiftTypeId)
    }

    // ==================== 請求 ====================
    override suspend fun createRequest(orgId: String, request: Request): Result<String> {
        return remoteDataSource.createRequest(orgId, request)
    }

    override fun observeRequests(orgId: String): Flow<List<Request>> {
        externalScope.launch {
            remoteDataSource.observeRequests(orgId)
                .collect { requests ->
                    database.requestDao().deleteRequestsByOrg(orgId)
                    database.requestDao().insertRequests(requests)
                }
        }
        return database.requestDao().getRequestsByOrg(orgId)
    }

    override fun observeUserRequests(userId: String): Flow<List<Request>> {
        return database.requestDao().getRequestsByUser(userId)
    }

    // ==================== 排班規則 ====================

    // --- Superuser: Rule Templates ---
    override fun observeRuleTemplates(): Flow<List<SchedulingRule>> {
        // 範本通常不需要本地快取，直接從遠端讀取
        return remoteDataSource.observeRuleTemplates()
    }

    override suspend fun addRuleTemplate(rule: SchedulingRule): Result<String> {
        return remoteDataSource.addRuleTemplate(rule)
    }

    override suspend fun updateRuleTemplate(ruleId: String, updates: Map<String, Any>): Result<Unit> {
        return remoteDataSource.updateRuleTemplate(ruleId, updates)
    }

    override suspend fun deleteRuleTemplate(ruleId: String): Result<Unit> {
        return remoteDataSource.deleteRuleTemplate(ruleId)
    }

    // --- Organization & Group Rules ---
    override fun observeSchedulingRules(orgId: String, groupId: String): Flow<List<SchedulingRule>> {
        externalScope.launch {
            remoteDataSource.observeSchedulingRules(orgId, groupId)
                .distinctUntilChanged() // 只有在遠端資料實際變更時才觸發
                .collect { remoteRules ->
                    // 1. 從本地資料庫獲取當前組織的所有規則
                    val localRules = database.schedulingRuleDao().getAllRulesByOrg(orgId)

                    val remoteRuleMap = remoteRules.associateBy { it.id }
                    val localRuleMap = localRules.associateBy { it.id }

                    // 2. 找出需要刪除的規則 (存在於本地，但遠端已不存在)
                    val rulesToDelete = localRules
                        .filter { it.id !in remoteRuleMap }
                        .map { it.id }

                    if (rulesToDelete.isNotEmpty()) {
                        database.schedulingRuleDao().deleteRulesByIds(rulesToDelete)
                    }

                    // 3. 找出需要新增或更新的規則 (遠端存在，但本地不存在或內容不一致)
                    // 因為 SchedulingRule 是 data class，可以直接用 != 比較內容
                    val rulesToInsertOrUpdate = remoteRules.filter { remoteRule ->
                        localRuleMap[remoteRule.id] != remoteRule
                    }

                    if (rulesToInsertOrUpdate.isNotEmpty()) {
                        // OnConflictStrategy.REPLACE 會自動處理新增和更新
                        database.schedulingRuleDao().insertRules(rulesToInsertOrUpdate)
                    }
                }
        }
        // UI 依然從本地 Room 讀取，現在的資料流會更穩定
        return database.schedulingRuleDao().getRulesByOrgAndGroup(orgId, groupId)
    }

    override suspend fun enableTemplateForRule(orgId: String, ruleTemplate: SchedulingRule): Result<String> {
        return remoteDataSource.enableTemplateForRule(orgId, ruleTemplate)
    }

    override suspend fun addCustomRuleForGroup(orgId: String, groupId: String, rule: SchedulingRule): Result<String> {
        return remoteDataSource.addCustomRuleForGroup(orgId, groupId, rule)
    }

    override suspend fun updateRuleForOrg(orgId: String, ruleId: String, updates: Map<String, Any>): Result<Unit> {
        return remoteDataSource.updateRuleForOrg(orgId, ruleId, updates)
    }

    override suspend fun deleteRuleForOrg(orgId: String, ruleId: String): Result<Unit> {
        return remoteDataSource.deleteRuleForOrg(orgId, ruleId)
    }



    // ==================== 班表 ====================
    override suspend fun createSchedule(orgId: String, schedule: Schedule): Result<String> {
        return remoteDataSource.createSchedule(orgId, schedule)
    }

    override fun observeSchedules(orgId: String, groupId: String): Flow<List<Schedule>> {
        externalScope.launch {
            remoteDataSource.observeSchedules(orgId, groupId)
                .collect { schedules ->
                    database.scheduleDao().deleteSchedulesByOrg(orgId)
                    schedules.forEach { database.scheduleDao().insertSchedule(it) }
                }
        }
        return database.scheduleDao().getSchedulesByGroup(orgId, groupId)
    }

    override fun observeSchedule(scheduleId: String): Flow<Schedule?> {
        return database.scheduleDao().getSchedule(scheduleId)
    }

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    override suspend fun updateScheduleAndAssignments(orgId: String, schedule: Schedule, assignments: List<Assignment>): Result<Unit> {
        return remoteDataSource.updateScheduleAndAssignments(orgId, schedule, assignments)
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    // ==================== 班表分配 ====================
    override suspend fun createAssignment(
        orgId: String,
        scheduleId: String,
        assignment: Assignment
    ): Result<String> {
        return remoteDataSource.createAssignment(orgId, scheduleId, assignment)
    }

    override fun observeAssignments(scheduleId: String): Flow<List<Assignment>> {
        externalScope.launch {
            // 需要從 Schedule 取得 orgId
            database.scheduleDao().getSchedule(scheduleId).first()?.let { schedule ->
                remoteDataSource.observeAssignments(schedule.orgId, scheduleId)
                    .collect { assignments ->
                        database.assignmentDao().deleteAssignmentsBySchedule(scheduleId)
                        database.assignmentDao().insertAssignments(assignments)
                    }
            }
        }
        return database.assignmentDao().getAssignmentsBySchedule(scheduleId)
    }
    // ==================== 人力規劃 ====================
    override fun observeManpowerPlan(orgId: String, groupId: String, month: String): Flow<ManpowerPlan?> {
        val planId = "${orgId}_${groupId}_${month}"
        externalScope.launch {
            remoteDataSource.observeManpowerPlan(orgId, groupId, month)
                .collect { plan ->
                    plan?.let { database.manpowerPlanDao().insertPlan(it) }
                }
        }
        return database.manpowerPlanDao().getPlan(planId)
    }

    override suspend fun saveManpowerPlan(orgId: String, plan: ManpowerPlan): Result<Unit> {
        return remoteDataSource.saveManpowerPlan(orgId, plan)
    }
    // ==================== 超級管理員 ====================
    override suspend fun createTestData(orgName: String, ownerId: String, testMemberEmail: String): Result<Unit> {
        // 呼叫 TestDataGenerator 來產生完整的資料集
        val dataSet = TestDataGenerator.generateCompleteTestDataSet(orgName, ownerId, testMemberEmail)
        // 將產生的資料集傳遞給 FirebaseDataSource 進行批次寫入
        return remoteDataSource.createTestData(dataSet)
    }

    override suspend fun clearAllLocalData() {
        database.clearAllData()
    }
}