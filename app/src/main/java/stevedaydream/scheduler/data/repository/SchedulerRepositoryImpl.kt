package stevedaydream.scheduler.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.local.*
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.data.remote.FirebaseDataSource
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject
import javax.inject.Singleton




@Singleton
class SchedulerRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseDataSource,
    private val database: SchedulerDatabase,
    private val externalScope: CoroutineScope
) : SchedulerRepository {

    // ==================== 組織 ====================
    override suspend fun createOrganization(org: Organization, user: User): Result<String> {
        // 我們直接呼叫 FirebaseDataSource，讓它處理交易
        return remoteDataSource.createOrganizationAndFirstUser(org, user)
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


    // ==================== 使用者 ====================
    override suspend fun createUser(orgId: String, user: User): Result<String> {
        return remoteDataSource.createUser(orgId, user)
    }

    override fun observeUsers(orgId: String): Flow<List<User>> {
        // 啟動同步監聽
        externalScope.launch {
            remoteDataSource.observeUsers(orgId)
                .collect { users ->
                    database.userDao().deleteUsersByOrg(orgId)
                    database.userDao().insertUsers(users)
                }
        }
        // 返回本地資料
        return database.userDao().getUsersByOrg(orgId)
    }

    // 🔽🔽🔽 修改這個函式 🔽🔽🔽
    override fun observeUser(userId: String): Flow<User?> {
        val localUserFlow = database.userDao().getUser(userId)
        val adminStatusFlow = observeAdminStatus(userId)

        // 使用 combine 結合兩個 Flow
        return combine(localUserFlow, adminStatusFlow) { user, isSuperuser ->
            if (isSuperuser && user != null) {
                // 如果是 Superuser，就覆寫其角色
                user.copy(role = "superuser")
            } else {
                // 否則，回傳原始的使用者資料
                user
            }
        }
    }
    // 🔼🔼🔼 到此為止 🔼🔼🔼

    // 🔽🔽🔽 新增這個函式 🔽🔽🔽
    override fun observeAdminStatus(userId: String): Flow<Boolean> {
        // 直接從遠端監聽，這個狀態不需要快取
        return remoteDataSource.observeAdminStatus(userId)
    }
    // 🔼🔼🔼 到此為止 🔼🔼🔼

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
    override fun observeShiftTypes(orgId: String): Flow<List<ShiftType>> {
        externalScope.launch {
            remoteDataSource.observeShiftTypes(orgId)
                .collect { types ->
                    database.shiftTypeDao().deleteShiftTypesByOrg(orgId)
                    database.shiftTypeDao().insertShiftTypes(types)
                }
        }
        return database.shiftTypeDao().getShiftTypesByOrg(orgId)
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
    override fun observeSchedulingRules(orgId: String): Flow<List<SchedulingRule>> {
        externalScope.launch {
            remoteDataSource.observeSchedulingRules(orgId)
                .collect { rules ->
                    database.schedulingRuleDao().deleteRulesByOrg(orgId)
                    database.schedulingRuleDao().insertRules(rules)
                }
        }
        return database.schedulingRuleDao().getRulesByOrg(orgId)
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
    // ✅ 新增這個函式的實作
    override suspend fun clearAllLocalData() {
        database.clearAllData()
    }
}