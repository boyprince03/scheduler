package stevedaydream.scheduler.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import stevedaydream.scheduler.data.model.*

// ==================== Type Converters ====================
class Converters {
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
        return value.entries.joinToString(";") { "${it.key}:${it.value}" }
    }

    @TypeConverter
    fun toAnyMap(value: String): Map<String, Any> {
        if (value.isEmpty()) return emptyMap()
        return value.split(";").associate {
            val (key, v) = it.split(":")
            key to v as Any
        }
    }
}

// ==================== DAOs ====================
@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE id = :orgId")
    fun getOrganization(orgId: String): Flow<Organization?>

    // 🔽🔽🔽 在下方加入這三個函式 🔽🔽🔽
    @Query("SELECT * FROM organizations WHERE ownerId = :ownerId")
    fun getOrganizationsByOwner(ownerId: String): Flow<List<Organization>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganizations(organizations: List<Organization>)

    @Query("DELETE FROM organizations WHERE ownerId = :ownerId")
    suspend fun deleteOrganizationsByOwner(ownerId: String)
    // 🔼🔼🔼 到此為止 🔼🔼🔼

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
    @Query("SELECT * FROM shift_types WHERE orgId = :orgId")
    fun getShiftTypesByOrg(orgId: String): Flow<List<ShiftType>>

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
    @Query("SELECT * FROM scheduling_rules WHERE orgId = :orgId")
    fun getRulesByOrg(orgId: String): Flow<List<SchedulingRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<SchedulingRule>)

    @Query("DELETE FROM scheduling_rules WHERE orgId = :orgId")
    suspend fun deleteRulesByOrg(orgId: String)
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
        Assignment::class
    ],
    version = 1,
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
}