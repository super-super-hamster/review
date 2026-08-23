package com.hamster.review.data.db

import androidx.room.TypeConverter
import com.hamster.review.data.model.CardState
import com.hamster.review.data.model.FsrsRating
import com.hamster.review.data.model.QuestionType

class Converters {
    @TypeConverter
    fun questionTypeToString(value: QuestionType): String = value.name

    @TypeConverter
    fun stringToQuestionType(value: String): QuestionType = QuestionType.valueOf(value)

    @TypeConverter
    fun fsrsRatingToString(value: FsrsRating): String = value.name

    @TypeConverter
    fun stringToFsrsRating(value: String): FsrsRating = FsrsRating.valueOf(value)

    @TypeConverter
    fun cardStateToString(value: CardState): String = value.name

    @TypeConverter
    fun stringToCardState(value: String): CardState = CardState.valueOf(value)
}
