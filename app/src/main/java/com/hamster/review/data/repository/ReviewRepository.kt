package com.hamster.review.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import android.content.Context
import com.hamster.review.data.db.AppDatabase
import com.hamster.review.data.db.DailyRecordEntity
import com.hamster.review.data.db.DailySubjectQuestionEntity
import com.hamster.review.data.db.DailySubjectRecordEntity
import com.hamster.review.data.db.QuestionDetail
import com.hamster.review.data.db.QuestionEntity
import com.hamster.review.data.db.QuestionOptionEntity
import com.hamster.review.data.db.QuestionTagCrossRef
import com.hamster.review.data.db.ReviewLogEntity
import com.hamster.review.data.db.SchedulerStateEntity
import com.hamster.review.data.db.SubjectEntity
import com.hamster.review.data.db.SubjectWithTodayCount
import com.hamster.review.data.db.TagEntity
import com.hamster.review.data.model.CardState
import com.hamster.review.data.model.DAILY_REVIEW_LIMIT_PER_SUBJECT
import com.hamster.review.data.model.FsrsRating
import com.hamster.review.data.model.QuestionType
import com.hamster.review.scheduler.FsrsScheduler
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.content.edit

private const val QUESTION_BANK_RELEASE_BASE =
    "https://github.com/super-super-hamster/review/releases/latest/download"

private const val QUESTION_BANK_PREFS = "official_bank"
private const val QUESTION_BANK_VERSION_KEY = "version"
private const val QUESTION_BANK_SHA_KEY = "sha256"

private const val NEW_QUESTION_QUOTA_RATIO = 0.3

