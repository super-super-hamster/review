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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
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

data class BankUpdateResult(val added: Int, val updated: Int, val removed: Int)

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
        val today = LocalDate.now().toString()
        return subjectDao.observeSubjectsWithTodayCount(
            now = System.currentTimeMillis(),
            today = today
        )
    }

    /**
     * 新增科目：名称会去除首尾空白，空白名或重名返回 false。
     * 新科目排到所有科目之后，每日题目数量使用默认值。
     */
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

    /**
     * 删除科目（其下的题目、错题/复习状态、当日本地记录等通过外键级联删除）。
     */
    suspend fun deleteSubject(subjectId: Long) {
        db.withTransaction {
            subjectDao.deleteById(subjectId)
        }
    }

    /**
     * 新增用户自建题目：插入题目与选项，并为新题建立 NEW 调度状态(使其进入每日补新题池)。
     * @return 新题目 id
     */
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

    fun observeCurrentMonthDailyRecords(): Flow<List<DailyRecordEntity>> {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).toString()
        val end = today.toString()
        return reviewLogDao.observeDailyRecords(start, end)
    }

    fun observeSubjectCurrentMonthDailyRecords(subjectId: Long): Flow<List<DailyRecordEntity>> {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).toString()
        val end = today.toString()
        return reviewLogDao.observeSubjectDailyRecords(subjectId, start, end)
    }

    fun observeSubjectCompletedToday(subjectId: Long): Flow<Boolean> {
        val today = LocalDate.now().toString()
        return dailySubjectRecordDao.observeCompleted(subjectId, today)
    }

    suspend fun isSubjectCompletedToday(subjectId: Long): Boolean {
        val today = LocalDate.now().toString()
        return dailySubjectRecordDao.get(subjectId, today)?.completed == true
    }

    /**
     * 确保今天（date）的题目池已生成。
     * 仅在当天还没有任何推送行且未标记完成时执行：
     * 数量 = min(dailyLimit, 未掌握题数)，优先到期题，不足补新题（不做重复推送）。
     */
    suspend fun prepareTodayPool(subjectId: Long) {
        val today = LocalDate.now().toString()
        if (dailySubjectQuestionDao.getForDate(subjectId, today).isNotEmpty()) return
        val record = dailySubjectRecordDao.get(subjectId, today)
        if (record?.completed == true) return

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

    /**
     * 按「到期题 -> 新题」的顺序挑选 [limit] 道未掌握、且不在 [excludeQuestionIds] 中的题。
     */
    private suspend fun selectPoolQuestions(
        subjectId: Long,
        limit: Int,
        excludeQuestionIds: Set<Long>
    ): List<QuestionDetail> {
        if (limit <= 0) return emptyList()
        val now = System.currentTimeMillis()
        val seen = HashSet<Long>()
        seen.addAll(excludeQuestionIds)
        val due = questionDao.getDuePoolQuestions(subjectId, now)
        val newQuestions = questionDao.getNewPoolQuestions(subjectId)
        return (due + newQuestions)
            .filter { seen.add(it.question.id) }
            .take(limit)
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
            // 当天还没开始过，按首次进入生成一组
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

    /** 当天是否还有没刷过的可推候选（到期题/新题且未掌握）。 */
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
        val existing = dailySubjectRecordDao.get(subjectId, today)
        dailySubjectRecordDao.upsert(
            DailySubjectRecordEntity(
                subjectId = subjectId,
                date = today,
                pushedCount = existing?.pushedCount ?: 0,
                completedCount = existing?.completedCount ?: existing?.pushedCount ?: 0,
                completed = true,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSubjectDailyLimit(subjectId: Long): Int {
        return subjectDao.getDailyLimit(subjectId)
    }

    suspend fun setSubjectDailyLimit(subjectId: Long, newLimit: Int) {
        val today = LocalDate.now().toString()

        // 今天还没生成过题目池：只更新每日数量，下次进入时按新数量生成
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
        // 重新计算当天队列：剩余待完成数 = 新数量 - 已完成数
        val targetPending = (newLimit - completedCount).coerceAtLeast(0)
        val surplus = pendingRows.size - targetPending

        val removeIds = if (surplus > 0) {
            // 清掉不再需要的多余待做题
            pendingRows.takeLast(surplus).map { it.questionId }
        } else {
            emptyList()
        }
        val extra = if (surplus < 0) {
            // 数量调大：从 到期/新题 补足，已推过的当天不再重复推
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

    suspend fun cleanupOldReviewLogs(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        reviewLogDao.deleteOlderThan(cutoff)
    }

    fun observeQuestionDetail(questionId: Long): Flow<QuestionDetail?> {
        return questionDao.observeQuestionDetail(questionId)
    }

    fun observeSchedulerState(questionId: Long): Flow<SchedulerStateEntity?> {
        return schedulerDao.observeSchedulerState(questionId)
    }

    suspend fun submitAnswer(
        questionId: Long,
        isCorrect: Boolean,
        responseTimeMs: Long
    ): Result<Unit> = runCatching {
        val question = questionDao.getQuestionDetail(questionId)?.question
            ?: error("Question not found: $questionId")

        val baseline = calculateBaseline(
            questionId = questionId,
            subjectId = question.subjectId,
            type = question.type
        )
        val rating = mapToRating(isCorrect, responseTimeMs, baseline)

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

    /**
     * Fallback seeder used when the prebuilt asset .db is not present.
     * Once a real default_questions.db is packaged, this method becomes a no-op
     * because subjects already exist.
     */
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

    // ---------------- 官方题库同步 ----------------

    /**
     * 用远端官方题库(完整快照)覆盖本地官方题：
     * - 科目按名称对齐：已存在则沿用本地科目，不存在则新增；
     * - 题目按 officialId 对齐：本地已有 -> 完整覆盖题面/题型/科目/解析/选项/标签
     *   (保留本地 mastered 标记与调度/日志/每日行)；本地没有 -> 新增；
     *   远端已删除 -> 删除本地对应官方题；
     * - officialId 为 null 的用户自建题目完全不受影响。
     */
    suspend fun applyOfficialQuestionBank(
        remoteSubjects: List<SubjectEntity>,
        remoteQuestions: List<QuestionDetail>
    ): BankUpdateResult {
        var added = 0
        var updated = 0
        var removed = 0
        db.withTransaction {
            val localSubjectsByName = subjectDao.getAll().associateBy { it.name }
            val remoteSubjectById = remoteSubjects.associateBy { it.id }
            val oldOfficial = questionDao.getAllOfficial().associateBy { it.officialId ?: it.id }
            val keepLocalIds = HashSet<Long>()
            val now = System.currentTimeMillis()

            suspend fun ensureLocalSubjectId(remoteSubject: SubjectEntity): Long {
                localSubjectsByName[remoteSubject.name]?.let { return it.id }
                val ids = subjectDao.insertAll(listOf(remoteSubject.copy(id = 0L)))
                return ids[0]
            }

            suspend fun insertOptionsFor(questionId: Long, options: List<QuestionOptionEntity>) {
                if (options.isEmpty()) return
                questionOptionDao.insertAll(options.map { it.copy(id = 0L, questionId = questionId) })
            }

            suspend fun ensureTagsFor(questionId: Long, tags: List<TagEntity>) {
                if (tags.isEmpty()) return
                val tagIds = tags.map { tag ->
                    tagDao.getByFullPath(tag.fullPath)?.id
                        ?: tagDao.insertAll(
                            listOf(
                                TagEntity(
                                    name = tag.name,
                                    parentId = tag.parentId,
                                    fullPath = tag.fullPath,
                                    sortOrder = tag.sortOrder
                                )
                            )
                        )[0]
                }
                questionTagDao.insertAll(tagIds.map { QuestionTagCrossRef(questionId, it) })
            }

            for (detail in remoteQuestions) {
                val remote = detail.question
                val officialId = remote.officialId ?: remote.id
                val remoteSubject = remoteSubjectById[remote.subjectId] ?: continue
                val localSubjectId = ensureLocalSubjectId(remoteSubject)

                val local = oldOfficial[officialId]
                if (local == null) {
                    val ids = questionDao.insertAll(
                        listOf(
                            QuestionEntity(
                                subjectId = localSubjectId,
                                officialId = officialId,
                                type = remote.type,
                                content = remote.content,
                                answer = remote.answer,
                                explanation = remote.explanation,
                                createdAt = now
                            )
                        )
                    )
                    val newId = ids[0]
                    insertOptionsFor(newId, detail.options)
                    ensureTagsFor(newId, detail.tags)
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
                    added++
                    keepLocalIds.add(newId)
                } else {
                    questionDao.updateQuestionFields(
                        id = local.id,
                        subjectId = localSubjectId,
                        type = remote.type.name,
                        content = remote.content,
                        answer = remote.answer,
                        explanation = remote.explanation
                    )
                    questionOptionDao.deleteByQuestionId(local.id)
                    insertOptionsFor(local.id, detail.options)
                    questionTagDao.deleteByQuestionId(local.id)
                    ensureTagsFor(local.id, detail.tags)
                    updated++
                    keepLocalIds.add(local.id)
                }
            }

            // 远端已删除的官方题 -> 本地一并删除(调度/日志/每日行经外键级联删除)
            for (local in oldOfficial.values) {
                if (local.id !in keepLocalIds) {
                    questionDao.deleteQuestion(local.id)
                    removed++
                }
            }
        }
        return BankUpdateResult(added, updated, removed)
    }

    /**
     * 手动更新：拉取 GitHub Release 上的官方题库并应用(见 [applyOfficialQuestionBank])。
     * 返回给用户看的提示文本；任何失败都会以「更新失败：…」返回，不会改动本地库。
     */
    suspend fun updateOfficialBankFromGitHub(context: Context): String = try {
        withContext(Dispatchers.IO) { doFetchAndApplyOfficialBank(context) }
    } catch (e: Exception) {
        "更新失败：${e.message ?: e.javaClass.simpleName}"
    }

    private suspend fun doFetchAndApplyOfficialBank(context: Context): String {
        val metaText = fetchText("$QUESTION_BANK_RELEASE_BASE/questionbank.version.json")
        val meta = JSONObject(metaText)
        val version = meta.getInt("version")
        val sha = meta.optString("sha256")
        val fileName = meta.optString("file", "questionbank.db")

        val prefs = context.getSharedPreferences(QUESTION_BANK_PREFS, Context.MODE_PRIVATE)
        val localVersion = prefs.getInt(QUESTION_BANK_VERSION_KEY, 0)
        val localSha = prefs.getString(QUESTION_BANK_SHA_KEY, "") ?: ""
        if (version < localVersion || (version == localVersion && localSha.equals(sha, ignoreCase = true))) {
            return "官方题库已是最新(版本 $localVersion)"
        }

        val bytes = fetchBytes("$QUESTION_BANK_RELEASE_BASE/$fileName")
        if (sha.isNotBlank() && !sha256Hex(bytes).equals(sha, ignoreCase = true)) {
            return "更新失败：下载文件校验失败(sha256 不一致)，已保留原题库"
        }

        val cache = File(context.cacheDir, "official_bank_$version.db")
        cache.writeBytes(bytes)
        try {
            val remote = openRemoteBank(context, cache)
            try {
                val subjects = remote.subjectDao().getAll()
                val details = remote.questionDao().getAllQuestionDetails()
                val result = applyOfficialQuestionBank(subjects, details)
                prefs.edit {
                    putInt(QUESTION_BANK_VERSION_KEY, version)
                        .putString(QUESTION_BANK_SHA_KEY, sha)
                }
                return "官方题库更新成功(版本 $version)：新增 ${result.added} 道、更新 ${result.updated} 道、移除 ${result.removed} 道"
            } finally {
                remote.close()
            }
        } finally {
            cache.delete()
        }
    }

    /** 把下载的官方库复制成普通 Room 库再打开(兼容旧版 v5 库：迁移后读取)。 */
    private fun openRemoteBank(context: Context, downloaded: File): AppDatabase {
        val dbFile = context.getDatabasePath("official_bank_remote.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        downloaded.copyTo(dbFile, overwrite = true)
        return Room.databaseBuilder(context, AppDatabase::class.java, "official_bank_remote.db")
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    private suspend fun fetchText(url: String): String {
        val bytes = fetchBytes(url)
        return String(bytes, Charsets.UTF_8)
    }

    private suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
