package com.hamster.review.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class,
        QuestionEntity::class,
        QuestionOptionEntity::class,
        TagEntity::class,
        QuestionTagCrossRef::class,
        SchedulerStateEntity::class,
        ReviewLogEntity::class,
        DailySubjectRecordEntity::class,
        DailySubjectQuestionEntity::class,
        DailyRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subjectDao(): SubjectDao
    abstract fun questionDao(): QuestionDao
    abstract fun schedulerStateDao(): SchedulerStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun tagDao(): TagDao
    abstract fun questionTagDao(): QuestionTagDao
    abstract fun questionOptionDao(): QuestionOptionDao
    abstract fun dailySubjectRecordDao(): DailySubjectRecordDao
    abstract fun dailySubjectQuestionDao(): DailySubjectQuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_subject_records` (
                        `subjectId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`subjectId`, `date`),
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_subject_records_subjectId` ON `daily_subject_records`(`subjectId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_subject_records_date` ON `daily_subject_records`(`date`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `daily_subject_records` ADD COLUMN `pushedCount` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `daily_subject_records` ADD COLUMN `completedCount` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_subject_questions` (
                        `subjectId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `questionId` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `wrongPending` INTEGER NOT NULL,
                        PRIMARY KEY(`subjectId`, `date`, `questionId`),
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`questionId`) REFERENCES `questions`(`id`) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_subject_questions_subjectId` ON `daily_subject_questions`(`subjectId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_subject_questions_date` ON `daily_subject_questions`(`date`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_subject_questions_questionId` ON `daily_subject_questions`(`questionId`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subjects` ADD COLUMN `dailyLimit` INTEGER NOT NULL DEFAULT 10"
                )
            }
        }





        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val builder = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        "review.db"
                    )

                    // 预置题库 .db 存在时才使用；不存在时先建空库，避免首次启动崩溃。
                    val hasDefaultDb = try {
                        appContext.assets.open("databases/default_questions.db").close()
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (hasDefaultDb) {
                        builder.createFromAsset("databases/default_questions.db")
                    }

                    builder
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
