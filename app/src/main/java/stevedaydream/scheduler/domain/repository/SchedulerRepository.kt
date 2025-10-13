package stevedaydream.scheduler.domain.repository

import kotlinx.coroutines.flow.Flow
import stevedaydream.scheduler.data.model.*

interface SchedulerRepository {
    // ==================== 組織 ====================
    suspend fun createOrganization(org: Organization, user: User): Result<String>
    fun observeOrganization(orgId: String): Flow<Organization?>
    fun observeOrganizationsByOwner(ownerId: String): Flow<List<Organization>>
    suspend fun refreshOrganizations(ownerId: String): Result<Unit>

    // ==================== 組織邀請管理 ====================
    /**
     * 生成組織邀請碼
     */
    suspend fun createOrganizationInvite(
        orgId: String,
        invite: OrganizationInvite
    ): Result<String>

    /**
     * 監聽組織的所有邀請碼
     */
    fun observeOrganizationInvites(orgId: String): Flow<List<OrganizationInvite>>

    /**
     * 根據邀請碼查詢組織資訊
     */
    suspend fun getOrganizationByInviteCode(inviteCode: String): Result<Organization?>

    /**
     * 驗證並使用邀請碼
     */
    suspend fun validateAndUseInviteCode(inviteCode: String): Result<OrganizationInvite>

    /**
     * 停用邀請碼
     */
    suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit>

    // ==================== 組織加入申請 ====================
    /**
     * 創建組織加入申請
     */
    suspend fun createOrganizationJoinRequest(
        request: OrganizationJoinRequest
    ): Result<String>

    /**
     * 監聽組織的所有加入申請
     */
    fun observeOrganizationJoinRequests(orgId: String): Flow<List<OrganizationJoinRequest>>

    /**
     * 監聽用戶的加入申請狀態
     */
    fun observeUserJoinRequests(userId: String): Flow<List<OrganizationJoinRequest>>

    /**
     * 審核加入申請 (批准/拒絕)
     */
    suspend fun processJoinRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String? = null
    ): Result<Unit>

    /**
     * 生成組織唯一代碼
     */
    suspend fun generateUniqueOrgCode(): String

    /**
     * 根據組織代碼查詢組織
     */
    suspend fun getOrganizationByCode(orgCode: String): Result<Organization?>


    // ==================== 使用者 ====================
    suspend fun createUser(orgId: String, user: User): Result<String>
    fun observeUsers(orgId: String): Flow<List<User>>
    fun observeUser(userId: String): Flow<User?>
    fun observeAdminStatus(userId: String): Flow<Boolean>
    suspend fun checkUserExists(userId: String): Boolean
    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Unit>

    // ==================== 群組 ====================
    suspend fun createGroup(orgId: String, group: Group): Result<String>
    suspend fun updateGroup(orgId: String, groupId: String, updates: Map<String, Any>): Result<Unit>
    fun observeGroups(orgId: String): Flow<List<Group>>
    fun observeGroup(groupId: String): Flow<Group?>
    // ==================== 組別加入申請 ====================
    suspend fun createGroupJoinRequest(orgId: String, request: GroupJoinRequest): Result<String>

    // ==================== 排班者生命週期 ====================
    suspend fun claimScheduler(orgId: String, groupId: String, userId: String, userName: String): Result<Boolean>
    suspend fun renewSchedulerLease(orgId: String, groupId: String, userId: String): Result<Boolean>
    suspend fun releaseScheduler(orgId: String, groupId: String): Result<Unit>

    // ==================== 班別類型 ====================

    fun observeShiftTypeTemplates(): Flow<List<ShiftType>>
    fun observeShiftTypes(orgId: String, groupId: String): Flow<List<ShiftType>>
    suspend fun addCustomShiftTypeForGroup(orgId: String, groupId: String, shiftType: ShiftType): Result<String>
    suspend fun updateShiftType(orgId: String, shiftTypeId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteShiftType(orgId: String, shiftTypeId: String): Result<Unit>



    // ==================== 請求 ====================
    suspend fun createRequest(orgId: String, request: Request): Result<String>
    fun observeRequests(orgId: String): Flow<List<Request>>
    fun observeUserRequests(userId: String): Flow<List<Request>>

    // ==================== 排班規則 ====================

    // Superuser: 管理規則範本
    fun observeRuleTemplates(): Flow<List<SchedulingRule>>
    suspend fun addRuleTemplate(rule: SchedulingRule): Result<String>
    suspend fun updateRuleTemplate(ruleId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteRuleTemplate(ruleId: String): Result<Unit>

    // Org/Group: 讀取規則
    fun observeSchedulingRules(orgId: String, groupId: String): Flow<List<SchedulingRule>>

    // Org Admin: 從範本啟用規則
    suspend fun enableTemplateForRule(orgId: String, ruleTemplate: SchedulingRule): Result<String>

    // Scheduler: 新增自訂規則
    suspend fun addCustomRuleForGroup(orgId: String, groupId: String, rule: SchedulingRule): Result<String>

    // 通用: 更新與刪除組織內的規則
    suspend fun updateRuleForOrg(orgId: String, ruleId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteRuleForOrg(orgId: String, ruleId: String): Result<Unit>



    // ==================== 班表 ====================
    suspend fun createSchedule(orgId: String, schedule: Schedule): Result<String>
    fun observeSchedules(orgId: String, groupId: String): Flow<List<Schedule>>
    fun observeSchedule(scheduleId: String): Flow<Schedule?>

    // ==================== 班表分配 ====================
    suspend fun createAssignment(orgId: String, scheduleId: String, assignment: Assignment): Result<String>
    fun observeAssignments(scheduleId: String): Flow<List<Assignment>>
    // ==================== 人力規劃 ====================
    fun observeManpowerPlan(orgId: String, groupId: String, month: String): Flow<ManpowerPlan?>
    suspend fun saveManpowerPlan(orgId: String, plan: ManpowerPlan): Result<Unit>
    // ==================== 超級管理員 ====================
    suspend fun createTestData(orgName: String, ownerId: String, testMemberEmail: String): Result<Unit>


    /**
     * 清除所有本地資料
     */
    suspend fun clearAllLocalData()
}