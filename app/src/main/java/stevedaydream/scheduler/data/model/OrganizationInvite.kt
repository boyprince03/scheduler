// 修改開始
package stevedaydream.scheduler.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName // ✅ 1. 匯入 PropertyName
import java.util.Date

// ==================== 組織邀請碼 ====================
@Entity(tableName = "organization_invites")
data class OrganizationInvite(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val inviteCode: String = "",
    val inviteType: String = "general",
    val createdBy: String = "",
    val createdAt: Date = Date(),
    val expiresAt: Date? = null,
    val usageLimit: Int? = null,
    val usedCount: Int = 0,
    // ✅ 2. 加上 @get: 和 @set: 註解，並將 val 改為 var
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    val targetGroupId: String? = null
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

    // 這個函式不需要更動
    fun isValid(): Boolean {
        if (!isActive) return false
        if (expiresAt != null && Date().after(expiresAt)) return false
        if (usageLimit != null && usedCount >= usageLimit) return false
        return true
    }
}
// 修改結束