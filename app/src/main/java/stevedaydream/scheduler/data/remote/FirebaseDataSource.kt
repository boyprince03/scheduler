// 修改開始
// scheduler/data/remote/FirebaseDataSource.kt
package stevedaydream.scheduler.data.remote

import android.graphics.Insets.add
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import stevedaydream.scheduler.data.model.*
import javax.inject.Inject
import javax.inject.Singleton
import stevedaydream.scheduler.util.TestDataGenerator
import java.util.Calendar
import kotlinx.coroutines.flow.combine

@Singleton
class FirebaseDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    /**
     * 產生一組預設的排班規則
     */
    private fun getDefaultSchedulingRules(): List<SchedulingRule> {
        return listOf(
            SchedulingRule(
                ruleName = "連續上班不超過N天",
                description = "避免員工因連續工作過多天而過勞。",
                ruleType = "soft",
                penaltyScore = -50,
                isEnabled = true,
                parameters = mapOf("maxDays" to "6")
            ),
            SchedulingRule(
                ruleName = "輪班間隔需大於N小時",
                description = "確保員工在兩次輪班之間有足夠的休息時間。",
                ruleType = "hard",
                penaltyScore = -1000,
                isEnabled = true,
                parameters = mapOf("minHours" to "11")
            )
        )
    }

    /**
     * 產生一組預設的班別
     */
    private fun getDefaultShiftTypes(): List<ShiftType> {
        return listOf(
            ShiftType(
                name = "放假",
                shortCode = "OFF",
                startTime = "00:00",
                endTime = "00:00",
                color = "#D0021B" // 紅色
            ),
            ShiftType(
                name = "白班",
                shortCode = "S",
                startTime = "09:00",
                endTime = "17:00",
                color = "#4A90E2" // 藍色
            ),
            ShiftType(
                name = "值班(夜)",
                shortCode = "N",
                startTime = "21:00",
                endTime = "09:00",
                color = "#000000" // 黑色
            ),
            ShiftType(
                name = "值班(日)",
                shortCode = "D",
                startTime = "09:00",
                endTime = "21:00",
                color = "#7ED321" // 綠色
            )
        )
    }
    suspend fun scheduleOrganizationForDeletion(orgId: String): Result<Unit> = runCatching {
        val thirtyDaysFromNow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }.time
        firestore.collection("organizations").document(orgId)
            .update(mapOf(
                "isActive" to false,
                "deletionScheduledAt" to thirtyDaysFromNow
            ))
            .await()
    }

    suspend fun transferOwnership(orgId: String, newOwnerId: String): Result<Unit> = runCatching {
        val orgRef = firestore.collection("organizations").document(orgId)
        val newOwnerUserRef = firestore.collection("users").document(newOwnerId)
        val newOwnerOrgUserRef = orgRef.collection("users").document(newOwnerId)

        firestore.runTransaction { transaction ->
            // 1. 更新組織的 ownerId
            transaction.update(orgRef, "ownerId", newOwnerId)

            // 2. 更新新擁有者的角色 (在頂層 user 和子集合 user 中)
            transaction.update(newOwnerUserRef, "role", "org_admin")
            transaction.update(newOwnerOrgUserRef, "role", "org_admin")

        }.await()
    }

    suspend fun leaveOrganization(orgId: String, userId: String): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(userId)
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)

        // 1. 在批次寫入前，先讀取所有需要的資料
        val userSnapshot = userRef.get().await()
        val user = userSnapshot.toObject(User::class.java)
        val groupsInOrg = firestore.collection("organizations/$orgId/groups")
            .whereArrayContains("memberIds", userId)
            .get()
            .await()

        // 2. 執行批次寫入
        firestore.runBatch { batch ->
            // 從頂層 User 物件中移除 orgId
            batch.update(userRef, "orgIds", FieldValue.arrayRemove(orgId))

            // 如果 currentOrgId 是要離開的組織，則清空
            if (user?.currentOrgId == orgId) {
                batch.update(userRef, "currentOrgId", "")
            }

            // 從組織底下的 users 子集合中刪除使用者
            batch.delete(subCollectionUserRef)

            // 從該組織的所有群組中移除使用者
            groupsInOrg.documents.forEach { doc ->
                batch.update(doc.reference, "memberIds", FieldValue.arrayRemove(userId))
            }
        }.await()
    }

    suspend fun updateEmploymentStatus(orgId: String, userId: String, status: String): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(userId)
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)
        val statusUpdate = mapOf("employmentStatus.$orgId" to status)

        firestore.runBatch { batch ->
            batch.update(userRef, statusUpdate)
            batch.update(subCollectionUserRef, statusUpdate)
        }.await()
    }


    // ==================== 組織 ====================
    suspend fun createOrganizationAndFirstUser(org: Organization, user: User): Result<String> = runCatching {
        val orgRef = firestore.collection("organizations").document()
        val topLevelUserRef = firestore.collection("users").document(user.id)
        val subCollectionUserRef = orgRef.collection("users").document(user.id) // 取得子集合中的使用者參照

        val orgWithId = org.copy(id = orgRef.id)
        val userWithOrgId = user.copy(
            orgIds = user.orgIds + orgRef.id,
            currentOrgId = orgRef.id
        )

        firestore.runBatch { batch ->
            // 1. 建立組織文件
            batch.set(orgRef, orgWithId.toFirestoreMap())

            // 2. 更新頂層的使用者文件
            batch.set(topLevelUserRef, userWithOrgId.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge())

            // 3. (新增的步驟) 在組織的子集合中建立使用者文件
            batch.set(subCollectionUserRef, userWithOrgId.toFirestoreMap())

            // 4. 建立預設規則
            val rulesCollection = orgRef.collection("schedulingRules")
            getDefaultSchedulingRules().forEach { rule ->
                val ruleRef = rulesCollection.document()
                batch.set(ruleRef, rule.toFirestoreMap())
            }

            // 5. 建立預設班別
            val shiftTypesCollection = orgRef.collection("shiftTypes")
            getDefaultShiftTypes().forEach { shiftType ->
                val shiftTypeRef = shiftTypesCollection.document()
                // 將 orgId 和 id 寫入物件中，並直接傳遞物件本身
                val finalShiftType = shiftType.copy(id = shiftTypeRef.id, orgId = orgRef.id)
                batch.set(shiftTypeRef, finalShiftType)
            }
        }.await()
        orgRef.id
    }
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    suspend fun deleteOrganizationAndSubcollections(orgId: String): Result<Unit> = runCatching {
        val orgRef = firestore.collection("organizations").document(orgId)

        // 警告：在生產環境中，強烈建議使用 Cloud Function 進行遞歸刪除，以確保原子性和完整性。
        // 這個客戶端實作是為了演示，可能會很慢或無法完整刪除。
        val batch = firestore.batch()

        // 刪除已知的子集合
        val subcollections = listOf(
            "users", "groups", "shiftTypes", "schedulingRules",
            "schedules", "invites", "groupJoinRequests", "manpowerPlans"
        )
        for (collectionName in subcollections) {
            val collectionRef = orgRef.collection(collectionName)
            val documents = collectionRef.get().await()
            for (document in documents) {
                // 特殊處理 schedules，因為它還有下一層的 assignments 子集合
                if (collectionName == "schedules") {
                    val assignments = document.reference.collection("assignments").get().await()
                    for (assignment in assignments) {
                        batch.delete(assignment.reference)
                    }
                }
                batch.delete(document.reference)
            }
        }

        // 刪除組織文件本身
        batch.delete(orgRef)
        // 提交第一批刪除（刪除組織和其子集合）
        batch.commit().await()

        // 第二階段：清理頂層 users 集合
        val usersQuery = firestore.collection("users").whereArrayContains("orgIds", orgId).get().await()
        firestore.runBatch { userBatch ->
            usersQuery.documents.forEach { doc ->
                val user = doc.toObject(User::class.java)
                // 如果使用者只屬於這個被刪除的組織，則直接刪除該使用者文件
                if (user != null && user.orgIds.size == 1 && user.orgIds.contains(orgId)) {
                    userBatch.delete(doc.reference)
                } else {
                    // 否則，只從 orgIds 陣列中移除該組織 ID
                    userBatch.update(doc.reference, "orgIds", FieldValue.arrayRemove(orgId))
                    // 如果被刪除的組織是當前組織，則清空 currentOrgId
                    if (doc.getString("currentOrgId") == orgId) {
                        userBatch.update(doc.reference, "currentOrgId", "")
                    }
                }
            }
        }.await()
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
    fun observeOrganization(orgId: String): Flow<Organization?> {
        if (orgId.isBlank()) {
            return flowOf(null)
        }
        return firestore.collection("organizations")
            .document(orgId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(Organization::class.java)?.copy(id = snapshot.id)
                } else null
            }
    }
    fun observeAllOrganizations(): Flow<List<Organization>> {
        return firestore.collection("organizations")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Organization::class.java)?.copy(id = it.id)
                }
            }
    }
    fun observeOrganizationsByOwner(ownerId: String): Flow<List<Organization>> {
        return firestore.collection("organizations")
            .whereEqualTo("ownerId", ownerId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Organization::class.java)?.copy(id = it.id)
                }
            }
    }
    suspend fun getOrganizationsByOwner(ownerId: String): List<Organization> {
        val snapshot = firestore.collection("organizations")
            .whereEqualTo("ownerId", ownerId)
            .get()
            .await()
        return snapshot.documents.mapNotNull {
            it.toObject(Organization::class.java)?.copy(id = it.id)
        }
    }

    suspend fun generateUniqueOrgCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var code: String
        var isUnique = false

        do {
            code = (1..8).map { chars.random() }.joinToString("")
            val existingOrg = firestore.collection("organizations")
                .whereEqualTo("orgCode", code)
                .get()
                .await()
            isUnique = existingOrg.isEmpty
        } while (!isUnique)

        return code
    }

    // ==================== 使用者 ====================
    suspend fun createUser(orgId: String, user: User): Result<String> = runCatching {
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(user.id)
        val topLevelUserRef = firestore.collection("users").document(user.id)

        val updatedUser = user.copy(
            orgIds = (user.orgIds + orgId).distinct(),
            currentOrgId = orgId
        )

        firestore.runBatch { batch ->
            batch.set(subCollectionUserRef, updatedUser.toFirestoreMap())
            batch.set(topLevelUserRef, updatedUser.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge())
        }.await()
        subCollectionUserRef.id
    }

    suspend fun createOrganizationInvite(
        orgId: String,
        invite: OrganizationInvite
    ): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/invites").document()
        val inviteWithId = invite.copy(id = docRef.id)
        docRef.set(inviteWithId.toFirestoreMap()).await()
        docRef.id
    }
    fun observeAllUsers(): Flow<List<User>> {
        return firestore.collection("users")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(User::class.java)?.copy(id = it.id)
                }
            }
    }
    fun observeUsers(orgId: String): Flow<List<User>> {
        // 改為查詢頂層的 'users' 集合，這是 App 主要的使用者資料來源
        return firestore.collection("users")
            .whereArrayContains("orgIds", orgId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(User::class.java)?.copy(id = it.id)
                }
            }
    }

    fun observeOrganizationInvites(orgId: String): Flow<List<OrganizationInvite>> {
        return firestore.collection("organizations/$orgId/invites")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationInvite::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun getOrganizationByInviteCode(inviteCode: String): Result<Organization?> = runCatching {
        val inviteSnapshot = firestore.collectionGroup("invites")
            .whereEqualTo("inviteCode", inviteCode)
            .whereEqualTo("isActive", true)
            .get()
            .await()

        if (inviteSnapshot.isEmpty) {
            return@runCatching null
        }

        val invite = inviteSnapshot.documents.first()
            .toObject(OrganizationInvite::class.java) ?: return@runCatching null

        if (!invite.isValid()) {
            return@runCatching null
        }

        val orgSnapshot = firestore.collection("organizations")
            .document(invite.orgId)
            .get()
            .await()

        orgSnapshot.toObject(Organization::class.java)?.copy(id = orgSnapshot.id)
    }

    suspend fun validateAndUseInviteCode(inviteCode: String): Result<OrganizationInvite> = runCatching {
        val inviteQuery = firestore.collectionGroup("invites")
            .whereEqualTo("inviteCode", inviteCode)
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .await()

        if (inviteQuery.isEmpty) {
            throw IllegalArgumentException("邀請碼不存在或已失效")
        }
        val inviteDocRef = inviteQuery.documents.first().reference

        firestore.runTransaction { transaction ->
            val inviteDoc = transaction.get(inviteDocRef)
            val invite = inviteDoc.toObject(OrganizationInvite::class.java)
                ?: throw IllegalArgumentException("無法解析邀請碼")

            if (!invite.isValid()) {
                throw IllegalArgumentException("邀請碼已過期或已達使用上限")
            }

            transaction.update(inviteDoc.reference, "usedCount", invite.usedCount + 1)
            invite
        }.await()
    }
    // ==================== 組織加入申請 ====================

    suspend fun createOrganizationJoinRequest(
        request: OrganizationJoinRequest
    ): Result<String> = runCatching {
        val docRef = firestore.collection("organizationJoinRequests").document()
        val requestWithId = request.copy(id = docRef.id)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeOrganizationJoinRequests(orgId: String): Flow<List<OrganizationJoinRequest>> {
        return firestore.collection("organizationJoinRequests")
            .whereEqualTo("orgId", orgId)
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    fun observeUserJoinRequests(userId: String): Flow<List<OrganizationJoinRequest>> {
        return firestore.collection("organizationJoinRequests")
            .whereEqualTo("userId", userId)
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/invites")
            .document(inviteId)
            .update("isActive", false)
            .await()
    }

    suspend fun processJoinRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String?
    ): Result<Unit> = runCatching {
        val requestRef = firestore.collection("organizationJoinRequests").document(requestId)

        firestore.runTransaction { transaction ->
            val requestSnapshot = transaction.get(requestRef)
            val request = requestSnapshot.toObject(OrganizationJoinRequest::class.java)
                ?: throw IllegalArgumentException("找不到申請記錄: $requestId")

            val userRef = firestore.collection("users").document(request.userId)
            val userSnapshot = transaction.get(userRef)
            val user = userSnapshot.toObject(User::class.java)

            val groupRef = if (targetGroupId != null) {
                firestore.collection("organizations/$orgId/groups").document(targetGroupId)
            } else null
            val groupSnapshot = groupRef?.let { transaction.get(it) }

            if (approve && groupRef != null && (groupSnapshot == null || !groupSnapshot.exists())) {
                throw IllegalArgumentException("指定的群組不存在: $targetGroupId")
            }

            val statusUpdate = mapOf(
                "status" to if (approve) "approved" else "rejected",
                "processedBy" to processedBy,
                "processedAt" to com.google.firebase.Timestamp.now()
            )
            transaction.update(requestRef, statusUpdate)

            if (approve) {
                transaction.update(userRef, "orgIds", com.google.firebase.firestore.FieldValue.arrayUnion(orgId))
                if (user?.currentOrgId.isNullOrBlank()) {
                    transaction.update(userRef, "currentOrgId", orgId)
                }

                groupRef?.let {
                    transaction.update(it, "memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(request.userId))
                }
            }
        }.await()
    }

    suspend fun getOrganizationByCode(orgCode: String): Result<Organization?> = runCatching {
        val snapshot = firestore.collection("organizations")
            .whereEqualTo("orgCode", orgCode)
            .get()
            .await()

        if (snapshot.isEmpty) {
            return@runCatching null
        }

        val doc = snapshot.documents.first()
        doc.toObject(Organization::class.java)?.copy(id = doc.id)
    }

    suspend fun checkUserExists(userId: String): Boolean {
        val userDoc = firestore.collection("users").document(userId).get().await()
        return userDoc.exists()
    }

    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val topLevelUserRef = firestore.collection("users").document(userId)

        val userSnapshot = topLevelUserRef.get().await()
        val user = userSnapshot.toObject(User::class.java)
        val orgIds = user?.orgIds ?: emptyList()

        firestore.runBatch { batch ->
            batch.set(topLevelUserRef, updates, com.google.firebase.firestore.SetOptions.merge())

            orgIds.forEach { orgId ->
                if (orgId.isNotEmpty()) {
                    val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)
                    batch.set(subCollectionUserRef, updates, com.google.firebase.firestore.SetOptions.merge())
                }
            }
        }.await()
    }

    fun observeUserFromTopLevel(userId: String): Flow<User?> {
        return firestore.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(User::class.java)?.copy(id = snapshot.id)
                } else {
                    null
                }
            }
    }

    suspend fun createGroup(orgId: String, group: Group): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/groups").document()
        val groupWithId = group.copy(id = docRef.id, orgId = orgId)
        docRef.set(groupWithId.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateGroup(orgId: String, groupId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groups")
            .document(groupId)
            .update(updates)
            .await()
    }
    suspend fun updateReservationStatus(orgId: String, groupId: String, month: String, status: String): Result<Unit> = runCatching {
        val updates = mapOf(
            "reservationStatus" to status,
            "reservationMonth" to if (status == "inactive") FieldValue.delete() else month
        )
        firestore.collection("organizations/$orgId/groups")
            .document(groupId)
            .update(updates)
            .await()
    }

    fun observeGroups(orgId: String): Flow<List<Group>> {
        return firestore.collection("organizations/$orgId/groups")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Group::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }
    // ==================== 預約班表 ====================
    fun observeReservations(orgId: String, groupId: String, month: String): Flow<List<Reservation>> {
        return firestore.collection("organizations/$orgId/reservations")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("month", month)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Reservation::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun saveReservation(orgId: String, reservation: Reservation): Result<Unit> = runCatching {
        val docRef = if (reservation.id.isNotEmpty()) {
            firestore.collection("organizations/$orgId/reservations").document(reservation.id)
        } else {
            firestore.collection("organizations/$orgId/reservations").document()
        }
        // 使用 set 搭配 merge a, 確保能同時處理新增和更新
        docRef.set(reservation.copy(id = docRef.id).toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun createGroupJoinRequest(orgId: String, request: GroupJoinRequest): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/groupJoinRequests").document()
        val requestWithId = request.copy(id = docRef.id)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }
    suspend fun cancelGroupJoinRequest(orgId: String, requestId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groupJoinRequests")
            .document(requestId)
            .update("status", "canceled")
            .await()
    }

    fun observeGroupJoinRequestsForUser(userId: String): Flow<List<GroupJoinRequest>> {
        return firestore.collectionGroup("groupJoinRequests")
            .whereEqualTo("userId", userId)
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(GroupJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }
    fun observeGroupJoinRequestsForOrg(orgId: String): Flow<List<GroupJoinRequest>> {
        return firestore.collection("organizations/$orgId/groupJoinRequests")
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(GroupJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun updateGroupJoinRequestStatus(orgId: String, requestId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groupJoinRequests")
            .document(requestId)
            .update(updates)
            .await()
    }

    suspend fun updateUserGroup(
        orgId: String,
        userId: String,
        newGroupId: String,
        oldGroupId: String?
    ): Result<Unit> = runCatching {
        val groupsCollection = firestore.collection("organizations/$orgId/groups")

        firestore.runBatch { batch ->
            if (oldGroupId != null && oldGroupId != newGroupId) {
                val oldGroupRef = groupsCollection.document(oldGroupId)
                batch.update(oldGroupRef, "memberIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
            }

            val newGroupRef = groupsCollection.document(newGroupId)
            batch.update(newGroupRef, "memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
        }.await()
    }
    suspend fun claimScheduler(orgId: String, groupId: String, userId: String, userName: String, leaseDuration: Long = 2 * 60 * 60 * 1000): Result<Boolean> = runCatching {
        val groupRef = firestore.collection("organizations/$orgId/groups").document(groupId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val currentSchedulerId = snapshot.getString("schedulerId")
            val expiresAt = snapshot.getTimestamp("schedulerLeaseExpiresAt")?.toDate()?.time

            if (currentSchedulerId != null && expiresAt != null && System.currentTimeMillis() < expiresAt) {
                return@runTransaction false
            }

            transaction.update(groupRef, mapOf(
                "schedulerId" to userId,
                "schedulerName" to userName,
                "schedulerLeaseExpiresAt" to com.google.firebase.Timestamp(
                    (System.currentTimeMillis() + leaseDuration) / 1000, 0
                )
            ))
            true
        }.await()
    }

    suspend fun renewSchedulerLease(orgId: String, groupId: String, userId: String, leaseDuration: Long = 2 * 60 * 60 * 1000): Result<Boolean> = runCatching {
        val groupRef = firestore.collection("organizations/$orgId/groups").document(groupId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val currentSchedulerId = snapshot.getString("schedulerId")

            if (currentSchedulerId != userId) {
                return@runTransaction false
            }

            transaction.update(groupRef, mapOf(
                "schedulerLeaseExpiresAt" to com.google.firebase.Timestamp(
                    (System.currentTimeMillis() + leaseDuration) / 1000, 0
                )
            ))
            true
        }.await()
    }

    suspend fun releaseScheduler(orgId: String, groupId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groups")
            .document(groupId)
            .update(mapOf(
                "schedulerId" to null,
                "schedulerName" to null,
                "schedulerLeaseExpiresAt" to null
            ))
            .await()
    }

    fun observeShiftTypeTemplates(): Flow<List<ShiftType>> {
        return firestore.collection("shiftTypeTemplates")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id)
                }
            }
    }

    fun observeShiftTypes(orgId: String, groupId: String): Flow<List<ShiftType>> {
        // 查詢一：組織層級的預設班別 (groupId 為 null)
        val orgShiftTypesFlow = firestore.collection("organizations/$orgId/shiftTypes")
            .whereEqualTo("groupId", null)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }

        // 查詢二：特定群組的自訂班別
        val groupShiftTypesFlow = firestore.collection("organizations/$orgId/shiftTypes")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }

        // 合併兩個 Flow 的結果
        return combine(orgShiftTypesFlow, groupShiftTypesFlow) { orgShifts, groupShifts ->
            orgShifts + groupShifts
        }
    }

    suspend fun addCustomShiftTypeForGroup(orgId: String, groupId: String, shiftType: ShiftType): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/shiftTypes").document()
        val newShiftType = shiftType.copy(
            id = docRef.id,
            orgId = orgId,
            groupId = groupId,
            isTemplate = false,
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newShiftType).await() // 直接傳遞物件
        docRef.id
    }

    suspend fun updateShiftType(orgId: String, shiftTypeId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/shiftTypes")
            .document(shiftTypeId)
            .update(updates)
            .await()
    }

    suspend fun deleteShiftType(orgId: String, shiftTypeId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/shiftTypes")
            .document(shiftTypeId)
            .delete()
            .await()
    }

    suspend fun createRequest(orgId: String, request: Request): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/requests").document()
        val requestWithId = request.copy(id = docRef.id, orgId = orgId)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeRequests(orgId: String): Flow<List<Request>> {
        return firestore.collection("organizations/$orgId/requests")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Request::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    fun observeRuleTemplates(): Flow<List<SchedulingRule>> {
        return firestore.collection("ruleTemplates")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(SchedulingRule::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun addRuleTemplate(rule: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("ruleTemplates").document()
        val template = rule.copy(id = docRef.id, isTemplate = true, orgId = "", groupId = null)
        docRef.set(template.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateRuleTemplate(ruleId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("ruleTemplates").document(ruleId).update(updates).await()
    }

    suspend fun deleteRuleTemplate(ruleId: String): Result<Unit> = runCatching {
        firestore.collection("ruleTemplates").document(ruleId).delete().await()
    }

    fun observeSchedulingRules(orgId: String, groupId: String): Flow<List<SchedulingRule>> {
        return firestore.collection("organizations/$orgId/schedulingRules")
            .whereIn("groupId", listOf(null, groupId))
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(SchedulingRule::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun enableTemplateForRule(orgId: String, ruleTemplate: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val newRule = ruleTemplate.copy(
            id = docRef.id,
            orgId = orgId,
            isTemplate = false,
            templateId = ruleTemplate.id,
            isEnabled = true,
            groupId = null,
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newRule.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun addCustomRuleForGroup(orgId: String, groupId: String, rule: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val newRule = rule.copy(
            id = docRef.id,
            orgId = orgId,
            groupId = groupId,
            isTemplate = false,
            templateId = null,
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newRule.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun addRuleForOrg(orgId: String, rule: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val ruleWithId = rule.copy(id = docRef.id, orgId = orgId)
        docRef.set(ruleWithId.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateRuleForOrg(orgId: String, ruleId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/schedulingRules")
            .document(ruleId)
            .update(updates)
            .await()
    }

    suspend fun deleteRuleForOrg(orgId: String, ruleId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/schedulingRules")
            .document(ruleId)
            .delete()
            .await()
    }

    suspend fun createSchedule(orgId: String, schedule: Schedule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedules").document()
        val scheduleWithId = schedule.copy(id = docRef.id, orgId = orgId)
        docRef.set(scheduleWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeSchedules(orgId: String, groupId: String): Flow<List<Schedule>> {
        return firestore.collection("organizations/$orgId/schedules")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Schedule::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }
    suspend fun createScheduleAndAssignments(orgId: String, schedule: Schedule, assignments: List<Assignment>): Result<String> = runCatching {
        val scheduleRef = firestore.collection("organizations/$orgId/schedules").document(schedule.id)

        firestore.runBatch { batch ->
            // 1. 建立 Schedule 文件
            batch.set(scheduleRef, schedule.toFirestoreMap())

            // 2. 在其子集合中建立所有 Assignment 文件
            assignments.forEach { assignment ->
                // 確保 assignment 物件中的 scheduleId 是正確的
                val finalAssignment = assignment.copy(scheduleId = schedule.id)
                val assignmentRef = scheduleRef.collection("assignments").document(finalAssignment.id)
                batch.set(assignmentRef, finalAssignment.toFirestoreMap())
            }
        }.await()

        // 回傳建立成功的 scheduleId
        schedule.id
    }
    //刪除班表
    suspend fun deleteScheduleAndAssignments(orgId: String, scheduleId: String): Result<Unit> = runCatching {
        val scheduleRef = firestore.collection("organizations/$orgId/schedules").document(scheduleId)
        val assignmentsQuery = scheduleRef.collection("assignments").get().await()

        firestore.runBatch { batch ->
            // 1. 刪除所有子集合中的 assignment 文件
            for (document in assignmentsQuery.documents) {
                batch.delete(document.reference)
            }
            // 2. 刪除 schedule 文件本身
            batch.delete(scheduleRef)
        }.await()
    }

    suspend fun updateScheduleAndAssignments(orgId: String, schedule: Schedule, assignments: List<Assignment>): Result<Unit> = runCatching {
        val scheduleRef = firestore.collection("organizations/$orgId/schedules").document(schedule.id)

        firestore.runBatch { batch ->
            // 1. 更新 Schedule 物件
            batch.set(scheduleRef, schedule.toFirestoreMap())

            // 2. 更新 (覆蓋) 所有相關的 Assignment
            assignments.forEach { assignment ->
                val assignmentRef = scheduleRef.collection("assignments").document(assignment.id)
                batch.set(assignmentRef, assignment.toFirestoreMap())
            }
        }.await()
    }

    suspend fun createAssignment(orgId: String, scheduleId: String, assignment: Assignment): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedules/$scheduleId/assignments").document()
        val assignmentWithId = assignment.copy(id = docRef.id, scheduleId = scheduleId)
        docRef.set(assignmentWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeAssignments(orgId: String, scheduleId: String): Flow<List<Assignment>> {
        return firestore.collection("organizations/$orgId/schedules/$scheduleId/assignments")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Assignment::class.java)?.copy(id = it.id, scheduleId = scheduleId)
                }
            }
    }

    fun observeManpowerPlan(orgId: String, groupId: String, month: String): Flow<ManpowerPlan?> {
        val planId = "${orgId}_${groupId}_${month}"
        return firestore.collection("organizations/$orgId/manpowerPlans")
            .document(planId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(ManpowerPlan::class.java)
            }
    }

    suspend fun saveManpowerPlan(orgId: String, plan: ManpowerPlan): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/manpowerPlans")
            .document(plan.id)
            .set(plan.toFirestoreMap())
            .await()
    }

    suspend fun getManpowerPlanOnce(orgId: String, groupId: String, month: String): ManpowerPlan? {
        val planId = "${orgId}_${groupId}_${month}"
        val snapshot = firestore.collection("organizations/$orgId/manpowerPlans")
            .document(planId)
            .get()
            .await()
        return snapshot.toObject(ManpowerPlan::class.java)
    }

    fun observeAdminStatus(userId: String): Flow<Boolean> {
        return firestore.collection("admins").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.exists() && snapshot.getString("role") == "superuser"
            }
    }

    suspend fun createTestData(dataSet: TestDataGenerator.TestDataSet): Result<Unit> = runCatching {
        val orgRef = firestore.collection("organizations").document(dataSet.organization.id)

        firestore.runBatch { batch ->
            batch.set(orgRef, dataSet.organization.toFirestoreMap())

            dataSet.users.forEach { user ->
                val userRef = orgRef.collection("users").document(user.id)
                batch.set(userRef, user.toFirestoreMap())
                // 同時將使用者資料寫入頂層集合，以確保資料一致性
                val topLevelUserRef = firestore.collection("users").document(user.id)
                batch.set(topLevelUserRef, user.toFirestoreMap())
            }

            dataSet.groups.forEach { group ->
                val groupRef = orgRef.collection("groups").document(group.id)
                batch.set(groupRef, group.toFirestoreMap())
            }

            dataSet.shiftTypes.forEach { shiftType ->
                val shiftTypeRef = orgRef.collection("shiftTypes").document(shiftType.id)
                batch.set(shiftTypeRef, shiftType) // 直接傳遞物件
            }

            dataSet.rules.forEach { rule ->
                val ruleRef = orgRef.collection("schedulingRules").document(rule.id)
                batch.set(ruleRef, rule.toFirestoreMap())
            }

            dataSet.schedules.forEach { schedule ->
                val scheduleRef = orgRef.collection("schedules").document(schedule.id)
                batch.set(scheduleRef, schedule.toFirestoreMap())

                val assignmentsForSchedule = dataSet.assignments.filter { it.scheduleId == schedule.id }
                assignmentsForSchedule.forEach { assignment ->
                    val assignmentRef = scheduleRef.collection("assignments").document(assignment.id)
                    batch.set(assignmentRef, assignment.toFirestoreMap())
                }
            }
        }.await()
    }
}
// 修改結束