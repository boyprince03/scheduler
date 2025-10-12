package stevedaydream.scheduler.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date // ✅ 1. 匯入 Date

// ==================== 組織加入申請 ====================
@Entity(tableName = "organization_join_requests")
data class OrganizationJoinRequest(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val inviteCode: String? = null, // 使用的邀請碼
    val joinMethod: String = "manual", // manual, qrcode, email
    val status: String = "pending", // pending, approved, rejected
    val requestedAt: Date = Date(),
    val processedBy: String? = null,
    val processedAt: Date? = null,
    val targetGroupId: String? = null, // 指定要加入的群組
    val message: String = "" // 申請訊息
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("orgId", orgId)
        put("orgName", orgName)
        put("userId", userId)
        put("userName", userName)
        put("userEmail", userEmail)
        inviteCode?.let { put("inviteCode", it) }
        put("joinMethod", joinMethod)
        put("status", status)
        put("requestedAt", requestedAt)
        processedBy?.let { put("processedBy", it) }
        processedAt?.let { put("processedAt", it) }
        targetGroupId?.let { put("targetGroupId", it) }
        put("message", message)
    }
}