// 修改開始
package stevedaydream.scheduler.data.local

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import stevedaydream.scheduler.data.model.*
import java.util.Date

// ==================== Type Converters ====================
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun fromMap(value: Map<String, String>): String {
        return value.entries.joinToString(";") { "${it.key}:${it.value}" }
    }

    @TypeConverter
    fun toMap(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        return value.split(";").associate {
            val (key, v) = it.split(":")
            key to v
        }
    }

    @TypeConverter
    fun fromAnyMap(value: Map<String, Any>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toAnyMap(value: String): Map<String, Any> {
        if (value.isEmpty()) return emptyMap()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromDailyRequirementMap(value: Map<String, DailyRequirement>?): String {
        return gson.toJson(value ?: emptyMap<String, DailyRequirement>())
    }

    @TypeConverter
    fun toDailyRequirementMap(value: String): Map<String, DailyRequirement> {
        if (value.isEmpty()) return emptyMap()
        val type = object : TypeToken<Map<String, DailyRequirement>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromIntMap(value: Map<String, Int>?): String {
        return gson.toJson(value ?: emptyMap<String, Int>())
    }

    @TypeConverter
    fun toIntMap(value: String): Map<String, Int> {
        if (value.isEmpty()) return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(value, type)
    }
}

// ==================== DAOs ====================
@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE id = :orgId")
    fun getOrganization(orgId: String): Flow<Organization?>

    @Query("SELECT * FROM organizations WHERE ownerId = :ownerId")
    fun getOrganizationsByOwner(ownerId: String): Flow<List<Organization>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganizations(organizations: List<Organization>)

    @Query("DELETE FROM organizations WHERE ownerId = :ownerId")
    suspend fun deleteOrganizationsByOwner(ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganization(org: Organization)

    @Query("DELETE FROM organizations WHERE id = :orgId")
    suspend fun deleteOrganization(orgId: String)
}

@Dao
interface UserDao {
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改點 1 ▼▼▼▼▼▼▼▼▼▼▼▼
    @Query("SELECT * FROM users WHERE orgIds LIKE '%' || :orgId || '%'")
    fun getUsersByOrg(orgId: String): Flow<List<User>>
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改點 1 ▲▲▲▲▲▲▲▲▲▲▲▲

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUser(userId: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改點 2 ▼▼▼▼▼▼▼▼▼▼▼▼
    @Query("DELETE FROM users WHERE orgIds LIKE '%' || :orgId || '%'")
    suspend fun deleteUsersByOrg(orgId: String)
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改點 2 ▲▲▲▲▲▲▲▲▲▲▲▲
}


@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` WHERE orgId = :orgId")
    fun getGroupsByOrg(orgId: String): Flow<List<Group>>

    @Query("SELECT * FROM `groups` WHERE id = :groupId")
    fun getGroup(groupId: String): Flow<Group?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<Group>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Query("DELETE FROM `groups` WHERE orgId = :orgId")
    suspend fun deleteGroupsByOrg(orgId: String)
}

@Dao
interface ShiftTypeDao {
    @Query("SELECT * FROM shift_types WHERE orgId = :orgId AND (groupId IS NULL OR groupId = :groupId)")
    fun getShiftTypesByOrgAndGroup(orgId: String, groupId: String): Flow<List<ShiftType>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShiftTypes(types: List<ShiftType>)

    @Query("DELETE FROM shift_types WHERE orgId = :orgId")
    suspend fun deleteShiftTypesByOrg(orgId: String)

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    @Query("DELETE FROM shift_types WHERE orgId = :orgId AND (groupId IS NULL OR groupId = :groupId)")
    suspend fun deleteDefaultAndGroupShiftTypes(orgId: String, groupId: String)
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
}

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests WHERE orgId = :orgId ORDER BY createdAt DESC")
    fun getRequestsByOrg(orgId: String): Flow<List<Request>>

    @Query("SELECT * FROM requests WHERE userId = :userId")
    fun getRequestsByUser(userId: String): Flow<List<Request>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<Request>)

    @Query("DELETE FROM requests WHERE orgId = :orgId")
    suspend fun deleteRequestsByOrg(orgId: String)
}

@Dao
interface SchedulingRuleDao {
    @Query("SELECT * FROM scheduling_rules WHERE orgId = :orgId AND (groupId IS NULL OR groupId = :groupId)")
    fun getRulesByOrgAndGroup(orgId: String, groupId: String): Flow<List<SchedulingRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<SchedulingRule>)

    @Query("DELETE FROM scheduling_rules WHERE orgId = :orgId")
    suspend fun deleteRulesByOrg(orgId: String)

    @Query("SELECT * FROM scheduling_rules WHERE orgId = :orgId")
    suspend fun getAllRulesByOrg(orgId: String): List<SchedulingRule>

    @Query("DELETE FROM scheduling_rules WHERE id IN (:ruleIds)")
    suspend fun deleteRulesByIds(ruleIds: List<String>)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE orgId = :orgId AND groupId = :groupId ORDER BY month DESC")
    fun getSchedulesByGroup(orgId: String, groupId: String): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE id = :scheduleId")
    fun getSchedule(scheduleId: String): Flow<Schedule?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE orgId = :orgId")
    suspend fun deleteSchedulesByOrg(orgId: String)

    @Query("DELETE FROM schedules WHERE orgId = :orgId AND groupId = :groupId")
    suspend fun deleteSchedulesByGroup(orgId: String, groupId: String)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments WHERE scheduleId = :scheduleId")
    fun getAssignmentsBySchedule(scheduleId: String): Flow<List<Assignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<Assignment>)

    @Query("DELETE FROM assignments WHERE scheduleId = :scheduleId")
    suspend fun deleteAssignmentsBySchedule(scheduleId: String)
}

@Dao
interface ManpowerPlanDao {
    @Query("SELECT * FROM manpower_plans WHERE id = :planId")
    fun getPlan(planId: String): Flow<ManpowerPlan?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: ManpowerPlan)
}

// ✨ ==================== 新增: 組織邀請碼 DAO ====================
@Dao
interface OrganizationInviteDao {
    @Query("SELECT * FROM organization_invites WHERE orgId = :orgId ORDER BY createdAt DESC")
    fun getInvitesByOrg(orgId: String): Flow<List<OrganizationInvite>>

    @Query("SELECT * FROM organization_invites WHERE inviteCode = :inviteCode")
    suspend fun getInviteByCode(inviteCode: String): OrganizationInvite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvite(invite: OrganizationInvite)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvites(invites: List<OrganizationInvite>)

    @Query("UPDATE organization_invites SET isActive = :isActive WHERE id = :inviteId")
    suspend fun updateInviteStatus(inviteId: String, isActive: Boolean)

    @Query("UPDATE organization_invites SET usedCount = :usedCount WHERE id = :inviteId")
    suspend fun updateInviteUsedCount(inviteId: String, usedCount: Int)

    @Query("DELETE FROM organization_invites WHERE orgId = :orgId")
    suspend fun deleteInvitesByOrg(orgId: String)
}

// ✨ ==================== 新增: 組織加入申請 DAO ====================
@Dao
interface OrganizationJoinRequestDao {
    @Query("SELECT * FROM organization_join_requests WHERE orgId = :orgId ORDER BY requestedAt DESC")
    fun getRequestsByOrg(orgId: String): Flow<List<OrganizationJoinRequest>>

    @Query("SELECT * FROM organization_join_requests WHERE userId = :userId ORDER BY requestedAt DESC")
    fun getRequestsByUser(userId: String): Flow<List<OrganizationJoinRequest>>

    @Query("SELECT * FROM organization_join_requests WHERE id = :requestId")
    suspend fun getRequest(requestId: String): OrganizationJoinRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: OrganizationJoinRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<OrganizationJoinRequest>)

    @Update
    suspend fun updateRequest(request: OrganizationJoinRequest)

    @Query("DELETE FROM organization_join_requests WHERE orgId = :orgId")
    suspend fun deleteRequestsByOrg(orgId: String)

    @Query("DELETE FROM organization_join_requests WHERE userId = :userId")
    suspend fun deleteRequestsByUser(userId: String)
}

// ✨ ==================== 新增: 組別加入申請 DAO ====================
@Dao
interface GroupJoinRequestDao {
    @Query("SELECT * FROM group_join_requests WHERE orgId = :orgId ORDER BY requestedAt DESC")
    fun getRequestsByOrg(orgId: String): Flow<List<GroupJoinRequest>>

    @Query("SELECT * FROM group_join_requests WHERE userId = :userId ORDER BY requestedAt DESC")
    fun getRequestsByUser(userId: String): Flow<List<GroupJoinRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: GroupJoinRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<GroupJoinRequest>)

    @Query("DELETE FROM group_join_requests WHERE orgId = :orgId")
    suspend fun deleteRequestsByOrg(orgId: String)
}

// ==================== Database ====================

@Database(
    entities = [
        Organization::class,
        User::class,
        Group::class,
        ShiftType::class,
        Request::class,
        SchedulingRule::class,
        Schedule::class,
        Assignment::class,
        ManpowerPlan::class,
        OrganizationInvite::class,        // ✨ 新增
        OrganizationJoinRequest::class,   // ✨ 新增
        GroupJoinRequest::class           // ✨ 新增
    ],
    version = 17, // ✨ 版本號記得更新
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SchedulerDatabase : RoomDatabase() {
    abstract fun organizationDao(): OrganizationDao
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun requestDao(): RequestDao
    abstract fun schedulingRuleDao(): SchedulingRuleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun manpowerPlanDao(): ManpowerPlanDao
    abstract fun organizationInviteDao(): OrganizationInviteDao           // ✨ 新增
    abstract fun organizationJoinRequestDao(): OrganizationJoinRequestDao // ✨ 新增
    abstract fun groupJoinRequestDao(): GroupJoinRequestDao               // ✨ 新增

    /**
     * 清除資料庫中的所有表格資料
     */
    suspend fun clearAllData() {
        clearAllTables()
    }
}
// 修改結束