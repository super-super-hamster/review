package com.hamster.review.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, id ASC")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query(
        """
        SELECT subjects.*,
               (SELECT COUNT(*) FROM questions WHERE subjectId = subjects.id) AS totalCount,
               CASE
                   WHEN EXISTS(
                       SELECT 1 FROM daily_subject_records d
                       WHERE d.subjectId = subjects.id
                         AND d.date = :today
                         AND d.completed = 1
                   ) THEN 0
                   WHEN EXISTS(
                       SELECT 1 FROM daily_subject_records d
                       WHERE d.subjectId = subjects.id
                         AND d.date = :today
                   ) THEN MAX(0, (
                       SELECT pushedCount - completedCount
                       FROM daily_subject_records
                       WHERE subjectId = subjects.id AND date = :today
                   ))
                   ELSE MIN((
                       SELECT COUNT(*)
                       FROM scheduler_state s
                       INNER JOIN questions q ON q.id = s.questionId
                       WHERE q.subjectId = subjects.id
                         AND q.mastered = 0
                         AND s.dueDate <= :now
                   ), subjects.dailyLimit)
               END AS todayCount
        FROM subjects
        ORDER BY subjects.sortOrder ASC, subjects.id ASC
        """
    )
    fun observeSubjectsWithTodayCount(
        now: Long,
        today: String
    ): Flow<List<SubjectWithTodayCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int

    @Query("SELECT dailyLimit FROM subjects WHERE id = :subjectId")
    suspend fun getDailyLimit(subjectId: Long): Int

    @Query("SELECT dailyLimit FROM subjects WHERE id = :subjectId")
    fun observeDailyLimit(subjectId: Long): Flow<Int>

    @Query("UPDATE subjects SET dailyLimit = :dailyLimit WHERE id = :subjectId")
    suspend fun updateDailyLimit(subjectId: Long, dailyLimit: Int)

}

@Dao
interface QuestionDao {
    @Transaction
    @Query(
        """
        SELECT q.*
        FROM questions q
        INNER JOIN scheduler_state s ON s.questionId = q.id
        WHERE q.subjectId = :subjectId
          AND q.mastered = 0
          AND s.dueDate <= :now
        ORDER BY s.dueDate ASC, s.retrievability ASC
        LIMIT :limit
        """
    )
    fun observeDueQuestionDetails(
        subjectId: Long,
        now: Long,
        limit: Int
    ): Flow<List<QuestionDetail>>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :questionId")
    fun observeQuestionDetail(questionId: Long): Flow<QuestionDetail?>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :questionId")
    suspend fun getQuestionDetail(questionId: Long): QuestionDetail?

    @Transaction
    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getQuestionDetailsByIds(ids: List<Long>): List<QuestionDetail>


    @Query(
        """
        SELECT responseTimeMs FROM review_logs
        WHERE questionId = :questionId
        ORDER BY reviewedAt DESC
        LIMIT 20
        """
    )
    suspend fun getRecentResponseTimes(questionId: Long): List<Long>

    @Query(
        """
        SELECT responseTimeMs FROM review_logs
        WHERE subjectId = :subjectId
        ORDER BY reviewedAt DESC
        LIMIT 200
        """
    )
    suspend fun getSubjectResponseTimes(subjectId: Long): List<Long>

    @Query(
        """
        SELECT responseTimeMs FROM review_logs
        WHERE questionId IN (
            SELECT id FROM questions WHERE type = :type
        )
        ORDER BY reviewedAt DESC
        LIMIT 200
        """
    )
    suspend fun getTypeResponseTimes(type: String): List<Long>

    @Query("UPDATE questions SET mastered = :mastered, masteredAt = :masteredAt WHERE id = :questionId")
    suspend fun updateMastered(questionId: Long, mastered: Boolean, masteredAt: Long?)

    @Query("SELECT COUNT(*) FROM questions WHERE subjectId = :subjectId AND mastered = 0")
    fun observeUnmasteredCount(subjectId: Long): Flow<Int>

    @Query("SELECT id FROM questions WHERE subjectId = :subjectId ORDER BY id ASC")
    suspend fun getSubjectQuestionIds(subjectId: Long): List<Long>


    @Query("UPDATE questions SET content = :content WHERE id = :questionId")
    suspend fun updateQuestionContent(questionId: Long, content: String)

    @Query("UPDATE questions SET answer = :answer WHERE id = :questionId")
    suspend fun updateQuestionAnswer(questionId: Long, answer: String?)

