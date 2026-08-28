package com.hamster.review.data.repository

import androidx.room.withTransaction
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
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

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

    suspend fun getOrCreateSubjectDailyRecord(
        subjectId: Long,
        pushedCount: Int
    ): DailySubjectRecordEntity {
        val today = LocalDate.now().toString()
        val existing = dailySubjectRecordDao.get(subjectId, today)
        if (existing != null) return existing

        val record = DailySubjectRecordEntity(
            subjectId = subjectId,
            date = today,
            pushedCount = pushedCount,
            completedCount = 0,
            completed = false,
            completedAt = null
        )
        dailySubjectRecordDao.upsert(record)
        return record
    }

    suspend fun ensureDailySubjectQuestions(
        subjectId: Long,
        questions: List<QuestionDetail>
    ) {
        val today = LocalDate.now().toString()
        val existing = dailySubjectQuestionDao.getForDate(subjectId, today)
        if (existing.isNotEmpty()) return

        dailySubjectQuestionDao.insertAll(
            questions.map { question ->
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

    suspend fun getSubjectDailyQuestions(subjectId: Long): List<DailySubjectQuestionEntity> {
        val today = LocalDate.now().toString()
        return dailySubjectQuestionDao.getForDate(subjectId, today)
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
        subjectDao.updateDailyLimit(subjectId, newLimit)

        val today = LocalDate.now().toString()
        val record = dailySubjectRecordDao.get(subjectId, today) ?: return
        if (record.completed) return

        if (newLimit <= record.completedCount) {
            dailySubjectRecordDao.upsert(
                record.copy(
                    completed = true,
                    completedAt = System.currentTimeMillis()
                )
            )
            return
        }

        val existingQuestions = dailySubjectQuestionDao.getForDate(subjectId, today)
        val existingIds = existingQuestions.map { it.questionId }.toMutableSet()
        val need = newLimit - existingIds.size

        if (need > 0) {
            val allQuestionIds = questionDao.getSubjectQuestionIds(subjectId)
            val newIds = allQuestionIds
                .filterNot { it in existingIds }
                .take(need)

            val reusedIds = if (newIds.size < need) {
                existingIds.take(need - newIds.size)
            } else {
                emptyList()
            }

            val selectedIds = newIds + reusedIds
            if (selectedIds.isNotEmpty()) {
                dailySubjectQuestionDao.insertAll(
                    selectedIds.map { questionId ->
                        DailySubjectQuestionEntity(
                            subjectId = subjectId,
                            date = today,
                            questionId = questionId,
                            completed = false,
                            wrongPending = false
                        )
                    }
                )
                existingIds.addAll(selectedIds)
            }
        }

        dailySubjectRecordDao.upsert(
            record.copy(
                pushedCount = existingIds.size,
                completed = false,
                completedAt = null
            )
        )
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


    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeDueQuestionDetails(subjectId: Long): Flow<List<QuestionDetail>> {
        return subjectDao.observeDailyLimit(subjectId).flatMapLatest { limit ->
            questionDao.observeDueQuestionDetails(
                subjectId = subjectId,
                now = System.currentTimeMillis(),
                limit = limit
            )
        }
    }

    suspend fun getDueQuestionDetailsOnce(subjectId: Long): List<QuestionDetail> {
        val limit = subjectDao.getDailyLimit(subjectId)
        return questionDao.observeDueQuestionDetails(
            subjectId = subjectId,
            now = System.currentTimeMillis(),
            limit = limit
        ).first()
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

        val optionIds = if (options.isEmpty()) {
            emptyList()
        } else {
            questionOptionDao.insertAll(
                options.mapIndexed { index, (text, correct) ->
                    QuestionOptionEntity(
                        questionId = questionId,
                        content = text,
                        isCorrect = correct,
                        sortOrder = index
                    )
                }
            )
        }

        val tagIds = tags.map { tag ->
            val existing = tagDao.getByFullPath(tag)
            if (existing != null) {
                existing.id
            } else {
                tagDao.insertAll(listOf(TagEntity(name = tag, fullPath = tag)))[0]
            }
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
}
