// scheduler/domain/repository/SchedulerRepository.kt
package stevedaydream.scheduler.domain.repository

import kotlinx.coroutines.flow.Flow
import stevedaydream.scheduler.data.model.*

interface SchedulerRepository {
    // ==================== 組織 ====================
    suspend fun createOrganization(org: Organization, user: User): Result<String>
    fun observeOrganization(orgId: String): Flow<Organization?>
    fun observeOrganizationsByOwner(ownerId: String): Flow<List<Organization>>
    suspend fun refreshOrganizations(ownerId: String): Result<Unit>
    fun observeAllOrganizations(): Flow<List<Organization>>
    suspend fun deleteOrganization(orgId: String): Result<Unit>
    suspend fun scheduleOrganizationForDeletion(orgId: String): Result<Unit>
    suspend fun transferOwnership(orgId: String, newOwnerId: String): Result<Unit>
    suspend fun leaveOrganization(orgId: String, userId: String): Result<Unit>
    suspend fun updateEmploymentStatus(orgId: String, userId: String, status: String): Result<Unit>

    // ==================== 組織邀請管理 ====================
    suspend fun createOrganizationInvite(
        orgId: String,
        invite: OrganizationInvite
    ): Result<String>
    fun observeOrganizationInvites(orgId: String): Flow<List<OrganizationInvite>>
    suspend fun getOrganizationByInviteCode(inviteCode: String): Result<Organization?>
    suspend fun validateAndUseInviteCode(inviteCode: String): Result<OrganizationInvite>
    suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit>

    // ==================== 組織加入申請 ====================
    suspend fun createOrganizationJoinRequest(
        request: OrganizationJoinRequest
    ): Result<String>
    fun observeOrganizationJoinRequests(orgId: String): Flow<List<OrganizationJoinRequest>>
    suspend fun cancelGroupJoinRequest(orgId: String, requestId: String): Result<Unit>
    fun observeGroupJoinRequestsForUser(userId: String): Flow<List<GroupJoinRequest>>
    fun observeUserJoinRequests(userId: String): Flow<List<OrganizationJoinRequest>>
    suspend fun processJoinRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String? = null
    ): Result<Unit>
    suspend fun generateUniqueOrgCode(): String
    suspend fun getOrganizationByCode(orgCode: String): Result<Organization?>


    // ==================== 使用者 ====================
    suspend fun createUser(orgId: String, user: User): Result<String>
    fun observeAllUsers(): Flow<List<User>>
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
    fun observeGroupJoinRequestsForOrg(orgId: String): Flow<List<GroupJoinRequest>>
    suspend fun updateGroupJoinRequestStatus(orgId: String, requestId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun updateUserGroup(orgId: String, userId: String, newGroupId: String, oldGroupId: String?): Result<Unit>


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
    fun observeRuleTemplates(): Flow<List<SchedulingRule>>
    suspend fun addRuleTemplate(rule: SchedulingRule): Result<String>
    suspend fun updateRuleTemplate(ruleId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteRuleTemplate(ruleId: String): Result<Unit>
    fun observeSchedulingRules(orgId: String, groupId: String): Flow<List<SchedulingRule>>
    suspend fun enableTemplateForRule(orgId: String, ruleTemplate: SchedulingRule): Result<String>
    suspend fun addCustomRuleForGroup(orgId: String, groupId: String, rule: SchedulingRule): Result<String>
    suspend fun updateRuleForOrg(orgId: String, ruleId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteRuleForOrg(orgId: String, ruleId: String): Result<Unit>



    // ==================== 班表 ====================
    suspend fun createSchedule(orgId: String, schedule: Schedule): Result<String>

    suspend fun createScheduleAndAssignments(orgId: String, schedule: Schedule, assignments: List<Assignment>): Result<String>
    fun observeSchedules(orgId: String, groupId: String): Flow<List<Schedule>>
    fun observeSchedule(scheduleId: String): Flow<Schedule?>
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    suspend fun updateScheduleAndAssignments(orgId: String, schedule: Schedule, assignments: List<Assignment>): Result<Unit>
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    // ==================== 班表分配 ====================
    suspend fun createAssignment(orgId: String, scheduleId: String, assignment: Assignment): Result<String>
    fun observeAssignments(orgId: String, scheduleId: String): Flow<List<Assignment>>
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