class ReviewRepository(
    private val db: AppDatabase
) {
    private val subjectDao = db.subjectDao()
    private val questionDao = db.questionDao()
    private val schedulerDao = db.schedulerStateDao()
    private val reviewLogDao = db.reviewLogDao()
    private val questionOptionDao = db.questionOptionDao()
    private val tagDao = db.tagDao()
    private val questionTagDao = db.questionTagDao()
    private val dailySubjectRecordDao = db.dailySubjectRecordDao()
    private val dailySubjectQuestionDao = db.dailySubjectQuestionDao()

    fun observeSubjectsWithTodayCount(): Flow<List<SubjectWithTodayCount>> {
        return subjectDao.observeSubjectsWithTodayCount()
    }

    suspend fun addSubject(name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || subjectDao.countByName(trimmedName) > 0) {
            return false
        }
        val nextSortOrder = (subjectDao.maxSortOrder() ?: 0) + 1
        subjectDao.insertAll(
            listOf(
                SubjectEntity(
                    name = trimmedName,
                    sortOrder = nextSortOrder,
                    dailyLimit = DAILY_REVIEW_LIMIT_PER_SUBJECT
                )
            )
        )
        return true
    }

    suspend fun deleteSubject(subjectId: Long) {
        db.withTransaction {
            subjectDao.deleteById(subjectId)
        }
    }

    suspend fun addQuestion(
        subjectId: Long,
        type: QuestionType,
        content: String,
        answer: String?,
        options: List<Pair<String, Boolean>>
    ): Long {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val ids = questionDao.insertAll(
                listOf(
                    QuestionEntity(
                        subjectId = subjectId,
                        type = type,
                        content = content.trim(),
                        answer = answer?.trim()?.ifEmpty { null },
                        explanation = "",
                        createdAt = now
                    )
                )
            )
            val newId = ids[0]
            if (options.isNotEmpty()) {
                questionOptionDao.insertAll(
                    options.mapIndexed { index, (text, correct) ->
                        QuestionOptionEntity(
                            questionId = newId,
                            content = text.trim(),
                            isCorrect = correct,
                            sortOrder = index
                        )
                    }
                )
            }
            schedulerDao.upsert(
                SchedulerStateEntity(
                    questionId = newId,
                    state = CardState.NEW,
                    dueDate = now,
                    stability = 2.5,
                    difficulty = 5.0,
                    retrievability = 1.0,
                    reps = 0,
                    lapses = 0,
                    lastReviewAt = null
                )
            )
            newId
        }
    }

    fun observeSubjectCurrentMonthDailyRecords(subjectId: Long): Flow<List<DailyRecordEntity>> {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).toString()
        val end = today.toString()
        return reviewLogDao.observeSubjectDailyRecords(subjectId, start, end)
    }

    suspend fun isSubjectCompletedToday(subjectId: Long): Boolean {
        val today = LocalDate.now().toString()
        return dailySubjectRecordDao.get(subjectId, today)?.completed == true
    }

    suspend fun prepareTodayPool(subjectId: Long) {
        val today = LocalDate.now().toString()
        val rows = dailySubjectQuestionDao.getForDate(subjectId, today)
        val record = dailySubjectRecordDao.get(subjectId, today)

        if (rows.isNotEmpty()) {
            val completedCount = rows.count { it.completed }
            val allCompleted = completedCount >= rows.size
            if (record == null ||
                record.pushedCount != rows.size ||
                record.completedCount != completedCount ||
                record.completed != allCompleted
            ) {
                dailySubjectRecordDao.upsert(
                    DailySubjectRecordEntity(
                        subjectId = subjectId,
                        date = today,
                        pushedCount = rows.size,
                        completedCount = completedCount,
                        completed = allCompleted,
                        completedAt = if (allCompleted) {
                            record?.completedAt ?: System.currentTimeMillis()
                        } else {
                            null
                        }
                    )
                )
            }
            return
        }

        val limit = subjectDao.getDailyLimit(subjectId)
        val target = min(limit, questionDao.countUnmastered(subjectId))
        val pool = if (target <= 0) {
            emptyList()
        } else {
            selectPoolQuestions(subjectId, limit = target, excludeQuestionIds = emptySet())
        }

        db.withTransaction {
            dailySubjectQuestionDao.insertAll(
                pool.map { question ->
                    DailySubjectQuestionEntity(
                        subjectId = subjectId,
                        date = today,
                        questionId = question.question.id,
                        completed = false,
                        wrongPending = false
                    )
                }
            )
            dailySubjectRecordDao.upsert(
                DailySubjectRecordEntity(
                    subjectId = subjectId,
                    date = today,
                    pushedCount = pool.size,
                    completedCount = 0,
                    completed = false,
                    completedAt = null
                )
            )
        }
    }

    private suspend fun selectPoolQuestions(
        subjectId: Long,
        limit: Int,
        excludeQuestionIds: Set<Long>
    ): List<QuestionDetail> {
        if (limit <= 0) return emptyList()
        val now = System.currentTimeMillis()
        val random = Random(subjectId * 1_000_003L + LocalDate.now().toEpochDay())

        val due = questionDao.getDuePoolQuestions(subjectId, now)
            .filterNot { it.question.id in excludeQuestionIds }
        val newQuestions = questionDao.getNewPoolQuestions(subjectId)
            .filterNot { it.question.id in excludeQuestionIds }

        val dueDateById = schedulerDao.getStatesForSubject(subjectId)
            .associate { it.questionId to it.dueDate }

        val tieredDue = due
            .groupBy { overdueTier(now, dueDateById[it.question.id] ?: now) }
            .let { groups ->
                (3 downTo 0).flatMap { tier -> groups[tier].orEmpty().shuffled(random) }
            }
        val shuffledNew = newQuestions.shuffled(random)

        var newQuota = ceil(limit * NEW_QUESTION_QUOTA_RATIO).toInt().coerceAtLeast(1)
        if (tieredDue.isNotEmpty()) newQuota = min(newQuota, limit - 1)
        newQuota = min(newQuota, shuffledNew.size)
        val dueQuota = (limit - newQuota).coerceAtLeast(0)

        val picked = ArrayList<QuestionDetail>(limit)
        picked.addAll(tieredDue.take(dueQuota))
        picked.addAll(shuffledNew.take(limit - picked.size))
        if (picked.size < limit) {
            picked.addAll(tieredDue.drop(dueQuota).take(limit - picked.size))
        }
        return picked
    }

    private fun overdueTier(now: Long, dueDate: Long): Int {
        val overdueDays = (now - dueDate).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)
        return when {
            overdueDays <= 1.0 -> 0
            overdueDays <= 3.0 -> 1
            overdueDays <= 7.0 -> 2
            else -> 3
        }
    }

    suspend fun getSubjectDailyQuestions(subjectId: Long): List<DailySubjectQuestionEntity> {
        val today = LocalDate.now().toString()
        return dailySubjectQuestionDao.getForDate(subjectId, today)
    }

    // 再来一组
    suspend fun startAnotherGroup(subjectId: Long): Int {
        val today = LocalDate.now().toString()
        val record = dailySubjectRecordDao.get(subjectId, today)
        if (record == null) {
            prepareTodayPool(subjectId)
            return dailySubjectQuestionDao.getForDate(subjectId, today).count { !it.completed }
        }

        val rows = dailySubjectQuestionDao.getForDate(subjectId, today)
        val extra = selectPoolQuestions(
            subjectId,
            limit = subjectDao.getDailyLimit(subjectId),
            excludeQuestionIds = rows.map { it.questionId }.toSet()
        )
        if (extra.isEmpty()) return 0

        db.withTransaction {
            dailySubjectQuestionDao.insertAll(
                extra.map { question ->
                    DailySubjectQuestionEntity(
                        subjectId = subjectId,
                        date = today,
                        questionId = question.question.id,
                        completed = false,
                        wrongPending = false
                    )
                }
            )
            val finalRows = dailySubjectQuestionDao.getForDate(subjectId, today)
            val finalPending = finalRows.count { !it.completed }
            dailySubjectRecordDao.upsert(
                record.copy(
                    pushedCount = finalRows.size,
                    completedCount = finalRows.size - finalPending,
                    completed = false,
                    completedAt = null
                )
            )
        }
        return extra.size
    }

    suspend fun hasMorePoolCandidates(subjectId: Long): Boolean {
        val today = LocalDate.now().toString()
        return questionDao.countPoolCandidates(subjectId, today, System.currentTimeMillis()) > 0
    }

    suspend fun markDailyQuestionCompleted(subjectId: Long, questionId: Long) {
        val today = LocalDate.now().toString()
        dailySubjectQuestionDao.updateStatus(
            subjectId = subjectId,
            date = today,
            questionId = questionId,
            completed = true,
            wrongPending = false
        )
    }

    suspend fun markDailyQuestionWrong(subjectId: Long, questionId: Long) {
        val today = LocalDate.now().toString()
        dailySubjectQuestionDao.updateStatus(
            subjectId = subjectId,
            date = today,
            questionId = questionId,
            completed = false,
            wrongPending = true
        )
    }


    suspend fun incrementSubjectCompletedCount(subjectId: Long) {
        val today = LocalDate.now().toString()
        val existing = dailySubjectRecordDao.get(subjectId, today) ?: return
        val newCompletedCount = existing.completedCount + 1
        dailySubjectRecordDao.upsert(
            existing.copy(
                completedCount = newCompletedCount,
                completed = newCompletedCount >= existing.pushedCount,
                completedAt = if (newCompletedCount >= existing.pushedCount) {
                    System.currentTimeMillis()
                } else {
                    existing.completedAt
                }
            )
        )
    }

    suspend fun markSubjectCompleted(subjectId: Long) {
        val today = LocalDate.now().toString()
        val existing = dailySubjectRecordDao.get(subjectId, today) ?: return
        if (existing.pushedCount <= 0) return
        dailySubjectRecordDao.upsert(
            existing.copy(
                completed = true,
                completedAt = existing.completedAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun setSubjectDailyLimit(subjectId: Long, newLimit: Int) {
        val today = LocalDate.now().toString()

        val record = dailySubjectRecordDao.get(subjectId, today) ?: run {
            subjectDao.updateDailyLimit(subjectId, newLimit)
            return
        }
        val rows = dailySubjectQuestionDao.getForDate(subjectId, today)
        if (rows.isEmpty()) {
            subjectDao.updateDailyLimit(subjectId, newLimit)
            return
        }

        val completedCount = rows.count { it.completed }
        val pendingRows = rows.filterNot { it.completed }
        val targetPending = (newLimit - completedCount).coerceAtLeast(0)
        val surplus = pendingRows.size - targetPending

        val removeIds = if (surplus > 0) {
            // 清掉不再需要的多余待做题
            pendingRows.takeLast(surplus).map { it.questionId }
        } else {
            emptyList()
        }
        val extra = if (surplus < 0) {
            selectPoolQuestions(
                subjectId,
                limit = -surplus,
                excludeQuestionIds = rows.map { it.questionId }.toSet()
            )
        } else {
            emptyList()
        }

        db.withTransaction {
            subjectDao.updateDailyLimit(subjectId, newLimit)
            if (removeIds.isNotEmpty()) {
                dailySubjectQuestionDao.deleteByQuestionIds(subjectId, today, removeIds)
            }
            if (extra.isNotEmpty()) {
                dailySubjectQuestionDao.insertAll(
                    extra.map { question ->
                        DailySubjectQuestionEntity(
                            subjectId = subjectId,
                            date = today,
                            questionId = question.question.id,
                            completed = false,
                            wrongPending = false
                        )
                    }
                )
            }

            val finalRows = dailySubjectQuestionDao.getForDate(subjectId, today)
            val finalPending = finalRows.count { !it.completed }
            val finalCompleted = finalRows.size - finalPending
            dailySubjectRecordDao.upsert(
                record.copy(
                    pushedCount = finalRows.size,
                    completedCount = finalCompleted,
                    completed = finalRows.isNotEmpty() && finalPending == 0,
                    completedAt = if (finalRows.isNotEmpty() && finalPending == 0) {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
                )
            )
        }
    }

    suspend fun updateQuestion(
        questionId: Long,
        content: String,
        answer: String?,
        explanation: String,
        options: List<Triple<Long, String, Boolean>>?
    ) {
        db.withTransaction {
            questionDao.updateQuestionContent(questionId, content)
            questionDao.updateQuestionAnswer(questionId, answer)
            questionDao.updateQuestionExplanation(questionId, explanation)
            options?.forEach { (optionId, text, correct) ->
                questionOptionDao.updateContent(optionId, text)
                questionOptionDao.updateCorrect(optionId, correct)
            }
        }
    }

    suspend fun deleteQuestion(questionId: Long) {
        db.withTransaction {
            questionDao.deleteQuestion(questionId)
        }
    }


    suspend fun getSubjectCurrentMonthDailyRecordsOnce(subjectId: Long): List<DailyRecordEntity> {
        return observeSubjectCurrentMonthDailyRecords(subjectId).first()
    }

    suspend fun getQuestionDetailsByIds(ids: List<Long>): List<QuestionDetail> {
        return questionDao.getQuestionDetailsByIds(ids)
    }

    suspend fun getMasteredQuestionsOnce(subjectId: Long): List<QuestionDetail> {
        return questionDao.getMasteredQuestionDetails(subjectId)
    }

    suspend fun getSubjectQuestionDetails(subjectId: Long): List<QuestionDetail> {
        return questionDao.getSubjectQuestionDetails(subjectId)
    }

    suspend fun getQuestionDetailOnce(questionId: Long): QuestionDetail? {
        return questionDao.getQuestionDetail(questionId)
    }

    suspend fun cleanupOldReviewLogs(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        reviewLogDao.deleteOlderThan(cutoff)
    }

    suspend fun submitAnswer(
        questionId: Long,
        isCorrect: Boolean,
        responseTimeMs: Long,
        ignoreResponseTime: Boolean = false
    ): Result<Unit> = runCatching {
        val question = questionDao.getQuestionDetail(questionId)?.question
            ?: error("Question not found: $questionId")

        val rating = when {
            !isCorrect -> FsrsRating.AGAIN
            ignoreResponseTime -> FsrsRating.GOOD
            else -> {
                val baseline = calculateBaseline(
                    questionId = questionId,
                    subjectId = question.subjectId,
                    type = question.type
                )
                mapToRating(isCorrect, responseTimeMs, baseline)
            }
        }

        db.withTransaction {
            val oldState = schedulerDao.getSchedulerState(questionId)
            val now = System.currentTimeMillis()
            val newState = FsrsScheduler.review(oldState, rating, now)
                .copy(questionId = questionId)

            schedulerDao.upsert(newState)

            reviewLogDao.insert(
                ReviewLogEntity(
                    questionId = questionId,
                    subjectId = question.subjectId,
                    reviewedAt = now,
                    rating = rating,
                    isCorrect = isCorrect,
                    responseTimeMs = responseTimeMs,
                    intervalDays = FsrsScheduler.intervalForRetention(newState.stability),
                    retrievability = newState.retrievability
                )
            )

            updateDailyRecord(now, isCorrect)
        }
    }

    suspend fun toggleMastered(questionId: Long, mastered: Boolean) {
        val now = if (mastered) System.currentTimeMillis() else null
        questionDao.updateMastered(questionId, mastered, now)
    }

    suspend fun seedIfEmpty() {
        if (subjectDao.count() > 0) return

        db.withTransaction {
            val now = System.currentTimeMillis()
            val subjectIds = subjectDao.insertAll(
                listOf(
                    SubjectEntity(name = "数据结构", sortOrder = 1),
                    SubjectEntity(name = "计算机网络", sortOrder = 2)
                )
            )
            val dsId = subjectIds[0]
            val netId = subjectIds[1]

            addSeedQuestion(
                subjectId = dsId,
                type = QuestionType.SINGLE_CHOICE,
                content = "以下哪个是线性结构？",
                explanation = "数组是线性结构；树和图是非线性结构。",
                answer = null,
                options = listOf("数组" to true, "树" to false, "图" to false),
                tags = listOf("数据结构/线性表"),
                now = now
            )
            addSeedQuestion(
                subjectId = dsId,
                type = QuestionType.MULTIPLE_CHOICE,
                content = "下列哪些属于线性表？",
                explanation = "数组、链表、栈、队列都是线性表。",
                answer = null,
                options = listOf("数组" to true, "链表" to true, "栈" to true, "二叉树" to false),
                tags = listOf("数据结构/线性表"),
                now = now
            )
            addSeedQuestion(
                subjectId = dsId,
                type = QuestionType.TRUE_FALSE,
                content = "栈是一种先进先出的数据结构。",
                explanation = "栈是后进先出。",
                answer = "false",
                options = listOf("对" to false, "错" to true),
                tags = listOf("数据结构/栈"),
                now = now
            )
            addSeedQuestion(
                subjectId = dsId,
                type = QuestionType.FILL_BLANK,
                content = "在长度为 n 的顺序表中插入一个元素，平均需要移动 __ 个元素。",
                explanation = "n/2",
                answer = "n/2",
                options = emptyList(),
                tags = listOf("数据结构/线性表"),
                now = now
            )
            addSeedQuestion(
                subjectId = netId,
                type = QuestionType.SINGLE_CHOICE,
                content = "TCP 建立连接需要几次握手？",
                explanation = "TCP 三次握手。",
                answer = null,
                options = listOf("1" to false, "2" to false, "3" to true, "4" to false),
                tags = listOf("计算机网络/TCP"),
                now = now
            )
            addSeedQuestion(
                subjectId = netId,
                type = QuestionType.MULTIPLE_CHOICE,
                content = "TCP 拥塞控制包含以下哪些算法？",
                explanation = "慢启动、拥塞避免、快重传、快恢复。",
                answer = null,
                options = listOf("慢启动" to true, "拥塞避免" to true, "快重传" to true, "流量控制" to false),
                tags = listOf("计算机网络/TCP/拥塞控制"),
                now = now
            )
            addSeedQuestion(
                subjectId = netId,
                type = QuestionType.TRUE_FALSE,
                content = "UDP 提供可靠传输。",
                explanation = "UDP 不提供可靠传输。",
                answer = "false",
                options = listOf("对" to false, "错" to true),
                tags = listOf("计算机网络/传输层"),
                now = now
            )
            addSeedQuestion(
                subjectId = netId,
                type = QuestionType.FILL_BLANK,
                content = "HTTP 默认端口号是 __。",
                explanation = "80",
                answer = "80",
                options = emptyList(),
                tags = listOf("计算机网络/应用层"),
                now = now
            )
        }
    }

    private suspend fun addSeedQuestion(
        subjectId: Long,
        type: QuestionType,
        content: String,
        explanation: String,
        answer: String?,
        options: List<Pair<String, Boolean>>,
        tags: List<String>,
        now: Long
    ) {
        val questionIds = questionDao.insertAll(
            listOf(
                QuestionEntity(
                    subjectId = subjectId,
                    type = type,
                    content = content,
                    answer = answer,
                    explanation = explanation,
                    createdAt = now
                )
            )
        )
        val questionId = questionIds[0]

        val tagIds = tags.map { tag ->
            val existing = tagDao.getByFullPath(tag)
            existing?.id ?: tagDao.insertAll(listOf(TagEntity(name = tag, fullPath = tag)))[0]
        }
        questionTagDao.insertAll(tagIds.map { tagId -> QuestionTagCrossRef(questionId, tagId) })

        schedulerDao.upsert(
            SchedulerStateEntity(
                questionId = questionId,
                state = CardState.NEW,
                dueDate = now,
                stability = 2.5,
                difficulty = 5.0,
                retrievability = 1.0,
                reps = 0,
                lapses = 0,
                lastReviewAt = null
            )
        )
    }

    private fun mapToRating(isCorrect: Boolean, responseTimeMs: Long, baseline: Long): FsrsRating {
        if (!isCorrect) return FsrsRating.AGAIN

        val ratio = responseTimeMs.toDouble() / baseline.coerceAtLeast(1).toDouble()
        return when {
            ratio >= 1.5 -> FsrsRating.HARD
            ratio <= 0.5 -> FsrsRating.EASY
            else -> FsrsRating.GOOD
        }
    }

    private suspend fun calculateBaseline(
        questionId: Long,
        subjectId: Long,
        type: QuestionType
    ): Long {
        val questionTimes = questionDao.getRecentResponseTimes(questionId)
        if (questionTimes.size >= 3) return median(questionTimes)

        val subjectTimes = questionDao.getSubjectResponseTimes(subjectId)
        if (subjectTimes.isNotEmpty()) return median(subjectTimes)

        val typeTimes = questionDao.getTypeResponseTimes(type.name)
        if (typeTimes.isNotEmpty()) return median(typeTimes)

        return 5000L
    }

    private fun median(sorted: List<Long>): Long {
        val values = sorted.sorted()
        val size = values.size
        if (size == 0) return 5000L
        return if (size % 2 == 1) {
            values[size / 2]
        } else {
            (values[size / 2 - 1] + values[size / 2]) / 2
        }
    }

    private suspend fun updateDailyRecord(reviewedAt: Long, isCorrect: Boolean) {
        val date = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(reviewedAt),
            ZoneId.systemDefault()
        ).toString()

        val record = reviewLogDao.getDailyRecordEntity(date)
            ?: DailyRecordEntity(
                date = date,
                reviewCount = 0,
                correctCount = 0,
                uniqueQuestionCount = 0
            )

        reviewLogDao.upsertDailyRecord(
            record.copy(
                reviewCount = record.reviewCount + 1,
                correctCount = record.correctCount + (if (isCorrect) 1 else 0),
                uniqueQuestionCount = record.uniqueQuestionCount
            )
        )
    }

    suspend fun updateOfficialBankFromGitHub(
        context: Context,
        onProgress: suspend (text: String, progress: Float, cancelable: Boolean) -> Unit = { _, _, _ -> }
    ): String = try {
        withContext(Dispatchers.IO) { doFetchAndApplyOfficialBank(context, onProgress) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "更新失败：${e.message ?: e.javaClass.simpleName}"
    }

    private suspend fun doFetchAndApplyOfficialBank(
        context: Context,
        onProgress: suspend (text: String, progress: Float, cancelable: Boolean) -> Unit
    ): String {
        onProgress("正在检查题库版本", 0.02f, true)
        val metaText = fetchText("$QUESTION_BANK_RELEASE_BASE/questionbank.version.json")
        val meta = JSONObject(metaText)
        val version = meta.getInt("version")
        val sha = meta.optString("sha256")
        val fileName = meta.optString("file", "questionbank.db")

        onProgress("正在对比题库版本", 0.07f, true)
        val prefs = context.getSharedPreferences(QUESTION_BANK_PREFS, Context.MODE_PRIVATE)
        val localVersion = prefs.getInt(QUESTION_BANK_VERSION_KEY, 0)
        val localSha = prefs.getString(QUESTION_BANK_SHA_KEY, "") ?: ""
        if (version < localVersion || (version == localVersion && localSha.equals(sha, ignoreCase = true))) {
            onProgress("题库已是最新版本", 1f, true)
            return "题库已是最新版本"
        }

        onProgress("正在下载题库 0%", 0.10f, true)
        val bytes = fetchBytes("$QUESTION_BANK_RELEASE_BASE/$fileName") { downloaded, total ->
            val ratio = if (total > 0) (downloaded.toDouble() / total).toFloat() else 0f
            onProgress("正在下载题库 ${(ratio * 100).toInt()}%", 0.10f + 0.60f * ratio, true)
        }

        onProgress("正在校验题库文件", 0.72f, true)
        if (sha.isNotBlank() && !sha256Hex(bytes).equals(sha, ignoreCase = true)) {
            return "更新失败：题库文件校验不通过"
        }

        val cache = File(context.cacheDir, "official_bank_$version.db")
        cache.writeBytes(bytes)
        try {
            onProgress("正在读取题库", 0.78f, true)
            val remote = openRemoteBank(context, cache)
            try {
                onProgress("正在写入本地题库 0%", 0.85f, false)
                applyOfficialBank(remote) { done, total ->
                    val ratio = if (total > 0) done.toFloat() / total else 1f
                    onProgress(
                        "正在写入本地题库 ${(ratio * 100).toInt()}%",
                        0.85f + 0.14f * ratio,
                        false
                    )
                }
                onProgress("正在保存题库信息", 0.99f, false)
                prefs.edit {
                    putInt(QUESTION_BANK_VERSION_KEY, version)
                        .putString(QUESTION_BANK_SHA_KEY, sha)
                }
                onProgress("题库更新完成", 1f, false)
                return "题库更新成功(版本 $version)"
            } finally {
                remote.close()
            }
        } finally {
            cache.delete()
        }
    }

    private suspend fun applyOfficialBank(
        remote: AppDatabase,
        onProgress: suspend (done: Int, total: Int) -> Unit
    ) {
        val remoteSubjects = remote.subjectDao().getAll()
        val remoteQuestions = remote.questionDao().getAllQuestionDetails()
        val total = remoteQuestions.size

        db.withTransaction {
            val localSubjectsByName = subjectDao.getAll().associateBy { it.name }
            val subjectIdByRemoteId = mutableMapOf<Long, Long>()
            remoteSubjects.forEach { remoteSubject ->
                val existing = localSubjectsByName[remoteSubject.name]
                subjectIdByRemoteId[remoteSubject.id] = existing?.id ?: subjectDao.insertAll(
                    listOf(
                        SubjectEntity(
                            name = remoteSubject.name,
                            sortOrder = remoteSubject.sortOrder,
                            dailyLimit = remoteSubject.dailyLimit
                        )
                    )
                )[0]
            }

            val remoteOfficialIds = remoteQuestions.map { it.question.id }.toSet()
            questionDao.getAllOfficial().forEach { local ->
                val officialId = local.officialId
                if (officialId != null && officialId !in remoteOfficialIds) {
                    questionDao.deleteQuestion(local.id)
                }
            }

            remoteQuestions.forEachIndexed { index, remoteDetail ->
                val subjectId = subjectIdByRemoteId[remoteDetail.question.subjectId]
                if (subjectId != null) {
                    val localId = questionDao.getByOfficialId(remoteDetail.question.id)?.id
                    if (localId == null) {
                        // 新增题
                        val now = System.currentTimeMillis()
                        val newId = questionDao.insertAll(
                            listOf(
                                QuestionEntity(
                                    subjectId = subjectId,
                                    officialId = remoteDetail.question.id,
                                    type = remoteDetail.question.type,
                                    content = remoteDetail.question.content,
                                    answer = remoteDetail.question.answer,
                                    explanation = remoteDetail.question.explanation,
                                    createdAt = now
                                )
                            )
                        )[0]
                        replaceOptionsAndTags(newId, remoteDetail)
                        schedulerDao.upsert(
                            SchedulerStateEntity(
                                questionId = newId,
                                state = CardState.NEW,
                                dueDate = now,
                                stability = 2.5,
                                difficulty = 5.0,
                                retrievability = 1.0,
                                reps = 0,
                                lapses = 0,
                                lastReviewAt = null
                            )
                        )
                    } else {
                        val localDetail = questionDao.getQuestionDetail(localId)
                        val localExplanation = localDetail?.question?.explanation.orEmpty()
                        questionDao.updateQuestionFields(
                            id = localId,
                            subjectId = subjectId,
                            type = remoteDetail.question.type.name,
                            content = remoteDetail.question.content,
                            answer = remoteDetail.question.answer,
                            explanation = if (localExplanation.isNotBlank()) {
                                localExplanation
                            } else {
                                remoteDetail.question.explanation
                            }
                        )
                        replaceOptionsAndTags(localId, remoteDetail)
                    }
                }
                onProgress(index + 1, total)
            }
        }
    }

    private suspend fun replaceOptionsAndTags(localQuestionId: Long, remoteDetail: QuestionDetail) {
        questionOptionDao.deleteByQuestionId(localQuestionId)
        questionTagDao.deleteByQuestionId(localQuestionId)

        val orderedOptions = remoteDetail.options.sortedBy { it.sortOrder }
        if (orderedOptions.isNotEmpty()) {
            questionOptionDao.insertAll(
                orderedOptions.mapIndexed { index, option ->
                    QuestionOptionEntity(
                        questionId = localQuestionId,
                        content = option.content,
                        isCorrect = option.isCorrect,
                        sortOrder = index
                    )
                }
            )
        }

        if (remoteDetail.tags.isNotEmpty()) {
            val tagIds = remoteDetail.tags.map { tag ->
                tagDao.getByFullPath(tag.fullPath)?.id
                    ?: tagDao.insertAll(
                        listOf(
                            TagEntity(
                                name = tag.name,
                                fullPath = tag.fullPath,
                                sortOrder = tag.sortOrder
                            )
                        )
                    )[0]
            }
            questionTagDao.insertAll(tagIds.map { tagId -> QuestionTagCrossRef(localQuestionId, tagId) })
        }
    }

    private fun openRemoteBank(context: Context, downloaded: File): AppDatabase {
        val remoteVersion = readUserVersion(downloaded)
        if (remoteVersion != AppDatabase.SCHEMA_VERSION) {
            error("题库文件版本不兼容(需要 v${AppDatabase.SCHEMA_VERSION}，实际 v$remoteVersion)")
        }
        val dbFile = context.getDatabasePath("official_bank_remote.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        downloaded.copyTo(dbFile, overwrite = true)
        return Room.databaseBuilder(context, AppDatabase::class.java, "official_bank_remote.db")
            .build()
    }

    private fun readUserVersion(file: File): Int {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            return db.version
        }
    }

    private suspend fun fetchText(url: String): String {
        val bytes = fetchBytes(url)
        return String(bytes, Charsets.UTF_8)
    }

    private suspend fun fetchBytes(
        url: String,
        onBytes: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        try {
            val total = connection.contentLengthLong
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onBytes(downloaded, total)
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
