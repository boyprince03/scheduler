package stevedaydream.scheduler.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date // ✅ 1. 匯入 Date

// ==================== 組織邀請碼 ====================
@Entity(tableName = "organization_invites")
data class OrganizationInvite(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val inviteCode: String = "", // 8位唯一邀請碼
    val inviteType: String = "general", // general, email, qrcode
    val createdBy: String = "",
    val createdAt: Date = Date(),
    val expiresAt: Date? = null,
    val usageLimit: Int? = null, // null = 無限制使用
    val usedCount: Int = 0,
    val isActive: Boolean = true,
    val targetGroupId: String? = null // 指定加入特定群組
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("orgId", orgId)
        put("orgName", orgName)
        put("inviteCode", inviteCode)
        put("inviteType", inviteType)
        put("createdBy", createdBy)
        put("createdAt", createdAt)
        expiresAt?.let { put("expiresAt", it) }
        usageLimit?.let { put("usageLimit", it) }
        put("usedCount", usedCount)
        put("isActive", isActive)
        targetGroupId?.let { put("targetGroupId", it) }
    }

    fun isValid(): Boolean {
        if (!isActive) return false
        if (expiresAt != null && Date().after(expiresAt)) return false
        if (usageLimit != null && usedCount >= usageLimit) return false
        return true
    }
}