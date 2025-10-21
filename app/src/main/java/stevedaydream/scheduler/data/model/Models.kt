// 修改開始
package stevedaydream.scheduler.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.util.Date
import androidx.room.TypeConverter
import com.google.gson.Gson // 需要 Gson 來處理 List<String> 的 Room 轉換 (如果需要)
import com.google.gson.reflect.TypeToken // 需要 TypeToken


// ... (Organization, Features, RotationSetting, RotationSettingsContainer 保持不變) ...
@Entity(tableName = "organizations")
data class Organization(
    @PrimaryKey val id: String = "",
    val orgName: String = "",
    val displayName: String = "",
    val orgCode: String = "",
    val location: String = "",
    val ownerId: String = "",
    val plan: String = "", // "free", "basic", "premium"
    val createdAt: Date = Date(),
    val requireApproval: Boolean = true,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    @Embedded val features: Features = Features(),
    val deletionScheduledAt: Date? = null // 用於軟刪除
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "orgName" to orgName,
            "displayName" to displayName,
            "orgCode" to orgCode,
            "location" to location,
            "ownerId" to ownerId,
            "plan" to plan,
            "createdAt" to createdAt,
            "requireApproval" to requireApproval,
            "isActive" to isActive,
            "features" to mapOf(
                "advanced_rules" to features.advancedRules,
                "excel_export" to features.excelExport,
                "api_access" to features.apiAccess
            ),
            "deletionScheduledAt" to deletionScheduledAt
        )
    }
}
data class Features(
    @get:PropertyName("advanced_rules") @set:PropertyName("advanced_rules") var advancedRules: Boolean = false,
    @get:PropertyName("excel_export") @set:PropertyName("excel_export") var excelExport: Boolean = false,
    @get:PropertyName("api_access") @set:PropertyName("api_access") var apiAccess: Boolean = false
)

data class RotationSetting(
    val daysOfWeek: Set<Int> = emptySet(), // 0=週日, 1=週一...6=週六
    val nextStartIndex: Int = 0 // 下次輪替從 orderedUsers 的哪個索引開始
) {
    constructor() : this(emptySet(), 0)

    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "daysOfWeek" to daysOfWeek.toList(),
            "nextStartIndex" to nextStartIndex
        )
    }

    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): RotationSetting {
            val daysList = (map["daysOfWeek"] as? List<*>)?.mapNotNull { it as? Long } ?: emptyList()
            val nextIndex = (map["nextStartIndex"] as? Long)?.toInt() ?: 0
            return RotationSetting(
                daysOfWeek = daysList.map { it.toInt() }.toSet(),
                nextStartIndex = nextIndex
            )
        }
    }
}

data class RotationSettingsContainer(
    val rules: Map<String, RotationSetting> = emptyMap() // Map<ShiftTypeId, RotationSetting>
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "rules" to rules.mapValues { it.value.toFirestoreMap() }
        )
    }
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): RotationSettingsContainer {
            val rulesMapData = map["rules"] as? Map<*, *> ?: emptyMap<Any,Any>()
            val rules = rulesMapData.mapNotNull { (key, value) ->
                if (key is String && value is Map<*, *>) {
                    val settingMap = value.mapNotNull { (k, v) ->
                        if (k is String && v != null) k to v else null
                    }.toMap()
                    key to RotationSetting.fromFirestoreMap(settingMap)
                } else {
                    null
                }
            }.toMap()
            return RotationSettingsContainer(rules)
        }
    }
}

// 修改 Converters 以處理 Map<String, List<String>> (使用 Gson)
class Converters {
    private val gson = Gson()

    // ... (其他 TypeConverter 保持不變) ...
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

