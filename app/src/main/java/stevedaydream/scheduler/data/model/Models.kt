package stevedaydream.scheduler.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.util.Date // ✅ 1. 匯入 Date

// ==================== 組織 ====================
// ✅ 1. 新增一個專門用來對應 "features" map 的資料類別
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
    val createdAt: Date = Date(), // ✅ 2. 將 Long 改為 Date
    val plan: String = "free",
    // ✅ 2. 將原本三個獨立的布林值，改為一個 Features 物件
    // @Embedded 會告訴 Room 資料庫如何儲存這個巢狀物件
    @Embedded val features: Features = Features()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "orgName" to orgName,
        "ownerId" to ownerId,
        "createdAt" to createdAt, // ✅ 3. 直接傳遞 Date 物件
        "plan" to plan,
        // ✅ 3. 在轉換回 Firestore map 時，直接使用 features 物件
        "features" to features
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
    val joinedAt: Date = Date() // ✅ 2. 將 Long 改為 Date
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "email" to email,
        "name" to name,
        "role" to role,
        "joinedAt" to joinedAt // ✅ 3. 直接傳遞 Date 物件
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
    val schedulerLeaseExpiresAt: Date? = null // ✅ 2. 將 Long? 改為 Date?
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("groupName", groupName)
        put("members", memberIds)
        schedulerId?.let { put("schedulerId", it) }
        schedulerName?.let { put("schedulerName", it) }
        schedulerLeaseExpiresAt?.let {
            put("schedulerLeaseExpiresAt", it) // ✅ 3. 直接傳遞 Date 物件
        }
    }

    fun isSchedulerActive(): Boolean {
        val expiresAt = schedulerLeaseExpiresAt ?: return false
        return Date().before(expiresAt) // ✅ 4. 使用 Date 的方法來比較
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
    val createdAt: Date = Date() // ✅ 2. 將 Long 改為 Date
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userName" to userName,
        "date" to date,
        "type" to type,
        "details" to details,
        "status" to status,
        "createdAt" to createdAt // ✅ 3. 直接傳遞 Date 物件
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
    val generatedAt: Date = Date(), // ✅ 2. 將 Long 改為 Date
    val totalScore: Int = 0,
    val violatedRules: List<String> = emptyList()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "month" to month,
        "status" to status,
        "generatedAt" to generatedAt, // ✅ 3. 直接傳遞 Date 物件
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