// scheduler/data/model/OrganizationJoinRequest.kt

package stevedaydream.scheduler.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

// ==================== 組織加入申請 ====================
@Entity(tableName = "organization_join_requests")
data class OrganizationJoinRequest(
    @PrimaryKey val id: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val inviteCode: String? = null,
    val joinMethod: String = "manual",
    val status: String = "pending",
    val requestedAt: Date = Date(),
    val processedBy: String? = null,
    val processedAt: Date? = null,
    val targetGroupId: String? = null,
    val message: String = ""
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