    // --- 修改 Map<String, String> 的轉換 ---
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String {
        return gson.toJson(value ?: emptyMap<String, String>())
    }

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, type)
    }
    // --- 新增 Map<String, List<String>> 的轉換 ---
    @TypeConverter
    fun fromStringListMap(value: Map<String, List<String>>?): String {
        return gson.toJson(value ?: emptyMap<String, List<String>>())
    }

    @TypeConverter
    fun toStringListMap(value: String): Map<String, List<String>> {
        if (value.isEmpty()) return emptyMap()
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        return gson.fromJson(value, type) ?: emptyMap() // 添加 null 檢查
    }
    // --- 保持 AnyMap 的轉換 ---
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
    // --- 保持 DailyRequirementMap 的轉換 ---
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
    // --- 保持 IntMap 的轉換 ---
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
    // --- 保持 IntSet 的轉換 ---
    @TypeConverter
    fun fromIntSet(value: Set<Int>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntSet(value: String): Set<Int> {
        if (value.isEmpty()) return emptySet()
        return try {
            value.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}


// ... (User, Group, GroupJoinRequest, ShiftType, Request 保持不變) ...
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = "",
    val orgIds: List<String> = emptyList(),
    val currentOrgId: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "member",
    val employeeId: String = "",
    val joinedAt: Date = Date(),
    val employmentStatus: Map<String, String> = emptyMap() // key: orgId, value: status
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgIds" to orgIds,
        "currentOrgId" to currentOrgId,
        "email" to email,
        "name" to name,
        "role" to role,
        "employeeId" to employeeId,
        "joinedAt" to joinedAt,
        "employmentStatus" to employmentStatus
    )
}
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val groupName: String = "",
    val memberIds: List<String> = emptyList(),
    val schedulerId: String? = null,
    val schedulerName: String? = null,
    val schedulerLeaseExpiresAt: Date? = null,
    val reservationStatus: String = "inactive", // "inactive", "active", "closed"
    val reservationMonth: String? = null,
    val userOrder: List<String>? = null,
    val rotationState: Map<String, Int>? = null
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("groupName", groupName)
        put("memberIds", memberIds)
        schedulerId?.let { put("schedulerId", it) }
        schedulerName?.let { put("schedulerName", it) }
        schedulerLeaseExpiresAt?.let { put("schedulerLeaseExpiresAt", it) }
        put("reservationStatus", reservationStatus)
        reservationMonth?.let { put("reservationMonth", it) }
        userOrder?.let { put("userOrder", it) }
        rotationState?.let { put("rotationState", it) }
    }

    fun isSchedulerActive(): Boolean {
        val expiresAt = schedulerLeaseExpiresAt ?: return false
        return Date().before(expiresAt)
    }
}
@Entity(tableName = "group_join_requests")
data class GroupJoinRequest(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val userId: String = "",
    val userName: String = "",
    val targetGroupId: String = "",
    val targetGroupName: String = "",
    val status: String = "pending", // pending, approved, rejected, canceled
    val requestedAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "userId" to userId,
        "userName" to userName,
        "targetGroupId" to targetGroupId,
        "targetGroupName" to targetGroupName,
        "status" to status,
        "requestedAt" to requestedAt
    )
}
@Entity(tableName = "shift_types")
data class ShiftType(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val name: String = "",
    val shortCode: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val color: String = "#4A90E2",
    val groupId: String? = null,
    @get:PropertyName("template") @set:PropertyName("template")
    var isTemplate: Boolean = false,
    val templateId: String? = null,
    val createdBy: String? = null
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("orgId", orgId)
        put("name", name)
        put("shortCode", shortCode)
        put("startTime", startTime)
        put("endTime", endTime)
        put("color", color)
        groupId?.let { put("groupId", it) }
        put("isTemplate", isTemplate)
        templateId?.let { put("templateId", it) }
        createdBy?.let { put("createdBy", it) }
    }
}
@Entity(tableName = "requests")
data class Request(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val userId: String = "",
    val userName: String = "",
    val date: String = "",
    val type: String = "",
    val details: Map<String, Any> = emptyMap(),
    val status: String = "pending",
    val createdAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userName" to userName,
        "date" to date,
        "type" to type,
        "details" to details,
        "status" to status,
        "createdAt" to createdAt
    )
}


