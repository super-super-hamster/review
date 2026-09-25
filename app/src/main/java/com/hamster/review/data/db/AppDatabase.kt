package com.hamster.review.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = AppDatabase.SCHEMA_VERSION,
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
        /** 当前 schema 版本。已无迁移逻辑，内置 asset 库与远端官方题库文件都必须等于该版本。 */
        const val SCHEMA_VERSION = 6

        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                    // 该 asset 库必须与当前 schema 版本(6)一致。
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
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