    @Query("UPDATE questions SET explanation = :explanation WHERE id = :questionId")
    suspend fun updateQuestionExplanation(questionId: Long, explanation: String)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestion(questionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>
}

@Dao
interface SchedulerStateDao {
    @Query("SELECT * FROM scheduler_state WHERE questionId = :questionId")
    fun observeSchedulerState(questionId: Long): Flow<SchedulerStateEntity?>

    @Query("SELECT * FROM scheduler_state WHERE questionId = :questionId")
    suspend fun getSchedulerState(questionId: Long): SchedulerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SchedulerStateEntity)
}

@Dao
interface ReviewLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReviewLogEntity)

    @Query(
        """
        SELECT * FROM review_logs
        WHERE questionId = :questionId
        ORDER BY reviewedAt DESC
        LIMIT 20
        """
    )
    suspend fun getRecentLogs(questionId: Long): List<ReviewLogEntity>

    @Query("SELECT * FROM daily_records WHERE date = :date")
    suspend fun getDailyRecordEntity(date: String): DailyRecordEntity?

    @Query(
        """
        SELECT date(reviewedAt / 1000, 'unixepoch', 'localtime') AS date,
               COUNT(*) AS reviewCount,
               SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correctCount,
               COUNT(DISTINCT questionId) AS uniqueQuestionCount
        FROM review_logs
        WHERE date(reviewedAt / 1000, 'unixepoch', 'localtime') = :date
        GROUP BY date
        """
    )
    suspend fun getAggregatedDailyRecord(date: String): DailyRecordEntity?

    @Query(
        """
        SELECT date(reviewedAt / 1000, 'unixepoch', 'localtime') AS date,
               COUNT(*) AS reviewCount,
               SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correctCount,
               COUNT(DISTINCT questionId) AS uniqueQuestionCount
        FROM review_logs
        WHERE date(reviewedAt / 1000, 'unixepoch', 'localtime') >= :startDate
          AND date(reviewedAt / 1000, 'unixepoch', 'localtime') <= :endDate
        GROUP BY date
        """
    )
    fun observeDailyRecords(startDate: String, endDate: String): Flow<List<DailyRecordEntity>>

    @Query(
        """
        SELECT date(reviewedAt / 1000, 'unixepoch', 'localtime') AS date,
               COUNT(*) AS reviewCount,
               SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correctCount,
               COUNT(DISTINCT questionId) AS uniqueQuestionCount
        FROM review_logs
        WHERE subjectId = :subjectId
          AND date(reviewedAt / 1000, 'unixepoch', 'localtime') >= :startDate
          AND date(reviewedAt / 1000, 'unixepoch', 'localtime') <= :endDate
        GROUP BY date
        """
    )
    fun observeSubjectDailyRecords(
        subjectId: Long,
        startDate: String,
        endDate: String
    ): Flow<List<DailyRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyRecord(record: DailyRecordEntity)

    @Query("DELETE FROM review_logs WHERE reviewedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface DailySubjectRecordDao {
    @Query(
        """
        SELECT * FROM daily_subject_records
        WHERE subjectId = :subjectId AND date = :date
        LIMIT 1
        """
    )
    suspend fun get(subjectId: Long, date: String): DailySubjectRecordEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM daily_subject_records
            WHERE subjectId = :subjectId AND date = :date AND completed = 1
        )
        """
    )
    fun observeCompleted(subjectId: Long, date: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailySubjectRecordEntity)
}

@Dao
interface DailySubjectQuestionDao {
    @Query(
        """
        SELECT * FROM daily_subject_questions
        WHERE subjectId = :subjectId AND date = :date
        """
    )
    suspend fun getForDate(subjectId: Long, date: String): List<DailySubjectQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<DailySubjectQuestionEntity>)

    @Query(
        """
        UPDATE daily_subject_questions
        SET completed = :completed, wrongPending = :wrongPending
        WHERE subjectId = :subjectId AND date = :date AND questionId = :questionId
        """
    )
    suspend fun updateStatus(
        subjectId: Long,
        date: String,
        questionId: Long,
        completed: Boolean,
        wrongPending: Boolean
    )
}


@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE fullPath = :fullPath LIMIT 1")
    suspend fun getByFullPath(fullPath: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>
}

@Dao
interface QuestionTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<QuestionTagCrossRef>)
}

@Dao
interface QuestionOptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(options: List<QuestionOptionEntity>): List<Long>

    @Query("UPDATE question_options SET content = :content WHERE id = :optionId")
    suspend fun updateContent(optionId: Long, content: String)

    @Query("UPDATE question_options SET isCorrect = :isCorrect WHERE id = :optionId")
    suspend fun updateCorrect(optionId: Long, isCorrect: Boolean)
}
