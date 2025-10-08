package stevedaydream.scheduler.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

// ==================== 組織 ====================
@Entity(tableName = "organizations")
data class Organization(
    @PrimaryKey val id: String = "",
    val orgName: String = "",
    val ownerId: String = "",
    val createdAt: Long = 0,
    val plan: String = "free", // free, standard, premium
    val advancedRules: Boolean = false,
    val excelExport: Boolean = false,
    val apiAccess: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgName" to orgName,
        "ownerId" to ownerId,
        "createdAt" to Timestamp(createdAt / 1000, 0),
        "plan" to plan,
        "features" to mapOf(
            "advanced_rules" to advancedRules,
            "excel_export" to excelExport,
            "api_access" to apiAccess
        )
    )
}

// ==================== 使用者 ====================
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "member", // org_admin, member
    val joinedAt: Long = 0
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "email" to email,
        "name" to name,
        "role" to role,
        "joinedAt" to Timestamp(joinedAt / 1000, 0)
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
    val schedulerLeaseExpiresAt: Long? = null
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("groupName", groupName)
        put("members", memberIds)
        schedulerId?.let { put("schedulerId", it) }
        schedulerName?.let { put("schedulerName", it) }
        schedulerLeaseExpiresAt?.let {
            put("schedulerLeaseExpiresAt", Timestamp(it / 1000, 0))
        }
    }

    fun isSchedulerActive(): Boolean {
        val expiresAt = schedulerLeaseExpiresAt ?: return false
        return System.currentTimeMillis() < expiresAt
    }
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
    val color: String = "#4A90E2"
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "name" to name,
        "shortCode" to shortCode,
        "startTime" to startTime,
        "endTime" to endTime,
        "color" to color
    )
}

// ==================== 請求 ====================
@Entity(tableName = "requests")
data class Request(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val userId: String = "",
    val userName: String = "",
    val date: String = "", // YYYY-MM-DD
    val type: String = "", // leave, shift_preference
    val details: Map<String, Any> = emptyMap(),
    val status: String = "pending", // pending, approved, rejected, coordination_needed
    val createdAt: Long = 0
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userName" to userName,
        "date" to date,
        "type" to type,
        "details" to details,
        "status" to status,
        "createdAt" to Timestamp(createdAt / 1000, 0)
    )
}

// ==================== 排班規則 ====================
@Entity(tableName = "scheduling_rules")
data class SchedulingRule(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val ruleName: String = "",
    val ruleType: String = "", // hard, soft
    val penaltyScore: Int = 0,
    val isEnabled: Boolean = true,
    val isPremiumFeature: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "ruleName" to ruleName,
        "ruleType" to ruleType,
        "penaltyScore" to penaltyScore,
        "isEnabled" to isEnabled,
        "isPremiumFeature" to isPremiumFeature
    )
}

// ==================== 班表 ====================
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val groupId: String = "",
    val month: String = "", // YYYY-MM
    val status: String = "draft", // draft, published
    val generatedAt: Long = 0,
    val totalScore: Int = 0,
    val violatedRules: List<String> = emptyList()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "month" to month,
        "status" to status,
        "generatedAt" to Timestamp(generatedAt / 1000, 0),
        "summary" to mapOf(
            "totalScore" to totalScore,
            "violatedRules" to violatedRules
        )
    )
}

// ==================== 班表分配 ====================
@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey val id: String = "",
    val scheduleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val dailyShifts: Map<String, String> = emptyMap() // "01" -> shiftTypeId
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userName" to userName,
        "dailyShifts" to dailyShifts
    )
}