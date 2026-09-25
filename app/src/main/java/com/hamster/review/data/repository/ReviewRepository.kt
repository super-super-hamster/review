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
import kotlin.math.min
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

    /** 取某科目全部已掌握题目（用于"已掌握题目测试"，随机顺序由调用方决定）。 */
    suspend fun getMasteredQuestionsOnce(subjectId: Long): List<QuestionDetail> {
        return questionDao.getMasteredQuestionDetails(subjectId)
    }

    /** 取某科目全部题目（含选项/标签），用于"管理题目"列表，按 id 升序。 */
    suspend fun getSubjectQuestionDetails(subjectId: Long): List<QuestionDetail> {
        return questionDao.getSubjectQuestionDetails(subjectId)
    }

    /** 取单道题目（含选项/标签），用于编辑页初始化。 */
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
     * 从 GitHub Release 更新官方题库，并把官方题合并进本地库。
     *
     * @param onProgress 进度回调：(当前操作文本, 进度 0f..1f, 是否允许取消)
     *                   写入本地库的事务阶段 cancelable = false。
     * @return 结果文本（成功 / 已是最新 / 更新失败原因）
     */
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

        // 下载阶段(10% -> 70%)：按已下载字节平滑推进，每块检查取消
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
                // 写入阶段(85% -> 99%)：事务内不响应取消
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

    /**
     * 把远端官方题库合并进本地库（单个事务）：
     * - 科目按名称匹配，远端新科目会新建，远端已删除的科目本地保留；
     * - 官方题按 officialId 对齐：新增 / 覆盖题型·题干·答案·选项 / 删除远端已移除的官方题；
     * - 备注(explanation)：本地已有内容时保留，本地为空才用远端内容；
     * - 用户自建题(officialId 为空)完全不动；题目行 id 不变，因此掌握状态与复习进度保留。
     */
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

            // 远端已删除的官方题：本地一并删除（级联清理选项/调度/日志/当天推送行）
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
                        // 新增官方题
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
                        // 已有官方题：覆盖题型/题干/答案/选项；备注仅本地为空时更新
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

    /** 用远端内容重建某题的选项与标签关联。 */
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

    /**
     * 把下载的官方库复制成普通 Room 库再打开。
     * 已移除所有数据库迁移：因此要求远端题库文件必须是当前 schema 版本(v6)，
     * 版本不符直接抛错（由调用方转成"更新失败"提示），绝不使用清库回退，避免误判为空库后清空本地官方题。
     */
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

    /** 读取 sqlite 文件的 user_version（不修改文件）。 */
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

    /**
     * 下载为字节数组。分块读取：每块回调已下载/总字节数，并检查协程取消，便于显示进度与随时终止。
     */
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
