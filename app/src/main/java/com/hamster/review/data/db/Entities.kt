package com.hamster.review.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.hamster.review.data.model.CardState
import com.hamster.review.data.model.FsrsRating
import com.hamster.review.data.model.QuestionType

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "10") val dailyLimit: Int = 10
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["mastered"]),
        Index(value = ["subjectId", "mastered"])
    ]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val type: QuestionType,
    val content: String,
    val answer: String? = null,
    val explanation: String = "",
    val mastered: Boolean = false,
    val masteredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "question_options",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["questionId"])]
)
data class QuestionOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val content: String,
    val isCorrect: Boolean,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["fullPath"], unique = true),
        Index(value = ["parentId"])
    ]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val fullPath: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "question_tags",
    primaryKeys = ["questionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["questionId"]),
        Index(value = ["tagId"])
    ]
)
data class QuestionTagCrossRef(
    val questionId: Long,
    val tagId: Long
)

@Entity(
    tableName = "scheduler_state",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["dueDate"]),
        Index(value = ["state"])
    ]
)
data class SchedulerStateEntity(
    @PrimaryKey val questionId: Long,
    val state: CardState = CardState.NEW,
    val dueDate: Long = System.currentTimeMillis(),
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val retrievability: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewAt: Long? = null
)

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["questionId"]),
        Index(value = ["reviewedAt"]),
        Index(value = ["subjectId"])
    ]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val subjectId: Long,
    val reviewedAt: Long,
    val rating: FsrsRating,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val intervalDays: Double,
    val retrievability: Double
)

@Entity(
    tableName = "daily_subject_records",
    primaryKeys = ["subjectId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId"]), Index(value = ["date"])]
)
data class DailySubjectRecordEntity(
    val subjectId: Long,
    val date: String,
    @ColumnInfo(defaultValue = "0") val pushedCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val completedCount: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null
)

@Entity(
    tableName = "daily_subject_questions",
    primaryKeys = ["subjectId", "date", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["date"]),
        Index(value = ["questionId"])
    ]
)
data class DailySubjectQuestionEntity(
    val subjectId: Long,
    val date: String,
    val questionId: Long,
    val completed: Boolean = false,
    val wrongPending: Boolean = false
)

@Entity(tableName = "daily_records")
data class DailyRecordEntity(
    @PrimaryKey val date: String,
    val reviewCount: Int,
    val correctCount: Int,
    val uniqueQuestionCount: Int
)

data class QuestionWithOptions(
    @Embedded val question: QuestionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "questionId"
    )
    val options: List<QuestionOptionEntity>
)

data class QuestionWithTags(
    @Embedded val question: QuestionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = QuestionTagCrossRef::class,
            parentColumn = "questionId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

data class QuestionDetail(
    @Embedded val question: QuestionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "questionId"
    )
    val options: List<QuestionOptionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = QuestionTagCrossRef::class,
            parentColumn = "questionId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

data class SubjectWithTodayCount(
    @Embedded val subject: SubjectEntity,
    val todayCount: Int = 0,
    val totalCount: Int = 0
)
