// scheduler/data/remote/FirebaseDataSource.kt
package stevedaydream.scheduler.data.remote

// ... (其他 imports 保持不變) ...
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
import com.google.firebase.firestore.ktx.toObject

@Singleton
class FirebaseDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ... (observeRotationSettings, saveRotationSettings, scheduleOrganizationForDeletion, transferOwnership, leaveOrganization, updateEmploymentStatus 保持不變) ...
    fun observeRotationSettings(orgId: String, groupId: String): Flow<RotationSettingsContainer?> {
        return firestore.collection("organizations/$orgId/groups").document(groupId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    val settingsData = snapshot.get("rotationSettings") as? Map<String, Any>
                    settingsData?.let { RotationSettingsContainer.fromFirestoreMap(it) }
                } else {
                    null
                }
            }
    }
    suspend fun saveRotationSettings(orgId: String, groupId: String, settings: RotationSettingsContainer): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groups").document(groupId)
            .update("rotationSettings", settings.toFirestoreMap())
            .await()
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
            transaction.update(orgRef, "ownerId", newOwnerId)
            transaction.update(newOwnerUserRef, "role", "org_admin")
            transaction.update(newOwnerOrgUserRef, "role", "org_admin")
        }.await()
    }
    suspend fun leaveOrganization(orgId: String, userId: String): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(userId)
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)
        val userSnapshot = userRef.get().await()
        val user = userSnapshot.toObject(User::class.java)
        val groupsInOrg = firestore.collection("organizations/$orgId/groups")
            .whereArrayContains("memberIds", userId)
            .get()
            .await()

        firestore.runBatch { batch ->
            batch.update(userRef, "orgIds", FieldValue.arrayRemove(orgId))
            if (user?.currentOrgId == orgId) {
                batch.update(userRef, "currentOrgId", "")
            }
            batch.delete(subCollectionUserRef)
            groupsInOrg.documents.forEach { doc ->
                batch.update(doc.reference, "memberIds", FieldValue.arrayRemove(userId))
                // 同時從 userOrder 移除 (如果存在)
                batch.update(doc.reference, "userOrder", FieldValue.arrayRemove(userId))
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

    // ... (createOrganizationAndFirstUser, deleteOrganizationAndSubcollections, observeOrganization, observeAllOrganizations, observeOrganizationsByOwner, getOrganizationsByOwner, generateUniqueOrgCode 保持不變) ...
    suspend fun createOrganizationAndFirstUser(org: Organization, user: User): Result<String> = runCatching {
        val orgRef = firestore.collection("organizations").document()
        val topLevelUserRef = firestore.collection("users").document(user.id)
        val subCollectionUserRef = orgRef.collection("users").document(user.id)

        val orgWithId = org.copy(id = orgRef.id)
        val userWithOrgId = user.copy(
            orgIds = user.orgIds + orgRef.id,
            currentOrgId = orgRef.id
        )

        firestore.runBatch { batch ->
            batch.set(orgRef, orgWithId.toFirestoreMap())
            batch.set(topLevelUserRef, userWithOrgId.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge())
            batch.set(subCollectionUserRef, userWithOrgId.toFirestoreMap())

            val rulesCollection = orgRef.collection("schedulingRules")
            getDefaultSchedulingRules().forEach { rule ->
                val ruleRef = rulesCollection.document()
                batch.set(ruleRef, rule.toFirestoreMap())
            }

            val shiftTypesCollection = orgRef.collection("shiftTypes")
            getDefaultShiftTypes().forEach { shiftType ->
                val shiftTypeRef = shiftTypesCollection.document()
                val finalShiftType = shiftType.copy(id = shiftTypeRef.id, orgId = orgRef.id)
                batch.set(shiftTypeRef, finalShiftType)
            }
        }.await()
        orgRef.id
    }
    suspend fun deleteOrganizationAndSubcollections(orgId: String): Result<Unit> = runCatching {
        val orgRef = firestore.collection("organizations").document(orgId)
        val batch = firestore.batch()
        val subcollections = listOf(
            "users", "groups", "shiftTypes", "schedulingRules",
            "schedules", "invites", "groupJoinRequests", "manpowerPlans"
            // "reservations" // reservations 應該在 org 層級
        )
        for (collectionName in subcollections) {
            val collectionRef = orgRef.collection(collectionName)
            val documents = collectionRef.get().await()
            for (document in documents) {
                if (collectionName == "schedules") {
                    val assignments = document.reference.collection("assignments").get().await()
                    for (assignment in assignments) {
                        batch.delete(assignment.reference)
                    }
                    // 刪除 group 下的 rotationSchedules
                    val rotationSchedules = document.reference.collection("rotationSchedules").get().await()
                    for(rsDoc in rotationSchedules) {
                        batch.delete(rsDoc.reference)
                    }
                }
                // 刪除 group 下的 rotationSchedules (移到 group 刪除邏輯外)
                if (collectionName == "groups") {
                    val rotationSchedules = document.reference.collection("rotationSchedules").get().await()
                    for(rsDoc in rotationSchedules) {
                        batch.delete(rsDoc.reference)
                    }
                }
                batch.delete(document.reference)
            }
        }
        // 刪除 org 層級的 reservations
        val reservations = firestore.collection("organizations/$orgId/reservations").get().await() // Line 1005 (approx)
        reservations.documents.forEach { batch.delete(it.reference) } // Line 1007 (approx)
        batch.delete(orgRef)
        batch.commit().await()

        val usersQuery = firestore.collection("users").whereArrayContains("orgIds", orgId).get().await()
        firestore.runBatch { userBatch ->
            usersQuery.documents.forEach { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null && user.orgIds.size == 1 && user.orgIds.contains(orgId)) {
                    userBatch.delete(doc.reference)
                } else {
                    userBatch.update(doc.reference, "orgIds", FieldValue.arrayRemove(orgId))
                    if (doc.getString("currentOrgId") == orgId) {
                        userBatch.update(doc.reference, "currentOrgId", "")
                    }
                    // 清理 employmentStatus
                    userBatch.update(doc.reference, "employmentStatus.$orgId", FieldValue.delete())
                }
            }
        }.await()
    }
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

    // ... (createUser, createOrganizationInvite, observeAllUsers, observeUsers 保持不變) ...
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
        return firestore.collection("users")
            .whereArrayContains("orgIds", orgId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(User::class.java)?.copy(id = it.id)
                }
            }
    }

    // ... (observeOrganizationInvites, getOrganizationByInviteCode, validateAndUseInviteCode 保持不變) ...
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

    // ==================== 組織加入申請 (修改) ====================
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

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    suspend fun processJoinRequest( // 修改此函數
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String? // 可以指定加入的群組
    ): Result<Unit> = runCatching {
        val requestRef = firestore.collection("organizationJoinRequests").document(requestId)

        firestore.runTransaction { transaction ->
            val requestSnapshot = transaction.get(requestRef)
            val request = requestSnapshot.toObject(OrganizationJoinRequest::class.java)
                ?: throw IllegalArgumentException("找不到申請記錄: $requestId")

            val userRef = firestore.collection("users").document(request.userId)
            val userSnapshot = transaction.get(userRef) // 需要讀取 User 以更新 orgIds
            val user = userSnapshot.toObject(User::class.java)

            // 檢查指定的 targetGroupId 是否存在 (如果有的話)
            val groupRef = if (targetGroupId != null) {
                firestore.collection("organizations/$orgId/groups").document(targetGroupId)
            } else null
            val groupSnapshot = groupRef?.let { transaction.get(it) } // 讀取 Group 文件

            if (approve && groupRef != null && (groupSnapshot == null || !groupSnapshot.exists())) {
                throw IllegalArgumentException("指定的群組不存在: $targetGroupId")
            }

            // 更新申請狀態
            val statusUpdate = mapOf(
                "status" to if (approve) "approved" else "rejected",
                "processedBy" to processedBy,
                "processedAt" to com.google.firebase.Timestamp.now()
            )
            transaction.update(requestRef, statusUpdate)

            if (approve) {
                // 將 orgId 加入 User 的 orgIds
                transaction.update(userRef, "orgIds", FieldValue.arrayUnion(orgId))
                // 如果 User 沒有 currentOrgId，則設定
                if (user?.currentOrgId.isNullOrBlank()) {
                    transaction.update(userRef, "currentOrgId", orgId)
                }
                // 設定預設在職狀態
                transaction.update(userRef, "employmentStatus.$orgId", "active")


                // 如果指定了群組，則將 userId 加入群組
                groupRef?.let { targetGroupRef ->
                    val groupData = groupSnapshot?.toObject(Group::class.java)

                    // 加入 memberIds
                    transaction.update(targetGroupRef, "memberIds", FieldValue.arrayUnion(request.userId))

                    // 更新 userOrder：如果存在就加入，如果不存在則初始化
                    if (groupData?.userOrder != null) {
                        // 檢查 userId 是否已存在於 userOrder (避免重複加入)
                        if (!groupData.userOrder.contains(request.userId)) {
                            transaction.update(targetGroupRef, "userOrder", FieldValue.arrayUnion(request.userId))
                        }else{

                        }

                    } else {
                        // userOrder 不存在，使用目前的 memberIds 加上新成員來初始化
                        val initialUserOrder = (groupData?.memberIds ?: emptyList()) + request.userId
                        transaction.update(targetGroupRef, mapOf("userOrder" to initialUserOrder.distinct())) // distinct 確保唯一性
                    }
                }
            }
        }.await()
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    // ... (deactivateInvite, getOrganizationByCode, checkUserExists, updateUser, observeUserFromTopLevel 保持不變) ...
    suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/invites")
            .document(inviteId)
            .update("isActive", false)
            .await()
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

    // ... (createGroup, updateGroup, updateReservationStatus, observeGroups, observeGroup 保持不變) ...
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
    fun observeGroup(groupId: String): Flow<Group?> {
        // Note: This observes across all organizations if groupId is unique globally.
        // If groupId is only unique within an org, this needs orgId.
        // Assuming unique globally for simplicity, otherwise adjust the query.
        return firestore.collectionGroup("groups").whereEqualTo("id", groupId).limit(1)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    doc.toObject(Group::class.java)?.copy(id = doc.id, orgId = doc.reference.parent.parent!!.id)
                } else null
            }
    }

    // ... (observeReservations, saveReservation 保持不變) ...
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
        docRef.set(reservation.copy(id = docRef.id).toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge()).await()
    }


    // ==================== 組別加入申請 (修改) ====================
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

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    suspend fun updateUserGroup( // 修改此函數，使用 Transaction
        orgId: String,
        userId: String,
        newGroupId: String,
        oldGroupId: String?
    ): Result<Unit> = runCatching {
        val groupsCollection = firestore.collection("organizations/$orgId/groups")

        firestore.runTransaction { transaction ->
            // 從舊群組移除 (如果需要)
            if (oldGroupId != null && oldGroupId != newGroupId) {
                val oldGroupRef = groupsCollection.document(oldGroupId)
                // 讀取舊群組資料以檢查 userOrder
                val oldGroupSnapshot = transaction.get(oldGroupRef)
                val oldGroupData = oldGroupSnapshot.toObject(Group::class.java)

                transaction.update(oldGroupRef, "memberIds", FieldValue.arrayRemove(userId))
                // 同時從 userOrder 移除 (如果存在)
                if (oldGroupData?.userOrder != null) {
                    transaction.update(oldGroupRef, "userOrder", FieldValue.arrayRemove(userId))
                }
            }

            // 加入新群組
            val newGroupRef = groupsCollection.document(newGroupId)
            // 讀取新群組資料以檢查 userOrder
            val newGroupSnapshot = transaction.get(newGroupRef)
            val newGroupData = newGroupSnapshot.toObject(Group::class.java)

            // 加入 memberIds
            transaction.update(newGroupRef, "memberIds", FieldValue.arrayUnion(userId))

            // 加入 userOrder：如果存在就加入，如果不存在則初始化
            if (newGroupData?.userOrder != null) {
                // 檢查 userId 是否已存在於 userOrder (避免重複加入)
                if (!newGroupData.userOrder.contains(userId)) {
                    transaction.update(newGroupRef, "userOrder", FieldValue.arrayUnion(userId))
                }else{}
            } else {
                // userOrder 不存在，使用目前的 memberIds 加上新成員來初始化
                val initialUserOrder = (newGroupData?.memberIds ?: emptyList()) + userId
                transaction.update(newGroupRef, mapOf("userOrder" to initialUserOrder.distinct())) // distinct 確保唯一性
            }
        }.await()
    }
    suspend fun addUserToGroupAndOrg( // 修改此函數，使用 Transaction
        orgId: String,
        groupId: String,
        userId: String
    ): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(userId)
        val groupRef = firestore.collection("organizations/$orgId/groups").document(groupId)

        // 加入 Log
        android.util.Log.d("DataSource", "Attempting transaction: addUserToGroupAndOrg for user $userId in group $groupId, org $orgId")

        try { // 加入 try-catch
            firestore.runTransaction { transaction ->
                android.util.Log.d("DataSource", "Inside transaction...") // Log 進入 transaction

                // 1. 讀取 Group 資料
                val groupSnapshot = transaction.get(groupRef)
                val groupData = groupSnapshot.toObject(Group::class.java)
                android.util.Log.d("DataSource", "Read group snapshot. Exists: ${groupSnapshot.exists()}") // Log Group 讀取結果

                // 2. 更新 User 資料
                android.util.Log.d("DataSource", "Updating user document...") // Log 更新 User
                transaction.update(userRef, "orgIds", FieldValue.arrayUnion(orgId))
                transaction.update(userRef, "employmentStatus.$orgId", "active")

                // 3. 更新 Group 資料
                android.util.Log.d("DataSource", "Updating group document...") // Log 更新 Group
                transaction.update(groupRef, "memberIds", FieldValue.arrayUnion(userId))

                // 4. 更新 userOrder
                if (groupData?.userOrder != null) {
                    // 如果 userOrder 存在，檢查 userId 是否已存在，不存在才加入
                    if (!groupData.userOrder.contains(userId)) {
                        android.util.Log.d("DataSource", "Adding user to existing userOrder.") // Log
                        transaction.update(groupRef, "userOrder", FieldValue.arrayUnion(userId))
                    } else {
                        android.util.Log.d("DataSource", "User already in userOrder, skipping update.") // Log
                    }
                } else {
                    // userOrder 不存在，使用目前的 memberIds 加上新成員來初始化
                    val initialUserOrder = (groupData?.memberIds ?: emptyList()) + userId
                    android.util.Log.d("DataSource", "Initializing userOrder with: ${initialUserOrder.distinct()}") // Log
                    transaction.update(groupRef, mapOf("userOrder" to initialUserOrder.distinct())) // distinct 確保唯一性
                }
                android.util.Log.d("DataSource", "Updates defined, committing transaction...") // Log 準備提交
            }.await() // 等待交易完成
            android.util.Log.d("DataSource", "Transaction successful for addUserToGroupAndOrg") // 成功 Log
        } catch (e: Exception) {
            android.util.Log.e("DataSource", "Transaction FAILED for addUserToGroupAndOrg", e) // 失敗 Log
            throw e // 重新拋出，讓 runCatching 捕捉
        }
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    // ... (claimScheduler, renewSchedulerLease, releaseScheduler, observeShiftTypeTemplates, observeShiftTypes, addCustomShiftTypeForGroup, updateShiftType, deleteShiftType 保持不變) ...
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
        val orgShiftTypesFlow = firestore.collection("organizations/$orgId/shiftTypes")
            .whereEqualTo("groupId", null)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
        val groupShiftTypesFlow = firestore.collection("organizations/$orgId/shiftTypes")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
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
        docRef.set(newShiftType).await()
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


    // ... (createRequest, observeRequests, observeRuleTemplates, addRuleTemplate, updateRuleTemplate, deleteRuleTemplate, observeSchedulingRules, enableTemplateForRule, addCustomRuleForGroup, addRuleForOrg, updateRuleForOrg, deleteRuleForOrg 保持不變) ...
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


    // ... (createSchedule, observeSchedules, createScheduleAndAssignments, deleteScheduleAndAssignments, updateScheduleAndAssignments 保持不變) ...
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
            batch.set(scheduleRef, schedule.toFirestoreMap())
            assignments.forEach { assignment ->
                val finalAssignment = assignment.copy(scheduleId = schedule.id)
                val assignmentRef = scheduleRef.collection("assignments").document(finalAssignment.id)
                batch.set(assignmentRef, finalAssignment.toFirestoreMap())
            }
        }.await()
        schedule.id
    }
    suspend fun deleteScheduleAndAssignments(orgId: String, scheduleId: String): Result<Unit> = runCatching {
        val scheduleRef = firestore.collection("organizations/$orgId/schedules").document(scheduleId)
        val assignmentsQuery = scheduleRef.collection("assignments").get().await()

        firestore.runBatch { batch ->
            for (document in assignmentsQuery.documents) {
                batch.delete(document.reference)
            }
            batch.delete(scheduleRef)
        }.await()
    }
    suspend fun updateScheduleAndAssignments(orgId: String, schedule: Schedule, assignments: List<Assignment>): Result<Unit> = runCatching {
        val scheduleRef = firestore.collection("organizations/$orgId/schedules").document(schedule.id)

        firestore.runBatch { batch ->
            batch.set(scheduleRef, schedule.toFirestoreMap())
            assignments.forEach { assignment ->
                val assignmentRef = scheduleRef.collection("assignments").document(assignment.id)
                batch.set(assignmentRef, assignment.toFirestoreMap())
            }
        }.await()
    }

    // ... (createAssignment, observeAssignments, observeRotationSchedule, saveRotationSchedule 保持不變) ...
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
    fun observeRotationSchedule(orgId: String, groupId: String, month: String): Flow<Map<String, Map<String, String>>> {
        val docPath = "organizations/$orgId/groups/$groupId/rotationSchedules/$month"

        return firestore.document(docPath)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    (snapshot.data as? Map<String, Map<String, String>>) ?: emptyMap()
                } else {
                    emptyMap()
                }
            }
    }
    suspend fun saveRotationSchedule(orgId: String, groupId: String, month: String, rotationSchedule: Map<String, Map<String, String>>): Result<Unit> = runCatching {
        val docPath = "organizations/$orgId/groups/$groupId/rotationSchedules/$month"
        firestore.document(docPath)
            .set(rotationSchedule, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }


    // ... (observeManpowerPlan, saveManpowerPlan, getManpowerPlanOnce, observeAdminStatus, createTestData, deleteAllTestData 保持不變) ...
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
                val topLevelUserRef = firestore.collection("users").document(user.id)
                batch.set(topLevelUserRef, user.toFirestoreMap())
            }

            dataSet.groups.forEach { group ->
                val groupRef = orgRef.collection("groups").document(group.id)
                batch.set(groupRef, group.toFirestoreMap())
            }

            dataSet.shiftTypes.forEach { shiftType ->
                val shiftTypeRef = orgRef.collection("shiftTypes").document(shiftType.id)
                batch.set(shiftTypeRef, shiftType)
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
            dataSet.manpowerPlans.forEach { plan ->
                val planRef = orgRef.collection("manpowerPlans").document(plan.id)
                batch.set(planRef, plan.toFirestoreMap())
            }
//            dataSet.reservations.forEach { reservation ->
//                val reservationRef = orgRef.collection("reservations").document(reservation.id)
//                batch.set(reservationRef, reservation.toFirestoreMap())
//            }
        }.await()
    }
    suspend fun deleteAllTestData(): Result<Int> = runCatching {
        val testOrgPrefixes = listOf("自動生成測試公司", "完整模擬公司", "單元測試組織")
        var deletedCount = 0
        for (prefix in testOrgPrefixes) {
            val querySnapshot = firestore.collection("organizations")
                .whereGreaterThanOrEqualTo("orgName", prefix)
                .whereLessThanOrEqualTo("orgName", prefix + '\uf8ff')
                .get()
                .await()
            for (document in querySnapshot.documents) {
                deleteOrganizationAndSubcollections(document.id).getOrThrow()
                deletedCount++
            }
        }
        deletedCount
    }

    // Helper functions
    private fun getDefaultSchedulingRules(): List<SchedulingRule> { /* ... */
        return listOf(
            SchedulingRule(
                ruleName = "連續上班不超過N天", description = "避免員工因連續工作過多天而過勞。",
                ruleType = "soft", penaltyScore = -50, isEnabled = true, parameters = mapOf("maxDays" to "6")
            ),
            SchedulingRule(
                ruleName = "輪班間隔需大於N小時", description = "確保員工在兩次輪班之間有足夠的休息時間。",
                ruleType = "hard", penaltyScore = -1000, isEnabled = true, parameters = mapOf("minHours" to "11")
            )
        )
    }
    private fun getDefaultShiftTypes(): List<ShiftType> { /* ... */
        return listOf(
            ShiftType(name = "放假", shortCode = "OFF", startTime = "00:00", endTime = "00:00", color = "#D0021B"),
            ShiftType(name = "白班", shortCode = "S", startTime = "09:00", endTime = "17:00", color = "#4A90E2"),
            ShiftType(name = "值班(夜)", shortCode = "N", startTime = "21:00", endTime = "09:00", color = "#000000"),
            ShiftType(name = "值班(日)", shortCode = "D", startTime = "09:00", endTime = "21:00", color = "#7ED321")
        )
    }
}