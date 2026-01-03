package com.krendstudio.cloudledger.data.local

import com.krendstudio.cloudledger.data.local.entity.LedgerMetaEntity
import com.krendstudio.cloudledger.data.local.entity.RecurringTemplateEntity
import com.krendstudio.cloudledger.data.local.entity.SavedLedgerEntity
import com.krendstudio.cloudledger.data.local.entity.TransactionEntity
import com.krendstudio.cloudledger.data.local.entity.UserProfileEntity
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.LedgerMeta
import com.krendstudio.cloudledger.model.RecurringTemplate
import com.krendstudio.cloudledger.model.SavedLedger
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

fun SavedLedgerEntity.toModel(): SavedLedger {
    return SavedLedger(
        id = ledgerId,
        alias = alias,
        lastAccessedAt = lastAccessedAt
    )
}

fun SavedLedger.toEntity(userUid: String): SavedLedgerEntity {
    return SavedLedgerEntity(
        userUid = userUid,
        ledgerId = id,
        alias = alias,
        lastAccessedAt = lastAccessedAt
    )
}

fun UserProfileEntity.toModel(savedLedgers: List<SavedLedger>): UserProfile {
    return UserProfile(
        uid = userUid,
        lastLedgerId = lastLedgerId,
        savedLedgers = savedLedgers
    )
}

fun UserProfile.toEntity(): UserProfileEntity {
    return UserProfileEntity(
        userUid = uid,
        lastLedgerId = lastLedgerId
    )
}

fun TransactionEntity.toModel(): Transaction {
    val type = runCatching { TransactionType.valueOf(type) }
        .getOrDefault(TransactionType.EXPENSE)
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

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        ledgerId = ledgerId,
        amount = amount,
        type = type.name,
        category = category,
        description = description,
        rewards = rewards,
        date = date,
        creatorUid = creatorUid,
        targetUserUid = targetUserUid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted
    )
}

fun LedgerMetaEntity.toModel(): LedgerMeta {
    return LedgerMeta(
        expenseCategories = decodeStringList(expenseCategoriesJson),
        incomeCategories = decodeStringList(incomeCategoriesJson),
        members = decodeMembers(membersJson)
    )
}

fun LedgerMeta.toEntity(ledgerId: String, updatedAt: Long): LedgerMetaEntity {
    return LedgerMetaEntity(
        ledgerId = ledgerId,
        expenseCategoriesJson = encodeStringList(expenseCategories),
        incomeCategoriesJson = encodeStringList(incomeCategories),
        membersJson = encodeMembers(members),
        updatedAt = updatedAt
    )
}

fun RecurringTemplateEntity.toModel(): RecurringTemplate {
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

fun RecurringTemplate.toEntity(ledgerId: String, userId: String): RecurringTemplateEntity {
    return RecurringTemplateEntity(
        id = id,
        ledgerId = ledgerId,
        userId = userId,
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

private fun encodeStringList(items: List<String>): String {
    val array = JSONArray()
    items.forEach { array.put(it) }
    return array.toString()
}

private fun decodeStringList(raw: String): List<String> {
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { index -> array.getString(index) }
    }.getOrDefault(emptyList())
}

private fun encodeMembers(items: List<LedgerMember>): String {
    val array = JSONArray()
    items.forEach { member ->
        val obj = JSONObject()
        obj.put("uid", member.uid)
        obj.put("displayName", member.displayName)
        obj.put("photoUrl", member.photoUrl)
        array.put(obj)
    }
    return array.toString()
}

private fun decodeMembers(raw: String): List<LedgerMember> {
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val uid = obj.optString("uid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LedgerMember(
                uid = uid,
                displayName = obj.optString("displayName").ifBlank { "成員" },
                photoUrl = obj.optString("photoUrl").ifBlank { null }
            )
        }
    }.getOrDefault(emptyList())
}
