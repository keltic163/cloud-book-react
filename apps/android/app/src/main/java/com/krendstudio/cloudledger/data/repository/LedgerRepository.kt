package com.krendstudio.cloudledger.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.krendstudio.cloudledger.data.local.dao.LedgerMetaDao
import com.krendstudio.cloudledger.data.local.dao.RecurringTemplateDao
import com.krendstudio.cloudledger.data.local.dao.SavedLedgerDao
import com.krendstudio.cloudledger.data.local.dao.SyncStateDao
import com.krendstudio.cloudledger.data.local.dao.TransactionDao
import com.krendstudio.cloudledger.data.local.dao.UserProfileDao
import com.krendstudio.cloudledger.data.local.entity.SyncStateEntity
import com.krendstudio.cloudledger.data.local.toEntity
import com.krendstudio.cloudledger.data.local.toModel
import com.krendstudio.cloudledger.data.remote.FirebaseProvider
import com.krendstudio.cloudledger.model.AppUser
import com.krendstudio.cloudledger.model.Defaults
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.LedgerMeta
import com.krendstudio.cloudledger.model.RecurringTemplate
import com.krendstudio.cloudledger.model.SavedLedger
import com.krendstudio.cloudledger.model.SystemAnnouncement
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LedgerRepository(
    private val savedLedgerDao: SavedLedgerDao,
    private val userProfileDao: UserProfileDao,
    private val transactionDao: TransactionDao,
    private val ledgerMetaDao: LedgerMetaDao,
    private val recurringTemplateDao: RecurringTemplateDao,
    private val syncStateDao: SyncStateDao,
    private val firestore: FirebaseFirestore = FirebaseProvider.firestore,
    private val functions: FirebaseFunctions = FirebaseProvider.functions
) {
    private fun transactionsSyncKey(ledgerId: String) = "transactions:$ledgerId"

    fun observeUserProfile(userUid: String): Flow<UserProfile> {
        return combine(
            userProfileDao.observeProfile(userUid),
            savedLedgerDao.observeSavedLedgers(userUid)
        ) { profileEntity, savedEntities ->
            val saved = savedEntities.map { it.toModel() }
            profileEntity?.toModel(saved) ?: UserProfile(
                uid = userUid,
                lastLedgerId = null,
                savedLedgers = saved
            )
        }
    }

    fun observeLedgerMeta(ledgerId: String): Flow<LedgerMeta> {
        return ledgerMetaDao.observe(ledgerId).map { entity ->
            entity?.toModel()
                ?: LedgerMeta(
                    expenseCategories = Defaults.expenseCategories,
                    incomeCategories = Defaults.incomeCategories,
                    members = emptyList()
                )
        }
    }

    fun observeSystemAnnouncement(): Flow<SystemAnnouncement?> = callbackFlow {
        val docRef = firestore.collection("app_settings").document("announcement_android")
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: emptyMap()
            val text = data["text"] as? String ?: ""
            val isEnabled = data["isEnabled"] as? Boolean ?: false
            val startAt = parseTimestamp(data["startAt"])
            val endAt = parseTimestamp(data["endAt"])
            val type = data["type"] as? String
            if (text.isBlank()) {
                trySend(null)
            } else {
                trySend(SystemAnnouncement(text, isEnabled, startAt, endAt, type))
            }
        }
        awaitClose { listener.remove() }
    }

    fun observeTransactions(ledgerId: String): Flow<List<Transaction>> {
        return transactionDao.observeByLedger(ledgerId)
            .map { entities ->
                entities.map { it.toModel() }.filterNot { it.deleted }
            }
    }

    fun observeRecurringTemplates(ledgerId: String, userId: String): Flow<List<RecurringTemplate>> {
        return recurringTemplateDao.observeByLedgerAndUser(ledgerId, userId)
            .map { items -> items.map { it.toModel() } }
    }

    suspend fun syncLedgerMeta(ledgerId: String): Result<LedgerMeta> {
        return runCatching {
            val snapshot = firestore.collection("ledgers").document(ledgerId).get().await()
            val data = snapshot.data ?: emptyMap()
            val expenseCategories = parseStringList(data["expenseCategories"])
                .ifEmpty { parseStringList(data["categories"]) }
                .ifEmpty { Defaults.expenseCategories }
            val incomeCategories = parseStringList(data["incomeCategories"])
                .ifEmpty { Defaults.incomeCategories }
            val members = parseMembers(data["members"])
            val meta = LedgerMeta(expenseCategories, incomeCategories, members)
            ledgerMetaDao.upsert(meta.toEntity(ledgerId, System.currentTimeMillis()))
            meta
        }
    }

    suspend fun syncTransactions(ledgerId: String, forceFull: Boolean = false): Result<Int> {
        return runCatching {
            val key = transactionsSyncKey(ledgerId)
            val lastSyncedAt = if (forceFull) 0L else syncStateDao.getValue(key) ?: 0L
            val collection = firestore.collection("ledgers")
                .document(ledgerId)
                .collection("transactions")
            if (forceFull || lastSyncedAt == 0L) {
                val snap = collection.orderBy("date", Query.Direction.DESCENDING)
                    .limit(500)
                    .get()
                    .await()
                val items = snap.documents.mapNotNull { doc ->
                    parseTransaction(doc.id, doc.data ?: emptyMap(), ledgerId)
                }.filterNot { it.deleted }
                transactionDao.deleteByLedger(ledgerId)
                if (items.isNotEmpty()) {
                    transactionDao.upsertAll(items.map { it.toEntity() })
                }
                val maxUpdatedAt = snap.documents.maxOfOrNull { it.getLong("updatedAt") ?: 0L }
                    ?: System.currentTimeMillis()
                syncStateDao.upsert(SyncStateEntity(key, maxUpdatedAt))
                return@runCatching items.size
            }

            val incSnap = collection.whereGreaterThan("updatedAt", lastSyncedAt)
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .get()
                .await()
            if (incSnap.isEmpty) {
                syncStateDao.upsert(SyncStateEntity(key, System.currentTimeMillis()))
                return@runCatching 0
            }
            val toUpsert = mutableListOf<Transaction>()
            val toDelete = mutableListOf<String>()
            var maxUpdatedAt = lastSyncedAt
            incSnap.documents.forEach { doc ->
                val tx = parseTransaction(doc.id, doc.data ?: emptyMap(), ledgerId) ?: return@forEach
                val updatedAt = tx.updatedAt ?: tx.createdAt
                if (updatedAt > maxUpdatedAt) {
                    maxUpdatedAt = updatedAt
                }
                if (tx.deleted) {
                    toDelete.add(tx.id)
                } else {
                    toUpsert.add(tx)
                }
            }
            if (toUpsert.isNotEmpty()) {
                transactionDao.upsertAll(toUpsert.map { it.toEntity() })
            }
            if (toDelete.isNotEmpty()) {
                transactionDao.deleteByIds(toDelete)
            }
            syncStateDao.upsert(SyncStateEntity(key, maxUpdatedAt))
            toUpsert.size + toDelete.size
        }
    }

    suspend fun syncRecurringTemplates(ledgerId: String, userId: String): Result<Int> {
        return runCatching {
            val snap = firestore.collection("recurring_templates")
                .whereEqualTo("ledgerId", ledgerId)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val items = snap.documents.mapNotNull { doc ->
                parseRecurringTemplate(doc.id, doc.data ?: emptyMap())
            }
            recurringTemplateDao.deleteByLedgerAndUser(ledgerId, userId)
            if (items.isNotEmpty()) {
                recurringTemplateDao.upsertAll(items.map { it.toEntity(ledgerId, userId) })
            }
            items.size
        }
    }

    suspend fun refreshUserProfile(user: AppUser): Result<UserProfile> {
        return runCatching {
            val doc = firestore.collection("users").document(user.uid).get().await()
            val savedLedgers = parseSavedLedgers(doc.get("savedLedgers"))
            val lastLedgerId = doc.getString("lastLedgerId")

            updateLocalProfile(user.uid, lastLedgerId, savedLedgers)

            if (!doc.exists()) {
                updateUserProfileRemote(user.uid, lastLedgerId, savedLedgers)
            }

            UserProfile(user.uid, lastLedgerId, savedLedgers)
        }
    }

    suspend fun createLedger(user: AppUser, name: String): Result<SavedLedger> {
        return runCatching {
            val now = System.currentTimeMillis()
            val docRef = firestore.collection("ledgers").document()
            val payload = mapOf(
                "name" to name,
                "createdAt" to now,
                "ownerUid" to user.uid,
                "members" to listOf(
                    mapOf(
                        "uid" to user.uid,
                        "displayName" to user.displayName,
                        "photoURL" to user.photoUrl,
                        "email" to user.email
                    )
                ),
                "expenseCategories" to Defaults.expenseCategories,
                "incomeCategories" to Defaults.incomeCategories
            )
            docRef.set(payload).await()

            val saved = savedLedgerDao.getSavedLedgers(user.uid).map { it.toModel() }
            val entry = SavedLedger(docRef.id, name, now)
            val newList = saved.filter { it.id != entry.id } + entry

            updateLocalProfile(user.uid, entry.id, newList)
            updateUserProfileRemote(user.uid, entry.id, newList)
            entry
        }
    }

    suspend fun joinLedger(user: AppUser, ledgerId: String): Result<SavedLedger> {
        return runCatching {
            val result = functions.getHttpsCallable("joinLedger")
                .call(mapOf("ledgerId" to ledgerId))
                .await()
            val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            val ok = data["ok"] as? Boolean ?: false
            if (!ok) {
                error("Join ledger failed")
            }
            val ledgerName = data["ledgerName"] as? String ?: "共享帳本"

            val now = System.currentTimeMillis()
            val saved = savedLedgerDao.getSavedLedgers(user.uid).map { it.toModel() }
            val entry = SavedLedger(ledgerId, ledgerName, now)
            val newList = saved.filter { it.id != ledgerId } + entry

            updateLocalProfile(user.uid, ledgerId, newList)
            updateUserProfileRemote(user.uid, ledgerId, newList)
            entry
        }
    }

    suspend fun switchLedger(user: AppUser, ledgerId: String): Result<Unit> {
        return runCatching {
            val saved = savedLedgerDao.getSavedLedgers(user.uid).map { it.toModel() }
            val updated = saved.map {
                if (it.id == ledgerId) it.copy(lastAccessedAt = System.currentTimeMillis()) else it
            }
            updateLocalProfile(user.uid, ledgerId, updated)
            updateUserProfileRemote(user.uid, ledgerId, updated)
        }
    }

    suspend fun leaveLedger(user: AppUser, ledgerId: String, currentLedgerId: String?): Result<String?> {
        return runCatching {
            functions.getHttpsCallable("leaveLedger")
                .call(mapOf("ledgerId" to ledgerId))
                .await()

            val saved = savedLedgerDao.getSavedLedgers(user.uid).map { it.toModel() }
            val updated = saved.filter { it.id != ledgerId }
            val nextLedgerId = if (currentLedgerId == ledgerId) {
                updated.firstOrNull()?.id
            } else {
                currentLedgerId
            }

            updateLocalProfile(user.uid, nextLedgerId, updated)
            updateUserProfileRemote(user.uid, nextLedgerId, updated)
            nextLedgerId
        }
    }

    suspend fun addTransaction(
        ledgerId: String,
        user: AppUser,
        amount: Double,
        type: TransactionType,
        category: String,
        description: String,
        rewards: Double,
        date: String,
        targetUserUid: String?
    ): Result<Unit> {
        return runCatching {
            val now = System.currentTimeMillis()
            val payload = mapOf(
                "amount" to amount,
                "type" to type.name,
                "category" to category,
                "description" to description,
                "rewards" to rewards,
                "date" to date,
                "creatorUid" to user.uid,
                "targetUserUid" to targetUserUid,
                "ledgerId" to ledgerId,
                "createdAt" to now,
                "updatedAt" to now,
                "deleted" to false
            )
            firestore.collection("ledgers")
                .document(ledgerId)
                .collection("transactions")
                .add(payload)
                .await()
        }
    }

    suspend fun updateCategories(
        ledgerId: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): Result<Unit> {
        return runCatching {
            val payload = mapOf(
                "expenseCategories" to expenseCategories,
                "incomeCategories" to incomeCategories
            )
            firestore.collection("ledgers")
                .document(ledgerId)
                .update(payload)
                .await()
            val current = ledgerMetaDao.get(ledgerId)?.toModel()
            val updatedMeta = LedgerMeta(
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                members = current?.members ?: emptyList()
            )
            ledgerMetaDao.upsert(updatedMeta.toEntity(ledgerId, System.currentTimeMillis()))
        }
    }

    suspend fun updateTransaction(
        ledgerId: String,
        transactionId: String,
        updates: Map<String, Any?>,
        expectedUpdatedAt: Long?
    ): Result<Unit> {
        return runCatching {
            val docRef = firestore.collection("ledgers")
                .document(ledgerId)
                .collection("transactions")
                .document(transactionId)
            val snapshot = docRef.get().await()
            val remoteUpdatedAt = snapshot.getLong("updatedAt")
            if (expectedUpdatedAt != null && remoteUpdatedAt != null && remoteUpdatedAt != expectedUpdatedAt) {
                error("資料已被其他人更新，請先同步再編輯")
            }
            val payload = updates.toMutableMap()
            payload["updatedAt"] = System.currentTimeMillis()
            docRef.update(payload).await()
        }
    }

    suspend fun deleteTransaction(
        ledgerId: String,
        transactionId: String
    ): Result<Unit> {
        return runCatching {
            val payload = mapOf(
                "deleted" to true,
                "deletedAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("ledgers")
                .document(ledgerId)
                .collection("transactions")
                .document(transactionId)
                .update(payload)
                .await()
        }
    }

    suspend fun createRecurringTemplate(
        ledgerId: String,
        userId: String,
        title: String,
        amount: Double,
        type: String,
        category: String,
        note: String?,
        intervalMonths: Int,
        executeDay: Int,
        nextRunAt: Long,
        totalRuns: Int?,
        remainingRuns: Int?
    ): Result<Unit> {
        return runCatching {
            val now = System.currentTimeMillis()
            val payload = mutableMapOf<String, Any?>(
                "ledgerId" to ledgerId,
                "userId" to userId,
                "title" to title,
                "amount" to amount,
                "type" to type,
                "category" to category,
                "note" to note,
                "intervalMonths" to intervalMonths,
                "executeDay" to executeDay,
                "nextRunAt" to java.util.Date(nextRunAt),
                "isActive" to true,
                "createdAt" to now,
                "updatedAt" to now
            )
            totalRuns?.let { payload["totalRuns"] = it }
            remainingRuns?.let { payload["remainingRuns"] = it }
            firestore.collection("recurring_templates")
                .add(payload)
                .await()
        }
    }

    suspend fun updateRecurringActive(templateId: String, isActive: Boolean): Result<Unit> {
        return runCatching {
            firestore.collection("recurring_templates")
                .document(templateId)
                .update(
                    mapOf(
                        "isActive" to isActive,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }
    }

    suspend fun deleteRecurringTemplate(templateId: String): Result<Unit> {
        return runCatching {
            firestore.collection("recurring_templates")
                .document(templateId)
                .delete()
                .await()
        }
    }

    suspend fun updateRecurringTemplate(
        templateId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return runCatching {
            firestore.collection("recurring_templates")
                .document(templateId)
                .update(updates)
                .await()
        }
    }

    suspend fun updateLedgerAlias(
        userUid: String,
        ledgerId: String,
        alias: String,
        lastLedgerId: String?,
        savedLedgers: List<SavedLedger>
    ): Result<Unit> {
        return runCatching {
            val safeAlias = alias.ifBlank { "帳本" }
            val updated = savedLedgers.map { ledger ->
                if (ledger.id == ledgerId) ledger.copy(alias = safeAlias) else ledger
            }
            updateLocalProfile(userUid, lastLedgerId, updated)
            updateUserProfileRemote(userUid, lastLedgerId, updated)
        }
    }

    suspend fun updateLocalProfile(
        userUid: String,
        lastLedgerId: String?,
        savedLedgers: List<SavedLedger>
    ) {
        userProfileDao.upsert(UserProfile(userUid, lastLedgerId, savedLedgers).toEntity())
        savedLedgerDao.clearForUser(userUid)
        if (savedLedgers.isNotEmpty()) {
            savedLedgerDao.upsertAll(savedLedgers.map { it.toEntity(userUid) })
        }
    }

    suspend fun clearLocalData(userUid: String) {
        savedLedgerDao.clearForUser(userUid)
        userProfileDao.delete(userUid)
    }

    private suspend fun updateUserProfileRemote(
        uid: String,
        lastLedgerId: String?,
        savedLedgers: List<SavedLedger>
    ) {
        val payload = mapOf(
            "lastLedgerId" to lastLedgerId,
            "savedLedgers" to savedLedgers.map { ledger ->
                mapOf(
                    "id" to ledger.id,
                    "alias" to ledger.alias,
                    "lastAccessedAt" to ledger.lastAccessedAt
                )
            }
        )
        firestore.collection("users").document(uid)
            .set(payload, SetOptions.merge())
            .await()
    }

    private fun parseSavedLedgers(raw: Any?): List<SavedLedger> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"] as? String ?: return@mapNotNull null
            val alias = map["alias"] as? String ?: "帳本"
            val lastAccessedAt = when (val value = map["lastAccessedAt"]) {
                is Number -> value.toLong()
                is Timestamp -> value.toDate().time
                else -> 0L
            }
            SavedLedger(id = id, alias = alias, lastAccessedAt = lastAccessedAt)
        }
    }

    private fun parseMembers(raw: Any?): List<LedgerMember> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val uid = map["uid"] as? String ?: return@mapNotNull null
            val displayName = map["displayName"] as? String ?: "成員"
            val photoUrl = map["photoURL"] as? String
            LedgerMember(uid = uid, displayName = displayName, photoUrl = photoUrl)
        }
    }

    private fun parseStringList(raw: Any?): List<String> {
        return (raw as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    }

    private fun parseTransaction(id: String, data: Map<String, Any>, fallbackLedgerId: String? = null): Transaction? {
        val amount = (data["amount"] as? Number)?.toDouble() ?: return null
        val typeValue = data["type"] as? String ?: TransactionType.EXPENSE.name
        val type = runCatching { TransactionType.valueOf(typeValue) }
            .getOrDefault(TransactionType.EXPENSE)
        val category = data["category"] as? String ?: ""
        val description = data["description"] as? String ?: ""
        val rewards = (data["rewards"] as? Number)?.toDouble() ?: 0.0
        val date = when (val value = data["date"]) {
            is String -> value
            is Timestamp -> value.toDate().toInstant().toString()
            else -> ""
        }
        val creatorUid = data["creatorUid"] as? String ?: ""
        val targetUserUid = data["targetUserUid"] as? String
        val ledgerId = data["ledgerId"] as? String ?: fallbackLedgerId.orEmpty()
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        val updatedAt = (data["updatedAt"] as? Number)?.toLong()
        val deleted = data["deleted"] as? Boolean ?: false
        return Transaction(
            id = id,
            amount = amount,
            type = type,
            category = category,
            description = description,
            rewards = rewards,
            date = date,
            creatorUid = creatorUid,
            targetUserUid = targetUserUid,
            ledgerId = ledgerId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deleted = deleted
        )
    }

    private fun parseRecurringTemplate(id: String, data: Map<String, Any>): RecurringTemplate? {
        val title = data["title"] as? String ?: return null
        val amount = (data["amount"] as? Number)?.toDouble() ?: return null
        val type = data["type"] as? String ?: return null
        val category = data["category"] as? String ?: ""
        val note = data["note"] as? String
        val intervalMonths = (data["intervalMonths"] as? Number)?.toInt() ?: 1
        val executeDay = (data["executeDay"] as? Number)?.toInt() ?: 1
        val nextRunAt = parseTimestamp(data["nextRunAt"])
        val isActive = data["isActive"] as? Boolean ?: true
        val createdAt = parseTimestamp(data["createdAt"])
        val updatedAt = parseTimestamp(data["updatedAt"])
        val totalRuns = (data["totalRuns"] as? Number)?.toInt()
        val remainingRuns = (data["remainingRuns"] as? Number)?.toInt()
        return RecurringTemplate(
            id = id,
            title = title,
            amount = amount,
            type = type,
            category = category,
            note = note,
            intervalMonths = intervalMonths,
            executeDay = executeDay,
            nextRunAt = nextRunAt,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
            totalRuns = totalRuns,
            remainingRuns = remainingRuns
        )
    }

    private fun parseTimestamp(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is java.util.Date -> value.time
            else -> 0L
        }
    }
}
