package com.hamster.review.data.model

enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    FILL_BLANK
}

enum class FsrsRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}

enum class CardState {
    NEW,
    LEARNING,
    REVIEW
}

const val DEFAULT_RETENTION = 0.9
const val DAILY_REVIEW_LIMIT_PER_SUBJECT = 10
