package stevedaydream.scheduler.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.util.Date

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
    val ownerId: String = "",
    val createdAt: Date = Date(),
    val plan: String = "free",
    @Embedded val features: Features = Features(),
    // ✨ 新增欄位
    val orgCode: String = "", // 組織唯一代碼 (8位)
    val displayName: String = "", // 顯示名稱 (含地區/分院資訊)
    val location: String = "", // 地點/分院資訊
    val requireApproval: Boolean = true // 是否需要審核加入
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgName" to orgName,
        "ownerId" to ownerId,
        "createdAt" to createdAt,
        "plan" to plan,
        "features" to features,
        "orgCode" to orgCode,
        "displayName" to displayName,
        "location" to location,
        "requireApproval" to requireApproval
    )
}

// ==================== 使用者 ====================
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "member",
    val employeeId: String = "",
    val joinedAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "email" to email,
        "name" to name,
        "role" to role,
        "employeeId" to employeeId,
        "joinedAt" to joinedAt
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
    val schedulerLeaseExpiresAt: Date? = null
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("groupName", groupName)
        put("memberIds", memberIds)
        schedulerId?.let { put("schedulerId", it) }
        schedulerName?.let { put("schedulerName", it) }
        schedulerLeaseExpiresAt?.let { put("schedulerLeaseExpiresAt", it) }
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
    val status: String = "pending", // pending, approved, rejected
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
    val isTemplate: Boolean = false,
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
    val violatedRules: List<String> = emptyList()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "month" to month,
        "status" to status,
        "generatedAt" to generatedAt,
        "totalScore" to totalScore,
        "violatedRules" to violatedRules
    )
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
        "userName" to userName,
        "dailyShifts" to dailyShifts
    )
}