// ==================== 預約班表 (修改) ====================
@Entity(tableName = "reservations")
data class Reservation(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val groupId: String = "",
    val month: String = "",
    val userId: String = "",
    val userName: String = "",
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改點 ▼▼▼▼▼▼▼▼▼▼▼▼
    // Map<"day", List<"shiftId">>, e.g., "01" -> ["off", "shift_s", "shift_d"]
    // 列表中的順序代表偏好順序
    val dailyShifts: Map<String, List<String>> = emptyMap(),
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
    val updatedAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "groupId" to groupId,
        "month" to month,
        "userId" to userId,
        "userName" to userName,
        "dailyShifts" to dailyShifts, // Firestore 原生支援 List<String>
        "updatedAt" to updatedAt
    )
}

// ... (SchedulingRule, ManpowerPlan, RequirementDefaults, DailyRequirement, Schedule, Assignment 保持不變) ...
@Entity(tableName = "scheduling_rules")
data class SchedulingRule(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val ruleName: String = "",
    val description: String = "",
    val ruleType: String = "",
    val penaltyScore: Int = 0,
    val isEnabled: Boolean = true,
    val isPremiumFeature: Boolean = false,
    val parameters: Map<String, String> = emptyMap(),
    val isTemplate: Boolean = false,
    val templateId: String? = null,
    val createdBy: String? = null,
    val groupId: String? = null
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("orgId", orgId)
        put("ruleName", ruleName)
        put("description", description)
        put("ruleType", ruleType)
        put("penaltyScore", penaltyScore)
        put("isEnabled", isEnabled)
        put("isPremiumFeature", isPremiumFeature)
        put("parameters", parameters)
        put("isTemplate", isTemplate)
        templateId?.let { put("templateId", it) }
        createdBy?.let { put("createdBy", it) }
        groupId?.let { put("groupId", it) }
    }
}
data class RequirementDefaults(
    val weekday: Map<String, Int> = emptyMap(),
    val saturday: Map<String, Int> = emptyMap(),
    val sunday: Map<String, Int> = emptyMap(),
    val holiday: Map<String, Int> = emptyMap()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "weekday" to weekday,
        "saturday" to saturday,
        "sunday" to sunday,
        "holiday" to holiday
    )
}

@Entity(tableName = "manpower_plans")
data class ManpowerPlan(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val groupId: String = "",
    val month: String = "",
    @Embedded val requirementDefaults: RequirementDefaults = RequirementDefaults(),
    val dailyRequirements: Map<String, DailyRequirement> = emptyMap(),
    val updatedAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "groupId" to groupId,
        "month" to month,
        "requirementDefaults" to requirementDefaults.toFirestoreMap(),
        "dailyRequirements" to dailyRequirements.mapValues { it.value.toFirestoreMap() },
        "updatedAt" to updatedAt
    )
}

data class DailyRequirement(
    val date: String = "",
    val isHoliday: Boolean = false,
    val holidayName: String? = null,
    val requirements: Map<String, Int> = emptyMap()
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("date", date)
        put("isHoliday", isHoliday)
        holidayName?.let { put("holidayName", it) }
        put("requirements", requirements)
    }
}
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val groupId: String = "",
    val month: String = "",
    val status: String = "draft",
    val generatedAt: Date = Date(),
    val totalScore: Int = 0,
    val violatedRules: List<String> = emptyList(),
    val generationMethod: String = "smart"
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "month" to month,
        "status" to status,
        "generatedAt" to generatedAt,
        "totalScore" to totalScore,
        "violatedRules" to violatedRules,
        "generationMethod" to generationMethod
    )
}
@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey val id: String = "",
    val scheduleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val dailyShifts: Map<String, String> = emptyMap()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userName" to userName,
        "dailyShifts" to dailyShifts
    )
}
// 修改結束