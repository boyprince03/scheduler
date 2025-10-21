// 修改開始
package stevedaydream.scheduler.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.util.Date
import androidx.room.TypeConverter


// ==================== 組織 ====================
data class Features(
    @get:PropertyName("advanced_rules") @set:PropertyName("advanced_rules") var advancedRules: Boolean = false,
    @get:PropertyName("excel_export") @set:PropertyName("excel_export") var excelExport: Boolean = false,
    @get:PropertyName("api_access") @set:PropertyName("api_access") var apiAccess: Boolean = false
)

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
// ==================== 輪替規則設定 ====================
// 这个 data class 不直接存入 Room，而是作为 GroupSettings 的一部分或独立存在 Firestore
data class RotationSetting(
    val daysOfWeek: Set<Int> = emptySet(), // 0=週日, 1=週一...6=週六
    // val order: String = "ascending", // 暫時只支援升冪
    val nextStartIndex: Int = 0 // 下次輪替從 orderedUsers 的哪個索引開始
) {
    // Firestore 需要無參數建構子
    constructor() : this(emptySet(), 0)

    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "daysOfWeek" to daysOfWeek.toList(), // Firestore 不直接支援 Set，存為 List
            "nextStartIndex" to nextStartIndex
        )
    }

    companion object {
        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修正點 ▼▼▼▼▼▼▼▼▼▼▼▼
        // 确保传入的 map 的 value 是非空的 Any
        fun fromFirestoreMap(map: Map<String, Any>): RotationSetting {
            // ▲▲▲▲▲▲▲▲▲▲▲▲ 修正結束 ▲▲▲▲▲▲▲▲▲▲▲▲
            val daysList = (map["daysOfWeek"] as? List<*>)?.mapNotNull { it as? Long } ?: emptyList()
            val nextIndex = (map["nextStartIndex"] as? Long)?.toInt() ?: 0
            return RotationSetting(
                daysOfWeek = daysList.map { it.toInt() }.toSet(), // 從 List 轉回 Set
                nextStartIndex = nextIndex
            )
        }
    }
}

// 用於儲存所有輪替規則的容器，可以存在 Group 文件下或獨立文件
// 用於儲存所有輪替規則的容器，可以存在 Group 文件下或獨立文件
data class RotationSettingsContainer(
    // Map<ShiftTypeId, RotationSetting>
    val rules: Map<String, RotationSetting> = emptyMap()
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
                    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修正點 ▼▼▼▼▼▼▼▼▼▼▼▼
                    // 安全地轉換 Map<*, *> 為 Map<String, Any>，过滤掉 value 为 null 的情况
                    val settingMap = value.mapNotNull { (k, v) ->
                        if (k is String && v != null) k to v else null // 确保 v 不为 null
                    }.toMap() // settingMap 现在是 Map<String, Any>
                    key to RotationSetting.fromFirestoreMap(settingMap) // 传入 Map<String, Any>
                    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修正結束 ▲▲▲▲▲▲▲▲▲▲▲▲
                } else {
                    null
                }
            }.toMap()
            return RotationSettingsContainer(rules)
        }
    }
}


// 在 Converters class 中加入 Set<Int> 的轉換器 (如果需要存 Room 的話，但目前規則存 Firestore)
class Converters {
    // ... (其他 TypeConverter 保持不變) ...

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

// ==================== 使用者 ====================
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

// ==================== 群組 ====================
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
    // 新增：用於醫院策略的使用者排序列表 (儲存 User ID)
    val userOrder: List<String>? = null,
    // 新增：用於輪替的狀態 (Map<ShiftTypeId, nextUserIndex>)
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
        userOrder?.let { put("userOrder", it) } // 新增
        rotationState?.let { put("rotationState", it) } // 新增
    }

    fun isSchedulerActive(): Boolean {
        val expiresAt = schedulerLeaseExpiresAt ?: return false
        return Date().before(expiresAt)
    }
}

// ==================== 組別加入申請 ====================
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

// ==================== 班別類型 ====================
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

// ==================== 請求 ====================
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

// ==================== 預約班表 ====================
@Entity(tableName = "reservations")
data class Reservation(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val groupId: String = "",
    val month: String = "",
    val userId: String = "",
    val userName: String = "",
    // Map<"day", "shiftId">, e.g., "01" -> "shift_id_123"
    val dailyShifts: Map<String, String> = emptyMap(),
    val updatedAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "groupId" to groupId,
        "month" to month,
        "userId" to userId,
        "userName" to userName,
        "dailyShifts" to dailyShifts,
        "updatedAt" to updatedAt
    )
}
// ==================== 排班規則 ====================
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

// ==================== 人力規劃 ====================
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

// ==================== 班表 ====================
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
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    val generationMethod: String = "smart" // "smart" 或 "manual"
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
) {
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "month" to month,
        "status" to status,
        "generatedAt" to generatedAt,
        "totalScore" to totalScore,
        "violatedRules" to violatedRules,
        "generationMethod" to generationMethod
    )
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
}

// ==================== 班表分配 ====================
@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey val id: String = "",
    val scheduleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val dailyShifts: Map<String, String> = emptyMap()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId, // ✅ 新增，這是最重要的欄位
        "userName" to userName,
        "dailyShifts" to dailyShifts
    )
}
// 修改結束