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
    @Query("SELECT * FROM users WHERE orgId = :orgId")
    fun getUsersByOrg(orgId: String): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUser(userId: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("DELETE FROM users WHERE orgId = :orgId")
    suspend fun deleteUsersByOrg(orgId: String)
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

    // ✅ 新增：獲取組織下的所有規則，用於同步比對
    @Query("SELECT * FROM scheduling_rules WHERE orgId = :orgId")
    suspend fun getAllRulesByOrg(orgId: String): List<SchedulingRule>

    // ✅ 新增：根據 ID 列表刪除規則
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
        ManpowerPlan::class
    ],
    version = 8, // ✅ 版本號 +1，因為 Schema 變更
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

    /**
     * 清除資料庫中的所有表格資料
     */
    suspend fun clearAllData() {
        clearAllTables()
    }
}